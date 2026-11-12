package com.example.api_tp.service.arca;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.api_tp.client.AfipWsaaClient;
import com.example.api_tp.client.AfipWsfeClient;
import com.example.api_tp.client.arca.exception.ArcaCommunicationException;
import com.example.api_tp.client.arca.exception.ArcaException;
import com.example.api_tp.client.arca.wsaa.WsaaTicket;
import com.example.api_tp.client.arca.wsfe.CompConsultarResultado;
import com.example.api_tp.client.arca.wsfe.CotizacionArca;
import com.example.api_tp.client.arca.wsfe.FeCaeDetalle;
import com.example.api_tp.client.arca.wsfe.FeCaeResultado;
import com.example.api_tp.client.arca.wsfe.FeCaeSolicitud;
import com.example.api_tp.client.arca.wsfe.ParametroArca;
import com.example.api_tp.client.arca.wsfe.PuntoVentaArca;
import com.example.api_tp.client.arca.wsfe.UltimoAutorizadoResult;
import com.example.api_tp.client.arca.wsfe.WsfeMessage;
import com.example.api_tp.model.FacturaVenta;
import com.example.api_tp.model.FacturaVentaDetalle;


@Service
public class ArcaFacturaElectronicaService {

    private static final DateTimeFormatter FECHA_ARCA = DateTimeFormatter.ofPattern("yyyyMMdd");
   

    private final AfipWsaaClient wsaaClient;
    private final AfipWsfeClient wsfeClient;

    public ArcaFacturaElectronicaService(AfipWsaaClient wsaaClient, AfipWsfeClient wsfeClient) {
        this.wsaaClient = wsaaClient;
        this.wsfeClient = wsfeClient;
    }

   
    public WsaaTicket obtenerOReutilizarTicketAcceso() {
        return wsaaClient.obtenerTicketAcceso();
    }

    public UltimoAutorizadoResult consultarUltimoComprobanteAutorizado(int puntoVenta, int tipoComprobante) {
        UltimoAutorizadoResult result = wsfeClient.feCompUltimoAutorizado(puntoVenta, tipoComprobante);
        if (result.tieneErrores()) {
            throw new ArcaException("WSFEv1 FECompUltimoAutorizado devolvió errores: " + resumir(result.getErrors()));
        }
        return result;
    }

    /** Catálogo de alícuotas admitidas. Se consulta una vez por lote de envío. */
    public List<ParametroArca> obtenerTiposIva() {
        return wsfeClient.feParamGetTiposIva();
    }

    /** Puntos de venta habilitados en ARCA. Se consulta una vez por lote de envío. */
    public List<PuntoVentaArca> obtenerPuntosVenta() {
        return wsfeClient.feParamGetPtosVenta();
    }

    /** Cotización oficial de la moneda informada. */
    public CotizacionArca obtenerCotizacion(String monId) {
        return wsfeClient.feParamGetCotizacion(monId);
    }


    public ArcaComprobanteDatos construirDatosFiscalesBase(FacturaVenta factura, List<ParametroArca> tiposIva) {
        if (factura == null) {
            throw new IllegalArgumentException("La factura es obligatoria");
        }
        if (tiposIva == null || tiposIva.isEmpty()) {
            throw new ArcaException("No se pudo validar el IVA: FEParamGetTiposIva no devolvió alícuotas");
        }
        ArcaComprobanteDatos datos = new ArcaComprobanteDatos();
        if (factura.getPuntoVenta() != null) {
            datos.setPuntoVenta(factura.getPuntoVenta().getNumero());
        }
        if (factura.getTipoMoneda() != null && StringUtils.hasText(factura.getTipoMoneda().getCodigoAfip())) {
            datos.setMonId(factura.getTipoMoneda().getCodigoAfip().trim());
        }
        datos.setMonCotiz(BigDecimal.ONE);
        datos.setCbteFch(formatearFechaArca(factura.getFechaEmision()));

        BigDecimal neto = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        java.util.Map<Integer, BigDecimal[]> ivaPorAlicuota = new java.util.LinkedHashMap<>();
        if (factura.getDetalles() != null) {
            for (FacturaVentaDetalle d : factura.getDetalles()) {
                BigDecimal impNeto = BigDecimal.valueOf(d.getImporteNeto());
                BigDecimal impIva = BigDecimal.valueOf(d.getImporteIva());
                neto = neto.add(impNeto);
                iva = iva.add(impIva);
                int alicuotaId = resolverAlicuotaIvaId(BigDecimal.valueOf(d.getPorcentajeIva()), tiposIva);
                ivaPorAlicuota.computeIfAbsent(alicuotaId, k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
                BigDecimal[] acum = ivaPorAlicuota.get(alicuotaId);
                acum[0] = acum[0].add(impNeto);
                acum[1] = acum[1].add(impIva);
            }
        }
        datos.setImpNeto(redondear(neto));
        datos.setImpIva(redondear(iva));
        BigDecimal totalCalculado = redondear(neto.add(iva));
        if (totalCalculado.subtract(redondear(BigDecimal.valueOf(factura.getImporteTotal()))).abs()
                .compareTo(new BigDecimal("0.01")) > 0) {
            throw new ArcaException("Los importes de la factura no son coherentes: neto + IVA = "
                    + totalCalculado + " pero el total persistido es " + factura.getImporteTotal());
        }
        datos.setImpTotal(totalCalculado);
        datos.setImpTotConc(BigDecimal.ZERO);
        datos.setImpOpEx(BigDecimal.ZERO);
        datos.setImpTrib(BigDecimal.ZERO);
        for (Map.Entry<Integer, BigDecimal[]> e : ivaPorAlicuota.entrySet()) {
            datos.getIva().add(new ArcaComprobanteDatos.AlicuotaIva(e.getKey(), redondear(e.getValue()[0]), redondear(e.getValue()[1])));
        }
        return datos;
    }


    public FeCaeSolicitud construirSolicitudFeCae(ArcaComprobanteDatos datos) {
        if (datos == null) {
            throw new IllegalArgumentException("Los datos fiscales son obligatorios");
        }
        FeCaeSolicitud solicitud = new FeCaeSolicitud();
        solicitud.setPuntoVenta(datos.getPuntoVenta());
        solicitud.setTipoComprobante(datos.getTipoComprobante());
        FeCaeDetalle det = new FeCaeDetalle();
        det.setConcepto(datos.getConcepto());
        det.setDocTipo(datos.getDocTipo());
        det.setDocNro(datos.getDocNro());
        det.setCbteDesde(datos.getCbteDesde());
        det.setCbteHasta(datos.getCbteHasta());
        det.setCbteFch(datos.getCbteFch());
        det.setImpTotal(redondear(datos.getImpTotal()));
        det.setImpTotConc(redondear(nvl(datos.getImpTotConc())));
        det.setImpNeto(redondear(datos.getImpNeto()));
        det.setImpOpEx(redondear(nvl(datos.getImpOpEx())));
        det.setImpIva(redondear(datos.getImpIva()));
        det.setImpTrib(redondear(nvl(datos.getImpTrib())));
        det.setMonId(datos.getMonId());
        det.setMonCotiz(datos.getMonCotiz() == null ? BigDecimal.ONE : datos.getMonCotiz());
        det.setCondicionIvaReceptorId(datos.getCondicionIvaReceptorId());
        det.setFchServDesde(datos.getFchServDesde());
        det.setFchServHasta(datos.getFchServHasta());
        det.setFchVtoPago(datos.getFchVtoPago());
        if (datos.getIva() != null) {
            for (ArcaComprobanteDatos.AlicuotaIva a : datos.getIva()) {
                det.getIva().add(new FeCaeDetalle.AlicuotaIva(a.getId(), redondear(a.getBaseImp()), redondear(a.getImporte())));
            }
        }
        solicitud.addComprobante(det);
        return solicitud;
    }

   
    public ArcaEmisionResultado enviarSolicitudCae(FeCaeSolicitud solicitud) {
        try {
            FeCaeResultado respuesta = wsfeClient.feCaeSolicitar(solicitud);
            return interpretarResultadoCae(respuesta, solicitud);
        } catch (ArcaCommunicationException ex) {
         
            if (ex.isTimeout() || ex.getCause() instanceof java.io.IOException || ex.getHttpStatus() == null) {
                return resultadoIncierto("Comunicación incierta con WSFEv1: " + ex.getMessage(), ex);
            }
          
            throw ex;
        }
    }

   
    public ArcaEmisionResultado consultarComprobanteParaConciliacion(int tipoComprobante, int puntoVenta, long numeroComprobante) {
        try {
            CompConsultarResultado r = wsfeClient.feCompConsultar(tipoComprobante, puntoVenta, numeroComprobante);
            return interpretarConsulta(r, puntoVenta, tipoComprobante, numeroComprobante);
        } catch (ArcaCommunicationException ex) {
            return resultadoIncierto("No se pudo consultar comprobante en WSFEv1: " + ex.getMessage(), ex);
        }
    }

    ArcaEmisionResultado interpretarResultadoCae(FeCaeResultado respuesta, FeCaeSolicitud solicitud) {
        ArcaEmisionResultado out = new ArcaEmisionResultado();
        out.setResultadoGlobal(respuesta.getResultadoGlobal());
        out.getErrores().addAll(respuesta.getErrors());
        out.getEventos().addAll(respuesta.getEvents());
        if (solicitud != null) {
            out.setPuntoVenta(solicitud.getPuntoVenta());
            out.setTipoComprobante(solicitud.getTipoComprobante());
        }
        FeCaeResultado.FeCaeDetalleResultado det = respuesta.getDetalles().isEmpty() ? null : respuesta.getDetalles().get(0);
        if (det != null) {
            out.setResultadoDetalle(det.getResultado());
            out.setCae(det.getCae());
            out.setCaeFechaVencimiento(det.getCaeFchVto());
            out.setCbteDesde(det.getCbteDesde());
            out.setCbteHasta(det.getCbteHasta());
            out.getObservaciones().addAll(det.getObservaciones());
        }
        boolean aprobado = det != null && det.aprobado() && StringUtils.hasText(det.getCae());
        boolean rechazado = (det != null && "R".equalsIgnoreCase(det.getResultado())) || respuesta.esRechazadoGlobal();
        if (aprobado) {
            out.setEstado(ArcaEmisionResultado.Estado.APROBADA);
            out.setMensaje("Comprobante autorizado por ARCA");
        } else if (rechazado) {
            out.setEstado(ArcaEmisionResultado.Estado.RECHAZADA);
            out.setMensaje(resumirMensajes(out));
        } else {
      
            out.setEstado(ArcaEmisionResultado.Estado.INCIERTO);
            out.setMensaje("Respuesta WSFEv1 no concluyente; se requiere conciliación por FECompConsultar");
        }
        return out;
    }

    ArcaEmisionResultado interpretarConsulta(CompConsultarResultado r, int puntoVenta, int tipoComprobante, long cbteNro) {
        ArcaEmisionResultado out = new ArcaEmisionResultado();
        out.setPuntoVenta(puntoVenta);
        out.setTipoComprobante(tipoComprobante);
        out.setCbteDesde(r.getCbteDesde() != null ? r.getCbteDesde() : cbteNro);
        out.setCbteHasta(r.getCbteHasta() != null ? r.getCbteHasta() : cbteNro);
        out.setCae(r.getCodAutorizacion());
        out.setCaeFechaVencimiento(r.getFchVto());
        out.setResultadoDetalle(r.getResultado());
        out.getErrores().addAll(r.getErrors());
        out.getEventos().addAll(r.getEvents());
        out.getObservaciones().addAll(r.getObservaciones());
        if ("A".equalsIgnoreCase(r.getResultado()) && StringUtils.hasText(r.getCodAutorizacion())) {
            out.setEstado(ArcaEmisionResultado.Estado.APROBADA);
            out.setMensaje("Comprobante autorizado (consulta)");
        } else if ("R".equalsIgnoreCase(r.getResultado())) {
            out.setEstado(ArcaEmisionResultado.Estado.RECHAZADA);
            out.setMensaje(resumirMensajes(out));
        } else if (!r.getErrors().isEmpty() || !StringUtils.hasText(r.getResultado())) {
            out.setEstado(ArcaEmisionResultado.Estado.INCIERTO);
            out.setMensaje("Consulta sin resultado concluyente; reintentar conciliación si corresponde");
        } else {
            out.setEstado(ArcaEmisionResultado.Estado.INCIERTO);
            out.setMensaje("Resultado de consulta no concluyente");
        }
        return out;
    }

    private ArcaEmisionResultado resultadoIncierto(String mensaje, Exception ex) {
        ArcaEmisionResultado out = new ArcaEmisionResultado();
        out.setEstado(ArcaEmisionResultado.Estado.INCIERTO);
        out.setMensaje(mensaje);
        return out;
    }

    private String resumirMensajes(ArcaEmisionResultado r) {
        if (!r.getErrores().isEmpty()) return resumir(r.getErrores());
        if (!r.getObservaciones().isEmpty()) return resumir(r.getObservaciones());
        return StringUtils.hasText(r.getMensaje()) ? r.getMensaje() : "Rechazado por ARCA";
    }

    private String resumir(List<WsfeMessage> msgs) {
        if (msgs == null || msgs.isEmpty()) return "";
        return msgs.stream().map(WsfeMessage::toString).filter(StringUtils::hasText)
                .collect(Collectors.joining(" | "));
    }

    private static String formatearFechaArca(Date fecha) {
        if (fecha == null) return null;
        return FECHA_ARCA.format(fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    private static BigDecimal redondear(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

  
   
    public static int resolverAlicuotaIvaId(BigDecimal porcentaje, List<ParametroArca> tiposIva) {
        if (porcentaje == null) {
            throw new ArcaException("Falta el porcentaje de IVA en uno de los detalles");
        }
        for (ParametroArca tipo : tiposIva) {
            BigDecimal porcentajeCatalogo = extraerPorcentaje(tipo.getDescripcion());
            if (porcentajeCatalogo != null && porcentajeCatalogo.compareTo(porcentaje) == 0) {
                try {
                    return Integer.parseInt(tipo.getId().trim());
                } catch (RuntimeException ex) {
                    throw new ArcaException("Id de alícuota IVA inválido en catálogo ARCA: " + tipo.getId());
                }
            }
        }
        throw new ArcaException("La alícuota de IVA " + porcentaje + "% no existe en FEParamGetTiposIva");
    }

    private static BigDecimal extraerPorcentaje(String descripcion) {
        if (!StringUtils.hasText(descripcion)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+(?:[.,]\\d+)?)").matcher(descripcion);
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group(1).replace(',', '.'));
    }
}

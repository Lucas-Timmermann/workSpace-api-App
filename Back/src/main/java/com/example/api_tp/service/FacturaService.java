package com.example.api_tp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.api_tp.client.arca.wsfe.CotizacionArca;
import com.example.api_tp.client.arca.wsfe.FeCaeSolicitud;
import com.example.api_tp.client.arca.wsfe.ParametroArca;
import com.example.api_tp.client.arca.wsfe.PuntoVentaArca;
import com.example.api_tp.client.arca.wsfe.WsfeMessage;
import com.example.api_tp.config.ArcaProperties;
import com.example.api_tp.dto.EnvioArcaItemResultadoDTO;
import com.example.api_tp.dto.EnvioArcaProcesoDTO;
import com.example.api_tp.dto.EnvioArcaRequestDTO;
import com.example.api_tp.dto.FacturaDetalleRequestDTO;
import com.example.api_tp.dto.FacturaEnvioArcaDTO;
import com.example.api_tp.dto.FacturaReporteDTO;
import com.example.api_tp.dto.FacturaRequestDTO;
import com.example.api_tp.dto.FacturaVentaDetalleDTO;
import com.example.api_tp.dto.FacturaVentaPaginaDTO;
import com.example.api_tp.model.Articulo;
import com.example.api_tp.model.Cliente;
import com.example.api_tp.model.CondicionIva;
import com.example.api_tp.model.EstadoFactura;
import com.example.api_tp.model.FacturaEnvioArca;
import com.example.api_tp.model.FacturaVenta;
import com.example.api_tp.model.FacturaVentaDetalle;
import com.example.api_tp.model.ListaPrecioArticulo;
import com.example.api_tp.model.PuntoVenta;
import com.example.api_tp.model.TipoMoneda;
import com.example.api_tp.model.Usuario;
import com.example.api_tp.repository.ArticuloRepository;
import com.example.api_tp.repository.ClienteRepository;
import com.example.api_tp.repository.CondicionIvaRepository;
import com.example.api_tp.repository.FacturaEnvioArcaRepository;
import com.example.api_tp.repository.FacturaVentaDetalleRepository;
import com.example.api_tp.repository.FacturaVentaRepository;
import com.example.api_tp.repository.ListaPrecioArticuloRepository;
import com.example.api_tp.repository.PuntoVentaRepository;
import com.example.api_tp.repository.TipoMonedaRepository;
import com.example.api_tp.repository.UsuarioRepository;
import com.example.api_tp.service.arca.ArcaComprobanteDatos;
import com.example.api_tp.service.arca.ArcaEmisionResultado;
import com.example.api_tp.service.arca.ArcaFacturaElectronicaService;

@Service
public class FacturaService {

    private static final int TAMANO_PAGINA_FACTURAS = 10;
    private static final int ESCALA_MONETARIA = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final int CONCEPTO_PRODUCTOS = 1;
    private static final int CONDICION_IVA_RESPONSABLE_INSCRIPTO = 1;
    private static final int DOC_TIPO_CUIT = 80;
    private static final int DOC_TIPO_DNI = 96;
    private static final int TIPO_COMPROBANTE_FACTURA_A = 1;
    private static final int TIPO_COMPROBANTE_FACTURA_B = 6;
    private static final String MONEDA_NACIONAL = "PES";
    private static final List<String> ESTADOS_PENDIENTES = List.of(
            EstadoFactura.PENDIENTE.name(), EstadoFactura.RECHAZADA.name());
    private static final List<String> ESTADOS_AUTORIZADAS = List.of(EstadoFactura.AUTORIZADA.name());

    private final FacturaVentaRepository facturaRepository;
    private final FacturaVentaDetalleRepository detalleRepository;
    private final ArticuloRepository articuloRepository;
    private final ClienteRepository clienteRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final UsuarioRepository usuarioRepository;
    private final FacturaEnvioArcaRepository envioArcaRepository;
    private final String usuarioAuditoriaId;
    private final FacturaEstadoMachine estadoMachine;
    private final ArcaFacturaElectronicaService arcaFacturaElectronicaService;
    private final ArcaProperties arcaProperties;
    private final TransactionTemplate transactionTemplate;

  
    private final ReentrantLock lockEmision = new ReentrantLock(true);

    public FacturaService(
            FacturaVentaRepository facturaRepository,
            FacturaVentaDetalleRepository detalleRepository,
            ArticuloRepository articuloRepository,
            ClienteRepository clienteRepository,
            CondicionIvaRepository condicionIvaRepository,
            PuntoVentaRepository puntoVentaRepository,
            TipoMonedaRepository tipoMonedaRepository,
            ListaPrecioArticuloRepository listaPrecioArticuloRepository,
            UsuarioRepository usuarioRepository,
            FacturaEnvioArcaRepository envioArcaRepository,
            @Value("${app.auditoria.usuario-id:}") String usuarioAuditoriaId,
            FacturaEstadoMachine estadoMachine,
            ArcaFacturaElectronicaService arcaFacturaElectronicaService,
            ArcaProperties arcaProperties,
            org.springframework.transaction.PlatformTransactionManager txManager) {
        this.facturaRepository = facturaRepository;
        this.detalleRepository = detalleRepository;
        this.articuloRepository = articuloRepository;
        this.clienteRepository = clienteRepository;
        this.condicionIvaRepository = condicionIvaRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.usuarioRepository = usuarioRepository;
        this.envioArcaRepository = envioArcaRepository;
        this.usuarioAuditoriaId = usuarioAuditoriaId;
        this.estadoMachine = estadoMachine;
        this.arcaFacturaElectronicaService = arcaFacturaElectronicaService;
        this.arcaProperties = arcaProperties;
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

  
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FacturaReporteDTO crear(FacturaRequestDTO request) {
        return aFacturaReporteDTO(crearFactura(request));
    }

    private FacturaVenta crearFactura(FacturaRequestDTO request) {
        if (request == null) {
            throw badRequest("La solicitud es obligatoria");
        }
        validarCabeceraBase(request);
        Usuario usuarioAuditoria = obtenerUsuarioAuditoria();
        Date ahora = new Date();
        FacturaVenta factura = new FacturaVenta();
        aplicarCabeceraComun(factura, request);
        aplicarDatosFiscales(factura);
        factura.setNumero(generarNumeroInterno());
        factura.setFechaAlta(ahora);
        factura.setFechaModificacion(ahora);
        factura.setUsuarioCarga(usuarioAuditoria);
        factura.setUsuarioModificacion(usuarioAuditoria);
        factura.setUsuarioBaja(null);

        factura.setEstado(EstadoFactura.PENDIENTE.name());
        factura.setResultadoAfip("PENDIENTE");
        factura.setCae(null);
        factura.setCaeFechaVencimiento(null);
        factura.setMotivoRechazo(null);
        factura.setFechaAnulacion(null);
        sincronizarDetalles(factura, request.getDetalles(), Map.of());
        recalcularTotales(factura, request.getImporteCobrado());
        return facturaRepository.saveAndFlush(factura);
    }

   
    @Transactional
    public FacturaReporteDTO actualizar(Long id, FacturaRequestDTO request) {
        return aFacturaReporteDTO(editarFactura(id, request));
    }

    private FacturaVenta editarFactura(Long id, FacturaRequestDTO request) {
        if (request == null) {
            throw badRequest("La solicitud es obligatoria");
        }
        validarCabeceraBase(request);
        FacturaVenta factura = facturaRepository.buscarPorIdParaActualizacion(id)
                .orElseThrow(() -> noEncontrada("Factura", id));
        EstadoFactura estado = estadoMachine.parsear(factura.getEstado());
        if (!estado.esEditable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden editar facturas en estado PENDIENTE o RECHAZADA");
        }
        Usuario usuarioAuditoria = obtenerUsuarioAuditoria();
        Map<Long, FacturaVentaDetalle> detallesExistentes = new HashMap<>();
        for (FacturaVentaDetalle detalle : factura.getDetalles()) {
            detallesExistentes.put(detalle.getId(), detalle);
        }
        aplicarCabeceraComun(factura, request);
        aplicarDatosFiscales(factura);
        sincronizarDetalles(factura, request.getDetalles(), detallesExistentes);
        recalcularTotales(factura, request.getImporteCobrado());
     
        estadoMachine.validarTransicion(estado, EstadoFactura.PENDIENTE);
        factura.setEstado(EstadoFactura.PENDIENTE.name());
        factura.setResultadoAfip("PENDIENTE");
        factura.setMotivoRechazo(null);
        factura.setCae(null);
        factura.setCaeFechaVencimiento(null);
        factura.setNumeroFiscal(null);
        factura.setFechaModificacion(new Date());
        factura.setUsuarioModificacion(usuarioAuditoria);
        return facturaRepository.saveAndFlush(factura);
    }

    @Transactional
    public FacturaReporteDTO anularPendiente(Long id) {
        FacturaVenta factura = facturaRepository.buscarPorIdParaActualizacion(id)
                .orElseThrow(() -> noEncontrada("Factura", id));
        EstadoFactura estado = estadoMachine.parsear(factura.getEstado());
        if (estado == EstadoFactura.AUTORIZADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede anular localmente una factura autorizada");
        }
        if (estado == EstadoFactura.EN_PROCESO_ENVIO || estado == EstadoFactura.RESULTADO_DESCONOCIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede anular la factura en estado " + factura.getEstado());
        }
        if (estado == EstadoFactura.ANULADA_LOCAL) {
            return aFacturaReporteDTO(factura);
        }
        estadoMachine.validarTransicion(estado, EstadoFactura.ANULADA_LOCAL);
        Usuario usuario = obtenerUsuarioAuditoria();
        Date ahora = new Date();
        factura.setEstado(EstadoFactura.ANULADA_LOCAL.name());
        factura.setFechaAnulacion(ahora);
        factura.setFechaBaja(ahora);
        factura.setUsuarioBaja(usuario);
        factura.setFechaModificacion(ahora);
        factura.setUsuarioModificacion(usuario);
        factura.setResultadoAfip("ANULADA_LOCAL");
        return aFacturaReporteDTO(facturaRepository.saveAndFlush(factura));
    }

    
    @Transactional
    public void eliminar(Long id) {
        anularPendiente(id);
    }



    @Transactional(readOnly = true)
    public FacturaReporteDTO consultarPorId(Long id) {
        FacturaVenta factura = facturaRepository.buscarPorIdConDetalles(id)
                .orElseThrow(() -> noEncontrada("Factura", id));
        return aFacturaReporteDTO(factura);
    }

    @Transactional(readOnly = true)
    public FacturaVentaPaginaDTO filtrar(
            Long numeroFactura,
            String clienteDenominacion,
            String condicionIva,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            int pagina) {
        return listar(null, numeroFactura, clienteDenominacion, condicionIva, fechaDesde, fechaHasta, pagina);
    }

    @Transactional(readOnly = true)
    public FacturaVentaPaginaDTO listarPendientes(Long numero, String cliente, LocalDate fechaDesde,
            LocalDate fechaHasta, int pagina) {
        return listar(ESTADOS_PENDIENTES, numero, cliente, null, fechaDesde, fechaHasta, pagina);
    }

   @Transactional(readOnly = true)
    public List<FacturaReporteDTO> listarAutorizadas(Long numero, String cliente) {
    
        Pageable sinPaginar = Pageable.unpaged(
                Sort.by(FacturaVenta::getNumero).ascending().and(Sort.by(FacturaVenta::getId).ascending())
        );

        
        Page<FacturaVenta> resultado = facturaRepository.filtrarPorEstados(
                ESTADOS_AUTORIZADAS,
                numero,
                normalizarFiltro(cliente),
                null, 
                null, 
                null, 
                sinPaginar
        );

        
        List<Long> ids = resultado.getContent().stream().map(FacturaVenta::getId).toList();
        List<FacturaVenta> conDetalles = ids.isEmpty() ? List.of() : facturaRepository.buscarConDetallesPorIds(ids);
        
        Map<Long, FacturaVenta> porId = conDetalles.stream()
                .collect(Collectors.toMap(FacturaVenta::getId, Function.identity()));

        return resultado.getContent().stream()
                .map(f -> aFacturaReporteDTO(porId.getOrDefault(f.getId(), f)))
                .toList();
    }


    private FacturaVentaPaginaDTO listar(List<String> estados, Long numero, String cliente, String condicionIva,
            LocalDate fechaDesde, LocalDate fechaHasta, int pagina) {
        if (pagina < 0) {
            throw badRequest("page debe ser un índice mayor o igual a cero");
        }
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw badRequest("fechaDesde no puede ser posterior a fechaHasta");
        }
        Date inicio = fechaDesde == null ? null : Date.from(fechaDesde.atStartOfDay().toInstant(ZoneOffset.UTC));
        Date finExclusivo = fechaHasta == null ? null
                : Date.from(fechaHasta.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        Pageable pageable = PageRequest.of(pagina, TAMANO_PAGINA_FACTURAS,
                Sort.by(FacturaVenta::getNumero).ascending().and(Sort.by(FacturaVenta::getId).ascending()));
        Page<FacturaVenta> resultado = facturaRepository.filtrarPorEstados(
                estados, numero, normalizarFiltro(cliente), normalizarFiltro(condicionIva), inicio, finExclusivo, pageable);
        List<Long> ids = resultado.getContent().stream().map(FacturaVenta::getId).toList();
        List<FacturaVenta> conDetalles = ids.isEmpty() ? List.of() : facturaRepository.buscarConDetallesPorIds(ids);
        Map<Long, FacturaVenta> porId = conDetalles.stream()
                .collect(Collectors.toMap(FacturaVenta::getId, Function.identity()));
        List<FacturaReporteDTO> contenido = resultado.getContent().stream()
                .map(f -> aFacturaReporteDTO(porId.getOrDefault(f.getId(), f)))
                .toList();
        return new FacturaVentaPaginaDTO(contenido, resultado.getNumber(), TAMANO_PAGINA_FACTURAS,
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<FacturaEnvioArcaDTO> consultarHistorialEnvios(Long facturaId) {
        facturaRepository.findById(facturaId).orElseThrow(() -> noEncontrada("Factura", facturaId));
        return envioArcaRepository.findByFacturaIdOrderByIdAsc(facturaId).stream()
                .map(this::aEnvioDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnvioArcaProcesoDTO consultarProcesoEnvio(String envioId) {
        List<FacturaEnvioArca> envios = envioArcaRepository.findByProcesoEnvioIdOrderByIdAsc(envioId);
        if (envios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proceso de envío no encontrado: " + envioId);
        }
        EnvioArcaProcesoDTO dto = new EnvioArcaProcesoDTO();
        dto.setEnvioId(envioId);
        for (FacturaEnvioArca envio : envios) {
            String resultado = normalizarEstadoIntento(envio.getEstadoIntento());
            dto.getDetalle().add(new EnvioArcaItemResultadoDTO(
                    envio.getFactura().getId(), envio.getFactura().getNumero(), envio.getFactura().getEstado(),
                    resultado, envio.getMensajeTecnico(), envio.getCae(), envio.getCaeVencimiento(),
                    envio.getNumeroComprobante()));
            dto.setProcesadas(dto.getProcesadas() + 1);
            switch (resultado) {
                case "APROBADA" -> dto.setAutorizadas(dto.getAutorizadas() + 1);
                case "RECHAZADA" -> dto.setRechazadas(dto.getRechazadas() + 1);
                case "INCIERTO" -> dto.setResultadoDesconocido(dto.getResultadoDesconocido() + 1);
                default -> dto.setNoEnviadasPorValidacion(dto.getNoEnviadasPorValidacion() + 1);
            }
        }
        return dto;
    }



    public EnvioArcaProcesoDTO enviarFacturasAArca(EnvioArcaRequestDTO request) {
        List<Long> idsObjetivo = resolverIdsParaEnvio(request);
        String procesoId = "ENV-" + UUID.randomUUID();
        List<ParametroArca> tiposIva = consultarCatalogo(arcaFacturaElectronicaService::obtenerTiposIva);
        List<PuntoVentaArca> puntosVenta = consultarCatalogo(arcaFacturaElectronicaService::obtenerPuntosVenta);
        EnvioArcaProcesoDTO resumen = new EnvioArcaProcesoDTO();
        resumen.setEnvioId(procesoId);
        for (Long id : idsObjetivo) {
            acumularResumen(resumen,
                    procesarEnvioFacturaIndividual(id, procesoId, tiposIva, puntosVenta));
        }
        return resumen;
    }

    /** Los catálogos se piden una vez por lote; un fallo se informa por factura, no corta el lote. */
    private static <T> List<T> consultarCatalogo(java.util.function.Supplier<List<T>> consulta) {
        try {
            return consulta.get();
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Long> resolverIdsParaEnvio(EnvioArcaRequestDTO request) {
        boolean enviarTodas = request == null || Boolean.TRUE.equals(request.getEnviarTodas())
                || request.getIds() == null || request.getIds().isEmpty();
        if (enviarTodas) {
            return facturaRepository.findByEstado(EstadoFactura.PENDIENTE.name()).stream()
                    .map(FacturaVenta::getId)
                    .sorted()
                    .toList();
        }
        List<Long> ids = request.getIds().stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) {
            throw badRequest("Debe indicar IDs de facturas a enviar");
        }
        List<FacturaVenta> encontradas = facturaRepository.findAllById(ids);
        if (encontradas.size() != ids.size()) {
            Set<Long> encontrados = encontradas.stream().map(FacturaVenta::getId).collect(Collectors.toSet());
            Long faltante = ids.stream().filter(i -> !encontrados.contains(i)).findFirst().orElse(-1L);
            throw noEncontrada("Factura", faltante);
        }
        return ids;
    }

    private EnvioArcaItemResultadoDTO procesarEnvioFacturaIndividual(Long facturaId, String procesoId,
            List<ParametroArca> tiposIva, List<PuntoVentaArca> puntosVenta) {
   
        DatosEnvio datos;
        try {
            datos = transactionTemplate.execute(status ->
                    prepararEnvio(facturaId, procesoId, tiposIva, puntosVenta));
        } catch (ResponseStatusException ex) {
            return itemNoEnviado(facturaId, null, ex.getReason());
        } catch (Exception ex) {
            return itemNoEnviado(facturaId, null, "Error de validación/preparación: " + mensajeSeguro(ex));
        }
        if (datos == null || datos.noEnviable) {
            return datos == null ? itemNoEnviado(facturaId, null, "No se pudo preparar el envío") : datos.itemNoEnviado;
        }

        BigDecimal cotizacion;
        try {
            cotizacion = resolverCotizacion(datos);
        } catch (RuntimeException ex) {
            String motivo = "No se pudo determinar la cotización de la moneda " + datos.moneda
                    + ": " + mensajeSeguro(ex);
            transactionTemplate.executeWithoutResult(status -> {
                FacturaVenta factura = facturaRepository.buscarPorIdParaActualizacion(datos.facturaId)
                        .orElseThrow(() -> noEncontrada("Factura", datos.facturaId));
                registrarIntentoNoEnviada(factura, procesoId, motivo);
            });
            return itemNoEnviado(facturaId, datos.numeroInterno, motivo);
        }

        lockEmision.lock();
        try {
         
            long proximo;
            try {
                proximo = arcaFacturaElectronicaService
                        .consultarUltimoComprobanteAutorizado(datos.puntoVenta, datos.tipoComprobante)
                        .getProximoCbteNro();
            } catch (Exception ex) {
       
                return itemNoEnviado(facturaId, datos.numeroInterno,
                        "No se pudo obtener la numeración fiscal de ARCA: " + mensajeSeguro(ex));
            }

            
            SolicitudEnvio solicitud;
            try {
                solicitud = transactionTemplate.execute(status ->
                        marcarEnProcesoYConstruirSolicitud(datos, proximo, cotizacion));
            } catch (ResponseStatusException ex) {
                return itemNoEnviado(facturaId, datos.numeroInterno, ex.getReason());
            } catch (Exception ex) {
                return itemNoEnviado(facturaId, datos.numeroInterno,
                        "Error preparando la solicitud ARCA: " + mensajeSeguro(ex));
            }
            if (solicitud == null || solicitud.noEnviable) {
                return solicitud == null ? itemNoEnviado(facturaId, datos.numeroInterno, "No se pudo preparar la solicitud")
                        : solicitud.itemNoEnviado;
            }

            
            ArcaEmisionResultado resultado;
            try {
                resultado = arcaFacturaElectronicaService.enviarSolicitudCae(solicitud.solicitud);
            } catch (Exception ex) {
                resultado = resultadoInciertoTecnico(ex);
            }

      
            final ArcaEmisionResultado resultadoFinal = resultado;
            return transactionTemplate.execute(status -> persistirResultado(
                    datos.facturaId, datos.puntoVenta, datos.tipoComprobante, solicitud.numeroFiscal,
                    resultadoFinal, procesoId));
        } finally {
            lockEmision.unlock();
        }
    }

    private DatosEnvio prepararEnvio(Long facturaId, String procesoId, List<ParametroArca> tiposIva,
            List<PuntoVentaArca> puntosVenta) {
        FacturaVenta factura = facturaRepository.buscarPorIdParaActualizacion(facturaId)
                .orElseThrow(() -> noEncontrada("Factura", facturaId));
        DatosEnvio datos = new DatosEnvio();
        datos.facturaId = factura.getId();
        datos.numeroInterno = factura.getNumero();
        EstadoFactura estado = estadoMachine.parsear(factura.getEstado());
        if (!estado.esEnviable()) {
            datos.noEnviable = true;
            datos.itemNoEnviado = itemNoEnviado(factura.getId(), factura.getNumero(),
                    "La factura no está en un estado elegible para envío: " + factura.getEstado());
            return datos;
        }
        String errorValidacion;
        try {
            aplicarDatosFiscales(factura);
            errorValidacion = validarDatosFiscalesParaEnvio(factura, tiposIva, puntosVenta);
        } catch (ResponseStatusException ex) {
            errorValidacion = ex.getReason();
        }
        if (errorValidacion != null) {
            registrarIntentoNoEnviada(factura, procesoId, errorValidacion);
            datos.noEnviable = true;
            datos.itemNoEnviado = itemNoEnviado(factura.getId(), factura.getNumero(), errorValidacion);
            return datos;
        }
        datos.tiposIva = tiposIva;
        datos.puntoVenta = factura.getPuntoVenta().getNumero();
        datos.tipoComprobante = factura.getTipoComprobanteArca();
        datos.moneda = factura.getMonedaArca();
        return datos;
    }

    private void registrarIntentoNoEnviada(FacturaVenta factura, String procesoId, String motivo) {
        registrarIntento(factura, procesoId, "NO_ENVIADA", null, null, null, null, null, null, null,
                null, null, null, motivo);
        factura.setFechaModificacion(new Date());
        factura.setUsuarioModificacion(obtenerUsuarioAuditoria());
        facturaRepository.saveAndFlush(factura);
    }

    private SolicitudEnvio marcarEnProcesoYConstruirSolicitud(DatosEnvio datos, long numeroFiscal,
            BigDecimal cotizacion) {
        FacturaVenta factura = facturaRepository.buscarPorIdParaActualizacion(datos.facturaId)
                .orElseThrow(() -> noEncontrada("Factura", datos.facturaId));
        EstadoFactura estado = estadoMachine.parsear(factura.getEstado());
        if (!estado.esEnviable()) {
            SolicitudEnvio noEnviable = new SolicitudEnvio();
            noEnviable.noEnviable = true;
            noEnviable.itemNoEnviado = itemNoEnviado(factura.getId(), factura.getNumero(),
                    "La factura no está en un estado elegible para envío: " + factura.getEstado());
            return noEnviable;
        }
        estadoMachine.validarTransicion(estado, EstadoFactura.EN_PROCESO_ENVIO);
        Date ahora = new Date();
        factura.setEstado(EstadoFactura.EN_PROCESO_ENVIO.name());
        factura.setResultadoAfip("EN_PROCESO");
        factura.setFechaEnvioArca(ahora);
        factura.setNumeroFiscal(numeroFiscal);
        factura.setCotizacionArca(cotizacion.doubleValue());
        factura.setFechaModificacion(ahora);
        factura.setUsuarioModificacion(obtenerUsuarioAuditoria());

        ArcaComprobanteDatos datosFiscales = arcaFacturaElectronicaService.construirDatosFiscalesBase(
                factura, datos.tiposIva);
        completarDatosFiscalesDesdeFactura(datosFiscales, factura, numeroFiscal, cotizacion);
        SolicitudEnvio solicitud = new SolicitudEnvio();
        solicitud.solicitud = arcaFacturaElectronicaService.construirSolicitudFeCae(datosFiscales);
        solicitud.numeroFiscal = numeroFiscal;
        facturaRepository.saveAndFlush(factura);
        return solicitud;
    }

   
    public EnvioArcaItemResultadoDTO conciliarResultadoIncierto(Long facturaId, String procesoId) {
        DatosConciliacion datos = transactionTemplate.execute(status -> prepararConciliacion(facturaId));
        if (datos == null) {
            throw noEncontrada("Factura", facturaId);
        }
        ArcaEmisionResultado resultado = arcaFacturaElectronicaService.consultarComprobanteParaConciliacion(
                datos.tipoComprobante, datos.puntoVenta, datos.numeroFiscal);
        String pid = procesoId == null || procesoId.isBlank() ? "CONC-" + UUID.randomUUID() : procesoId;
        return transactionTemplate.execute(status -> persistirResultado(
                datos.facturaId, datos.puntoVenta, datos.tipoComprobante, datos.numeroFiscal, resultado, pid));
    }

    private DatosConciliacion prepararConciliacion(Long facturaId) {
        FacturaVenta factura = facturaRepository.buscarPorIdConDetalles(facturaId)
                .orElseThrow(() -> noEncontrada("Factura", facturaId));
        EstadoFactura estado = estadoMachine.parsear(factura.getEstado());
        if (estado != EstadoFactura.RESULTADO_DESCONOCIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La factura no está en estado RESULTADO_DESCONOCIDO");
        }
        if (factura.getTipoComprobanteArca() == null || factura.getPuntoVenta() == null
                || factura.getNumeroFiscal() == null) {
            throw badRequest("Faltan datos fiscales (tipo comprobante/punto de venta/número fiscal) para conciliar");
        }
        return new DatosConciliacion(factura.getId(), factura.getPuntoVenta().getNumero(),
                factura.getTipoComprobanteArca(), factura.getNumeroFiscal());
    }

    
    private EnvioArcaItemResultadoDTO persistirResultado(Long facturaId, Integer puntoVenta, Integer tipoComprobante,
            Long numeroFiscal, ArcaEmisionResultado resultado, String procesoId) {
        if (resultado == null || resultado.getEstado() == null) {
            resultado = resultadoInciertoTecnico(null);
        }
        FacturaVenta factura = facturaRepository.buscarPorIdParaActualizacion(facturaId)
                .orElseThrow(() -> noEncontrada("Factura", facturaId));
        Date ahora = new Date();
        String errores = joinMensajes(resultado.getErrores());
        String observaciones = joinMensajes(resultado.getObservaciones());
        String eventos = joinMensajes(resultado.getEventos());
        Long numeroComprobante = resultado.getCbteDesde() != null ? resultado.getCbteDesde() : numeroFiscal;
        EstadoFactura estadoActual = estadoMachine.parsear(factura.getEstado());

        switch (resultado.getEstado()) {
            case APROBADA -> {
                estadoMachine.validarTransicion(estadoActual, EstadoFactura.AUTORIZADA);
                factura.setEstado(EstadoFactura.AUTORIZADA.name());
                factura.setResultadoAfip("A");
                factura.setCae(resultado.getCae());
                factura.setCaeFechaVencimiento(parseFechaCae(resultado.getCaeFechaVencimiento()));
                factura.setNumeroFiscal(numeroComprobante);
                factura.setMotivoRechazo(null);
                factura.setFechaAutorizacionArca(ahora);
                factura.setFechaResultadoArca(ahora);
                factura.setObservaciones(combinarObs(factura.getObservaciones(), observaciones));
                registrarIntento(factura, procesoId, "APROBADA", puntoVenta, tipoComprobante, numeroComprobante,
                        resultado.getResultadoGlobal(), resultado.getResultadoDetalle(), resultado.getCae(),
                        resultado.getCaeFechaVencimiento(), observaciones, errores, eventos, resultado.getMensaje());
            }
            case RECHAZADA -> {
                estadoMachine.validarTransicion(estadoActual, EstadoFactura.RECHAZADA);
                factura.setEstado(EstadoFactura.RECHAZADA.name());
                factura.setResultadoAfip("R");
                factura.setMotivoRechazo(truncar(joinNoVacio(resultado.getMensaje(), errores, observaciones), 1000));
                factura.setFechaResultadoArca(ahora);
                factura.setCae(null);
                factura.setCaeFechaVencimiento(null);
                registrarIntento(factura, procesoId, "RECHAZADA", puntoVenta, tipoComprobante, numeroComprobante,
                        resultado.getResultadoGlobal(), resultado.getResultadoDetalle(), null, null,
                        observaciones, errores, eventos, resultado.getMensaje());
            }
            case INCIERTO -> {
                if (estadoActual != EstadoFactura.RESULTADO_DESCONOCIDO) {
                    estadoMachine.validarTransicion(estadoActual, EstadoFactura.RESULTADO_DESCONOCIDO);
                }
                factura.setEstado(EstadoFactura.RESULTADO_DESCONOCIDO.name());
                factura.setResultadoAfip("INCIERTO");
                factura.setMotivoRechazo(truncar(resultado.getMensaje(), 1000));
                factura.setFechaResultadoArca(ahora);
                registrarIntento(factura, procesoId, "INCIERTO", puntoVenta, tipoComprobante, numeroComprobante,
                        resultado.getResultadoGlobal(), resultado.getResultadoDetalle(), resultado.getCae(),
                        resultado.getCaeFechaVencimiento(), observaciones, errores, eventos, resultado.getMensaje());
            }
        }

        factura.setFechaModificacion(ahora);
        factura.setUsuarioModificacion(obtenerUsuarioAuditoria());
        facturaRepository.saveAndFlush(factura);
        return new EnvioArcaItemResultadoDTO(factura.getId(), factura.getNumero(), factura.getEstado(),
                resultado.getEstado().name(), resultado.getMensaje(), factura.getCae(),
                resultado.getCaeFechaVencimiento(), factura.getNumeroFiscal());
    }

    private ArcaEmisionResultado resultadoInciertoTecnico(Exception ex) {
        ArcaEmisionResultado resultado = new ArcaEmisionResultado();
        resultado.setEstado(ArcaEmisionResultado.Estado.INCIERTO);
        resultado.setMensaje(truncar("Error de comunicación con ARCA (resultado incierto): " + mensajeSeguro(ex), 1000));
        return resultado;
    }

    

    private void aplicarCabeceraComun(FacturaVenta factura, FacturaRequestDTO request) {
        factura.setFechaEmision(request.getFechaEmision());
        factura.setCliente(buscarCliente(request.getClienteId()));
        factura.setCondicionIva(buscarCondicionIva(request.getCondicionIvaId()));
        factura.setPuntoVenta(buscarPuntoVenta(request.getPuntoVentaId()));
        factura.setTipoMoneda(buscarTipoMoneda(request.getTipoMonedaId()));
        factura.setObservaciones(request.getObservaciones());
    }

    
    private void aplicarDatosFiscales(FacturaVenta factura) {
        factura.setTipoComprobanteArca(determinarTipoComprobante(factura));
        factura.setConceptoArca(CONCEPTO_PRODUCTOS);
        factura.setDocTipoReceptorArca(determinarDocTipo(factura.getCliente()));
        factura.setDocNroReceptorArca(determinarDocNro(factura.getCliente()));
        factura.setCondicionIvaReceptorArca(codigoAfipCondicionIva(factura.getCondicionIva()));
        factura.setMonedaArca(codigoAfipMoneda(factura.getTipoMoneda()));
 
        factura.setCotizacionArca(esMonedaNacional(factura.getMonedaArca()) ? 1.0 : null);
    }

    private int determinarTipoComprobante(FacturaVenta factura) {
        int condicionEmisor = arcaProperties.getCondicionIvaEmisor();
        if (condicionEmisor != CONDICION_IVA_RESPONSABLE_INSCRIPTO) {
            throw badRequest("El sistema solo contempla emisión con emisor IVA Responsable Inscripto"
                    + " (código AFIP 1); revisar app.arca.condicion-iva-emisor");
        }
        int condicionReceptor = codigoAfipCondicionIva(factura.getCondicionIva());
        return condicionReceptor == CONDICION_IVA_RESPONSABLE_INSCRIPTO
                ? TIPO_COMPROBANTE_FACTURA_A
                : TIPO_COMPROBANTE_FACTURA_B;
    }

    private static int codigoAfipCondicionIva(CondicionIva condicionIva) {
        if (condicionIva == null || condicionIva.getCodigoAfip() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La condición IVA seleccionada no tiene un código AFIP válido");
        }
        return condicionIva.getCodigoAfip();
    }

    private static int determinarDocTipo(Cliente cliente) {
        int digitos = documentoReceptor(cliente).length();
        return digitos == 11 ? DOC_TIPO_CUIT : DOC_TIPO_DNI;
    }

    private static long determinarDocNro(Cliente cliente) {
        return Long.parseLong(documentoReceptor(cliente));
    }

    private static String documentoReceptor(Cliente cliente) {
        if (cliente == null || cliente.getCuitCuil() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El cliente no tiene CUIT/CUIL o DNI para determinar el documento del receptor");
        }
        String digitos = cliente.getCuitCuil().replaceAll("\\D", "");
        if (digitos.length() != 11 && digitos.length() != 7 && digitos.length() != 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El CUIT/CUIL o DNI del cliente debe tener 11, 8 o 7 dígitos para emitir");
        }
        return digitos;
    }

    private static String codigoAfipMoneda(TipoMoneda moneda) {
        if (moneda == null || moneda.getCodigoAfip() == null || moneda.getCodigoAfip().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La moneda seleccionada no tiene un código AFIP válido");
        }
        return moneda.getCodigoAfip().trim();
    }

    private static boolean esMonedaNacional(String moneda) {
        return MONEDA_NACIONAL.equalsIgnoreCase(moneda);
    }

    private BigDecimal resolverCotizacion(DatosEnvio datos) {
        if (esMonedaNacional(datos.moneda)) {
            return BigDecimal.ONE;
        }
        CotizacionArca cotizacion = arcaFacturaElectronicaService.obtenerCotizacion(datos.moneda);
        if (cotizacion == null || cotizacion.getMonCotiz() == null
                || cotizacion.getMonCotiz().signum() <= 0) {
            throw new IllegalStateException(
                    "ARCA no devolvió una cotización válida para la moneda " + datos.moneda);
        }
        return cotizacion.getMonCotiz();
    }

    private String validarDatosFiscalesParaEnvio(FacturaVenta factura, List<ParametroArca> tiposIva,
            List<PuntoVentaArca> puntosVenta) {
        if (factura.getTipoComprobanteArca() == null
                || !Set.of(TIPO_COMPROBANTE_FACTURA_A, TIPO_COMPROBANTE_FACTURA_B)
                        .contains(factura.getTipoComprobanteArca())) {
            return "Tipo de comprobante ARCA no soportado: " + factura.getTipoComprobanteArca()
                    + " (admitidos: 1, 6)";
        }
        if (factura.getPuntoVenta() == null || factura.getPuntoVenta().getNumero() <= 0) {
            return "Punto de venta inválido para emisión";
        }
        if (!esEmisionElectronica(factura.getPuntoVenta().getTipoEmision())) {
            return "El punto de venta " + factura.getPuntoVenta().getNumero()
                    + " no está configurado para emisión electrónica";
        }
        if (puntosVenta == null) {
            return "No se pudo consultar FEParamGetPtosVenta para validar el punto de venta";
        }
        PuntoVentaArca puntoArca = puntosVenta.stream()
                .filter(p -> p.getNumero() == factura.getPuntoVenta().getNumero())
                .findFirst().orElse(null);
        if (puntoArca == null) {
            return "El punto de venta " + factura.getPuntoVenta().getNumero()
                    + " no está habilitado en ARCA";
        }
        if (puntoArca.isBloqueado()) {
            return "El punto de venta " + factura.getPuntoVenta().getNumero() + " está bloqueado en ARCA";
        }
        if (factura.getMonedaArca() == null || factura.getMonedaArca().isBlank()) {
            return "Falta dato fiscal obligatorio: código AFIP de la moneda";
        }
        if (tiposIva == null || tiposIva.isEmpty()) {
            return "No se pudo consultar FEParamGetTiposIva para validar las alícuotas";
        }
        for (FacturaVentaDetalle detalle : factura.getDetalles()) {
            try {
                ArcaFacturaElectronicaService.resolverAlicuotaIvaId(
                        BigDecimal.valueOf(detalle.getPorcentajeIva()), tiposIva);
            } catch (RuntimeException ex) {
                return ex.getMessage();
            }
        }
        return null;
    }

    private static boolean esEmisionElectronica(String tipoEmision) {
        if (tipoEmision == null) {
            return false;
        }
        String valor = tipoEmision.trim().toUpperCase(Locale.ROOT);
        return "ELECTRONICA".equals(valor) || "CAE".equals(valor);
    }

    private void completarDatosFiscalesDesdeFactura(ArcaComprobanteDatos datos, FacturaVenta factura,
            long numeroFiscal, BigDecimal cotizacion) {
        datos.setPuntoVenta(factura.getPuntoVenta().getNumero());
        datos.setTipoComprobante(factura.getTipoComprobanteArca());
        datos.setConcepto(factura.getConceptoArca());
        datos.setDocTipo(factura.getDocTipoReceptorArca());
        datos.setDocNro(factura.getDocNroReceptorArca());
        datos.setCondicionIvaReceptorId(factura.getCondicionIvaReceptorArca());
        datos.setCbteDesde(numeroFiscal);
        datos.setCbteHasta(numeroFiscal);
        if (datos.getMonId() == null || datos.getMonId().isBlank()) {
            datos.setMonId(factura.getMonedaArca() != null ? factura.getMonedaArca()
                    : factura.getTipoMoneda().getCodigoAfip());
        }
        datos.setMonCotiz(cotizacion);
    }

    private void registrarIntento(FacturaVenta factura, String procesoId, String estadoIntento,
            Integer puntoVenta, Integer tipoComprobante, Long numeroComprobante, String resultadoGeneral,
            String resultadoDetalle, String cae, String caeVencimiento, String observaciones, String errores,
            String eventos, String mensajeTecnico) {
        int intento = factura.getEnviosArca() == null ? 1 : factura.getEnviosArca().size() + 1;
        FacturaEnvioArca envio = FacturaEnvioArca.builder()
                .factura(factura)
                .fechaIntento(new Date())
                .numeroIntento(intento)
                .estadoIntento(estadoIntento)
                .puntoVenta(puntoVenta)
                .tipoComprobante(tipoComprobante)
                .numeroComprobante(numeroComprobante)
                .resultadoGeneral(resultadoGeneral)
                .resultadoDetalle(resultadoDetalle)
                .cae(cae)
                .caeVencimiento(caeVencimiento)
                .observaciones(truncar(observaciones, 2000))
                .errores(truncar(errores, 2000))
                .eventos(truncar(eventos, 2000))
                .mensajeTecnico(truncar(mensajeTecnico, 1000))
                .procesoEnvioId(procesoId)
                .build();
        if (factura.getEnviosArca() == null) {
            factura.setEnviosArca(new ArrayList<>());
        }
        factura.getEnviosArca().add(envio);
    }

    private void acumularResumen(EnvioArcaProcesoDTO resumen, EnvioArcaItemResultadoDTO item) {
        resumen.getDetalle().add(item);
        resumen.setProcesadas(resumen.getProcesadas() + 1);
        switch (normalizarEstadoIntento(item.getResultado())) {
            case "APROBADA" -> resumen.setAutorizadas(resumen.getAutorizadas() + 1);
            case "RECHAZADA" -> resumen.setRechazadas(resumen.getRechazadas() + 1);
            case "INCIERTO" -> resumen.setResultadoDesconocido(resumen.getResultadoDesconocido() + 1);
            default -> resumen.setNoEnviadasPorValidacion(resumen.getNoEnviadasPorValidacion() + 1);
        }
    }

    private EnvioArcaItemResultadoDTO itemNoEnviado(Long id, Long numero, String motivo) {
        return new EnvioArcaItemResultadoDTO(id, numero, null, "NO_ENVIADA", motivo, null, null, null);
    }

  

    private FacturaReporteDTO aFacturaReporteDTO(FacturaVenta factura) {
        if (factura == null) {
            return null;
        }
        FacturaReporteDTO dto = new FacturaReporteDTO();
        dto.setId(factura.getId());
        dto.setNumeroFactura(factura.getNumero());
        dto.setFechaEmision(factura.getFechaEmision() == null ? null
                : factura.getFechaEmision().toInstant().atZone(ZoneOffset.UTC).toLocalDate());
        if (factura.getCliente() != null) {
            dto.setClienteId(factura.getCliente().getId());
            dto.setClienteDenominacion(factura.getCliente().getDenominacion());
        }
        if (factura.getCondicionIva() != null) {
            dto.setCondicionIvaId(factura.getCondicionIva().getId());
            dto.setCondicionIva(factura.getCondicionIva().getDenominacion());
        }
        if (factura.getPuntoVenta() != null) {
            dto.setPuntoVentaId(factura.getPuntoVenta().getId());
            dto.setPuntoVentaDescripcion(factura.getPuntoVenta().getDescripcion());
        }
        if (factura.getTipoMoneda() != null) {
            dto.setTipoMonedaId(factura.getTipoMoneda().getId());
            dto.setMonedaDenominacion(factura.getTipoMoneda().getDenominacion());
        }
        double importeNeto = factura.getDetalles() == null ? 0
                : factura.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getImporteNeto).sum();
        double importeIva = factura.getDetalles() == null ? 0
                : factura.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getImporteIva).sum();
        dto.setImporteNeto(importeNeto);
        dto.setImporteIva(importeIva);
        dto.setImporteTotal(factura.getImporteTotal());
        dto.setImporteCobrado(factura.getImporteCobrado());
        dto.setImporteSaldo(factura.getImporteSaldo());
        dto.setEstado(factura.getEstado());
        dto.setObservaciones(factura.getObservaciones());
        dto.setDetalles(factura.getDetalles() == null ? List.of() : factura.getDetalles().stream()
                .map(detalle -> new FacturaVentaDetalleDTO(
                        detalle.getId(), detalle.getDescripcion(), detalle.getCantidad(),
                        detalle.getPrecioUnitario(), detalle.getPorcentajeBonificacion(),
                        detalle.getPorcentajeIva(), detalle.getImporteNeto(),
                        detalle.getImporteIva(), detalle.getImporteSubtotal(), 
                        detalle.getListaPrecioArticulo().getArticulo().getId(), detalle.getListaPrecioArticulo().getListaPrecio().getId(), 
                        detalle.getListaPrecioArticulo().getId(), detalle.getListaPrecioArticulo().getArticulo().getRubro().getDenominacion()))
                .toList());
        dto.setCantidadItems(dto.getDetalles().size());
        return dto;
    }

    private FacturaEnvioArcaDTO aEnvioDto(FacturaEnvioArca envio) {
        FacturaEnvioArcaDTO dto = new FacturaEnvioArcaDTO();
        dto.setId(envio.getId());
        dto.setFacturaId(envio.getFactura() != null ? envio.getFactura().getId() : null);
        dto.setFechaIntento(envio.getFechaIntento());
        dto.setNumeroIntento(envio.getNumeroIntento());
        dto.setEstadoIntento(envio.getEstadoIntento());
        dto.setPuntoVenta(envio.getPuntoVenta());
        dto.setTipoComprobante(envio.getTipoComprobante());
        dto.setNumeroComprobante(envio.getNumeroComprobante());
        dto.setResultadoGeneral(envio.getResultadoGeneral());
        dto.setResultadoDetalle(envio.getResultadoDetalle());
        dto.setCae(envio.getCae());
        dto.setCaeVencimiento(envio.getCaeVencimiento());
        dto.setObservaciones(envio.getObservaciones());
        dto.setErrores(envio.getErrores());
        dto.setEventos(envio.getEventos());
        dto.setMensajeTecnico(envio.getMensajeTecnico());
        dto.setProcesoEnvioId(envio.getProcesoEnvioId());
        return dto;
    }

    private String normalizarFiltro(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private Long generarNumeroInterno() {
        puntoVentaRepository.bloquearTodosParaNumeracion();
        return Math.addExact(facturaRepository.obtenerUltimoNumero(), 1L);
    }

    private void sincronizarDetalles(FacturaVenta factura, List<FacturaDetalleRequestDTO> solicitudes,
            Map<Long, FacturaVentaDetalle> detallesExistentes) {
        Set<Long> idsRecibidos = new HashSet<>();
        List<FacturaVentaDetalle> detallesActualizados = new ArrayList<>();
        List<FacturaVentaDetalle> detallesEliminados = new ArrayList<>();
        for (FacturaDetalleRequestDTO solicitud : solicitudes) {
            validarDetalle(solicitud);
            FacturaVentaDetalle detalle;
            if (solicitud.getId() == null) {
                detalle = new FacturaVentaDetalle();
            } else {
                if (!idsRecibidos.add(solicitud.getId())) {
                    throw badRequest("El detalle " + solicitud.getId() + " está repetido en la solicitud");
                }
                detalle = detallesExistentes.get(solicitud.getId());
                if (detalle == null) {
                    throw badRequest("El detalle " + solicitud.getId() + " no pertenece a esta factura");
                }
            }
            mapearDetalle(detalle, solicitud);
            detalle.setFactura(factura);
            detallesActualizados.add(detalle);
        }
        for (Map.Entry<Long, FacturaVentaDetalle> existente : detallesExistentes.entrySet()) {
            if (!idsRecibidos.contains(existente.getKey())) {
                detallesEliminados.add(existente.getValue());
            }
        }
        if (!detallesEliminados.isEmpty()) {
            detalleRepository.deleteAll(detallesEliminados);
        }
        if (factura.getDetalles() == null) {
            factura.setDetalles(new ArrayList<>());
        }
        factura.getDetalles().clear();
        factura.getDetalles().addAll(detallesActualizados);
    }

    private void mapearDetalle(FacturaVentaDetalle detalle, FacturaDetalleRequestDTO request) {
        Articulo articulo = articuloRepository.findById(request.getArticuloId())
                .orElseThrow(() -> noEncontrada("Artículo", request.getArticuloId()));
        ListaPrecioArticulo listaPrecioArticulo = listaPrecioArticuloRepository
                .findById(request.getListaPrecioArticuloId())
                .orElseThrow(() -> noEncontrada("ListaPrecioArticulo", request.getListaPrecioArticuloId()));
        if (!listaPrecioArticulo.getArticulo().getId().equals(articulo.getId())) {
            throw badRequest("El artículo indicado no corresponde al registro de ListaPrecioArticulo");
        }
        detalle.setListaPrecioArticulo(listaPrecioArticulo);
        detalle.setDescripcion(request.getDescripcion() == null || request.getDescripcion().isBlank()
                ? articulo.getDenominacion() : request.getDescripcion().trim());
        detalle.setCantidad(request.getCantidad().doubleValue());
        BigDecimal precioUnitario = redondear(request.getPrecioUnitario());
        detalle.setPrecioUnitario(precioUnitario.doubleValue());
        detalle.setPorcentajeBonificacion(valorOpcional(request.getPorcentajeBonificacion()).doubleValue());
        detalle.setPorcentajeIva(request.getPorcentajeIva().doubleValue());
        BigDecimal bruto = request.getCantidad().multiply(precioUnitario);
        BigDecimal descuento = bruto.multiply(valorOpcional(request.getPorcentajeBonificacion()))
                .divide(CIEN, ESCALA_MONETARIA + 6, REDONDEO);
        BigDecimal neto = redondear(bruto.subtract(descuento));
        BigDecimal iva = redondear(neto.multiply(request.getPorcentajeIva())
                .divide(CIEN, ESCALA_MONETARIA + 6, REDONDEO));
        BigDecimal subtotal = redondear(neto.add(iva));
        detalle.setImporteNeto(neto.doubleValue());
        detalle.setImporteIva(iva.doubleValue());
        detalle.setImporteSubtotal(subtotal.doubleValue());
    }

    private void recalcularTotales(FacturaVenta factura, BigDecimal importeCobrado) {
        BigDecimal totalNeto = BigDecimal.ZERO;
        BigDecimal totalIva = BigDecimal.ZERO;
        for (FacturaVentaDetalle detalle : factura.getDetalles()) {
            totalNeto = totalNeto.add(BigDecimal.valueOf(detalle.getImporteNeto()));
            totalIva = totalIva.add(BigDecimal.valueOf(detalle.getImporteIva()));
        }
        totalNeto = redondear(totalNeto);
        totalIva = redondear(totalIva);
        BigDecimal total = redondear(totalNeto.add(totalIva));
        BigDecimal cobrado = redondear(importeCobrado == null ? BigDecimal.ZERO : importeCobrado);
        if (cobrado.compareTo(total) > 0) {
            throw badRequest("importeCobrado no puede superar el total calculado de la factura");
        }
        factura.setImporteCobrado(cobrado.doubleValue());
        factura.setImporteSaldo(redondear(total.subtract(cobrado)).doubleValue());
        factura.setImporteTotal(total.doubleValue());
    }

    private void validarCabeceraBase(FacturaRequestDTO request) {
        if (request.getFechaEmision() == null || request.getClienteId() == null || request.getClienteId() <= 0
                || request.getCondicionIvaId() == null || request.getCondicionIvaId() <= 0
                || request.getPuntoVentaId() == null || request.getPuntoVentaId() <= 0
                || request.getTipoMonedaId() == null || request.getTipoMonedaId() <= 0
                || request.getImporteCobrado() == null || request.getImporteCobrado().signum() < 0
                || request.getDetalles() == null || request.getDetalles().isEmpty()) {
            throw badRequest("La cabecera y al menos un detalle deben contener todos los datos obligatorios válidos");
        }
    }

    private void validarDetalle(FacturaDetalleRequestDTO request) {
        if (request == null || request.getArticuloId() == null || request.getArticuloId() <= 0
                || request.getListaPrecioArticuloId() == null || request.getListaPrecioArticuloId() <= 0
                || request.getCantidad() == null || request.getCantidad().signum() <= 0
                || request.getPrecioUnitario() == null || request.getPrecioUnitario().signum() < 0
                || !porcentajeBonificacionValido(request.getPorcentajeBonificacion())
                || !porcentajeValido(request.getPorcentajeIva())) {
            throw badRequest("Cada detalle requiere IDs válidos, cantidad positiva, precio no negativo "
                    + "y porcentajes entre 0 y 100");
        }
    }

    private boolean porcentajeValido(BigDecimal porcentaje) {
        return porcentaje != null && porcentaje.signum() >= 0 && porcentaje.compareTo(CIEN) <= 0;
    }

    private boolean porcentajeBonificacionValido(BigDecimal porcentaje) {
        return porcentaje == null || porcentajeValido(porcentaje);
    }

    private Usuario obtenerUsuarioAuditoria() {
        if (usuarioAuditoriaId == null || usuarioAuditoriaId.isBlank()) {
            throw new IllegalStateException(
                    "Debe configurarse app.auditoria.usuario-id (o AUDITORIA_USUARIO_ID) para registrar la auditoría");
        }
        final Long id;
        try {
            id = Long.valueOf(usuarioAuditoriaId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("app.auditoria.usuario-id debe ser un ID numérico", ex);
        }
        return usuarioRepository.findById(id)
                .orElseThrow(() -> noEncontrada("Usuario de auditoría", id));
    }

    private Cliente buscarCliente(Long id) {
        return clienteRepository.findById(id).orElseThrow(() -> noEncontrada("Cliente", id));
    }

    private CondicionIva buscarCondicionIva(Long id) {
        return condicionIvaRepository.findById(id).orElseThrow(() -> noEncontrada("Condición IVA", id));
    }

    private PuntoVenta buscarPuntoVenta(Long id) {
        return puntoVentaRepository.findById(id).orElseThrow(() -> noEncontrada("Punto de venta", id));
    }

    private TipoMoneda buscarTipoMoneda(Long id) {
        return tipoMonedaRepository.findById(id).orElseThrow(() -> noEncontrada("Tipo de moneda", id));
    }

    private ResponseStatusException noEncontrada(String tipo, Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, tipo + " con ID " + id + " no existe");
    }

    private ResponseStatusException badRequest(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private BigDecimal valorOpcional(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, REDONDEO);
    }

    private static String joinMensajes(List<WsfeMessage> mensajes) {
        if (mensajes == null || mensajes.isEmpty()) {
            return null;
        }
        return mensajes.stream()
                .map(WsfeMessage::toString)
                .filter(mensaje -> mensaje != null && !mensaje.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private static String joinNoVacio(String... valores) {
        List<String> resultado = new ArrayList<>();
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                resultado.add(valor);
            }
        }
        return resultado.isEmpty() ? null : String.join(" | ", resultado);
    }

    private static String combinarObs(String actual, String nueva) {
        if (actual == null || actual.isBlank()) {
            return nueva;
        }
        if (nueva == null || nueva.isBlank()) {
            return actual;
        }
        return actual + " | " + nueva;
    }

    private static String truncar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }

    private static String mensajeSeguro(Exception ex) {
        if (ex == null) {
            return "error desconocido";
        }
        String mensaje = ex.getMessage();
        return mensaje == null || mensaje.isBlank() ? ex.getClass().getSimpleName() : mensaje;
    }

    private static Date parseFechaCae(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank()) {
            return null;
        }
        try {
            SimpleDateFormat formato = new SimpleDateFormat("yyyyMMdd");
            formato.setLenient(false);
            return formato.parse(yyyymmdd.trim());
        } catch (ParseException ex) {
            return null;
        }
    }

    
    private static String normalizarEstadoIntento(String estado) {
        if (estado == null || estado.isBlank()) {
            return "NO_ENVIADA";
        }
        return switch (estado.trim().toUpperCase(Locale.ROOT)) {
            case "APROBADA", "APROBADO", "AUTORIZADA" -> "APROBADA";
            case "RECHAZADA", "RECHAZADO" -> "RECHAZADA";
            case "INCIERTO", "ENVIADO" -> "INCIERTO";
            default -> "NO_ENVIADA";
        };
    }

    private static final class DatosEnvio {
        Long facturaId;
        Long numeroInterno;
        Integer puntoVenta;
        Integer tipoComprobante;
        String moneda;
        List<ParametroArca> tiposIva;
        boolean noEnviable;
        EnvioArcaItemResultadoDTO itemNoEnviado;
    }

    private static final class SolicitudEnvio {
        FeCaeSolicitud solicitud;
        Long numeroFiscal;
        boolean noEnviable;
        EnvioArcaItemResultadoDTO itemNoEnviado;
    }

    private record DatosConciliacion(Long facturaId, Integer puntoVenta, Integer tipoComprobante,
            Long numeroFiscal) {
    }
}

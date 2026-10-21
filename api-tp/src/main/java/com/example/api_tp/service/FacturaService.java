package com.example.api_tp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.api_tp.dto.FacturaDetalleRequestDTO;
import com.example.api_tp.dto.FacturaReporteDTO;
import com.example.api_tp.dto.FacturaRequestDTO;
import com.example.api_tp.dto.FacturaVentaDTO;
import com.example.api_tp.dto.FacturaVentaDetalleDTO;
import com.example.api_tp.model.Articulo;
import com.example.api_tp.model.Cliente;
import com.example.api_tp.model.CondicionIva;
import com.example.api_tp.model.FacturaVenta;
import com.example.api_tp.model.FacturaVentaDetalle;
import com.example.api_tp.model.ListaPrecioArticulo;
import com.example.api_tp.model.PuntoVenta;
import com.example.api_tp.model.TipoMoneda;
import com.example.api_tp.model.Usuario;
import com.example.api_tp.repository.ArticuloRepository;
import com.example.api_tp.repository.ClienteRepository;
import com.example.api_tp.repository.CondicionIvaRepository;
import com.example.api_tp.repository.FacturaVentaDetalleRepository;
import com.example.api_tp.repository.FacturaVentaRepository;
import com.example.api_tp.repository.ListaPrecioArticuloRepository;
import com.example.api_tp.repository.PuntoVentaRepository;
import com.example.api_tp.repository.TipoMonedaRepository;
import com.example.api_tp.repository.UsuarioRepository;

@Service
public class FacturaService {

    private static final int ESCALA_MONETARIA = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final FacturaVentaRepository facturaRepository;
    private final FacturaVentaDetalleRepository detalleRepository;
    private final ArticuloRepository articuloRepository;
    private final ClienteRepository clienteRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final UsuarioRepository usuarioRepository;
    private final String usuarioAuditoriaId;

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
            @Value("${app.auditoria.usuario-id:}") String usuarioAuditoriaId) {
        this.facturaRepository = facturaRepository;
        this.detalleRepository = detalleRepository;
        this.articuloRepository = articuloRepository;
        this.clienteRepository = clienteRepository;
        this.condicionIvaRepository = condicionIvaRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioAuditoriaId = usuarioAuditoriaId;
    }

    @Transactional(readOnly = true)
    public List<FacturaVentaDTO> filtrar(
            Long numeroFactura,
            String clienteDenominacion,
            String condicionIva,
            LocalDate fechaDesde,
            LocalDate fechaHasta) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fechaDesde no puede ser posterior a fechaHasta");
        }

        Date inicio = fechaDesde == null ? null : Date.from(fechaDesde.atStartOfDay()
                .toInstant(ZoneOffset.UTC));
        Date finExclusivo = fechaHasta == null ? null : Date.from(fechaHasta.plusDays(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC));

        return facturaRepository.filtrar(
                numeroFactura,
                normalizarFiltro(clienteDenominacion),
                normalizarFiltro(condicionIva),
                inicio,
                finExclusivo)
                .stream()
                .map(this::aFacturaVentaDTO)
                .toList();
    }

    private FacturaVentaDTO aFacturaVentaDTO(FacturaVenta factura) {
        LocalDate fechaEmision = factura.getFechaEmision() == null
                ? null
                : factura.getFechaEmision().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        double importeNeto = factura.getDetalles().stream()
                .mapToDouble(FacturaVentaDetalle::getImporteNeto).sum();
        double importeIva = factura.getDetalles().stream()
                .mapToDouble(FacturaVentaDetalle::getImporteIva).sum();
        List<FacturaVentaDetalleDTO> detalles = factura.getDetalles().stream()
                .map(detalle -> new FacturaVentaDetalleDTO(
                        detalle.getId(),
                        detalle.getDescripcion(),
                        detalle.getCantidad(),
                        detalle.getPrecioUnitario(),
                        detalle.getPorcentajeBonificacion(),
                        detalle.getPorcentajeIva(),
                        detalle.getImporteNeto(),
                        detalle.getImporteIva(),
                        detalle.getImporteSubtotal()))
                .toList();

        return new FacturaVentaDTO(
                factura.getNumero(),
                fechaEmision,
                factura.getCliente().getDenominacion(),
                factura.getCondicionIva().getDenominacion(),
                factura.getTipoMoneda().getDenominacion(),
                factura.getPuntoVenta().getDescripcion(),
                importeNeto,
                importeIva,
                factura.getImporteTotal(),
                factura.getImporteCobrado(),
                factura.getImporteSaldo(),
                factura.getEstado(),
                detalles);
    }

    private String normalizarFiltro(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    @Transactional
    public FacturaReporteDTO crear(FacturaRequestDTO request) {
        validarCabecera(request);
        Usuario usuarioAuditoria = obtenerUsuarioAuditoria();
        Date ahora = new Date();
        FacturaVenta factura = new FacturaVenta();
        aplicarCabecera(factura, request);
        factura.setFechaAlta(ahora);
        factura.setFechaModificacion(ahora);
        factura.setUsuarioCarga(usuarioAuditoria);
        factura.setUsuarioModificacion(usuarioAuditoria);
        factura.setUsuarioBaja(null);
        sincronizarDetalles(factura, request.getDetalles(), Map.of());
        recalcularTotales(factura, request.getImporteCobrado());
        return aReporte(facturaRepository.saveAndFlush(factura));
    }

    @Transactional
    public FacturaReporteDTO actualizar(Long id, FacturaRequestDTO request) {
        validarCabecera(request);
        FacturaVenta factura = facturaRepository.findById(id)
                .orElseThrow(() -> noEncontrada("Factura", id));
        Usuario usuarioAuditoria = obtenerUsuarioAuditoria();

        Map<Long, FacturaVentaDetalle> detallesExistentes = new HashMap<>();
        for (FacturaVentaDetalle detalle : factura.getDetalles()) {
            detallesExistentes.put(detalle.getId(), detalle);
        }

        aplicarCabecera(factura, request);
        sincronizarDetalles(factura, request.getDetalles(), detallesExistentes);
        recalcularTotales(factura, request.getImporteCobrado());
        factura.setFechaModificacion(new Date());
        factura.setUsuarioModificacion(usuarioAuditoria);
        return aReporte(facturaRepository.saveAndFlush(factura));
    }

    @Transactional
    public void eliminar(Long id) {
        FacturaVenta factura = facturaRepository.findById(id)
                .orElseThrow(() -> noEncontrada("Factura", id));
        Usuario usuarioAuditoria = obtenerUsuarioAuditoria();
        Date ahora = new Date();
        factura.setEstado("ANULADA");
        factura.setFechaAnulacion(ahora);
        factura.setFechaBaja(ahora);
        factura.setUsuarioBaja(usuarioAuditoria);
        factura.setFechaModificacion(ahora);
        factura.setUsuarioModificacion(usuarioAuditoria);
        facturaRepository.saveAndFlush(factura);
    }

    private void aplicarCabecera(FacturaVenta factura, FacturaRequestDTO request) {
        factura.setNumero(request.getNumero());
        factura.setFechaEmision(request.getFechaEmision());
        factura.setCliente(buscarCliente(request.getClienteId()));
        factura.setCondicionIva(buscarCondicionIva(request.getCondicionIvaId()));
        factura.setPuntoVenta(buscarPuntoVenta(request.getPuntoVentaId()));
        factura.setTipoMoneda(buscarTipoMoneda(request.getTipoMonedaId()));
        factura.setCae(request.getCae());
        factura.setCaeFechaVencimiento(request.getCaeFechaVencimiento());
        factura.setResultadoAfip(request.getResultadoAfip().trim());
        factura.setMotivoRechazo(request.getMotivoRechazo());
        factura.setEstado(request.getEstado().trim());
        factura.setFechaAnulacion(request.getFechaAnulacion());
        factura.setObservaciones(request.getObservaciones());
    }

        private FacturaReporteDTO aReporte(FacturaVenta factura) {
        LocalDate fechaEmision = factura.getFechaEmision() == null
            ? null
            : factura.getFechaEmision().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        return new FacturaReporteDTO(
            factura.getNumero(),
            fechaEmision,
            factura.getCliente().getDenominacion(),
            factura.getCondicionIva().getDenominacion(),
            factura.getPuntoVenta().getDescripcion(),
            factura.getImporteTotal(),
            factura.getDetalles().size());
        }

    private void sincronizarDetalles(
            FacturaVenta factura,
            List<FacturaDetalleRequestDTO> solicitudes,
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
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El detalle " + solicitud.getId() + " está repetido en la solicitud");
                }
                detalle = detallesExistentes.get(solicitud.getId());
                if (detalle == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El detalle " + solicitud.getId() + " no pertenece a esta factura");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El artículo indicado no corresponde al registro de ListaPrecioArticulo");
        }

        detalle.setListaPrecioArticulo(listaPrecioArticulo);
        detalle.setDescripcion(request.getDescripcion() == null || request.getDescripcion().isBlank()
                ? articulo.getDenominacion()
                : request.getDescripcion().trim());
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
        BigDecimal cobrado = redondear(importeCobrado);
        if (cobrado.compareTo(total) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "importeCobrado no puede superar el total calculado de la factura");
        }
        factura.setImporteCobrado(cobrado.doubleValue());
        factura.setImporteSaldo(redondear(total.subtract(cobrado)).doubleValue());
        factura.setImporteTotal(total.doubleValue());
    }

    private void validarCabecera(FacturaRequestDTO request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La solicitud es obligatoria");
        }
        if (request.getNumero() == null || request.getNumero() <= 0
                || request.getFechaEmision() == null
                || request.getClienteId() == null || request.getClienteId() <= 0
                || request.getCondicionIvaId() == null || request.getCondicionIvaId() <= 0
                || request.getPuntoVentaId() == null || request.getPuntoVentaId() <= 0
                || request.getTipoMonedaId() == null || request.getTipoMonedaId() <= 0
                || request.getImporteCobrado() == null || request.getImporteCobrado().signum() < 0
                || request.getResultadoAfip() == null || request.getResultadoAfip().isBlank()
                || request.getEstado() == null || request.getEstado().isBlank()
                || request.getDetalles() == null || request.getDetalles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cabecera y al menos un detalle deben contener todos los datos obligatorios válidos");
        }
    }

    private void validarDetalle(FacturaDetalleRequestDTO request) {
        if (request == null || request.getArticuloId() == null || request.getArticuloId() <= 0
                || request.getListaPrecioArticuloId() == null || request.getListaPrecioArticuloId() <= 0
                || request.getCantidad() == null || request.getCantidad().signum() <= 0
                || request.getPrecioUnitario() == null || request.getPrecioUnitario().signum() < 0
                || !porcentajeBonificacionValido(request.getPorcentajeBonificacion())
                || !porcentajeValido(request.getPorcentajeIva())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cada detalle requiere IDs válidos, cantidad positiva, precio no negativo y porcentajes entre 0 y 100");
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

    private BigDecimal valorOpcional(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, REDONDEO);
    }
}

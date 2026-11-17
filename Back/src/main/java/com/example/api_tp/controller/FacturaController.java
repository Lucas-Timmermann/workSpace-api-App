package com.example.api_tp.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api_tp.dto.EnvioArcaItemResultadoDTO;
import com.example.api_tp.dto.EnvioArcaProcesoDTO;
import com.example.api_tp.dto.EnvioArcaRequestDTO;
import com.example.api_tp.dto.FacturaEnvioArcaDTO;
import com.example.api_tp.dto.FacturaReporteDTO;
import com.example.api_tp.dto.FacturaRequestDTO;
import com.example.api_tp.dto.FacturaVentaPaginaDTO;
import com.example.api_tp.service.FacturaPdfService;
import com.example.api_tp.service.FacturaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/facturas")
@Validated
@RequiredArgsConstructor
public class FacturaController {

    private final FacturaService facturaService;
    private final FacturaPdfService facturaPdfService;


    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarFacturasPdf() {
        byte[] pdf = facturaPdfService.generarFacturas();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"facturas.pdf\"")
                .contentLength(pdf.length)
                .body(pdf);
    }

    @GetMapping
    public ResponseEntity<FacturaVentaPaginaDTO> getFacturasVenta(
            @RequestParam(name = "numeroFactura", required = false) Long numeroFactura,
            @RequestParam(name = "clienteDenominacion", required = false) String clienteDenominacion,
            @RequestParam(name = "condicionIva", required = false) String condicionIva,
            @RequestParam(name = "fechaDesde", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaDesde,
            @RequestParam(name = "fechaHasta", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaHasta,
            @RequestParam(name = "page", defaultValue = "0") int pagina) {
        return ResponseEntity.ok(facturaService.filtrar(
                numeroFactura, clienteDenominacion, condicionIva, fechaDesde, fechaHasta, pagina));
    }

    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(@Valid @RequestBody FacturaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facturaService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FacturaReporteDTO> actualizar(@PathVariable Long id,
            @Valid @RequestBody FacturaRequestDTO request) {
        return ResponseEntity.ok(facturaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        facturaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    

    @GetMapping("/pendientes")
    public ResponseEntity<FacturaVentaPaginaDTO> listarPendientes(
            @RequestParam(name = "numeroFactura", required = false) Long numeroFactura,
            @RequestParam(name = "clienteDenominacion", required = false) String clienteDenominacion,
            @RequestParam(name = "fechaDesde", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaDesde,
            @RequestParam(name = "fechaHasta", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaHasta,
            @RequestParam(name = "page", defaultValue = "0") int pagina) {
        return ResponseEntity.ok(facturaService.listarPendientes(
                numeroFactura, clienteDenominacion, fechaDesde, fechaHasta, pagina));
    }

    @GetMapping("/autorizadas") 
    public ResponseEntity<List<FacturaReporteDTO>> listarAutorizadas(
        @RequestParam(required = false) Long numero,
        @RequestParam(required = false) String cliente) {
    return ResponseEntity.ok(facturaService.listarAutorizadas(numero, cliente));
    }


    @GetMapping("/{id}")
    public ResponseEntity<FacturaReporteDTO> consultarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(facturaService.consultarPorId(id));
    }

    @PostMapping("/{id}/conciliar")
    public ResponseEntity<EnvioArcaItemResultadoDTO> conciliar(@PathVariable Long id,
            @RequestParam(name = "procesoId", required = false) String procesoId) {
        return ResponseEntity.ok(facturaService.conciliarResultadoIncierto(id, procesoId));
    }

    @PostMapping("/envios/arca")
    public ResponseEntity<EnvioArcaProcesoDTO> enviarAArca(
            @RequestBody(required = false) EnvioArcaRequestDTO request) {
        return ResponseEntity.ok(facturaService.enviarFacturasAArca(request));
    }

    @GetMapping("/envios/{envioId}")
    public ResponseEntity<EnvioArcaProcesoDTO> consultarEnvio(@PathVariable String envioId) {
        return ResponseEntity.ok(facturaService.consultarProcesoEnvio(envioId));
    }

    @GetMapping("/{id}/envios")
    public ResponseEntity<List<FacturaEnvioArcaDTO>> historialEnvios(@PathVariable Long id) {
        return ResponseEntity.ok(facturaService.consultarHistorialEnvios(id));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdfFacturaAutorizada(@PathVariable Long id) {
        byte[] pdf = facturaPdfService.generarFacturaAutorizada(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"factura-" + id + ".pdf\"")
                .contentLength(pdf.length)
                .body(pdf);
    }    

}

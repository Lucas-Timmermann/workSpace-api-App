package com.example.api_tp.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.api_tp.dto.FacturaReporteDTO;
import com.example.api_tp.dto.FacturaRequestDTO;
import com.example.api_tp.dto.FacturaVentaDTO;
import com.example.api_tp.service.FacturaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/facturas")
@Validated
@RequiredArgsConstructor
public class FacturaController {

    private final FacturaService facturaService;

    @GetMapping
    public ResponseEntity<List<FacturaVentaDTO>> getFacturasVenta(
            @RequestParam(name = "numeroFactura", required = false) Long numeroFactura,
            @RequestParam(name = "clienteDenominacion", required = false) String clienteDenominacion,
            @RequestParam(name = "condicionIva", required = false) String condicionIva,
            @RequestParam(name = "fechaDesde", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaDesde,
            @RequestParam(name = "fechaHasta", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fechaHasta) {
        return ResponseEntity.ok(facturaService.filtrar(
                numeroFactura, clienteDenominacion, condicionIva, fechaDesde, fechaHasta));
    }

    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(@Valid @RequestBody FacturaRequestDTO request) {
        FacturaReporteDTO respuesta = facturaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FacturaReporteDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody FacturaRequestDTO request) {
        return ResponseEntity.ok(facturaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        facturaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

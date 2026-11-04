package com.example.api_tp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.api_tp.dto.CatalogoDenominacionDTO;
import com.example.api_tp.dto.ListaPrecioArticuloDTO;
import com.example.api_tp.dto.PuntoVentaDTO;
import com.example.api_tp.service.CatalogoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogoService;

    @GetMapping("/puntos-venta")
    public ResponseEntity<List<PuntoVentaDTO>> obtenerPuntosVenta() {
        return ResponseEntity.ok(catalogoService.obtenerPuntosVenta());
    }

    @GetMapping("/clientes")
    public ResponseEntity<List<CatalogoDenominacionDTO>> obtenerClientes() {
        return ResponseEntity.ok(catalogoService.obtenerClientes());
    }

    @GetMapping("/condiciones-iva")
    public ResponseEntity<List<CatalogoDenominacionDTO>> obtenerCondicionesIva() {
        return ResponseEntity.ok(catalogoService.obtenerCondicionesIva());
    }

    @GetMapping("/tipos-moneda")
    public ResponseEntity<List<CatalogoDenominacionDTO>> obtenerTiposMoneda() {
        return ResponseEntity.ok(catalogoService.obtenerTiposMoneda());
    }

    @GetMapping("/listas-precio")
    public ResponseEntity<List<CatalogoDenominacionDTO>> obtenerListasPrecio() {
        return ResponseEntity.ok(catalogoService.obtenerListasPrecio());
    }

    @GetMapping("/listas-precio/{listaPrecioId}/articulos")
    public ResponseEntity<List<ListaPrecioArticuloDTO>> obtenerArticulosDeLista(
            @PathVariable Long listaPrecioId) {
        return ResponseEntity.ok(catalogoService.obtenerArticulosDeLista(listaPrecioId));
    }
}
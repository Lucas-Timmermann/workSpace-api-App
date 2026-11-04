package com.example.api_tp.dto;

public record ListaPrecioArticuloDTO(
        Long id,
        Long articuloId,
        String denominacion,
        String rubro,
        double precioVenta) {
}
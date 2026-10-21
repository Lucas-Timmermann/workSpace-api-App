package com.example.api_tp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacturaVentaDetalleDTO {
    private Long id;
    private String descripcion;
    private double cantidad;
    private double precioUnitario;
    private double porcentajeBonificacion;
    private double porcentajeIva;
    private double importeNeto;
    private double importeIva;
    private double importeSubtotal;
}
package com.example.api_tp.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacturaVentaDTO {
    private Long numeroFactura;
    private LocalDate fechaEmision;
    private String clienteDenominacion;
    private String condicionIva;
    private String monedaDenominacion;
    private String puntoVentaDescripcion;
    private double importeNeto;
    private double importeIva;
    private double importeTotal;
    private double importeCobrado;
    private double importeSaldo;
    private String estado;
    private List<FacturaVentaDetalleDTO> detalles;
}
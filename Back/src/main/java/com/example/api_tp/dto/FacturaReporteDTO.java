package com.example.api_tp.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class FacturaReporteDTO {
    private Long id;
    private Long numeroFactura;
    private LocalDate fechaEmision;

    private Long clienteId;
    private String clienteDenominacion;
    private Long condicionIvaId;
    private String condicionIva;
    private Long puntoVentaId;
    private String puntoVentaDescripcion;
    private Long tipoMonedaId;
    private String monedaDenominacion;

    private double importeNeto;
    private double importeIva;
    private double importeTotal;
    private double importeCobrado;
    private double importeSaldo;

    private String estado;
    private String observaciones;
    private List<FacturaVentaDetalleDTO> detalles;
    
    private long cantidadItems;
}

package com.example.api_tp.dto;

import java.util.Date;

import lombok.Data;

@Data
public class FacturaEnvioArcaDTO {
    private Long id;
    private Long facturaId;
    private Date fechaIntento;
    private Integer numeroIntento;
    private String estadoIntento;
    private Integer puntoVenta;
    private Integer tipoComprobante;
    private Long numeroComprobante;
    private String resultadoGeneral;
    private String resultadoDetalle;
    private String cae;
    private String caeVencimiento;
    private String observaciones;
    private String errores;
    private String eventos;
    private String mensajeTecnico;
    private String procesoEnvioId;
}

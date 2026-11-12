package com.example.api_tp.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class EnvioArcaProcesoDTO {
    private String envioId;
    private int procesadas;
    private int autorizadas;
    private int rechazadas;
    private int resultadoDesconocido;
    private int noEnviadasPorValidacion;
    private List<EnvioArcaItemResultadoDTO> detalle = new ArrayList<>();
}

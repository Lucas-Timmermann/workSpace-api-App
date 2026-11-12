package com.example.api_tp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvioArcaItemResultadoDTO {
    private Long facturaId;
    private Long numeroInterno;
    private String estado;
    private String resultado; // APROBADA, RECHAZADA, INCIERTO, NO_ENVIADA
    private String mensaje;
    private String cae;
    private String caeVencimiento;
    private Long numeroFiscal;
}

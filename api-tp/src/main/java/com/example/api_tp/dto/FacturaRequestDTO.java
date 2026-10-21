package com.example.api_tp.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FacturaRequestDTO {
    @NotNull
    @Positive
    private Long numero;

    @NotNull
    private Date fechaEmision;

    @NotNull
    @Positive
    private Long clienteId;

    @NotNull
    @Positive
    private Long condicionIvaId;

    @NotNull
    @Positive
    private Long puntoVentaId;

    @NotNull
    @Positive
    private Long tipoMonedaId;

    @NotNull
    @PositiveOrZero
    private BigDecimal importeCobrado;

    private String cae;
    private Date caeFechaVencimiento;

    @NotBlank
    private String resultadoAfip;

    private String motivoRechazo;

    @NotBlank
    private String estado;

    private Date fechaAnulacion;
    private String observaciones;

    @NotEmpty
    private List<@Valid FacturaDetalleRequestDTO> detalles;
}

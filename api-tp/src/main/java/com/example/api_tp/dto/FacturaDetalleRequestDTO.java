package com.example.api_tp.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
public class FacturaDetalleRequestDTO {
    @Positive
    private Long id;

    @NotNull
    @Positive
    private Long articuloId;

    @NotNull
    @Positive
    private Long listaPrecioArticuloId;

    private String descripcion;

    @NotNull
    @Positive
    private BigDecimal cantidad;

    @NotNull
    @PositiveOrZero
    private BigDecimal precioUnitario;

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal porcentajeBonificacion;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal porcentajeIva;

}

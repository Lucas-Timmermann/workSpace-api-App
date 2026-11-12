package com.example.api_tp.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class FacturaRequestDTO {

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

    private String observaciones;

    @NotEmpty
    private List<@Valid FacturaDetalleRequestDTO> detalles;

   
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long numero;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String estado;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String resultadoAfip;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String cae;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Date caeFechaVencimiento;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String motivoRechazo;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Date fechaAnulacion;

    @JsonAnySetter
    public void rechazarPropiedadDesconocida(String nombre, Object valor) {
       
        if (CAMPOS_DE_CONTROL.contains(nombre)) {
            return;
        }
        throw new IllegalArgumentException("Propiedad no permitida en la solicitud: " + nombre);
    }

    private static final java.util.Set<String> CAMPOS_DE_CONTROL = java.util.Set.of(
            "numero", "estado", "resultadoAfip", "cae", "caeFechaVencimiento",
            "motivoRechazo", "fechaAnulacion");
}

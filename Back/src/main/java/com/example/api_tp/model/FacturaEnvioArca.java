package com.example.api_tp.model;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "factura_envio_arca")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"factura"})
@EqualsAndHashCode(callSuper = true, exclude = {"factura"})
public class FacturaEnvioArca extends EntityId {

    @ManyToOne
    @JoinColumn(name = "factura_id", nullable = false)
    private FacturaVenta factura;

    @Column(nullable = false)
    private Date fechaIntento;

    private Integer numeroIntento;
    private String estadoIntento; // ENVIADO, APROBADO, RECHAZADO, INCIERTO, ERROR_VALIDACION
    private Integer puntoVenta;
    private Integer tipoComprobante;
    private Long numeroComprobante;
    private String resultadoGeneral;
    private String resultadoDetalle;
    private String cae;
    private String caeVencimiento;

    @Column(length = 2000)
    private String observaciones;

    @Column(length = 2000)
    private String errores;

    @Column(length = 2000)
    private String eventos;

    @Column(length = 1000)
    private String mensajeTecnico;

    private String procesoEnvioId;
}

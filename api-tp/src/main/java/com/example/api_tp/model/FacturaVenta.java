package com.example.api_tp.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "factura_venta")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString(exclude = {"detalles"})
@EqualsAndHashCode(callSuper = true, exclude = {"detalles"})
public class FacturaVenta extends AuditoriaApp {
    @Column(nullable = false)
    private Long numero;
    @Column(nullable = false)
    private Date fechaEmision;
    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;
    @ManyToOne
    @JoinColumn(name = "condicion_iva_id", nullable = false)
    private CondicionIva condicionIva;
    @ManyToOne
    @JoinColumn(name = "tipo_moneda_id", nullable = false)
    private TipoMoneda tipoMoneda;
    @ManyToOne
    @JoinColumn(name = "punto_venta_id", nullable = false)
    private PuntoVenta puntoVenta;
    @Column(nullable = false)
    private double importeCobrado;
    @Column(nullable = false)
    private double importeSaldo;
    @Column(nullable = false)
    private double importeTotal;
    private String cae;
    private Date caeFechaVencimiento;
    @Column(nullable = false)
    private String resultadoAfip;
    private String motivoRechazo;
    @Column(nullable = false)
    private String estado;
    private Date fechaAnulacion;
    private String observaciones;
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL)
    @Builder.Default
    private List<FacturaVentaDetalle> detalles = new ArrayList<>();

    public void addDetalle(FacturaVentaDetalle detalle) {
        detalles.add(detalle);
        detalle.setFactura(this);
    }
}

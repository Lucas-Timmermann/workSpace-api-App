package com.example.api_tp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "factura_venta_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"factura", "listaPrecioArticulo"})
@EqualsAndHashCode(callSuper = true, exclude = {"factura"})
public class FacturaVentaDetalle extends EntityId {
    @ManyToOne
    @JoinColumn(name = "factura_id", nullable = false)
    private FacturaVenta factura;
    @ManyToOne
    @JoinColumn(name = "lista_precio_articulo_id", nullable = false)
    private ListaPrecioArticulo listaPrecioArticulo;
    private String descripcion;
    @Column(nullable = false)
    private double cantidad;
    @Column(nullable = false)
    private double precioUnitario;
    private double porcentajeBonificacion;
    private double importeNeto;
    private double importeIva;
    @Column(nullable = false)
    private double importeSubtotal;
    @Column (nullable = false)
    private double porcentajeIva;
}

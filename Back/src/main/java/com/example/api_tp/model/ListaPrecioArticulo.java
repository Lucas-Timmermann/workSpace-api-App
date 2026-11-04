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
@Table(name = "lista_precio_articulo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"listaPrecio", "articulo"})
@EqualsAndHashCode(callSuper = true, exclude = {"listaPrecio", "articulo"})
public class ListaPrecioArticulo extends AuditoriaApp {
    @ManyToOne
    @JoinColumn(name = "lista_precio_id", nullable = false)
    private ListaPrecio listaPrecio;
    @Column(nullable = false)
    private double precioVenta;
    @ManyToOne
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
}

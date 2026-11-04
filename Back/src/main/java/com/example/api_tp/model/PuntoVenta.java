package com.example.api_tp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "punto_venta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class PuntoVenta extends AuditoriaApp {
    @Column(nullable = false)
    private int numero;
    @Column(nullable = false)
    private String descripcion;
    @Column(nullable = false)
    private String tipoEmision;
    @Column(nullable = false)
    private String domicilioComercial;
}

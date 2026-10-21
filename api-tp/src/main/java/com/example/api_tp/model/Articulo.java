package com.example.api_tp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "articulo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"rubro", "marca"})
public class Articulo extends AuditoriaApp {
    @ManyToOne
    @JoinColumn(name = "rubro_id", nullable = false)
    private Rubro rubro;
    @Column(nullable = false)
    private String codigo;
    @Column(nullable = false)
    private String denominacion;
    @ManyToOne
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;
}

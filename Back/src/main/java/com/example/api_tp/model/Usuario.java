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
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Usuario extends EntityId {
    @Column(nullable = false)
    private String usuario;
    @Column(nullable = false)
    private String clave;
    @Column(nullable = false)
    private String nombre;
    @Column(nullable = false)
    private String apellido;
}

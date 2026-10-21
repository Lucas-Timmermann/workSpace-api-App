package com.example.api_tp.model;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public abstract class AuditoriaApp extends EntityId {
    @Column(name = "fecha_alta", nullable = false)
    protected Date fechaAlta;
    @Column(name = "fecha_baja")
    protected Date fechaBaja;
    @Column(name = "fecha_modificacion", nullable = false)
    protected Date fechaModificacion;
    @ManyToOne
    @JoinColumn(name = "usuario_carga_id", nullable = false)
    protected Usuario usuarioCarga;
    @ManyToOne
    @JoinColumn(name = "usuario_baja_id")
    protected Usuario usuarioBaja;
    @ManyToOne
    @JoinColumn(name = "usuario_modificacion_id", nullable = false)
    protected Usuario usuarioModificacion;
}

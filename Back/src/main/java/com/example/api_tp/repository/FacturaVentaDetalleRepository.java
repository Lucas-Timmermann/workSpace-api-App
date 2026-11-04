package com.example.api_tp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.api_tp.model.FacturaVentaDetalle;

public interface FacturaVentaDetalleRepository extends JpaRepository<FacturaVentaDetalle, Long> {
}

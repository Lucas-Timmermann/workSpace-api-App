package com.example.api_tp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.api_tp.model.FacturaEnvioArca;

public interface FacturaEnvioArcaRepository extends JpaRepository<FacturaEnvioArca, Long> {
    List<FacturaEnvioArca> findByFacturaIdOrderByIdAsc(Long facturaId);
    List<FacturaEnvioArca> findByProcesoEnvioIdOrderByIdAsc(String procesoEnvioId);
}

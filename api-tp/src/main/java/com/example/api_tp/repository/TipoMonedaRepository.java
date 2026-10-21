package com.example.api_tp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.api_tp.model.TipoMoneda;

public interface TipoMonedaRepository extends JpaRepository<TipoMoneda, Long> {
}

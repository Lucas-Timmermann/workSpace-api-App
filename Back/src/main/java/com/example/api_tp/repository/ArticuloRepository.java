package com.example.api_tp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.api_tp.model.Articulo;

public interface ArticuloRepository extends JpaRepository<Articulo, Long> {
}

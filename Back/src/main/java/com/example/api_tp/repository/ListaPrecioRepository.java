package com.example.api_tp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.api_tp.model.ListaPrecio;

public interface ListaPrecioRepository extends JpaRepository<ListaPrecio, Long> {
}
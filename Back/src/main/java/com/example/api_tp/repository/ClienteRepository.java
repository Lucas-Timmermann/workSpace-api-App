package com.example.api_tp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.api_tp.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}

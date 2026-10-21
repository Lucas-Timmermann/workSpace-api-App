package com.example.api_tp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.api_tp.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
}

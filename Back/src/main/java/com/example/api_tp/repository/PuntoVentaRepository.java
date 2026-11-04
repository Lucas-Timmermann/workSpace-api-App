package com.example.api_tp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.example.api_tp.model.PuntoVenta;

import jakarta.persistence.LockModeType;

public interface PuntoVentaRepository extends JpaRepository<PuntoVenta, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select pv from PuntoVenta pv order by pv.id")
	java.util.List<PuntoVenta> bloquearTodosParaNumeracion();
}

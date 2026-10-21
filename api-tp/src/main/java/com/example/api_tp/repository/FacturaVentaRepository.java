package com.example.api_tp.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.api_tp.model.FacturaVenta;

public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long> {

    @Query("""
						select distinct f
						from FacturaVenta f
						join fetch f.cliente c
						join fetch f.condicionIva ci
						join fetch f.tipoMoneda tm
						join fetch f.puntoVenta pv
						left join fetch f.detalles d
						where (:numero is null or f.numero = :numero)
							and (:cliente is null or lower(c.denominacion) like concat('%', lower(:cliente), '%'))
							and (:condicionIva is null or lower(ci.denominacion) like concat('%', lower(:condicionIva), '%'))
							and (:fechaDesde is null or f.fechaEmision >= :fechaDesde)
							and (:fechaHastaExclusiva is null or f.fechaEmision < :fechaHastaExclusiva)
						order by f.numero
						""")
    List<FacturaVenta> filtrar(
						@Param("numero") Long numero,
						@Param("cliente") String clienteDenominacion,
						@Param("condicionIva") String condicionIva,
						@Param("fechaDesde") Date fechaDesde,
						@Param("fechaHastaExclusiva") Date fechaHastaExclusiva);
}

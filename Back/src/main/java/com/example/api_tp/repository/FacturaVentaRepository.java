package com.example.api_tp.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.api_tp.model.FacturaVenta;

public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long> {

	    @Query(value = """
		    select f
		    from FacturaVenta f
		    join f.cliente c
		    join f.condicionIva ci
		    where (:numero is null or f.numero = :numero)
			and (:cliente is null or lower(c.denominacion) like concat('%', lower(:cliente), '%'))
			and (:condicionIva is null or lower(ci.denominacion) like concat('%', lower(:condicionIva), '%'))
			and (:fechaDesde is null or f.fechaEmision >= :fechaDesde)
			and (:fechaHastaExclusiva is null or f.fechaEmision < :fechaHastaExclusiva)
		    """,
		    countQuery = """
		    select count(f)
		    from FacturaVenta f
		    join f.cliente c
		    join f.condicionIva ci
		    where (:numero is null or f.numero = :numero)
			and (:cliente is null or lower(c.denominacion) like concat('%', lower(:cliente), '%'))
			and (:condicionIva is null or lower(ci.denominacion) like concat('%', lower(:condicionIva), '%'))
			and (:fechaDesde is null or f.fechaEmision >= :fechaDesde)
			and (:fechaHastaExclusiva is null or f.fechaEmision < :fechaHastaExclusiva)
		    """)
	    Page<FacturaVenta> filtrar(
		    @Param("numero") Long numero,
		    @Param("cliente") String clienteDenominacion,
		    @Param("condicionIva") String condicionIva,
		    @Param("fechaDesde") Date fechaDesde,
		    @Param("fechaHastaExclusiva") Date fechaHastaExclusiva,
		    Pageable pageable);

	    @Query("""
		    select coalesce(max(f.numero), 0)
		    from FacturaVenta f
		    """)
	    Long obtenerUltimoNumero();

	    @Query("""
		    select distinct f
		    from FacturaVenta f
		    join fetch f.cliente
		    join fetch f.condicionIva
		    join fetch f.tipoMoneda
		    join fetch f.puntoVenta
		    left join fetch f.detalles
		    where f.id in :ids
		    """)
	    List<FacturaVenta> buscarConDetallesPorIds(@Param("ids") List<Long> ids);

		@Query("""
				select distinct f
				from FacturaVenta f
				join fetch f.cliente
				join fetch f.condicionIva
				join fetch f.tipoMoneda
				join fetch f.puntoVenta
				left join fetch f.detalles d
				left join fetch d.listaPrecioArticulo
				order by f.numero, f.id
				""")
		List<FacturaVenta> buscarTodasConDetallesParaPdf();
}

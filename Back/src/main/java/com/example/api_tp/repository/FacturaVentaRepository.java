package com.example.api_tp.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.api_tp.model.FacturaVenta;

import jakarta.persistence.LockModeType;

public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long> {

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
			left join fetch d.listaPrecioArticulo lpa
			left join fetch lpa.articulo
			where f.estado = 'AUTORIZADA'
			order by f.numero, f.id
			""")
	List<FacturaVenta> buscarTodasConDetallesParaPdf();

	@Query("""
			select distinct f
			from FacturaVenta f
			join fetch f.cliente
			join fetch f.condicionIva
			join fetch f.tipoMoneda
			join fetch f.puntoVenta
			left join fetch f.detalles d
			left join fetch d.listaPrecioArticulo lpa
			left join fetch lpa.articulo
			where f.id = :id
			""")
	Optional<FacturaVenta> buscarPorIdConDetalles(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from FacturaVenta f where f.id = :id")
	Optional<FacturaVenta> buscarPorIdParaActualizacion(@Param("id") Long id);

	@Query(value = """
			select f
			from FacturaVenta f
			where (:estados is null or f.estado in :estados)
			  and (:numero is null or f.numero = :numero)
			  and (:cliente is null or lower(f.cliente.denominacion) like concat('%', lower(:cliente), '%'))
			  and (:condicionIva is null or lower(f.condicionIva.denominacion) like concat('%', lower(:condicionIva), '%'))
			  and (:fechaDesde is null or f.fechaEmision >= :fechaDesde)
			  and (:fechaHastaExclusiva is null or f.fechaEmision < :fechaHastaExclusiva)
			""",
			countQuery = """
			select count(f)
			from FacturaVenta f
			where (:estados is null or f.estado in :estados)
			  and (:numero is null or f.numero = :numero)
			  and (:cliente is null or lower(f.cliente.denominacion) like concat('%', lower(:cliente), '%'))
			  and (:condicionIva is null or lower(f.condicionIva.denominacion) like concat('%', lower(:condicionIva), '%'))
			  and (:fechaDesde is null or f.fechaEmision >= :fechaDesde)
			  and (:fechaHastaExclusiva is null or f.fechaEmision < :fechaHastaExclusiva)
			""")
	Page<FacturaVenta> filtrarPorEstados(
			@Param("estados") List<String> estados,
			@Param("numero") Long numero,
			@Param("cliente") String cliente,
			@Param("condicionIva") String condicionIva,
			@Param("fechaDesde") Date fechaDesde,
			@Param("fechaHastaExclusiva") Date fechaHastaExclusiva,
			Pageable pageable);

	List<FacturaVenta> findByEstado(String estado);
}

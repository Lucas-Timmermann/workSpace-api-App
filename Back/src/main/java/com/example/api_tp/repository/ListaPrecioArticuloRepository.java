package com.example.api_tp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.api_tp.model.ListaPrecioArticulo;

public interface ListaPrecioArticuloRepository extends JpaRepository<ListaPrecioArticulo, Long> {

    @Query("""
	    select lpa
	    from ListaPrecioArticulo lpa
	    join fetch lpa.articulo articulo
	    join fetch articulo.rubro rubro
	    where lpa.listaPrecio.id = :listaPrecioId
	    order by lpa.id
	    """)
    java.util.List<ListaPrecioArticulo> buscarPorListaPrecioId(
	    @Param("listaPrecioId") Long listaPrecioId);
}

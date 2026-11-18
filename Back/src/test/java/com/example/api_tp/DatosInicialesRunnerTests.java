package com.example.api_tp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.api_tp.config.DatosInicialesRunner;
import com.example.api_tp.model.ListaPrecio;
import com.example.api_tp.model.ListaPrecioArticulo;
import com.example.api_tp.model.PuntoVenta;
import com.example.api_tp.model.TipoMoneda;
import com.example.api_tp.repository.ArticuloRepository;
import com.example.api_tp.repository.ClienteRepository;
import com.example.api_tp.repository.CondicionIvaRepository;
import com.example.api_tp.repository.ListaPrecioArticuloRepository;
import com.example.api_tp.repository.ListaPrecioRepository;
import com.example.api_tp.repository.PuntoVentaRepository;
import com.example.api_tp.repository.TipoMonedaRepository;
import com.example.api_tp.repository.UsuarioRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seed;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=true"
})
class DatosInicialesRunnerTests {

    @Autowired
    private DatosInicialesRunner runner;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private ListaPrecioRepository listaPrecioRepository;
    @Autowired
    private CondicionIvaRepository condicionIvaRepository;
    @Autowired
    private TipoMonedaRepository tipoMonedaRepository;
    @Autowired
    private ArticuloRepository articuloRepository;
    @Autowired
    private ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    @Autowired
    private PuntoVentaRepository puntoVentaRepository;

    @Test
    void precargaDatosInicialesYNoDuplicaAlReejecutar() {
        verificarCantidades();

        runner.run();

        verificarCantidades();
    }

    private void verificarCantidades() {
        assertEquals(1, usuarioRepository.count());
        assertEquals(10, clienteRepository.count());
        assertEquals(2, listaPrecioRepository.count());
        assertEquals(6, condicionIvaRepository.count());
        assertEquals(4, tipoMonedaRepository.count());
        assertEquals(20, articuloRepository.count());
        assertEquals(40, listaPrecioArticuloRepository.count());
        assertEquals(4, puntoVentaRepository.count());

        assertEquals(Set.of("MAYORISTA", "MINORISTA"), listaPrecioRepository.findAll().stream()
                .map(ListaPrecio::getCodigo)
                .collect(Collectors.toSet()));

        assertTrue(condicionIvaRepository.findAll().stream()
                .anyMatch(condicion -> condicion.getCodigoAfip() == 5
                        && "Consumidor Final".equals(condicion.getDenominacion())));
        assertEquals(Set.of("PES", "DOL", "060", "012"), tipoMonedaRepository.findAll().stream()
                .map(TipoMoneda::getCodigoAfip)
                .collect(Collectors.toSet()));
        assertTrue(tipoMonedaRepository.findAll().stream()
                .anyMatch(moneda -> "PES".equals(moneda.getCodigoAfip())
                        && "$".equals(moneda.getSimbolo())));

        List<ListaPrecioArticulo> precios = listaPrecioArticuloRepository.findAll();
        assertEquals(20, precios.stream().map(precio -> precio.getArticulo().getId()).distinct().count());
        assertTrue(precios.stream().allMatch(precio -> precio.getPrecioVenta() > 0));
        assertTrue(precios.stream().allMatch(precio -> precio.getListaPrecio() != null));

        clienteRepository.findAll().forEach(cliente -> {
            assertNotNull(cliente.getContacto());
            assertNotNull(cliente.getDomicilio());
            assertNotNull(cliente.getCuitCuil());
            assertNotNull(cliente.getDenominacion());
        });

        List<PuntoVenta> puntosVenta = puntoVentaRepository.findAll();
        assertEquals(Set.of(1, 2, 3, 4), puntosVenta.stream()
                .map(PuntoVenta::getNumero)
                .collect(Collectors.toSet()));
        assertTrue(puntosVenta.stream().allMatch(punto -> "CAE".equals(punto.getTipoEmision())));
        assertTrue(puntosVenta.stream().allMatch(punto -> punto.getDomicilioComercial() != null));
    }
}

package com.example.api_tp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.example.api_tp.dto.FacturaDetalleRequestDTO;
import com.example.api_tp.dto.FacturaRequestDTO;
import com.example.api_tp.model.Articulo;
import com.example.api_tp.model.AuditoriaApp;
import com.example.api_tp.model.Cliente;
import com.example.api_tp.model.CondicionIva;
import com.example.api_tp.model.Contacto;
import com.example.api_tp.model.Domicilio;
import com.example.api_tp.model.FacturaVenta;
import com.example.api_tp.model.ListaPrecio;
import com.example.api_tp.model.ListaPrecioArticulo;
import com.example.api_tp.model.Marca;
import com.example.api_tp.model.PuntoVenta;
import com.example.api_tp.model.Rubro;
import com.example.api_tp.model.TipoMoneda;
import com.example.api_tp.model.Usuario;
import com.example.api_tp.service.FacturaService;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:facturas;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.auditoria.usuario-id=1"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FacturaIntegrationTests {

    private static final String FECHA = "2026-10-21T00:00:00.000Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FacturaService facturaService;

    private TransactionTemplate transactions;
    private Long usuarioId;

    @BeforeAll
    void configurarUsuarioAuditoria() {
        transactions = new TransactionTemplate(transactionManager);
        usuarioId = transactions.execute(status -> {
            Usuario usuario = new Usuario();
            usuario.setUsuario("test-auditoria");
            usuario.setClave("test");
            usuario.setNombre("Test");
            usuario.setApellido("Auditoria");
            entityManager.persist(usuario);
            entityManager.flush();
            return usuario.getId();
        });
        assertEquals(1L, usuarioId);
    }

    @Test
    void creaFacturaConUnDetalleYCalculaImportes() throws Exception {
        Fixture fixture = crearFixture();

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(2026102101L, fixture, fixture.clienteId(),
                                lineJson(null, fixture, "2", "100", "10", "21"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroFactura").value(2026102101L))
                .andExpect(jsonPath("$.importeTotal").value(217.8))
                .andExpect(jsonPath("$.cantidadItems").value(1));

        FacturaVenta guardada = buscarFactura(2026102101L);
        assertEquals(217.8, guardada.getImporteTotal(), 0.001);
        assertEquals(180.0, guardada.getDetalles().get(0).getImporteNeto(), 0.001);
        assertEquals(37.8, guardada.getDetalles().get(0).getImporteIva(), 0.001);
        assertEquals(217.8, guardada.getImporteSaldo(), 0.001);
    }

    @Test
    void creaFacturaConVariosDetallesYTasasDiferentes() throws Exception {
        Fixture fixture = crearFixture();
        String detalles = lineJson(null, fixture, "1", "100", "0", "21") + ","
                + lineJson(null, fixture, "2", "50", "10", "10");

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(2026102102L, fixture, fixture.clienteId(), detalles)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importeTotal").value(220.0))
                .andExpect(jsonPath("$.cantidadItems").value(2));

        FacturaVenta guardada = buscarFactura(2026102102L);
        assertEquals(2, guardada.getDetalles().size());
        assertEquals(121.0, guardada.getDetalles().get(0).getImporteSubtotal(), 0.001);
        assertEquals(99.0, guardada.getDetalles().get(1).getImporteSubtotal(), 0.001);
    }

    @Test
    void rechazaRelacionesInexistentes() throws Exception {
        Fixture fixture = crearFixture();
        String linea = lineJson(null, fixture, "1", "100", "0", "21");
        String base = cuerpoFactura(2026102103L, fixture, fixture.clienteId(), linea);
        List<String> solicitudesInvalidas = List.of(
            base.replace("\"clienteId\": " + fixture.clienteId(), "\"clienteId\": 999999"),
            base.replace("\"condicionIvaId\": " + fixture.condicionIvaId(),
                "\"condicionIvaId\": 999999"),
            base.replace("\"puntoVentaId\": " + fixture.puntoVentaId(),
                "\"puntoVentaId\": 999999"),
            base.replace("\"tipoMonedaId\": " + fixture.tipoMonedaId(),
                "\"tipoMonedaId\": 999999"),
            cuerpoFactura(2026102103L, fixture, fixture.clienteId(),
                linea.replace("\"articuloId\": " + fixture.articuloId(),
                    "\"articuloId\": 999999")),
            cuerpoFactura(2026102103L, fixture, fixture.clienteId(),
                linea.replace("\"listaPrecioArticuloId\": " + fixture.listaPrecioArticuloId(),
                    "\"listaPrecioArticuloId\": 999999")));

        for (String solicitud : solicitudesInvalidas) {
            mockMvc.perform(post("/api/facturas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(solicitud))
                .andExpect(status().isNotFound());
            assertEquals(0L, contarFacturas(2026102103L));
        }
    }

    @Test
    void rechazaCantidadPrecioYPorcentajeIvaInvalidos() throws Exception {
        Fixture fixture = crearFixture();
        esperarBadRequest(2026102104L, fixture, lineJson(null, fixture, "-1", "100", "0", "21"));
        esperarBadRequest(2026102105L, fixture, lineJson(null, fixture, "1", "-0.01", "0", "21"));
        esperarBadRequest(2026102106L, fixture, lineJson(null, fixture, "1", "100", "0", "100.01"));
        esperarBadRequest(2026102107L, fixture, lineJson(null, fixture, "1", "100", "0", "-0.01"));
    }

    @Test
    void actualizaCabeceraYDetalleExistenteSinDuplicarlo() throws Exception {
        Fixture fixture = crearFixture();
        publicarFactura(2026102108L, fixture,
                lineJson(null, fixture, "1", "100", "0", "21"));
        FacturaVenta original = buscarFactura(2026102108L);
        Long detalleId = original.getDetalles().get(0).getId();

        mockMvc.perform(put("/api/facturas/{id}", original.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(2026102188L, fixture, fixture.clienteId(),
                                lineJson(detalleId, fixture, "2", "200", "0", "10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroFactura").value(2026102188L))
                .andExpect(jsonPath("$.importeTotal").value(440.0))
                .andExpect(jsonPath("$.cantidadItems").value(1));

        FacturaVenta actualizada = buscarFactura(2026102188L);
        assertEquals(original.getId(), actualizada.getId());
        assertEquals(1, actualizada.getDetalles().size());
        assertEquals(detalleId, actualizada.getDetalles().get(0).getId());
        assertEquals(400.0, actualizada.getDetalles().get(0).getImporteNeto(), 0.001);
        assertEquals(40.0, actualizada.getDetalles().get(0).getImporteIva(), 0.001);
    }

    @Test
    void actualizaAgregandoYQuitandoDetalles() throws Exception {
        Fixture fixture = crearFixture();
        publicarFactura(2026102109L, fixture,
                lineJson(null, fixture, "1", "100", "0", "0") + ","
                        + lineJson(null, fixture, "1", "50", "0", "0"));
        FacturaVenta original = buscarFactura(2026102109L);
        Long conservado = original.getDetalles().get(0).getId();
        Long eliminado = original.getDetalles().get(1).getId();

        mockMvc.perform(put("/api/facturas/{id}", original.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(2026102109L, fixture, fixture.clienteId(),
                                lineJson(conservado, fixture, "1", "120", "0", "0") + ","
                                        + lineJson(null, fixture, "1", "30", "0", "0"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadItems").value(2));

        FacturaVenta actualizada = buscarFactura(2026102109L);
        assertEquals(2, actualizada.getDetalles().size());
        assertTrue(actualizada.getDetalles().stream().anyMatch(d -> conservado.equals(d.getId())));
        assertTrue(actualizada.getDetalles().stream().noneMatch(d -> eliminado.equals(d.getId())));
        assertEquals(150.0, actualizada.getImporteTotal(), 0.001);
    }

    @Test
    void rechazaActualizarFacturaInexistente() throws Exception {
        Fixture fixture = crearFixture();
        mockMvc.perform(put("/api/facturas/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(2026102110L, fixture, fixture.clienteId(),
                                lineJson(null, fixture, "1", "100", "0", "21"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anulaFacturaExistenteYRechazaInexistente() throws Exception {
        Fixture fixture = crearFixture();
        publicarFactura(2026102111L, fixture,
                lineJson(null, fixture, "1", "100", "0", "21"));
        FacturaVenta factura = buscarFactura(2026102111L);

        mockMvc.perform(delete("/api/facturas/{id}", factura.getId()))
                .andExpect(status().isNoContent());
        FacturaVenta anulada = buscarFactura(2026102111L);
        assertEquals("ANULADA", anulada.getEstado());
        assertNotNull(anulada.getFechaAnulacion());
        assertNotNull(anulada.getFechaBaja());

        mockMvc.perform(delete("/api/facturas/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

            @Test
            void consultaFacturasSinFiltrosPorNumeroYResultadoVacio() throws Exception {
            Fixture fixture = crearFixture();
            long numero = 2026102121L;
            publicarFactura(numero, fixture, lineJson(null, fixture, "1", "100", "0", "21"));

            mockMvc.perform(get("/api/facturas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"numeroFactura\":" + numero)));

            mockMvc.perform(get("/api/facturas").param("numeroFactura", Long.toString(numero)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroFactura").value(numero))
                .andExpect(jsonPath("$[0].detalles.length()").value(1));

            mockMvc.perform(get("/api/facturas").param("numeroFactura", "999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
            }

            @Test
            void filtraPorClienteCondicionIvaYCombinaLosCriterios() throws Exception {
            Fixture fixture = crearFixture();
            long numero = 2026102122L;
            publicarFactura(numero, fixture, lineJson(null, fixture, "1", "100", "0", "21"));

            mockMvc.perform(get("/api/facturas")
                    .param("clienteDenominacion", "CLIENTE DE PRUE")
                    .param("condicionIva", "iva PRUEBA"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"numeroFactura\":" + numero)))
                .andExpect(content().string(containsString("\"clienteDenominacion\":\"Cliente de prueba\"")))
                .andExpect(content().string(containsString("\"condicionIva\":\"IVA prueba\"")));

            mockMvc.perform(get("/api/facturas")
                    .param("numeroFactura", Long.toString(numero))
                    .param("clienteDenominacion", "   ")
                    .param("condicionIva", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroFactura").value(numero));
            }

            @Test
            void filtraFechasIncluyendoTodoElDiaFinalYRechazaRangoInvalido() throws Exception {
            Fixture fixture = crearFixture();
            long inicioDia = 2026102123L;
            long finalDia = 2026102124L;
            long diaSiguiente = 2026102125L;
            String detalle = lineJson(null, fixture, "1", "100", "0", "21");
            publicarFacturaConFecha(inicioDia, fixture, detalle, "2026-10-20T00:00:00.000Z");
            publicarFacturaConFecha(finalDia, fixture, detalle, "2026-10-21T23:59:59.000Z");
            publicarFacturaConFecha(diaSiguiente, fixture, detalle, "2026-10-22T00:00:00.000Z");

            mockMvc.perform(get("/api/facturas")
                    .param("fechaDesde", "2026-10-20")
                    .param("fechaHasta", "2026-10-21"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"numeroFactura\":" + inicioDia)))
                .andExpect(content().string(containsString("\"numeroFactura\":" + finalDia)));

            mockMvc.perform(get("/api/facturas")
                    .param("numeroFactura", Long.toString(finalDia))
                    .param("fechaDesde", "2026-10-21")
                    .param("fechaHasta", "2026-10-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroFactura").value(finalDia));

            mockMvc.perform(get("/api/facturas")
                    .param("numeroFactura", Long.toString(diaSiguiente))
                    .param("fechaDesde", "2026-10-20")
                    .param("fechaHasta", "2026-10-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

            mockMvc.perform(get("/api/facturas")
                    .param("fechaDesde", "2026-10-22")
                    .param("fechaHasta", "2026-10-21"))
                .andExpect(status().isBadRequest());
            }

    @Test
    void revierteLaFacturaCompletaSiFallaLaPersistenciaDeUnDetalle() {
        Fixture fixture = crearFixture();
        FacturaRequestDTO request = request(2026102112L, fixture);
        FacturaDetalleRequestDTO detalleValido = detalle(fixture, "1", "100", "0", "21");
        FacturaDetalleRequestDTO detalleQueExcedeLongitudDeColumna = detalle(fixture, "1", "50", "0", "21");
        detalleQueExcedeLongitudDeColumna.setDescripcion("X".repeat(300));
        request.setDetalles(List.of(detalleValido, detalleQueExcedeLongitudDeColumna));

        assertThrows(RuntimeException.class, () -> facturaService.crear(request));
        assertEquals(0L, contarFacturas(2026102112L));
    }

    private void esperarBadRequest(long numero, Fixture fixture, String detalle) throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(numero, fixture, fixture.clienteId(), detalle)))
                .andExpect(status().isBadRequest());
        assertEquals(0L, contarFacturas(numero));
    }

    private void publicarFactura(long numero, Fixture fixture, String detalles) throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(numero, fixture, fixture.clienteId(), detalles)))
                .andExpect(status().isCreated());
    }

    private void publicarFacturaConFecha(long numero, Fixture fixture, String detalles, String fecha)
            throws Exception {
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(numero, fixture, fixture.clienteId(), detalles)
                                .replace(FECHA, fecha)))
                .andExpect(status().isCreated());
    }

    private String cuerpoFactura(long numero, Fixture fixture, long clienteId, String detalles) {
        return """
                {
                  "numero": %d,
                  "fechaEmision": "%s",
                  "clienteId": %d,
                  "condicionIvaId": %d,
                  "puntoVentaId": %d,
                  "tipoMonedaId": %d,
                  "importeCobrado": 0,
                  "resultadoAfip": "PENDIENTE",
                  "estado": "PENDIENTE",
                  "detalles": [%s]
                }
                """.formatted(numero, FECHA, clienteId, fixture.condicionIvaId(),
                fixture.puntoVentaId(), fixture.tipoMonedaId(), detalles);
    }

    private String lineJson(Long id, Fixture fixture, String cantidad, String precio,
            String descuento, String iva) {
        return """
                {
                  %s
                  "articuloId": %d,
                  "listaPrecioArticuloId": %d,
                  "cantidad": %s,
                  "precioUnitario": %s,
                  "porcentajeBonificacion": %s,
                  "porcentajeIva": %s
                }
                """.formatted(id == null ? "" : "\"id\": " + id + ",", fixture.articuloId(),
                fixture.listaPrecioArticuloId(), cantidad, precio, descuento, iva);
    }

    private Fixture crearFixture() {
        return transactions.execute(status -> {
            Usuario usuario = entityManager.getReference(Usuario.class, usuarioId);
            Date ahora = new Date();

            Contacto contacto = new Contacto();
            contacto.setEmail("test@example.test");
            contacto.setTelefono("1100000000");
            contacto.setCelular("1500000000");
            entityManager.persist(contacto);

            Domicilio domicilio = new Domicilio();
            domicilio.setNombreCalle("Calle Test");
            domicilio.setNumeroCalle("123");
            entityManager.persist(domicilio);

            Rubro rubro = new Rubro();
            rubro.setDenominacion("Pruebas");
            rubro.setCodigo(1);
            auditar(rubro, usuario, ahora);
            entityManager.persist(rubro);

            Marca marca = new Marca();
            marca.setDenominacion("Marca test");
            marca.setCodigo(1);
            auditar(marca, usuario, ahora);
            entityManager.persist(marca);

            ListaPrecio listaPrecio = new ListaPrecio();
            listaPrecio.setCodigo("TEST");
            listaPrecio.setDenominacion("Lista test");
            auditar(listaPrecio, usuario, ahora);
            entityManager.persist(listaPrecio);

            Cliente cliente = new Cliente();
            cliente.setCuitCuil("20123456789");
            cliente.setDenominacion("Cliente de prueba");
            cliente.setContacto(contacto);
            cliente.setDomicilio(domicilio);
            auditar(cliente, usuario, ahora);
            entityManager.persist(cliente);

            CondicionIva condicionIva = new CondicionIva();
            condicionIva.setCodigoAfip(1);
            condicionIva.setDenominacion("IVA prueba");
            auditar(condicionIva, usuario, ahora);
            entityManager.persist(condicionIva);

            PuntoVenta puntoVenta = new PuntoVenta();
            puntoVenta.setNumero(1);
            puntoVenta.setDescripcion("Punto de prueba");
            puntoVenta.setTipoEmision("ELECTRONICA");
            puntoVenta.setDomicilioComercial("Calle Test 123");
            auditar(puntoVenta, usuario, ahora);
            entityManager.persist(puntoVenta);

            TipoMoneda moneda = new TipoMoneda();
            moneda.setCodigoAfip("PES");
            moneda.setDenominacion("Peso");
            moneda.setSimbolo("$");
            auditar(moneda, usuario, ahora);
            entityManager.persist(moneda);

            Articulo articulo = new Articulo();
            articulo.setRubro(rubro);
            articulo.setMarca(marca);
            articulo.setCodigo("ART-TEST");
            articulo.setDenominacion("Artículo de prueba");
            auditar(articulo, usuario, ahora);
            entityManager.persist(articulo);

            ListaPrecioArticulo listaPrecioArticulo = new ListaPrecioArticulo();
            listaPrecioArticulo.setListaPrecio(listaPrecio);
            listaPrecioArticulo.setArticulo(articulo);
            listaPrecioArticulo.setPrecioVenta(100.0);
            auditar(listaPrecioArticulo, usuario, ahora);
            entityManager.persist(listaPrecioArticulo);

            entityManager.flush();
            return new Fixture(cliente.getId(), condicionIva.getId(), puntoVenta.getId(),
                    moneda.getId(), articulo.getId(), listaPrecioArticulo.getId());
        });
    }

    private void auditar(AuditoriaApp entidad, Usuario usuario, Date ahora) {
        entidad.setFechaAlta(ahora);
        entidad.setFechaModificacion(ahora);
        entidad.setUsuarioCarga(usuario);
        entidad.setUsuarioModificacion(usuario);
    }

    private FacturaVenta buscarFactura(long numero) {
        return transactions.execute(status -> entityManager.createQuery(
                        "select distinct f from FacturaVenta f left join fetch f.detalles where f.numero = :numero",
                        FacturaVenta.class)
                .setParameter("numero", numero)
                .getSingleResult());
    }

    private long contarFacturas(long numero) {
        return transactions.execute(status -> entityManager.createQuery(
                        "select count(f) from FacturaVenta f where f.numero = :numero", Long.class)
                .setParameter("numero", numero)
                .getSingleResult());
    }

    private FacturaRequestDTO request(long numero, Fixture fixture) {
        FacturaRequestDTO request = new FacturaRequestDTO();
        request.setNumero(numero);
        request.setFechaEmision(new Date());
        request.setClienteId(fixture.clienteId());
        request.setCondicionIvaId(fixture.condicionIvaId());
        request.setPuntoVentaId(fixture.puntoVentaId());
        request.setTipoMonedaId(fixture.tipoMonedaId());
        request.setImporteCobrado(BigDecimal.ZERO);
        request.setResultadoAfip("PENDIENTE");
        request.setEstado("PENDIENTE");
        return request;
    }

    private FacturaDetalleRequestDTO detalle(Fixture fixture, String cantidad, String precio,
            String descuento, String iva) {
        FacturaDetalleRequestDTO detalle = new FacturaDetalleRequestDTO();
        detalle.setArticuloId(fixture.articuloId());
        detalle.setListaPrecioArticuloId(fixture.listaPrecioArticuloId());
        detalle.setCantidad(new BigDecimal(cantidad));
        detalle.setPrecioUnitario(new BigDecimal(precio));
        detalle.setPorcentajeBonificacion(new BigDecimal(descuento));
        detalle.setPorcentajeIva(new BigDecimal(iva));
        return detalle;
    }

    private record Fixture(Long clienteId, Long condicionIvaId, Long puntoVentaId,
            Long tipoMonedaId, Long articuloId, Long listaPrecioArticuloId) {
    }
}

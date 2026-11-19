package com.example.api_tp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.web.servlet.ResultActions;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

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
        "app.auditoria.usuario-id=1",
        "app.seed.enabled=false"
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
    private final Map<Long, Long> numerosPorMarcador = new HashMap<>();

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
                .andExpect(jsonPath("$.numeroFactura").isNumber())
                .andExpect(jsonPath("$.importeTotal").value(217.8))
                .andExpect(jsonPath("$.cantidadItems").value(1));

        FacturaVenta guardada = buscarUltimaFactura();
        assertNotEquals(2026102101L, guardada.getNumero());
        assertEquals(217.8, guardada.getImporteTotal(), 0.001);
        assertEquals(180.0, guardada.getDetalles().get(0).getImporteNeto(), 0.001);
        assertEquals(37.8, guardada.getDetalles().get(0).getImporteIva(), 0.001);
        assertEquals(217.8, guardada.getImporteSaldo(), 0.001);
        assertEquals(1, guardada.getTipoComprobanteArca());
        assertEquals(1, guardada.getConceptoArca());
        assertEquals(80, guardada.getDocTipoReceptorArca());
        assertEquals(20123456789L, guardada.getDocNroReceptorArca());
        assertEquals(1, guardada.getCondicionIvaReceptorArca());
        assertEquals("PES", guardada.getMonedaArca());
        assertEquals(1.0, guardada.getCotizacionArca(), 0.001);
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

        FacturaVenta guardada = buscarUltimaFactura();
        assertEquals(2, guardada.getDetalles().size());
        assertEquals(121.0, guardada.getDetalles().get(0).getImporteSubtotal(), 0.001);
        assertEquals(99.0, guardada.getDetalles().get(1).getImporteSubtotal(), 0.001);
    }

    @Test
    void rechazaRelacionesInexistentes() throws Exception {
        Fixture fixture = crearFixture();
        String linea = lineJson(null, fixture, "1", "100", "0", "21");
        String base = cuerpoFactura(2026102103L, fixture, fixture.clienteId(), linea);
        long facturasAntes = contarTodasFacturas();
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
            assertEquals(facturasAntes, contarTodasFacturas());
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
                .andExpect(jsonPath("$.numeroFactura").value(original.getNumero()))
                .andExpect(jsonPath("$.importeTotal").value(440.0))
                .andExpect(jsonPath("$.cantidadItems").value(1));

        FacturaVenta actualizada = buscarUltimaFactura();
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
        assertEquals("ANULADA_LOCAL", anulada.getEstado());
        assertNotNull(anulada.getFechaAnulacion());
        assertNotNull(anulada.getFechaBaja());

        mockMvc.perform(delete("/api/facturas/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

            @Test
            void consultaFacturasSinFiltrosPorNumeroYResultadoVacio() throws Exception {
            Fixture fixture = crearFixture();
            long numero = 2026102121L;
            numero = publicarFactura(numero, fixture, lineJson(null, fixture, "1", "100", "0", "21"));

            mockMvc.perform(get("/api/facturas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanoPagina").value(10));

            mockMvc.perform(get("/api/facturas").param("numeroFactura", Long.toString(numero)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].numeroFactura").value(numero))
                .andExpect(jsonPath("$.contenido[0].id").isNumber())
                .andExpect(jsonPath("$.contenido[0].detalles.length()").value(1));

            mockMvc.perform(get("/api/facturas").param("numeroFactura", "999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty());
            }

            @Test
            void filtraPorClienteCondicionIvaYCombinaLosCriterios() throws Exception {
            Fixture fixture = crearFixture();
            long numero = 2026102122L;
            numero = publicarFactura(numero, fixture, lineJson(null, fixture, "1", "100", "0", "21"));

            mockMvc.perform(get("/api/facturas")
                    .param("clienteDenominacion", "CLIENTE DE PRUE")
                    .param("condicionIva", "iva PRUEBA")
                    .param("numeroFactura", Long.toString(numero)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"numeroFactura\":" + numero)))
                .andExpect(content().string(containsString("\"clienteDenominacion\":\"Cliente de prueba\"")))
                .andExpect(content().string(containsString("\"condicionIva\":\"IVA prueba\"")));

            mockMvc.perform(get("/api/facturas")
                    .param("numeroFactura", Long.toString(numero))
                    .param("clienteDenominacion", "   ")
                    .param("condicionIva", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].numeroFactura").value(numero));
            }

            @Test
            void filtraFechasIncluyendoTodoElDiaFinalYRechazaRangoInvalido() throws Exception {
            Fixture fixture = crearFixture();
            String detalle = lineJson(null, fixture, "1", "100", "0", "21");
                long inicioDia = publicarFacturaConFecha(2026102123L, fixture, detalle,
                    "2026-10-20T00:00:00.000Z");
                long finalDia = publicarFacturaConFecha(2026102124L, fixture, detalle,
                    "2026-10-21T23:59:59.000Z");
                long diaSiguiente = publicarFacturaConFecha(2026102125L, fixture, detalle,
                    "2026-10-22T00:00:00.000Z");

            mockMvc.perform(get("/api/facturas")
                    .param("numeroFactura", Long.toString(inicioDia))
                    .param("fechaDesde", "2026-10-20")
                    .param("fechaHasta", "2026-10-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].numeroFactura").value(inicioDia));

            mockMvc.perform(get("/api/facturas")
                    .param("numeroFactura", Long.toString(finalDia))
                    .param("fechaDesde", "2026-10-21")
                    .param("fechaHasta", "2026-10-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].numeroFactura").value(finalDia));

            mockMvc.perform(get("/api/facturas")
                    .param("numeroFactura", Long.toString(diaSiguiente))
                    .param("fechaDesde", "2026-10-20")
                    .param("fechaHasta", "2026-10-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty());

            mockMvc.perform(get("/api/facturas")
                    .param("fechaDesde", "2026-10-22")
                    .param("fechaHasta", "2026-10-21"))
                .andExpect(status().isBadRequest());
            }

    @Test
    void revierteLaFacturaCompletaSiFallaLaPersistenciaDeUnDetalle() {
        Fixture fixture = crearFixture();
        long facturasAntes = contarTodasFacturas();
        FacturaRequestDTO request = request(2026102112L, fixture);
        FacturaDetalleRequestDTO detalleValido = detalle(fixture, "1", "100", "0", "21");
        FacturaDetalleRequestDTO detalleQueExcedeLongitudDeColumna = detalle(fixture, "1", "50", "0", "21");
        detalleQueExcedeLongitudDeColumna.setDescripcion("X".repeat(300));
        request.setDetalles(List.of(detalleValido, detalleQueExcedeLongitudDeColumna));

        assertThrows(RuntimeException.class, () -> facturaService.crear(request));
        assertEquals(facturasAntes, contarTodasFacturas());
    }

        @Test
        void paginaFacturasConMaximoDiezYMetadatosFiltrados() throws Exception {
        Fixture fixture = crearFixture();
            long facturasAntes = contarTodasFacturas();
            List<Long> numerosCreados = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
                numerosCreados.add(publicarFactura(300000L + i, fixture,
                        lineJson(null, fixture, "1", "10", "0", "0")));
        }
            long totalEsperado = facturasAntes + numerosCreados.size();
            int paginasEsperadas = (int) ((totalEsperado + 9) / 10);
            int elementosUltimaPagina = (int) (totalEsperado % 10 == 0 ? 10 : totalEsperado % 10);
            int elementosPaginaUno = (int) Math.min(10, Math.max(0, totalEsperado - 10));

        mockMvc.perform(get("/api/facturas").param("size", "1000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagina").value(0))
            .andExpect(jsonPath("$.tamanoPagina").value(10))
            .andExpect(jsonPath("$.totalElementos").value(totalEsperado))
            .andExpect(jsonPath("$.totalPaginas").value(paginasEsperadas))
            .andExpect(jsonPath("$.contenido.length()").value(10));

        mockMvc.perform(get("/api/facturas").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.contenido.length()").value(elementosPaginaUno));

        mockMvc.perform(get("/api/facturas").param("page", Integer.toString(paginasEsperadas - 1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido.length()").value(elementosUltimaPagina))
            .andExpect(jsonPath("$.totalElementos").value(totalEsperado));

        mockMvc.perform(get("/api/facturas").param("page", Integer.toString(paginasEsperadas)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido").isEmpty())
            .andExpect(jsonPath("$.totalElementos").value(totalEsperado));

        mockMvc.perform(get("/api/facturas").param("page", "-1"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/facturas").param("page", "1")
                .param("numeroFactura", Long.toString(numerosCreados.get(0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(1))
            .andExpect(jsonPath("$.contenido").isEmpty());
        }

        @Test
        void ordenaFacturasDeMasRecienteAMasAntiguaYConservaElOrdenConFiltros() throws Exception {
        Fixture fixture = crearFixture();
        String detalle = lineJson(null, fixture, "1", "100", "0", "21");
        long primera = publicarFactura(600001L, fixture, detalle);
        long segunda = publicarFactura(600002L, fixture, detalle);

        mockMvc.perform(get("/api/facturas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].numeroFactura").value(segunda))
            .andExpect(jsonPath("$.contenido[1].numeroFactura").value(primera));

        mockMvc.perform(get("/api/facturas").param("clienteDenominacion", "Cliente de prueba"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].numeroFactura").value(segunda));

        mockMvc.perform(get("/api/facturas").param("numeroFactura", Long.toString(primera)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].numeroFactura").value(primera));
        }

        @Test
        void exponeCatalogosConCamposMinimosYArticulosFiltradosPorLista() throws Exception {
        Fixture fixture = crearFixture();

        mockMvc.perform(get("/api/puntos-venta"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"id\":" + fixture.puntoVentaId()
                + ",\"descripcion\":\"Punto de prueba\"")));
        mockMvc.perform(get("/api/clientes"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"id\":" + fixture.clienteId()
                + ",\"denominacion\":\"Cliente de prueba\"")));
        mockMvc.perform(get("/api/condiciones-iva"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"denominacion\":\"IVA prueba\"")));
        mockMvc.perform(get("/api/tipos-moneda"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"denominacion\":\"Peso\"")));
        mockMvc.perform(get("/api/listas-precio"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"denominacion\":\"Lista test\"")));
        mockMvc.perform(get("/api/rubros"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"denominacion\":\"Pruebas\"")));

        Long listaPrecioId = transactions.execute(status -> entityManager.find(
            ListaPrecioArticulo.class, fixture.listaPrecioArticuloId())
            .getListaPrecio().getId());
        mockMvc.perform(get("/api/listas-precio/{listaPrecioId}/articulos", listaPrecioId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(fixture.listaPrecioArticuloId()))
            .andExpect(jsonPath("$[0].articuloId").value(fixture.articuloId()))
            .andExpect(jsonPath("$[0].denominacion").value("Artículo de prueba"))
            .andExpect(jsonPath("$[0].rubro").value("Pruebas"))
            .andExpect(jsonPath("$[0].precioVenta").value(100.0));
        mockMvc.perform(get("/api/listas-precio/{listaPrecioId}/articulos", 999999L))
            .andExpect(status().isNotFound());

        Long listaVaciaId = transactions.execute(status -> {
            ListaPrecio listaVacia = new ListaPrecio();
            listaVacia.setCodigo("VACIA-TEST");
            listaVacia.setDenominacion("Lista vacía");
            Date ahora = new Date();
            Usuario usuario = entityManager.getReference(Usuario.class, usuarioId);
            auditar(listaVacia, usuario, ahora);
            entityManager.persist(listaVacia);
            entityManager.flush();
            return listaVacia.getId();
        });
        mockMvc.perform(get("/api/listas-precio/{listaPrecioId}/articulos", listaVaciaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        void corsPermiteOrigenDelFrontendYRechazaOrigenNoConfigurado() throws Exception {
        mockMvc.perform(options("/api/facturas")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Content-Type"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Access-Control-Allow-Headers", "Content-Type"));

        mockMvc.perform(options("/api/facturas")
                .header("Origin", "https://no-autorizado.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().doesNotExist("Access-Control-Allow-Origin"));
        }

    @Test
    void asignaNumerosGlobalesUnicosAnteAltasConcurrentes() throws Exception {
        Fixture fixture = crearFixture();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            List<Future<Long>> tareas = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                FacturaRequestDTO request = request(400000L + i, fixture);
                request.setDetalles(List.of(detalle(fixture, "1", "10", "0", "0")));
                tareas.add(executor.submit(() -> facturaService.crear(request).getNumeroFactura()));
            }
            Set<Long> numeros = tareas.stream().map(tarea -> {
                try {
                    return tarea.get();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).collect(java.util.stream.Collectors.toSet());
            assertEquals(5, numeros.size());
        } finally {
            executor.shutdownNow();
        }
    }

        @Test
        void exportaTodasLasFacturasEnPdfDescargable() throws Exception {
        Fixture fixture = crearFixture();
            List<Long> numeros = new java.util.ArrayList<>();
            for (int i = 0; i < 12; i++) {
                numeros.add(publicarFactura(500001L + i, fixture,
                        lineJson(null, fixture, "2", "1250", "5", "21")));
            }

        byte[] pdf = mockMvc.perform(get("/api/facturas/pdf"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Content-Disposition", containsString("facturas.pdf")))
            .andReturn().getResponse().getContentAsByteArray();

        assertTrue(pdf.length > 100);
        try (var documento = Loader.loadPDF(pdf)) {
            assertTrue(documento.getNumberOfPages() >= 2);
            String texto = new PDFTextStripper().getText(documento);
            assertTrue(texto.contains("Registro de facturas"));
            assertTrue(texto.contains("Factura " + numeros.get(0)));
            assertTrue(texto.contains("Factura " + numeros.get(10)));
            assertTrue(texto.contains("Factura " + numeros.get(11)));
            assertTrue(texto.contains("Cliente de prueba"));
            assertTrue(texto.contains("Artículo de prueba"));
        }
        }

    @Test
    void rechazaPropiedadesDesconocidasYNoAceptaCamposDeControl() throws Exception {
        Fixture fixture = crearFixture();
        String conDesconocida = cuerpoFactura(600001L, fixture, fixture.clienteId(),
                lineJson(null, fixture, "1", "100", "0", "21"))
                .replace("\"detalles\"", "\"propiedadInventada\": 1, \"detalles\"");
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conDesconocida))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.tipo").value("FORMATO"))
                .andExpect(content().string(containsString("propiedadInventada")));

        String conControl = cuerpoFactura(600002L, fixture, fixture.clienteId(),
                lineJson(null, fixture, "1", "100", "0", "21"))
                .replace("\"estado\": \"PENDIENTE\"", "\"estado\": \"AUTORIZADA\"")
                .replace("\"resultadoAfip\": \"PENDIENTE\"", "\"resultadoAfip\": \"A\"");
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conControl))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void rechazaDatosFiscalesEnviadosDesdeElFrontend() throws Exception {
        Fixture fixture = crearFixture();
        String base = cuerpoFactura(600010L, fixture, fixture.clienteId(),
                lineJson(null, fixture, "1", "100", "0", "21"));
        List<String> propiedades = List.of(
                "\"tipoComprobanteArca\": 1",
                "\"conceptoArca\": 1",
                "\"docTipoReceptorArca\": 80",
                "\"docNroReceptorArca\": 20123456789",
                "\"condicionIvaReceptorArca\": 5",
                "\"monedaArca\": \"USD\"",
                "\"cotizacionArca\": 1000");
        for (String propiedad : propiedades) {
            mockMvc.perform(post("/api/facturas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(base.replace("\"detalles\"", propiedad + ", \"detalles\"")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(containsString("no permitida")));
        }
    }

    @Test
    void recalculaDatosFiscalesAlEditarFacturaPendiente() throws Exception {
        Fixture fixture = crearFixture();
        publicarFactura(2026102130L, fixture, lineJson(null, fixture, "1", "100", "0", "21"));
        FacturaVenta original = buscarFactura(2026102130L);
        assertEquals(1, original.getTipoComprobanteArca());

        Long condicionConsumidorFinal = transactions.execute(status -> {
            Usuario usuario = entityManager.getReference(Usuario.class, usuarioId);
            Date ahora = new Date();
            CondicionIva condicion = new CondicionIva();
            condicion.setCodigoAfip(5);
            condicion.setDenominacion("Consumidor Final");
            auditar(condicion, usuario, ahora);
            entityManager.persist(condicion);
            entityManager.flush();
            return condicion.getId();
        });
        Long monedaUsd = transactions.execute(status -> {
            Usuario usuario = entityManager.getReference(Usuario.class, usuarioId);
            Date ahora = new Date();
            TipoMoneda moneda = new TipoMoneda();
            moneda.setCodigoAfip("USD");
            moneda.setDenominacion("Dólar");
            moneda.setSimbolo("US$");
            auditar(moneda, usuario, ahora);
            entityManager.persist(moneda);
            entityManager.flush();
            return moneda.getId();
        });

        mockMvc.perform(put("/api/facturas/{id}", original.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(2026102130L, fixture, fixture.clienteId(),
                                lineJson(null, fixture, "1", "100", "0", "21"))
                                .replace("\"condicionIvaId\": " + fixture.condicionIvaId(),
                                        "\"condicionIvaId\": " + condicionConsumidorFinal)
                                .replace("\"tipoMonedaId\": " + fixture.tipoMonedaId(),
                                        "\"tipoMonedaId\": " + monedaUsd)))
                .andExpect(status().isOk());

        FacturaVenta actualizada = buscarFactura(2026102130L);
        assertEquals(6, actualizada.getTipoComprobanteArca());
        assertEquals(5, actualizada.getCondicionIvaReceptorArca());
        assertEquals("USD", actualizada.getMonedaArca());
        assertNull(actualizada.getCotizacionArca());
        assertEquals(80, actualizada.getDocTipoReceptorArca());
        assertEquals(20123456789L, actualizada.getDocNroReceptorArca());
    }

    @Test
    void rechazaClienteSinDocumentoFiscalValido() throws Exception {
        Fixture fixture = crearFixture();
        Long clienteInvalido = crearCliente("S/D");
        long facturasAntes = contarTodasFacturas();

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(2026102131L, fixture, clienteInvalido,
                                lineJson(null, fixture, "1", "100", "0", "21"))))
                .andExpect(status().isBadRequest());

        assertEquals(facturasAntes, contarTodasFacturas());
    }

    @Test
    void determinaDocumentoTipoDniCuandoElClienteNoTieneCuit() throws Exception {
        Fixture fixture = crearFixture();
        Long clienteDni = crearCliente("12345678");

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(2026102132L, fixture, clienteDni,
                                lineJson(null, fixture, "1", "100", "0", "21"))))
                .andExpect(status().isCreated());

        FacturaVenta guardada = buscarUltimaFactura();
        assertEquals(96, guardada.getDocTipoReceptorArca());
        assertEquals(12345678L, guardada.getDocNroReceptorArca());
    }

    @Test
    void respuestasDeErrorUsanEstructuraUniformeYCodigosCoherentes() throws Exception {
        mockMvc.perform(get("/api/facturas/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.tipo").value("SOLICITUD"))
                .andExpect(jsonPath("$.message").isString());

        mockMvc.perform(put("/api/facturas"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.tipo").value("METODO"));

        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        mockMvc.perform(get("/api/facturas").param("numeroFactura", "no-es-numero"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.tipo").value("PARAMETRO"));

        mockMvc.perform(get("/api/facturas").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private void esperarBadRequest(long numero, Fixture fixture, String detalle) throws Exception {
        long facturasAntes = contarTodasFacturas();
        mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(numero, fixture, fixture.clienteId(), detalle)))
                .andExpect(status().isBadRequest());
            assertEquals(facturasAntes, contarTodasFacturas());
    }

    private long publicarFactura(long marcador, Fixture fixture, String detalles) throws Exception {
        ResultActions respuesta = mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(marcador, fixture, fixture.clienteId(), detalles)))
                .andExpect(status().isCreated());
        Number numeroAsignadoJson = com.jayway.jsonpath.JsonPath.read(
            respuesta.andReturn().getResponse().getContentAsString(), "$.numeroFactura");
        long numeroAsignado = numeroAsignadoJson.longValue();
        numerosPorMarcador.put(marcador, numeroAsignado);
        return numeroAsignado;
    }

    private long publicarFacturaConFecha(long marcador, Fixture fixture, String detalles, String fecha)
            throws Exception {
        ResultActions respuesta = mockMvc.perform(post("/api/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoFactura(marcador, fixture, fixture.clienteId(), detalles)
                                .replace(FECHA, fecha)))
                .andExpect(status().isCreated());
        Number numeroAsignadoJson = com.jayway.jsonpath.JsonPath.read(
            respuesta.andReturn().getResponse().getContentAsString(), "$.numeroFactura");
        long numeroAsignado = numeroAsignadoJson.longValue();
        numerosPorMarcador.put(marcador, numeroAsignado);
        return numeroAsignado;
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

    private Long crearCliente(String cuitCuil) {
        return transactions.execute(status -> {
            Usuario usuario = entityManager.getReference(Usuario.class, usuarioId);
            Date ahora = new Date();

            Contacto contacto = new Contacto();
            contacto.setEmail("cliente@example.test");
            contacto.setTelefono("1100000000");
            contacto.setCelular("1500000000");
            entityManager.persist(contacto);

            Domicilio domicilio = new Domicilio();
            domicilio.setNombreCalle("Calle Cliente");
            domicilio.setNumeroCalle("456");
            entityManager.persist(domicilio);

            Cliente cliente = new Cliente();
            cliente.setCuitCuil(cuitCuil);
            cliente.setDenominacion("Cliente " + cuitCuil);
            cliente.setContacto(contacto);
            cliente.setDomicilio(domicilio);
            auditar(cliente, usuario, ahora);
            entityManager.persist(cliente);
            entityManager.flush();
            return cliente.getId();
        });
    }

    private FacturaVenta buscarFactura(long numero) {
        Long numeroAsignado = numerosPorMarcador.get(numero);
        if (numeroAsignado == null) {
            return buscarUltimaFactura();
        }
        return transactions.execute(status -> entityManager.createQuery(
                        "select distinct f from FacturaVenta f left join fetch f.detalles where f.numero = :numero",
                        FacturaVenta.class)
                .setParameter("numero", numeroAsignado)
                .getSingleResult());
    }

    private FacturaVenta buscarUltimaFactura() {
        return transactions.execute(status -> {
            Long id = entityManager.createQuery("select max(f.id) from FacturaVenta f", Long.class)
                .getSingleResult();
            return entityManager.createQuery(
                    "select distinct f from FacturaVenta f left join fetch f.detalles where f.id = :id",
                    FacturaVenta.class)
                .setParameter("id", id)
                .getSingleResult();
        });
    }

    private long contarTodasFacturas() {
        return transactions.execute(status -> entityManager.createQuery(
                        "select count(f) from FacturaVenta f", Long.class)
                .getSingleResult());
    }

    private FacturaRequestDTO request(long numero, Fixture fixture) {
        FacturaRequestDTO request = new FacturaRequestDTO();
        request.setFechaEmision(new Date());
        request.setClienteId(fixture.clienteId());
        request.setCondicionIvaId(fixture.condicionIvaId());
        request.setPuntoVentaId(fixture.puntoVentaId());
        request.setTipoMonedaId(fixture.tipoMonedaId());
        request.setImporteCobrado(BigDecimal.ZERO);
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

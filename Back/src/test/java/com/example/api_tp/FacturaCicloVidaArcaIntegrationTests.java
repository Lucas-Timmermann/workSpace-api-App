package com.example.api_tp;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;


import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.api_tp.client.AfipWsaaClient;
import com.example.api_tp.client.AfipWsfeClient;
import com.example.api_tp.client.arca.exception.ArcaCommunicationException;
import com.example.api_tp.client.arca.wsfe.CompConsultarResultado;
import com.example.api_tp.client.arca.wsfe.CotizacionArca;
import com.example.api_tp.client.arca.wsfe.FeCaeDetalle;
import com.example.api_tp.client.arca.wsfe.FeCaeResultado;
import com.example.api_tp.client.arca.wsfe.FeCaeSolicitud;
import com.example.api_tp.client.arca.wsfe.ParametroArca;
import com.example.api_tp.client.arca.wsfe.PuntoVentaArca;
import com.example.api_tp.client.arca.wsfe.UltimoAutorizadoResult;
import com.example.api_tp.dto.FacturaDetalleRequestDTO;
import com.example.api_tp.dto.FacturaRequestDTO;
import com.example.api_tp.model.Articulo;
import com.example.api_tp.model.AuditoriaApp;
import com.example.api_tp.model.Cliente;
import com.example.api_tp.model.CondicionIva;
import com.example.api_tp.model.Contacto;
import com.example.api_tp.model.Domicilio;
import com.example.api_tp.model.EstadoFactura;
import com.example.api_tp.model.FacturaVenta;
import com.example.api_tp.model.ListaPrecio;
import com.example.api_tp.model.ListaPrecioArticulo;
import com.example.api_tp.model.Marca;
import com.example.api_tp.model.PuntoVenta;
import com.example.api_tp.model.Rubro;
import com.example.api_tp.model.TipoMoneda;
import com.example.api_tp.model.Usuario;
import com.example.api_tp.repository.FacturaVentaRepository;


import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:arcaciclo;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.auditoria.usuario-id=1",
        "app.seed.enabled=false"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FacturaCicloVidaArcaIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager txManager;
    @Autowired FacturaVentaRepository facturaVentaRepository;

    @MockitoBean AfipWsaaClient afipWsaaClient;
    @MockitoBean AfipWsfeClient afipWsfeClient;

    private TransactionTemplate tx;
    private Long usuarioId;

    @BeforeAll
    void setup() {
        tx = new TransactionTemplate(txManager);
        usuarioId = tx.execute(s -> {
            Usuario u = new Usuario();
            u.setUsuario("arca"); u.setClave("x"); u.setNombre("A"); u.setApellido("R");
            entityManager.persist(u);
            entityManager.flush();
            return u.getId();
        });
        assertEquals(1L, usuarioId);
    }

    @Test
    void creaListaEditaYAnulaPendiente() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, "obs1");

        String list = mockMvc.perform(get("/api/facturas/pendientes"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(list, "$.contenido[*].id");
        assertTrue(ids.stream().map(Number::longValue).anyMatch(v -> v == id));

        mockMvc.perform(get("/api/facturas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        FacturaRequestDTO edit = req(fx);
        edit.setObservaciones("editada");
        mockMvc.perform(put("/api/facturas/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(edit)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/facturas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observaciones").value("editada"));

        mockMvc.perform(delete("/api/facturas/{id}", id))
                .andExpect(status().isNoContent());

        FacturaVenta anulada = facturaVentaRepository.findById(id).orElseThrow();
        assertEquals("ANULADA_LOCAL", anulada.getEstado());
    }

    @Test
    void noPermiteEditarNiAnularAutorizadaYGeneraPdfIndividual() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, null);
        stubCatalogos();
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 10, List.of(), List.of()));
        FeCaeResultado cae = new FeCaeResultado();
        cae.setResultadoGlobal("A");
        FeCaeResultado.FeCaeDetalleResultado det = new FeCaeResultado.FeCaeDetalleResultado();
        det.setResultado("A"); det.setCae("12345678901234"); det.setCaeFchVto("20261231");
        det.setCbteDesde(11L); det.setCbteHasta(11L);
        cae.getDetalles().add(det);
        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class))).thenReturn(cae);

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizadas").value(1));

        FacturaVenta aut = facturaVentaRepository.findById(id).orElseThrow();
        assertEquals(EstadoFactura.AUTORIZADA.name(), aut.getEstado());
        assertEquals("12345678901234", aut.getCae());
        Integer tipoComprobante = aut.getTipoComprobanteArca();
        Long docNro = aut.getDocNroReceptorArca();
        String moneda = aut.getMonedaArca();

        mockMvc.perform(put("/api/facturas/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legacyBody(fx)))
                .andExpect(status().isConflict());

        FacturaVenta sinCambios = facturaVentaRepository.findById(id).orElseThrow();
        assertEquals(tipoComprobante, sinCambios.getTipoComprobanteArca());
        assertEquals(docNro, sinCambios.getDocNroReceptorArca());
        assertEquals(moneda, sinCambios.getMonedaArca());
        assertEquals("12345678901234", sinCambios.getCae());

        mockMvc.perform(delete("/api/facturas/{id}", id))
                .andExpect(status().isConflict());

        byte[] pdf = mockMvc.perform(get("/api/facturas/{id}/pdf", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 100);
        try (var doc = Loader.loadPDF(pdf)) {
            String txt = new PDFTextStripper().getText(doc);
            assertTrue(txt.contains("12345678901234"));
        }

        // no reenvía autorizada
        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noEnviadasPorValidacion").value(1));
    }

    @Test
    void rechazadaEInciertaYConciliacion() throws Exception {
        Fixture fx = fixture();
        long rech = crearPendiente(fx, "r");
        stubCatalogos();
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 20, List.of(), List.of()));

        FeCaeResultado rejRes = new FeCaeResultado();
        rejRes.setResultadoGlobal("R");
        FeCaeResultado.FeCaeDetalleResultado rejDet = new FeCaeResultado.FeCaeDetalleResultado();
        rejDet.setResultado("R"); rejDet.setCbteDesde(21L); rejDet.setCbteHasta(21L);
        rejDet.getObservaciones().add(new com.example.api_tp.client.arca.wsfe.WsfeMessage(100, "rechazo"));
        rejRes.getDetalles().add(rejDet);

        FeCaeResultado incRes = new FeCaeResultado();
        incRes.setResultadoGlobal("P"); // no concluyente -> incierto

        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class))).thenReturn(rejRes).thenReturn(incRes);

        mockMvc.perform(post("/api/facturas/envios/arca").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[" + rech + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rechazadas").value(1));
        assertEquals("RECHAZADA", facturaVentaRepository.findById(rech).orElseThrow().getEstado());

        long inc = crearPendiente(fx, "i");
        mockMvc.perform(post("/api/facturas/envios/arca").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[" + inc + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultadoDesconocido").value(1));
        assertEquals("RESULTADO_DESCONOCIDO", facturaVentaRepository.findById(inc).orElseThrow().getEstado());
        // no se reenvía automáticamente una factura con resultado desconocido
        mockMvc.perform(post("/api/facturas/envios/arca").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[" + inc + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.noEnviadasPorValidacion").value(1));
    }

    @Test
    void reenviaRechazadaConNumeracionNueva() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, null);
        stubCatalogos();
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 20, List.of(), List.of()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 21, List.of(), List.of()));

        FeCaeResultado rechazo = new FeCaeResultado();
        rechazo.setResultadoGlobal("R");
        FeCaeResultado.FeCaeDetalleResultado detRechazo = new FeCaeResultado.FeCaeDetalleResultado();
        detRechazo.setResultado("R");
        detRechazo.setCbteDesde(21L);
        detRechazo.setCbteHasta(21L);
        rechazo.getDetalles().add(detRechazo);
        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class)))
                .thenReturn(rechazo, aprobada(22L));

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rechazadas").value(1));
        assertEquals(21L, facturaVentaRepository.findById(id).orElseThrow().getNumeroFiscal());

        // editar la rechazada la devuelve a PENDIENTE y limpia el número fiscal consumido
        mockMvc.perform(put("/api/facturas/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req(fx))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizadas").value(1));

        ArgumentCaptor<FeCaeSolicitud> captor = ArgumentCaptor.forClass(FeCaeSolicitud.class);
        verify(afipWsfeClient, times(2)).feCaeSolicitar(captor.capture());
        assertEquals(22L, captor.getAllValues().get(1).getComprobantes().get(0).getCbteDesde());
        assertEquals(22L, facturaVentaRepository.findById(id).orElseThrow().getNumeroFiscal());
    }

    @Test
    void conciliaResultadoInciertoConsultandoArca() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, null);
        stubCatalogos();
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 30, List.of(), List.of()));

        FeCaeResultado incierto = new FeCaeResultado();
        incierto.setResultadoGlobal("P"); // no concluyente -> RESULTADO_DESCONOCIDO
        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class))).thenReturn(incierto);

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultadoDesconocido").value(1));

        FacturaVenta incierta = facturaVentaRepository.findById(id).orElseThrow();
        assertEquals("RESULTADO_DESCONOCIDO", incierta.getEstado());
        long numeroFiscal = incierta.getNumeroFiscal();

        CompConsultarResultado consulta = new CompConsultarResultado();
        consulta.setResultado("A");
        consulta.setCodAutorizacion("99999999999999");
        consulta.setFchVto("20271231");
        consulta.setCbteDesde(numeroFiscal);
        consulta.setCbteHasta(numeroFiscal);
        when(afipWsfeClient.feCompConsultar(eq(1), eq(1), eq(numeroFiscal))).thenReturn(consulta);

        mockMvc.perform(post("/api/facturas/{id}/conciliar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("APROBADA"))
                .andExpect(jsonPath("$.estado").value("AUTORIZADA"));

        FacturaVenta autorizada = facturaVentaRepository.findById(id).orElseThrow();
        assertEquals("AUTORIZADA", autorizada.getEstado());
        assertEquals("99999999999999", autorizada.getCae());
        assertEquals("A", autorizada.getResultadoAfip());
    }

    @Test
    void pdfIndividualRechazaNoAutorizada() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, null);
        mockMvc.perform(get("/api/facturas/{id}/pdf", id)).andExpect(status().isConflict());
    }

    @Test
    void enviaSolicitudConDatosFiscalesDeterminadosPorElBackend() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, null);
        stubCatalogos();
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 40, List.of(), List.of()));
        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class))).thenReturn(aprobada(41L));

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizadas").value(1));

        ArgumentCaptor<FeCaeSolicitud> captor = ArgumentCaptor.forClass(FeCaeSolicitud.class);
        verify(afipWsfeClient).feCaeSolicitar(captor.capture());
        FeCaeSolicitud solicitud = captor.getValue();
        assertEquals(1, solicitud.getPuntoVenta());
        assertEquals(1, solicitud.getTipoComprobante());
        FeCaeDetalle detalle = solicitud.getComprobantes().get(0);
        assertEquals(1, detalle.getConcepto());
        assertEquals(80, detalle.getDocTipo());
        assertEquals(20123456789L, detalle.getDocNro());
        assertEquals(1, detalle.getCondicionIvaReceptorId());
        assertEquals("PES", detalle.getMonId());
        assertEquals(0, detalle.getMonCotiz().compareTo(BigDecimal.ONE));

        FacturaVenta guardada = facturaVentaRepository.findById(id).orElseThrow();
        assertEquals(1, guardada.getTipoComprobanteArca());
        assertEquals(1, guardada.getConceptoArca());
        assertEquals(80, guardada.getDocTipoReceptorArca());
        assertEquals(20123456789L, guardada.getDocNroReceptorArca());
        assertEquals(1, guardada.getCondicionIvaReceptorArca());
        assertEquals("PES", guardada.getMonedaArca());
        assertEquals(1.0, guardada.getCotizacionArca(), 0.001);
    }

    @Test
    void determinaCotizacionDeMonedaExtranjeraEnElBackend() throws Exception {
        Fixture fx = fixture();
        Long usdId = crearTipoMoneda("DOL", "Dólar Estadounidense", "US$");
        long id = crearPendiente(fx, null, usdId);
        stubCatalogos();
        when(afipWsfeClient.feParamGetCotizacion("DOL"))
                .thenReturn(new CotizacionArca("DOL", new BigDecimal("1234.56"), "20261021"));
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 50, List.of(), List.of()));
        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class))).thenReturn(aprobada(51L));

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizadas").value(1));

        ArgumentCaptor<FeCaeSolicitud> captor = ArgumentCaptor.forClass(FeCaeSolicitud.class);
        verify(afipWsfeClient).feCaeSolicitar(captor.capture());
        FeCaeDetalle detalle = captor.getValue().getComprobantes().get(0);
        assertEquals("DOL", detalle.getMonId());
        assertEquals(0, detalle.getMonCotiz().compareTo(new BigDecimal("1234.56")));
        assertEquals(1234.56,
                facturaVentaRepository.findById(id).orElseThrow().getCotizacionArca(), 0.001);
    }

    @Test
    void noEnviaSiNoPuedeDeterminarLaCotizacion() throws Exception {
        Fixture fx = fixture();
        Long usdId = crearTipoMoneda("DOL", "Dólar Estadounidense", "US$");
        long id = crearPendiente(fx, null, usdId);
        stubCatalogos();
        when(afipWsfeClient.feParamGetCotizacion("DOL")).thenReturn(null);

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noEnviadasPorValidacion").value(1));

        verify(afipWsfeClient, never()).feCaeSolicitar(any());
        FacturaVenta factura = facturaVentaRepository.findById(id).orElseThrow();
        assertEquals("PENDIENTE", factura.getEstado());
        assertEquals("DOL", factura.getMonedaArca());
    }

    @Test
    void persisteInciertoConMensajeLargoSin409() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, null);
        stubCatalogos();
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 60, List.of(), List.of()));
        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class)))
                .thenThrow(new ArcaCommunicationException("WSFEv1 Fault [500]: " + "detalle ".repeat(80), 500, "body"));

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultadoDesconocido").value(1));

        FacturaVenta factura = facturaVentaRepository.findById(id).orElseThrow();
        assertEquals("RESULTADO_DESCONOCIDO", factura.getEstado());
        assertTrue(factura.getMotivoRechazo().length() > 255);
        assertTrue(factura.getMotivoRechazo().length() <= 1000);

        mockMvc.perform(get("/api/facturas/{id}/envios", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoIntento").value("INCIERTO"))
                .andExpect(jsonPath("$[0].mensajeTecnico").isNotEmpty());
    }

    @Test
    void persisteAutorizadaConObservacionesLargas() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, null);
        stubCatalogos();
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 80, List.of(), List.of()));
        FeCaeResultado cae = aprobada(81L);
        cae.getDetalles().get(0).getObservaciones()
                .add(new com.example.api_tp.client.arca.wsfe.WsfeMessage(100, "observacion ".repeat(50)));
        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class))).thenReturn(cae);

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizadas").value(1));

        FacturaVenta factura = facturaVentaRepository.findById(id).orElseThrow();
        assertEquals("AUTORIZADA", factura.getEstado());
        assertTrue(factura.getObservaciones().length() >= 600);
        assertTrue(factura.getObservaciones().length() <= 1000);
    }

    @Test
    void unFalloDePersistenciaNoCortaElLote() throws Exception {
        Fixture fx = fixture();
        long falla = crearPendiente(fx, null);
        long ok = crearPendiente(fx, null);
        stubCatalogos();
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 90, List.of(), List.of()));
        // un CAE de 300 caracteres no entra en la columna varchar(255): fuerza el fallo de persistencia
        FeCaeResultado caeInvalido = aprobada(91L);
        caeInvalido.getDetalles().get(0).setCae("X".repeat(300));
        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class)))
                .thenReturn(caeInvalido, aprobada(92L));

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + falla + "," + ok + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesadas").value(2))
                .andExpect(jsonPath("$.autorizadas").value(1))
                .andExpect(jsonPath("$.resultadoDesconocido").value(1));

        assertEquals("RESULTADO_DESCONOCIDO", facturaVentaRepository.findById(falla).orElseThrow().getEstado());
        assertEquals("AUTORIZADA", facturaVentaRepository.findById(ok).orElseThrow().getEstado());
        mockMvc.perform(get("/api/facturas/{id}/envios", falla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoIntento").value("INCIERTO"));
    }

    @Test
    void noEnviaSiLaCondicionIvaNoEstaEnElCatalogoDeSuClase() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, null);
        stubCatalogos();
        // la factura es clase A (receptor RI) pero el catálogo A no incluye el código 1
        when(afipWsfeClient.feParamGetCondicionIvaReceptor("A"))
                .thenReturn(List.of(new ParametroArca("6", "Responsable Monotributo")));

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noEnviadasPorValidacion").value(1));

        verify(afipWsfeClient, never()).feCaeSolicitar(any());
        assertEquals("PENDIENTE", facturaVentaRepository.findById(id).orElseThrow().getEstado());
        mockMvc.perform(get("/api/facturas/{id}/envios", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoIntento").value("NO_ENVIADA"))
                .andExpect(jsonPath("$[0].mensajeTecnico").value(containsString("condición IVA")));
    }

    @Test
    void enviaLaCondicionIvaResueltaDelCatalogo() throws Exception {
        Fixture fx = fixture();
        long id = crearPendiente(fx, null);
        stubCatalogos();
        when(afipWsfeClient.feCompUltimoAutorizado(anyInt(), anyInt()))
                .thenReturn(new UltimoAutorizadoResult(1, 1, 30, List.of(), List.of()));
        when(afipWsfeClient.feCaeSolicitar(any(FeCaeSolicitud.class))).thenReturn(aprobada(31L));

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizadas").value(1));

        ArgumentCaptor<FeCaeSolicitud> captor = ArgumentCaptor.forClass(FeCaeSolicitud.class);
        verify(afipWsfeClient).feCaeSolicitar(captor.capture());
        assertEquals(1, captor.getValue().getComprobantes().get(0).getCondicionIvaReceptorId());
        assertEquals(1, facturaVentaRepository.findById(id).orElseThrow().getCondicionIvaReceptorArca());
    }

    @Test
    void noEnviaSiLaMonedaNoEstaEnElCatalogo() throws Exception {
        Fixture fx = fixture();
        Long libraId = crearTipoMoneda("GBP", "Libra Esterlina", "£");
        long id = crearPendiente(fx, null, libraId);
        stubCatalogos();

        mockMvc.perform(post("/api/facturas/envios/arca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noEnviadasPorValidacion").value(1));

        verify(afipWsfeClient, never()).feCaeSolicitar(any());
        assertEquals("PENDIENTE", facturaVentaRepository.findById(id).orElseThrow().getEstado());
        mockMvc.perform(get("/api/facturas/{id}/envios", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mensajeTecnico").value(containsString("FEParamGetTiposMonedas")));
    }

    // --- helpers ---
    private long crearPendiente(Fixture fx, String obs) throws Exception {
        return crearPendiente(fx, obs, fx.tipoMonedaId);
    }

    private long crearPendiente(Fixture fx, String obs, Long tipoMonedaId) throws Exception {
        FacturaRequestDTO r = req(fx);
        r.setObservaciones(obs);
        r.setTipoMonedaId(tipoMonedaId);
        String resp = mockMvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(toJson(r)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(resp, "$.id");
        return id.longValue();
    }

    private FeCaeResultado aprobada(long cbte) {
        FeCaeResultado cae = new FeCaeResultado();
        cae.setResultadoGlobal("A");
        FeCaeResultado.FeCaeDetalleResultado det = new FeCaeResultado.FeCaeDetalleResultado();
        det.setResultado("A");
        det.setCae("12345678901234");
        det.setCaeFchVto("20261231");
        det.setCbteDesde(cbte);
        det.setCbteHasta(cbte);
        cae.getDetalles().add(det);
        return cae;
    }

    private Long crearTipoMoneda(String codigo, String denominacion, String simbolo) {
        return tx.execute(status -> {
            Usuario usuario = entityManager.getReference(Usuario.class, usuarioId);
            Date ahora = new Date();
            TipoMoneda moneda = new TipoMoneda();
            moneda.setCodigoAfip(codigo);
            moneda.setDenominacion(denominacion);
            moneda.setSimbolo(simbolo);
            auditar(moneda, usuario, ahora);
            entityManager.persist(moneda);
            entityManager.flush();
            return moneda.getId();
        });
    }

    private void stubCatalogos() {
        when(afipWsfeClient.feParamGetTiposIva()).thenReturn(List.of(
                new ParametroArca("5", "IVA 21%"),
                new ParametroArca("4", "IVA 10.5%"),
                new ParametroArca("3", "IVA 0%")));
        when(afipWsfeClient.feParamGetPtosVenta()).thenReturn(List.of(
                new PuntoVentaArca(1, "CAE", false)));
        when(afipWsfeClient.feParamGetCondicionIvaReceptor("A")).thenReturn(List.of(
                new ParametroArca("1", "IVA Responsable Inscripto")));
        when(afipWsfeClient.feParamGetCondicionIvaReceptor("B")).thenReturn(List.of(
                new ParametroArca("4", "IVA Sujeto Exento"),
                new ParametroArca("5", "Consumidor Final")));
        when(afipWsfeClient.feParamGetTiposMonedas()).thenReturn(List.of(
                new ParametroArca("PES", "Pesos Argentinos"),
                new ParametroArca("DOL", "Dólar Estadounidense"),
                new ParametroArca("060", "Euro"),
                new ParametroArca("012", "Real Brasileño")));
    }

    private FacturaRequestDTO req(Fixture fx) {
        FacturaRequestDTO r = new FacturaRequestDTO();
        r.setFechaEmision(new Date());
        r.setClienteId(fx.clienteId); r.setCondicionIvaId(fx.condicionIvaId); r.setPuntoVentaId(fx.puntoVentaId); r.setTipoMonedaId(fx.tipoMonedaId);
        r.setImporteCobrado(BigDecimal.ZERO);
        FacturaDetalleRequestDTO d = new FacturaDetalleRequestDTO();
        d.setArticuloId(fx.articuloId); d.setListaPrecioArticuloId(fx.listaPrecioArticuloId);
        d.setCantidad(new BigDecimal("1")); d.setPrecioUnitario(new BigDecimal("100")); d.setPorcentajeIva(new BigDecimal("21"));
        r.setDetalles(List.of(d));
        return r;
    }

    private String toJson(FacturaRequestDTO r) {
        var d = r.getDetalles().get(0);
        return "{" +
                "\"fechaEmision\":\"2026-10-21T00:00:00.000Z\"," +
                "\"clienteId\":" + r.getClienteId() + "," +
                "\"condicionIvaId\":" + r.getCondicionIvaId() + "," +
                "\"puntoVentaId\":" + r.getPuntoVentaId() + "," +
                "\"tipoMonedaId\":" + r.getTipoMonedaId() + "," +
                "\"importeCobrado\":0," +
                (r.getObservaciones()!=null?"\"observaciones\":\""+r.getObservaciones()+"\",":"") +
                "\"detalles\":[{" +
                "\"articuloId\":" + d.getArticuloId() + "," +
                "\"listaPrecioArticuloId\":" + d.getListaPrecioArticuloId() + "," +
                "\"cantidad\":1,\"precioUnitario\":100,\"porcentajeBonificacion\":0,\"porcentajeIva\":21}]}"
                ;
    }

    private String legacyBody(Fixture fx) {
        return "{" +
                "\"fechaEmision\":\"2026-10-21T00:00:00.000Z\"," +
                "\"clienteId\":" + fx.clienteId + "," +
                "\"condicionIvaId\":" + fx.condicionIvaId + "," +
                "\"puntoVentaId\":" + fx.puntoVentaId + "," +
                "\"tipoMonedaId\":" + fx.tipoMonedaId + "," +
                "\"importeCobrado\":0,\"detalles\":[{" +
                "\"articuloId\":" + fx.articuloId + "," +
                "\"listaPrecioArticuloId\":" + fx.listaPrecioArticuloId + "," +
                "\"cantidad\":1,\"precioUnitario\":100,\"porcentajeBonificacion\":0,\"porcentajeIva\":21}]}";
    }

    private Fixture fixture() {
        return tx.execute(status -> {
            Usuario usuario = entityManager.getReference(Usuario.class, usuarioId);
            Date ahora = new Date();
            Contacto contacto = new Contacto(); contacto.setEmail("a@b.c"); contacto.setTelefono("1"); contacto.setCelular("1"); entityManager.persist(contacto);
            Domicilio domicilio = new Domicilio(); domicilio.setNombreCalle("C"); domicilio.setNumeroCalle("1"); entityManager.persist(domicilio);
            Rubro rubro = new Rubro(); rubro.setDenominacion("R"); rubro.setCodigo(1); auditar(rubro, usuario, ahora); entityManager.persist(rubro);
            Marca marca = new Marca(); marca.setDenominacion("M"); marca.setCodigo(1); auditar(marca, usuario, ahora); entityManager.persist(marca);
            ListaPrecio lp = new ListaPrecio(); lp.setCodigo("L"); lp.setDenominacion("L"); auditar(lp, usuario, ahora); entityManager.persist(lp);
            Cliente cliente = new Cliente(); cliente.setCuitCuil("20123456789"); cliente.setDenominacion("Cli"); cliente.setContacto(contacto); cliente.setDomicilio(domicilio); auditar(cliente, usuario, ahora); entityManager.persist(cliente);
            CondicionIva ci = new CondicionIva(); ci.setCodigoAfip(1); ci.setDenominacion("RI"); auditar(ci, usuario, ahora); entityManager.persist(ci);
            PuntoVenta pv = new PuntoVenta(); pv.setNumero(1); pv.setDescripcion("PV1"); pv.setTipoEmision("ELECTRONICA"); pv.setDomicilioComercial("D"); auditar(pv, usuario, ahora); entityManager.persist(pv);
            TipoMoneda tm = new TipoMoneda(); tm.setCodigoAfip("PES"); tm.setDenominacion("Peso"); tm.setSimbolo("$"); auditar(tm, usuario, ahora); entityManager.persist(tm);
            Articulo art = new Articulo(); art.setRubro(rubro); art.setMarca(marca); art.setCodigo("A"); art.setDenominacion("Art"); auditar(art, usuario, ahora); entityManager.persist(art);
            ListaPrecioArticulo lpa = new ListaPrecioArticulo(); lpa.setListaPrecio(lp); lpa.setArticulo(art); lpa.setPrecioVenta(100.0); auditar(lpa, usuario, ahora); entityManager.persist(lpa);
            entityManager.flush();
            return new Fixture(cliente.getId(), ci.getId(), pv.getId(), tm.getId(), art.getId(), lpa.getId());
        });
    }
    private void auditar(AuditoriaApp e, Usuario u, Date d) { e.setFechaAlta(d); e.setFechaModificacion(d); e.setUsuarioCarga(u); e.setUsuarioModificacion(u); }
    record Fixture(Long clienteId, Long condicionIvaId, Long puntoVentaId, Long tipoMonedaId, Long articuloId, Long listaPrecioArticuloId) {}
}

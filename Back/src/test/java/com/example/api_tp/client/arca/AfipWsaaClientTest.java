package com.example.api_tp.client.arca;

import static org.junit.jupiter.api.Assertions.*;


import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.example.api_tp.client.AfipWsaaClient;
import com.example.api_tp.client.arca.exception.ArcaCommunicationException;
import com.example.api_tp.client.arca.exception.ArcaConfigurationException;
import com.example.api_tp.client.arca.exception.ArcaWsaaException;
import com.example.api_tp.client.arca.wsaa.WsaaTicket;
import com.example.api_tp.config.ArcaProperties;

class AfipWsaaClientTest {

    private ArcaProperties propsHomologacion() {
        ArcaProperties p = new ArcaProperties();
        p.setEnvironment(ArcaProperties.Environment.HOMOLOGACION);
        p.setCuit("20123456789");
        p.getWsaa().setUrl("https://wsaahomo.afip.gov.ar/ws/services/LoginCms");
        p.getWsaa().setService("wsfe");
        p.getWsaa().setRenewalMargin(Duration.ofMinutes(10));
        p.getWsaa().setReadTimeout(Duration.ofSeconds(5));
        p.getCredentials().setCertPath("classpath:certs/FacturaTestUtn.crt");
        p.getCredentials().setKeyPath("classpath:certs/privada_test.key");
        return p;
    }

    private String loginCmsResponse(String token, String sign, String expiration) {
        String inner = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<loginTicketResponse version=\"1.0\">"
                + "<header><source>CN=wsaa</source><destination>CN=test</destination>"
                + "<uniqueId>1</uniqueId><generationTime>2026-01-01T10:00:00.000-03:00</generationTime>"
                + "<expirationTime>" + expiration + "</expirationTime></header>"
                + "<credentials><token>" + token + "</token><sign>" + sign + "</sign></credentials>"
                + "</loginTicketResponse>";
        String escaped = inner.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Body><loginCmsResponse xmlns=\"http://wsaa.afip.gov.ar/ws/services/LoginCms\">"
                + "<loginCmsReturn>" + escaped + "</loginCmsReturn>"
                + "</loginCmsResponse></soapenv:Body></soapenv:Envelope>";
    }

    @Test
    void generaTraConCamposRequeridos() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        AfipWsaaClient client = new AfipWsaaClient(propsHomologacion(), clock, new DefaultResourceLoader(),
                new StubHttpClient(r -> StubHttpClient.response(200, "")));
        String tra = client.generarTra("wsfe");
        assertTrue(tra.contains("<loginTicketRequest"));
        assertTrue(tra.contains("<uniqueId>"));
        assertTrue(tra.contains("<generationTime>"));
        assertTrue(tra.contains("<expirationTime>"));
        assertTrue(tra.contains("<service>wsfe</service>"));
    }

    @Test
    void cargaCertificadoYClaveYVerificaCorrespondencia() {
        Clock clock = Clock.fixed(Instant.parse("2027-01-01T12:00:00Z"), ZoneId.of("UTC"));
        AfipWsaaClient client = new AfipWsaaClient(propsHomologacion(), clock);
        X509Certificate cert = client.cargarCertificado("classpath:certs/FacturaTestUtn.crt");
        PrivateKey key = client.cargarClavePrivada("classpath:certs/privada_test.key");
        assertNotNull(cert);
        assertNotNull(key);
        assertDoesNotThrow(() -> client.validarVigenciaCertificado(cert));
        assertDoesNotThrow(() -> client.validarCorrespondenciaCertificadoClave(cert, key));
    }

    @Test
    void firmaTraCmsPkcs7() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2027-01-01T12:00:00Z"), ZoneId.of("UTC"));
        AfipWsaaClient client = new AfipWsaaClient(propsHomologacion(), clock);
        X509Certificate cert = client.cargarCertificado("classpath:certs/FacturaTestUtn.crt");
        PrivateKey key = client.cargarClavePrivada("classpath:certs/privada_test.key");
        byte[] cms = client.firmarTra(client.generarTra("wsfe"), cert, key);
        assertNotNull(cms);
        assertTrue(cms.length > 100);
    }

    @Test
    void parseaTokenSignYVencimiento() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC"));
        AfipWsaaClient client = new AfipWsaaClient(propsHomologacion(), clock);
        String exp = "2026-01-01T23:00:00.000-03:00";
        String resp = loginCmsResponse("TOKEN123", "SIGN456", exp);
        WsaaTicket ticket = client.parsearRespuestaLoginCms(resp, "wsfe", "20123456789");
        assertEquals("TOKEN123", ticket.getToken());
        assertEquals("SIGN456", ticket.getSign());
        assertNotNull(ticket.getExpirationTime());
        assertEquals("wsfe", ticket.getService());
    }

    @Test
    void interpretaSoapFaultWsaa() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC"));
        AfipWsaaClient client = new AfipWsaaClient(propsHomologacion(), clock);
        String fault = "<?xml version=\"1.0\"?><soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soapenv:Body><soapenv:Fault><faultcode>ns1:coe.alreadyAuthenticated</faultcode>"
                + "<faultstring>El CEE ya posee un TA valido</faultstring></soapenv:Fault></soapenv:Body></soapenv:Envelope>";
        ArcaWsaaException ex = assertThrows(ArcaWsaaException.class,
                () -> client.parsearRespuestaLoginCms(fault, "wsfe", "20"));
        assertTrue(ex.getMessage().contains("Fault"));
    }

    @Test
    void manejaErroresHttpYTimeout() {
        Clock clock = Clock.fixed(Instant.parse("2027-01-01T12:00:00Z"), ZoneId.of("UTC"));
        StubHttpClient http500 = new StubHttpClient(r -> StubHttpClient.response(500, "error"));
        AfipWsaaClient c1 = new AfipWsaaClient(propsHomologacion(), clock, new DefaultResourceLoader(), http500);
        assertThrows(ArcaCommunicationException.class, c1::obtenerTicketAcceso);

        StubHttpClient timeout = new StubHttpClient(r -> { throw new java.net.http.HttpTimeoutException("t"); });
        AfipWsaaClient c2 = new AfipWsaaClient(propsHomologacion(), clock, new DefaultResourceLoader(), timeout);
        assertThrows(ArcaCommunicationException.class, c2::obtenerTicketAcceso);
    }

    @Test
    void reutilizaTicketVigenteYRenuevaVencido() {
        Instant now = Instant.parse("2027-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        String expVigente = "2027-01-01T13:00:00Z";
        StubHttpClient http = new StubHttpClient(r -> StubHttpClient.response(200, loginCmsResponse("T1", "S1", expVigente)));
        AfipWsaaClient client = new AfipWsaaClient(propsHomologacion(), clock, new DefaultResourceLoader(), http);
        WsaaTicket t1 = client.obtenerTicketAcceso();
        WsaaTicket t2 = client.obtenerTicketAcceso();
        assertSame(t1.getToken(), t2.getToken());
        assertEquals(1, http.calls(), "Debe reutilizar ticket vigente");

        // ticket cercano a vencer (dentro del margen) -> renueva
        String expCorto = "2027-01-01T12:05:00Z"; // dentro del margen de 10m
        StubHttpClient http2 = new StubHttpClient(r -> StubHttpClient.response(200, loginCmsResponse("T2", "S2", expCorto)));
        AfipWsaaClient client2 = new AfipWsaaClient(propsHomologacion(), clock, new DefaultResourceLoader(), http2);
        client2.obtenerTicketAcceso();
        client2.obtenerTicketAcceso();
        assertEquals(2, http2.calls(), "Debe renovar cuando no es vigente con margen");
    }

    @Test
    void controlConcurrenciaEvitaSolicitudesSimultaneas() throws Exception {
        Instant now = Instant.parse("2027-01-01T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        String exp = "2027-01-01T13:00:00Z";
        StubHttpClient http = new StubHttpClient(r -> {
            try { Thread.sleep(30); } catch (InterruptedException ignored) {}
            return StubHttpClient.response(200, loginCmsResponse("TC", "SC", exp));
        });
        AfipWsaaClient client = new AfipWsaaClient(propsHomologacion(), clock, new DefaultResourceLoader(), http);
        ExecutorService ex = Executors.newFixedThreadPool(6);
        try {
            List<Callable<WsaaTicket>> tasks = new ArrayList<>();
            for (int i = 0; i < 6; i++) tasks.add(client::obtenerTicketAcceso);
            List<Future<WsaaTicket>> futures = ex.invokeAll(tasks);
            for (Future<WsaaTicket> f : futures) assertNotNull(f.get().getToken());
            assertEquals(1, http.calls(), "Solo una solicitud concurrente debe ir a WSAA");
        } finally {
            ex.shutdownNow();
        }
    }

    @Test
    void impideConfiguracionDeProduccion() {
        ArcaProperties p = propsHomologacion();
        p.setEnvironment(ArcaProperties.Environment.PRODUCCION);
        assertThrows(ArcaConfigurationException.class,
                () -> new AfipWsaaClient(p, Clock.systemUTC()));
    }
}

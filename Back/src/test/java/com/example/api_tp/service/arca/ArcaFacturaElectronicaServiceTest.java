package com.example.api_tp.service.arca;

import static org.junit.jupiter.api.Assertions.*;


import org.junit.jupiter.api.Test;

import com.example.api_tp.client.AfipWsaaClient;
import com.example.api_tp.client.AfipWsfeClient;
import com.example.api_tp.client.arca.exception.ArcaException;
import com.example.api_tp.client.arca.wsaa.WsaaTicket;
import com.example.api_tp.client.arca.wsfe.CompConsultarResultado;
import com.example.api_tp.client.arca.wsfe.FeCaeResultado;
import com.example.api_tp.client.arca.wsfe.FeCaeSolicitud;
import com.example.api_tp.client.arca.wsfe.ParametroArca;
import com.example.api_tp.client.arca.wsfe.WsfeMessage;
import com.example.api_tp.config.ArcaProperties;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

class ArcaFacturaElectronicaServiceTest {

    static class FixedWsaaClient extends AfipWsaaClient {
        FixedWsaaClient(ArcaProperties props) {
            super(props, Clock.fixed(Instant.parse("2025-01-01T12:00:00Z"), ZoneId.of("UTC")));
        }
        @Override public WsaaTicket obtenerTicketAcceso() {
            return new WsaaTicket("T","S", Instant.parse("2030-01-01T00:00:00Z"), "wsfe", null, null, "20123456789");
        }
    }

    private ArcaFacturaElectronicaService service() {
        ArcaProperties p = new ArcaProperties();
        p.setEnvironment(ArcaProperties.Environment.HOMOLOGACION);
        p.setCuit("20123456789");
        AfipWsaaClient wsaa = new FixedWsaaClient(p);
        AfipWsfeClient wsfe = new AfipWsfeClient(wsaa, p);
        return new ArcaFacturaElectronicaService(wsaa, wsfe);
    }

    @Test
    void interpretaCaeAprobado() {
        ArcaFacturaElectronicaService svc = service();
        FeCaeResultado resp = new FeCaeResultado();
        resp.setResultadoGlobal("A");
        FeCaeResultado.FeCaeDetalleResultado d = new FeCaeResultado.FeCaeDetalleResultado();
        d.setResultado("A");
        d.setCae("123");
        d.setCaeFchVto("20260111");
        d.setCbteDesde(1L); d.setCbteHasta(1L);
        resp.getDetalles().add(d);
        FeCaeSolicitud sol = new FeCaeSolicitud(); sol.setPuntoVenta(1); sol.setTipoComprobante(11);
        ArcaEmisionResultado r = svc.interpretarResultadoCae(resp, sol);
        assertEquals(ArcaEmisionResultado.Estado.APROBADA, r.getEstado());
        assertEquals("123", r.getCae());
    }

    @Test
    void interpretaCaeRechazado() {
        ArcaFacturaElectronicaService svc = service();
        FeCaeResultado resp = new FeCaeResultado();
        resp.setResultadoGlobal("R");
        FeCaeResultado.FeCaeDetalleResultado d = new FeCaeResultado.FeCaeDetalleResultado();
        d.setResultado("R");
        d.getObservaciones().add(new WsfeMessage(100, "x"));
        resp.getDetalles().add(d);
        ArcaEmisionResultado r = svc.interpretarResultadoCae(resp, new FeCaeSolicitud());
        assertEquals(ArcaEmisionResultado.Estado.RECHAZADA, r.getEstado());
        assertFalse(r.getObservaciones().isEmpty());
    }

    @Test
    void interpretaRespuestaIncierta() {
        ArcaFacturaElectronicaService svc = service();
        FeCaeResultado resp = new FeCaeResultado();
        resp.setResultadoGlobal("P");
        // sin detalle concluyente
        ArcaEmisionResultado r = svc.interpretarResultadoCae(resp, new FeCaeSolicitud());
        assertEquals(ArcaEmisionResultado.Estado.INCIERTO, r.getEstado());
    }

    @Test
    void interpretaConsultaAprobadaRechazadaEIncierta() {
        ArcaFacturaElectronicaService svc = service();
        CompConsultarResultado ok = new CompConsultarResultado();
        ok.setResultado("A"); ok.setCodAutorizacion("CAE1"); ok.setFchVto("20260101");
        assertEquals(ArcaEmisionResultado.Estado.APROBADA, svc.interpretarConsulta(ok,1,11,1).getEstado());

        CompConsultarResultado rej = new CompConsultarResultado();
        rej.setResultado("R");
        assertEquals(ArcaEmisionResultado.Estado.RECHAZADA, svc.interpretarConsulta(rej,1,11,1).getEstado());

        CompConsultarResultado inc = new CompConsultarResultado();
        assertEquals(ArcaEmisionResultado.Estado.INCIERTO, svc.interpretarConsulta(inc,1,11,1).getEstado());
    }

    @Test
    void resuelveAlicuotaContraCatalogoSinFallbackACero() {
        List<ParametroArca> catalogo = List.of(
                new ParametroArca("5", "IVA 21%"),
                new ParametroArca("4", "IVA 10.5%"),
                new ParametroArca("8", "IVA 5%"),
                new ParametroArca("3", "IVA 0%"));
        assertEquals(5, ArcaFacturaElectronicaService.resolverAlicuotaIvaId(new BigDecimal("21"), catalogo));
        assertEquals(4, ArcaFacturaElectronicaService.resolverAlicuotaIvaId(new BigDecimal("10.5"), catalogo));
        assertEquals(8, ArcaFacturaElectronicaService.resolverAlicuotaIvaId(new BigDecimal("5"), catalogo));
        assertEquals(3, ArcaFacturaElectronicaService.resolverAlicuotaIvaId(BigDecimal.ZERO, catalogo));
        assertThrows(ArcaException.class, () ->
                ArcaFacturaElectronicaService.resolverAlicuotaIvaId(new BigDecimal("17"), catalogo));
    }
}

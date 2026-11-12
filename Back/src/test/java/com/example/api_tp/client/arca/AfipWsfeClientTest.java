package com.example.api_tp.client.arca;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.example.api_tp.client.AfipWsaaClient;
import com.example.api_tp.client.AfipWsfeClient;
import com.example.api_tp.client.arca.exception.ArcaCommunicationException;
import com.example.api_tp.client.arca.exception.ArcaWsfeException;
import com.example.api_tp.client.arca.wsaa.WsaaTicket;
import com.example.api_tp.client.arca.wsfe.CompConsultarResultado;
import com.example.api_tp.client.arca.wsfe.FeCaeDetalle;
import com.example.api_tp.client.arca.wsfe.FeCaeResultado;
import com.example.api_tp.client.arca.wsfe.FeCaeSolicitud;
import com.example.api_tp.client.arca.wsfe.UltimoAutorizadoResult;
import com.example.api_tp.config.ArcaProperties;

class AfipWsfeClientTest {

    static class FixedWsaaClient extends AfipWsaaClient {
        private final WsaaTicket ticket;
        FixedWsaaClient(ArcaProperties props, WsaaTicket ticket) {
            super(props, Clock.fixed(Instant.parse("2025-01-01T12:00:00Z"), ZoneId.of("UTC")),
                    new org.springframework.core.io.DefaultResourceLoader(),
                    new StubHttpClient(r -> StubHttpClient.response(200, "")));
            this.ticket = ticket;
        }
        @Override public WsaaTicket obtenerTicketAcceso() { return ticket; }
        @Override public WsaaTicket obtenerTicketAcceso(String service) { return ticket; }
    }

    private ArcaProperties props() {
        ArcaProperties p = new ArcaProperties();
        p.setEnvironment(ArcaProperties.Environment.HOMOLOGACION);
        p.setCuit("20123456789");
        p.getWsfev1().setUrl("https://wswhomo.afip.gov.ar/wsfev1/service.asmx");
        return p;
    }

    private AfipWsfeClient clientWithResponse(String body) {
        ArcaProperties p = props();
        WsaaTicket ticket = new WsaaTicket("T", "S", Instant.parse("2030-01-01T00:00:00Z"), "wsfe", null, null, "20123456789");
        StubHttpClient http = new StubHttpClient(r -> StubHttpClient.response(200, body));
        return new AfipWsfeClient(new FixedWsaaClient(p, ticket), p, http);
    }

    private String envelope(String inner) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">"
                + "<soap:Body>" + inner + "</soap:Body></soap:Envelope>";
    }

    @Test
    void parseaFeCompUltimoAutorizado() {
        String resp = envelope("<FECompUltimoAutorizadoResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FECompUltimoAutorizadoResult><PtoVta>1</PtoVta><CbteTipo>11</CbteTipo><CbteNro>42</CbteNro></FECompUltimoAutorizadoResult>"
                + "</FECompUltimoAutorizadoResponse>");
        AfipWsfeClient client = clientWithResponse(resp);
        UltimoAutorizadoResult r = client.feCompUltimoAutorizado(1, 11);
        assertEquals(1, r.getPuntoVenta());
        assertEquals(11, r.getTipoComprobante());
        assertEquals(42, r.getCbteNro());
        assertEquals(43, r.getProximoCbteNro());
        assertFalse(r.tieneErrores());
    }

    @Test
    void interpretaResultadoFeCaeAprobado() {
        String resp = envelope("<FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FECAESolicitarResult>"
                + "<FeCabResp><Cuit>20123456789</Cuit><PtoVta>1</PtoVta><CbteTipo>11</CbteTipo><FchProceso>20260101</FchProceso><CantReg>1</CantReg><Resultado>A</Resultado></FeCabResp>"
                + "<FeDetResp><FECAEDetResponse><Concepto>1</Concepto><DocTipo>80</DocTipo><DocNro>20111111112</DocNro><CbteDesde>43</CbteDesde><CbteHasta>43</CbteHasta><CbteFch>20260101</CbteFch><Resultado>A</Resultado><CAE>12345678901234</CAE><CAEFchVto>20260111</CAEFchVto></FECAEDetResponse></FeDetResp>"
                + "</FECAESolicitarResult></FECAESolicitarResponse>");
        AfipWsfeClient client = clientWithResponse(resp);
        FeCaeSolicitud sol = buildSolicitudMinima();
        FeCaeResultado res = client.feCaeSolicitar(sol);
        assertEquals("A", res.getResultadoGlobal());
        assertTrue(res.getDetalles().get(0).aprobado());
        assertEquals("12345678901234", res.getDetalles().get(0).getCae());
        assertEquals("20260111", res.getDetalles().get(0).getCaeFchVto());
    }

    @Test
    void interpretaResultadoFeCaeRechazadoConObservacionesYErrores() {
        String resp = envelope("<FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FECAESolicitarResult>"
                + "<FeCabResp><Resultado>R</Resultado></FeCabResp>"
                + "<FeDetResp><FECAEDetResponse><CbteDesde>43</CbteDesde><CbteHasta>43</CbteHasta><Resultado>R</Resultado>"
                + "<Observaciones><Obs><Code>10016</Code><Msg>Importe invalido</Msg></Obs></Observaciones>"
                + "</FECAEDetResponse></FeDetResp>"
                + "<Errors><Err><Code>1000</Code><Msg>Error validacion</Msg></Err></Errors>"
                + "</FECAESolicitarResult></FECAESolicitarResponse>");
        AfipWsfeClient client = clientWithResponse(resp);
        FeCaeResultado res = client.feCaeSolicitar(buildSolicitudMinima());
        assertTrue(res.esRechazadoGlobal());
        assertFalse(res.getDetalles().get(0).aprobado());
        assertEquals(1, res.getDetalles().get(0).getObservaciones().size());
        assertEquals(1, res.getErrors().size());
    }

    @Test
    void parseaFeCompConsultar() {
        String resp = envelope("<FECompConsultarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FECompConsultarResult>"
                + "<ResultGet><Concepto>1</Concepto><DocTipo>80</DocTipo><DocNro>20111111112</DocNro><CbteDesde>43</CbteDesde><CbteHasta>43</CbteHasta><CbteFch>20260101</CbteFch><ImpTotal>121.00</ImpTotal><ImpNeto>100.00</ImpNeto><ImpIVA>21.00</ImpIVA><Resultado>A</Resultado><CodAutorizacion>999</CodAutorizacion><FchVto>20260111</FchVto></ResultGet>"
                + "</FECompConsultarResult></FECompConsultarResponse>");
        AfipWsfeClient client = clientWithResponse(resp);
        CompConsultarResultado r = client.feCompConsultar(11, 1, 43);
        assertEquals(Long.valueOf(43), r.getCbteDesde());
        assertEquals("A", r.getResultado());
        assertEquals("999", r.getCodAutorizacion());
        assertEquals("20260111", r.getFchVto());
    }

    @Test
    void serializaSolicitudFeCaeConCamposPrincipales() {
        String resp = envelope("<FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECAESolicitarResult><FeCabResp><Resultado>R</Resultado></FeCabResp></FECAESolicitarResult></FECAESolicitarResponse>");
        ArcaProperties p = props();
        WsaaTicket ticket = new WsaaTicket("T", "S", Instant.parse("2030-01-01T00:00:00Z"), "wsfe", null, null, "20123456789");
        
        StubHttpClient http = new StubHttpClient(r -> { /* capture not trivial via HttpRequest body publisher; rely on no-exception */ return StubHttpClient.response(200, resp); });
        AfipWsfeClient client = new AfipWsfeClient(new FixedWsaaClient(p, ticket), p, http);
        FeCaeSolicitud sol = buildSolicitudMinima();
        String xml = client.serializarDetalle(sol.getComprobantes().get(0));
        assertTrue(xml.contains("<ar:FECAEDetRequest>"));
        assertTrue(xml.contains("<ar:CbteDesde>43</ar:CbteDesde>"));
        assertTrue(xml.contains("<ar:ImpTotal>121.00</ar:ImpTotal>"));
        assertTrue(xml.contains("<ar:CondicionIVAReceptorId>5</ar:CondicionIVAReceptorId>"));
    }

    @Test
    void interpretaSoapFaultWsfe() {
        String fault = "<?xml version=\"1.0\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body><soap:Fault><faultcode>soap:Client</faultcode><faultstring>Error</faultstring></soap:Fault></soap:Body></soap:Envelope>";
        AfipWsfeClient client = clientWithResponse(fault);
        assertThrows(ArcaWsfeException.class, () -> client.feCompUltimoAutorizado(1, 11));
    }

    @Test
    void propagaTimeoutComoComunicacion() {
        ArcaProperties p = props();
        WsaaTicket ticket = new WsaaTicket("T", "S", Instant.parse("2030-01-01T00:00:00Z"), "wsfe", null, null, "20123456789");
        StubHttpClient http = new StubHttpClient(r -> { throw new java.net.http.HttpTimeoutException("t"); });
        AfipWsfeClient client = new AfipWsfeClient(new FixedWsaaClient(p, ticket), p, http);
        assertThrows(ArcaCommunicationException.class, () -> client.feCompUltimoAutorizado(1, 11));
    }

    @Test
    void parseaCatalogoTiposIva() {
        String resp = envelope("<FEParamGetTiposIvaResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FEParamGetTiposIvaResult><ResultGet>"
                + "<IvaTipo><Id>5</Id><Desc>IVA 21%</Desc><FchDesde>20000101</FchDesde><FchHasta>NULL</FchHasta></IvaTipo>"
                + "<IvaTipo><Id>3</Id><Desc>IVA 0%</Desc><FchDesde>20000101</FchDesde><FchHasta>NULL</FchHasta></IvaTipo>"
                + "</ResultGet></FEParamGetTiposIvaResult></FEParamGetTiposIvaResponse>");
        AfipWsfeClient client = clientWithResponse(resp);
        var tipos = client.feParamGetTiposIva();
        assertEquals(2, tipos.size());
        assertEquals("5", tipos.get(0).getId());
        assertEquals("IVA 21%", tipos.get(0).getDescripcion());
    }

    @Test
    void parseaPuntosVentaYCotizacion() {
        String resp = envelope("<FEParamGetPtosVentaResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FEParamGetPtosVentaResult><ResultGet>"
                + "<PtoVenta><Nro>1</Nro><EmisionTipo>CAE</EmisionTipo><Bloqueado>N</Bloqueado></PtoVenta>"
                + "<PtoVenta><Nro>2</Nro><EmisionTipo>CAE</EmisionTipo><Bloqueado>S</Bloqueado></PtoVenta>"
                + "</ResultGet></FEParamGetPtosVentaResult></FEParamGetPtosVentaResponse>");
        AfipWsfeClient client = clientWithResponse(resp);
        var puntos = client.feParamGetPtosVenta();
        assertEquals(2, puntos.size());
        assertEquals(1, puntos.get(0).getNumero());
        assertFalse(puntos.get(0).isBloqueado());
        assertTrue(puntos.get(1).isBloqueado());

        String respCotizacion = envelope("<FEParamGetCotizacionResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FEParamGetCotizacionResult><ResultGet>"
                + "<MonId>USD</MonId><MonCotiz>1050.25</MonCotiz><FchCotiz>20260101</FchCotiz>"
                + "</ResultGet></FEParamGetCotizacionResult></FEParamGetCotizacionResponse>");
        var cotizacion = clientWithResponse(respCotizacion).feParamGetCotizacion("USD");
        assertEquals("USD", cotizacion.getMonId());
        assertEquals(0, new BigDecimal("1050.25").compareTo(cotizacion.getMonCotiz()));
    }

    @Test
    void propagaErroresDeParametros() {
        String resp = envelope("<FEParamGetTiposIvaResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FEParamGetTiposIvaResult><Errors><Err><Code>600</Code><Msg>No autorizado</Msg></Err></Errors>"
                + "</FEParamGetTiposIvaResult></FEParamGetTiposIvaResponse>");
        AfipWsfeClient client = clientWithResponse(resp);
        assertThrows(ArcaWsfeException.class, client::feParamGetTiposIva);
    }

    private FeCaeSolicitud buildSolicitudMinima() {
        FeCaeSolicitud sol = new FeCaeSolicitud();
        sol.setPuntoVenta(1);
        sol.setTipoComprobante(11);
        FeCaeDetalle det = new FeCaeDetalle();
        det.setConcepto(1);
        det.setDocTipo(80);
        det.setDocNro(20111111112L);
        det.setCbteDesde(43L);
        det.setCbteHasta(43L);
        det.setCbteFch("20260101");
        det.setImpTotal(new BigDecimal("121.00"));
        det.setImpTotConc(BigDecimal.ZERO);
        det.setImpNeto(new BigDecimal("100.00"));
        det.setImpOpEx(BigDecimal.ZERO);
        det.setImpIva(new BigDecimal("21.00"));
        det.setImpTrib(BigDecimal.ZERO);
        det.setMonId("PES");
        det.setMonCotiz(BigDecimal.ONE);
        det.setCondicionIvaReceptorId(5);
        det.getIva().add(new FeCaeDetalle.AlicuotaIva(5, new BigDecimal("100.00"), new BigDecimal("21.00")));
        sol.addComprobante(det);
        return sol;
    }
}

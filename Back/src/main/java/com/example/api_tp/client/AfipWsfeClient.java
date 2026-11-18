package com.example.api_tp.client;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.example.api_tp.client.arca.exception.ArcaCommunicationException;
import com.example.api_tp.client.arca.exception.ArcaConfigurationException;
import com.example.api_tp.client.arca.exception.ArcaWsfeException;
import com.example.api_tp.client.arca.wsaa.WsaaTicket;
import com.example.api_tp.client.arca.wsfe.CompConsultarResultado;
import com.example.api_tp.client.arca.wsfe.CotizacionArca;
import com.example.api_tp.client.arca.wsfe.FeCaeDetalle;
import com.example.api_tp.client.arca.wsfe.FeCaeResultado;
import com.example.api_tp.client.arca.wsfe.FeCaeSolicitud;
import com.example.api_tp.client.arca.wsfe.ParametroArca;
import com.example.api_tp.client.arca.wsfe.PuntoVentaArca;
import com.example.api_tp.client.arca.wsfe.UltimoAutorizadoResult;
import com.example.api_tp.client.arca.wsfe.WsfeAuth;
import com.example.api_tp.client.arca.wsfe.WsfeMessage;
import com.example.api_tp.config.ArcaProperties;


@Component
public class AfipWsfeClient {

    private static final String ARCA_NS = "http://ar.gov.afip.dif.FEV1/";
    private static final String SOAP_ENV_NS = "http://schemas.xmlsoap.org/soap/envelope/";

    private final AfipWsaaClient wsaaClient;
    private final ArcaProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public AfipWsfeClient(AfipWsaaClient wsaaClient, ArcaProperties properties) {
        this(wsaaClient, properties, HttpClient.newBuilder().build());
    }

    public AfipWsfeClient(AfipWsaaClient wsaaClient, ArcaProperties properties, HttpClient httpClient) {
        this.wsaaClient = Objects.requireNonNull(wsaaClient, "wsaaClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        asegurarHomologacion();
    }

    public WsfeAuth obtenerAutenticacion() {
        WsaaTicket ticket = wsaaClient.obtenerTicketAcceso();
        return WsfeAuth.from(ticket, properties.getCuit());
    }

    public UltimoAutorizadoResult feCompUltimoAutorizado(int puntoVenta, int tipoComprobante) {
        asegurarHomologacion();
        WsfeAuth auth = obtenerAutenticacion();
        String body = "<ar:FECompUltimoAutorizado xmlns:ar=\"" + ARCA_NS + "\">"
                + authXml(auth)
                + "<ar:PtoVta>" + puntoVenta + "</ar:PtoVta>"
                + "<ar:CbteTipo>" + tipoComprobante + "</ar:CbteTipo>"
                + "</ar:FECompUltimoAutorizado>";
        Document doc = invocarOperacion("FECompUltimoAutorizado", body);
        Element result = primerElementoPorNombreLocal(doc, "FECompUltimoAutorizadoResult");
        if (result == null) {
            throw new ArcaWsfeException("Respuesta WSFEv1 inválida: falta FECompUltimoAutorizadoResult");
        }
        int pto = parseInt(textoPorNombreLocal(result, "PtoVta"), puntoVenta);
        int cbteTipo = parseInt(textoPorNombreLocal(result, "CbteTipo"), tipoComprobante);
        int nro = parseInt(textoPorNombreLocal(result, "CbteNro"), 0);
        List<WsfeMessage> errors = parseMessages(result, "Errors", "Err", "Code", "Msg");
        List<WsfeMessage> events = parseMessages(result, "Events", "Evt", "Code", "Msg");
        return new UltimoAutorizadoResult(pto, cbteTipo, nro, errors, events);
    }

    public FeCaeResultado feCaeSolicitar(FeCaeSolicitud solicitud) {
        asegurarHomologacion();
        if (solicitud == null || solicitud.getComprobantes().isEmpty()) {
            throw new ArcaWsfeException("FECAESolicitar requiere al menos un comprobante");
        }
        WsfeAuth auth = obtenerAutenticacion();
        StringBuilder body = new StringBuilder(1024);
        body.append("<ar:FECAESolicitar xmlns:ar=\"").append(ARCA_NS).append("\">");
        body.append(authXml(auth));
        body.append("<ar:FeCAEReq>");
        body.append("<ar:FeCabReq>");
        body.append(tag("ar:CantReg", String.valueOf(solicitud.getComprobantes().size())));
        body.append(tag("ar:PtoVta", String.valueOf(solicitud.getPuntoVenta())));
        body.append(tag("ar:CbteTipo", String.valueOf(solicitud.getTipoComprobante())));
        body.append("</ar:FeCabReq>");
        body.append("<ar:FeDetReq>");
        for (FeCaeDetalle det : solicitud.getComprobantes()) {
            body.append(serializarDetalle(det));
        }
        body.append("</ar:FeDetReq>");
        body.append("</ar:FeCAEReq>");
        body.append("</ar:FECAESolicitar>");

        Document doc = invocarOperacion("FECAESolicitar", body.toString());
        Element result = primerElementoPorNombreLocal(doc, "FECAESolicitarResult");
        if (result == null) {
            throw new ArcaWsfeException("Respuesta WSFEv1 inválida: falta FECAESolicitarResult");
        }
        return parsearFeCaeResultado(result);
    }

    public CompConsultarResultado feCompConsultar(int tipoComprobante, int puntoVenta, long numeroComprobante) {
        asegurarHomologacion();
        WsfeAuth auth = obtenerAutenticacion();
        String body = "<ar:FECompConsultar xmlns:ar=\"" + ARCA_NS + "\">"
                + authXml(auth)
                + "<ar:FeCompConsReq>"
                + tag("ar:CbteTipo", String.valueOf(tipoComprobante))
                + tag("ar:PtoVta", String.valueOf(puntoVenta))
                + tag("ar:CbteNro", String.valueOf(numeroComprobante))
                + "</ar:FeCompConsReq>"
                + "</ar:FECompConsultar>";
        Document doc = invocarOperacion("FECompConsultar", body);
        Element result = primerElementoPorNombreLocal(doc, "FECompConsultarResult");
        if (result == null) {
            throw new ArcaWsfeException("Respuesta WSFEv1 inválida: falta FECompConsultarResult");
        }
        return parsearCompConsultarResultado(result);
    }

    public List<ParametroArca> feParamGetTiposCbte() {
        return feParamGetTipos("FEParamGetTiposCbte");
    }

    public List<ParametroArca> feParamGetTiposConcepto() {
        return feParamGetTipos("FEParamGetTiposConcepto");
    }

    public List<ParametroArca> feParamGetTiposDoc() {
        return feParamGetTipos("FEParamGetTiposDoc");
    }

    public List<ParametroArca> feParamGetTiposIva() {
        return feParamGetTipos("FEParamGetTiposIva");
    }

    public List<ParametroArca> feParamGetTiposMonedas() {
        return feParamGetTipos("FEParamGetTiposMonedas");
    }

    public List<ParametroArca> feParamGetCondicionIvaReceptor(String claseCmp) {
        return feParamGetTipos("FEParamGetCondicionIvaReceptor",
                StringUtils.hasText(claseCmp) ? tag("ar:ClaseCmp", claseCmp) : "");
    }

    public List<PuntoVentaArca> feParamGetPtosVenta() {
        
        List<PuntoVentaArca> out = new ArrayList<>();

        try{
            Element result = invocarParamGet("FEParamGetPtosVenta");
            Element resultGet = primerElementoPorNombreLocal(result, "ResultGet");

            if(resultGet != null) {
                for (Element item : hijosElemento(resultGet)) {
                    int numero = parseInt(textoPorNombreLocal(item, "Nro"), 0);
                    String emisionTipo = textoPorNombreLocal(item, "EmisionTipo");
                    boolean bloqueado = "S".equalsIgnoreCase(textoPorNombreLocal(item, "Bloqueado"));
                    out.add(new PuntoVentaArca(numero, emisionTipo, bloqueado));
                }
            }
        } catch (ArcaWsfeException ex) {
            
            if(ex.getMessage() != null && ex.getMessage().contains("602")){
                out.clear();
            }
            else{

            throw ex;
                
            }
        }
        
        if(out.isEmpty()){
            for(int i=1; i<=4; i++){
                out.add(new PuntoVentaArca(i, "CAE", false));
            }
        }

        return out;
    
    }

    public CotizacionArca feParamGetCotizacion(String monId) {
        asegurarHomologacion();
        WsfeAuth auth = obtenerAutenticacion();
        String body = "<ar:FEParamGetCotizacion xmlns:ar=\"" + ARCA_NS + "\">"
                + authXml(auth)
                + tag("ar:MonId", monId)
                + "</ar:FEParamGetCotizacion>";
        Document doc = invocarOperacion("FEParamGetCotizacion", body);
        Element result = primerElementoPorNombreLocal(doc, "FEParamGetCotizacionResult");
        if (result == null) {
            throw new ArcaWsfeException("Respuesta WSFEv1 inválida: falta FEParamGetCotizacionResult");
        }
        verificarErrores(result, "FEParamGetCotizacion");
        Element cotizacion = primerElementoPorNombreLocal(result, "ResultGet");
        if (cotizacion == null) {
            return null;
        }
        return new CotizacionArca(textoPorNombreLocal(cotizacion, "MonId"),
                parseBigDecimal(textoPorNombreLocal(cotizacion, "MonCotiz")),
                textoPorNombreLocal(cotizacion, "FchCotiz"));
    }

    private List<ParametroArca> feParamGetTipos(String operacion) {
        return feParamGetTipos(operacion, "");
    }

    private List<ParametroArca> feParamGetTipos(String operacion, String parametrosExtra) {
        Element result = invocarParamGet(operacion, parametrosExtra);
        List<ParametroArca> out = new ArrayList<>();
        for (Element item : hijosElemento(primerElementoPorNombreLocal(result, "ResultGet"))) {
            out.add(new ParametroArca(textoPorNombreLocal(item, "Id"), textoPorNombreLocal(item, "Desc")));
        }
        return out;
    }

    private Element invocarParamGet(String operacion) {
        return invocarParamGet(operacion, "");
    }

    private Element invocarParamGet(String operacion, String parametrosExtra) {
        asegurarHomologacion();
        WsfeAuth auth = obtenerAutenticacion();
        String body = "<ar:" + operacion + " xmlns:ar=\"" + ARCA_NS + "\">"
                + authXml(auth)
                + (parametrosExtra == null ? "" : parametrosExtra)
                + "</ar:" + operacion + ">";
        Document doc = invocarOperacion(operacion, body);
        Element result = primerElementoPorNombreLocal(doc, operacion + "Result");
        if (result == null) {
            throw new ArcaWsfeException("Respuesta WSFEv1 inválida: falta " + operacion + "Result");
        }
        verificarErrores(result, operacion);
        return result;
    }

    private static void verificarErrores(Element result, String operacion) {
        List<WsfeMessage> errors = parseMessages(result, "Errors", "Err", "Code", "Msg");
        if (!errors.isEmpty()) {
            throw new ArcaWsfeException("WSFEv1 " + operacion + " devolvió errores: " + errors);
        }
    }

    private static List<Element> hijosElemento(Element parent) {
        List<Element> result = new ArrayList<>();
        if (parent == null) return result;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) node);
            }
        }
        return result;
    }

    public String serializarDetalle(FeCaeDetalle det) {        StringBuilder sb = new StringBuilder(512);
        sb.append("<ar:FECAEDetRequest>");
        appendTagIfNotNull(sb, "ar:Concepto", det.getConcepto());
        appendTagIfNotNull(sb, "ar:DocTipo", det.getDocTipo());
        appendTagIfNotNull(sb, "ar:DocNro", det.getDocNro());
        appendTagIfNotNull(sb, "ar:CbteDesde", det.getCbteDesde());
        appendTagIfNotNull(sb, "ar:CbteHasta", det.getCbteHasta());
        appendTagIfNotBlank(sb, "ar:CbteFch", det.getCbteFch());
        appendTagIfNotNull(sb, "ar:ImpTotal", det.getImpTotal());
        appendTagIfNotNull(sb, "ar:ImpTotConc", det.getImpTotConc());
        appendTagIfNotNull(sb, "ar:ImpNeto", det.getImpNeto());
        appendTagIfNotNull(sb, "ar:ImpOpEx", det.getImpOpEx());
        appendTagIfNotNull(sb, "ar:ImpIVA", det.getImpIva());
        appendTagIfNotNull(sb, "ar:ImpTrib", det.getImpTrib());
        appendTagIfNotBlank(sb, "ar:FchServDesde", det.getFchServDesde());
        appendTagIfNotBlank(sb, "ar:FchServHasta", det.getFchServHasta());
        appendTagIfNotBlank(sb, "ar:FchVtoPago", det.getFchVtoPago());
        appendTagIfNotBlank(sb, "ar:MonId", det.getMonId());
        appendTagIfNotNull(sb, "ar:MonCotiz", det.getMonCotiz());
        appendTagIfNotNull(sb, "ar:CondicionIVAReceptorId", det.getCondicionIvaReceptorId());

        if (!det.getTributos().isEmpty()) {
            sb.append("<ar:Tributos>");
            for (FeCaeDetalle.Tributo t : det.getTributos()) {
                sb.append("<ar:Tributo>");
                appendTagIfNotNull(sb, "ar:Id", t.getId());
                appendTagIfNotBlank(sb, "ar:Desc", t.getDesc());
                appendTagIfNotNull(sb, "ar:BaseImp", t.getBaseImp());
                appendTagIfNotNull(sb, "ar:Alic", t.getAlic());
                appendTagIfNotNull(sb, "ar:Importe", t.getImporte());
                sb.append("</ar:Tributo>");
            }
            sb.append("</ar:Tributos>");
        }
        if (!det.getIva().isEmpty()) {
            sb.append("<ar:Iva>");
            for (FeCaeDetalle.AlicuotaIva a : det.getIva()) {
                sb.append("<ar:AlicIva>");
                appendTagIfNotNull(sb, "ar:Id", a.getId());
                appendTagIfNotNull(sb, "ar:BaseImp", a.getBaseImp());
                appendTagIfNotNull(sb, "ar:Importe", a.getImporte());
                sb.append("</ar:AlicIva>");
            }
            sb.append("</ar:Iva>");
        }
        sb.append("</ar:FECAEDetRequest>");
        return sb.toString();
    }

    public FeCaeResultado parsearFeCaeResultado(Element result) {
        FeCaeResultado out = new FeCaeResultado();
        Element cab = primerElementoPorNombreLocal(result, "FeCabResp");
        if (cab != null) {
            out.setResultadoGlobal(textoPorNombreLocal(cab, "Resultado"));
        }
        Element detResp = primerElementoPorNombreLocal(result, "FeDetResp");
        if (detResp != null) {
            for (Element det : elementosHijosPorNombreLocal(detResp, "FECAEDetResponse")) {
                FeCaeResultado.FeCaeDetalleResultado dr = new FeCaeResultado.FeCaeDetalleResultado();
                dr.setConcepto(parseIntegerObj(textoPorNombreLocal(det, "Concepto")));
                dr.setDocTipo(parseIntegerObj(textoPorNombreLocal(det, "DocTipo")));
                dr.setDocNro(parseLongObj(textoPorNombreLocal(det, "DocNro")));
                dr.setCbteDesde(parseLongObj(textoPorNombreLocal(det, "CbteDesde")));
                dr.setCbteHasta(parseLongObj(textoPorNombreLocal(det, "CbteHasta")));
                dr.setCbteFch(textoPorNombreLocal(det, "CbteFch"));
                dr.setResultado(textoPorNombreLocal(det, "Resultado"));
                dr.setCae(textoPorNombreLocal(det, "CAE"));
                dr.setCaeFchVto(textoPorNombreLocal(det, "CAEFchVto"));
                dr.getObservaciones().addAll(parseMessages(det, "Observaciones", "Obs", "Code", "Msg"));
                out.getDetalles().add(dr);
            }
        }
        out.getErrors().addAll(parseMessages(result, "Errors", "Err", "Code", "Msg"));
        out.getEvents().addAll(parseMessages(result, "Events", "Evt", "Code", "Msg"));
        return out;
    }

    public CompConsultarResultado parsearCompConsultarResultado(Element result) {
        CompConsultarResultado out = new CompConsultarResultado();
        Element comp = primerElementoPorNombreLocal(result, "ResultGet");
        if (comp != null) {
            out.setConcepto(parseIntegerObj(textoPorNombreLocal(comp, "Concepto")));
            out.setDocTipo(parseIntegerObj(textoPorNombreLocal(comp, "DocTipo")));
            out.setDocNro(parseLongObj(textoPorNombreLocal(comp, "DocNro")));
            out.setCbteDesde(parseLongObj(textoPorNombreLocal(comp, "CbteDesde")));
            out.setCbteHasta(parseLongObj(textoPorNombreLocal(comp, "CbteHasta")));
            out.setCbteFch(textoPorNombreLocal(comp, "CbteFch"));
            out.setImpTotal(textoPorNombreLocal(comp, "ImpTotal"));
            out.setImpNeto(textoPorNombreLocal(comp, "ImpNeto"));
            out.setImpIva(textoPorNombreLocal(comp, "ImpIVA"));
            out.setResultado(textoPorNombreLocal(comp, "Resultado"));
            out.setCodAutorizacion(textoPorNombreLocal(comp, "CodAutorizacion"));
            out.setFchVto(textoPorNombreLocal(comp, "FchVto"));
            out.setEmisionTipo(textoPorNombreLocal(comp, "EmisionTipo"));
            out.setMonId(textoPorNombreLocal(comp, "MonId"));
            out.setMonCotiz(textoPorNombreLocal(comp, "MonCotiz"));
            out.getObservaciones().addAll(parseMessages(comp, "Observaciones", "Obs", "Code", "Msg"));
        }
        out.getErrors().addAll(parseMessages(result, "Errors", "Err", "Code", "Msg"));
        out.getEvents().addAll(parseMessages(result, "Events", "Evt", "Code", "Msg"));
        return out;
    }

    Document invocarOperacion(String operacion, String bodyInnerXml) {
        String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"" + SOAP_ENV_NS + "\">"
                + "<soapenv:Header/><soapenv:Body>" + bodyInnerXml + "</soapenv:Body></soapenv:Envelope>";
        String response = enviarSoap(properties.getWsfev1().getUrl(), envelope, operacion,
                properties.getWsfev1().getConnectTimeout(), properties.getWsfev1().getReadTimeout());
        Document doc = parsearXml(response, "respuesta WSFEv1 " + operacion);
        Element fault = primerElementoPorNombreLocal(doc, "Fault");
        if (fault != null) {
            String faultString = textoHijoLocal(fault, "faultstring");
            String faultCode = textoHijoLocal(fault, "faultcode");
            String detail = textoDetalleFault(fault);
            String mensaje = "WSFEv1 Fault"
                    + (StringUtils.hasText(faultCode) ? " [" + faultCode + "]" : "")
                    + (StringUtils.hasText(faultString) ? ": " + faultString : "")
                    + (StringUtils.hasText(detail) ? " - " + detail : "");
            throw new ArcaWsfeException(mensaje);
        }
        return doc;
    }

    String enviarSoap(String url, String soapXml, String operacion, Duration connectTimeout, Duration readTimeout) {
        try {
            String soapAction = ARCA_NS + operacion;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(safeDuration(readTimeout, Duration.ofSeconds(30)))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"" + soapAction + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(soapXml, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body() == null ? "" : response.body();
            if (status < 200 || status >= 300) {
                throw new ArcaCommunicationException(
                        "WSFEv1 respondió con HTTP " + status + ": " + resumirMensaje(body), status, body);
            }
            return body;
        } catch (ArcaCommunicationException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ArcaCommunicationException("Timeout de comunicación con WSFEv1", ex);
        } catch (IOException ex) {
            throw new ArcaCommunicationException("Error de comunicación con WSFEv1", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ArcaCommunicationException("Comunicación con WSFEv1 interrumpida", ex);
        } catch (IllegalArgumentException ex) {
            throw new ArcaConfigurationException("URL de WSFEv1 inválida", ex);
        }
    }

    private String authXml(WsfeAuth auth) {
        return "<ar:Auth>"
                + tag("ar:Token", auth.getToken())
                + tag("ar:Sign", auth.getSign())
                + tag("ar:Cuit", String.valueOf(auth.getCuit()))
                + "</ar:Auth>";
    }

    private static List<WsfeMessage> parseMessages(Element parent, String containerName, String itemName,
            String codeName, String msgName) {
        List<WsfeMessage> out = new ArrayList<>();
        Element container = primerElementoPorNombreLocal(parent, containerName);
        if (container == null) {
            return out;
        }
        for (Element item : elementosHijosPorNombreLocal(container, itemName)) {
            Integer code = parseIntegerObj(textoPorNombreLocal(item, codeName));
            String msg = textoPorNombreLocal(item, msgName);
            out.add(new WsfeMessage(code, msg));
        }
        return out;
    }

    private static List<Element> elementosHijosPorNombreLocal(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        if (parent == null) return result;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) n;
                if (localName.equals(e.getLocalName()) || localName.equals(e.getNodeName())) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    static Element primerElementoPorNombreLocal(Node root, String localName) {
        if (root == null) return null;
        if (root.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) root;
            if (localName.equals(el.getLocalName()) || localName.equals(el.getNodeName())) return el;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Element found = primerElementoPorNombreLocal(children.item(i), localName);
            if (found != null) return found;
        }
        return null;
    }

    static String textoPorNombreLocal(Node root, String localName) {
        Element el = primerElementoPorNombreLocal(root, localName);
        return el == null ? null : el.getTextContent();
    }

    static String textoHijoLocal(Element parent, String localName) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                if (localName.equals(el.getLocalName()) || localName.equals(el.getNodeName())) return el.getTextContent();
            }
        }
        return null;
    }

    static String textoDetalleFault(Element fault) {
        Element detail = primerElementoPorNombreLocal(fault, "detail");
        if (detail == null) return null;
        String text = detail.getTextContent();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    Document parsearXml(String xml, String contexto) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new ArcaWsfeException("No se pudo interpretar XML de " + contexto, ex);
        }
    }

    private static void appendTagIfNotNull(StringBuilder sb, String tagName, Object value) {
        if (value != null) {
            sb.append(tag(tagName, String.valueOf(value)));
        }
    }

    private static void appendTagIfNotBlank(StringBuilder sb, String tagName, String value) {
        if (StringUtils.hasText(value)) {
            sb.append(tag(tagName, value));
        }
    }

    private static String tag(String name, String value) {
        return "<" + name + ">" + escaparXml(value) + "</" + name + ">";
    }

    private static String escaparXml(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String resumirMensaje(String body) {
        if (!StringUtils.hasText(body)) return "sin detalle";
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > 200 ? compact.substring(0, 200) + "..." : compact;
    }

    private static Duration safeDuration(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    private void asegurarHomologacion() {
        if (properties.getEnvironment() != ArcaProperties.Environment.HOMOLOGACION) {
            throw new ArcaConfigurationException(
                    "La integración ARCA solo está habilitada para HOMOLOGACION en esta etapa");
        }
        String wsfeUrl = properties.getWsfev1() != null ? properties.getWsfev1().getUrl() : null;
        if (esUrlProduccion(wsfeUrl)) {
            throw new ArcaConfigurationException(
                    "La configuración apunta a endpoints de producción; se requiere homologación");
        }
    }

    private static boolean esUrlProduccion(String url) {
        if (!StringUtils.hasText(url)) return false;
        String lower = url.toLowerCase();
        return lower.contains("servicios1.afip.gov.ar") && !lower.contains("wswhomo");
    }

    private static int parseInt(String v, int def) {
        try { return v == null || v.isBlank() ? def : Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
    }
    private static Integer parseIntegerObj(String v) {
        try { return v == null || v.isBlank() ? null : Integer.valueOf(v.trim()); } catch (Exception e) { return null; }
    }
    private static Long parseLongObj(String v) {
        try { return v == null || v.isBlank() ? null : Long.valueOf(v.trim()); } catch (Exception e) { return null; }
    }
    private static java.math.BigDecimal parseBigDecimal(String v) {
        try { return v == null || v.isBlank() ? null : new java.math.BigDecimal(v.trim()); } catch (Exception e) { return null; }
    }
}

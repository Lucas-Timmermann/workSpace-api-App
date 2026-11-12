package com.example.api_tp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.example.api_tp.client.arca.exception.ArcaCommunicationException;
import com.example.api_tp.client.arca.exception.ArcaConfigurationException;
import com.example.api_tp.client.arca.exception.ArcaWsaaException;
import com.example.api_tp.client.arca.wsaa.WsaaTicket;
import com.example.api_tp.config.ArcaProperties;


@Component
public class AfipWsaaClient {

    private static final String SOAP_ENV_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String WSAA_NS = "http://wsaa.afip.gov.ar/ws/services/LoginCms";
    private static final Object BC_LOCK = new Object();

    private final ArcaProperties properties;
    private final Clock clock;
    private final ResourceLoader resourceLoader;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, CachedTicket> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> refreshLocks = new ConcurrentHashMap<>();

    @Autowired
    public AfipWsaaClient(ArcaProperties properties, Clock clock) {
        this(properties, clock, new DefaultResourceLoader(), HttpClient.newBuilder().build());
    }

    public AfipWsaaClient(ArcaProperties properties, Clock clock, ResourceLoader resourceLoader, HttpClient httpClient) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.resourceLoader = Objects.requireNonNullElseGet(resourceLoader, DefaultResourceLoader::new);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        asegurarHomologacion();
    }

    public WsaaTicket obtenerTicketAcceso() {
        return obtenerTicketAcceso(properties.getWsaa().getService());
    }

    public WsaaTicket obtenerTicketAcceso(String service) {
        asegurarHomologacion();
        String normalizedService = requireText(service, "El servicio WSAA es obligatorio");
        String cacheKey = cacheKey(normalizedService);
        Instant now = clock.instant();
        Duration margin = safeDuration(properties.getWsaa().getRenewalMargin(), Duration.ofMinutes(10));

        CachedTicket cached = cache.get(cacheKey);
        if (cached != null && cached.isValid(now, margin)) {
            return cached.ticket();
        }

        Object lock = refreshLocks.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (lock) {
            cached = cache.get(cacheKey);
            Instant refreshedNow = clock.instant();
            if (cached != null && cached.isValid(refreshedNow, margin)) {
                return cached.ticket();
            }
            WsaaTicket ticket = solicitarNuevoTicket(normalizedService);
            cache.put(cacheKey, new CachedTicket(ticket));
            return ticket;
        }
    }

    /** Compatibilidad: devuelve [token, sign]. Preferir {@link #obtenerTicketAcceso()}. */
    public String[] obtenerTokenYSign() {
        WsaaTicket ticket = obtenerTicketAcceso();
        return new String[] { ticket.getToken(), ticket.getSign() };
    }

    public void invalidarCache() {
        cache.clear();
    }

    public WsaaTicket solicitarNuevoTicket(String service) {
        asegurarProveedorBouncyCastle();
        X509Certificate certificate = cargarCertificado(properties.getCredentials().getCertPath());
        validarVigenciaCertificado(certificate);
        PrivateKey privateKey = cargarClavePrivada(properties.getCredentials().getKeyPath());
        validarCorrespondenciaCertificadoClave(certificate, privateKey);

        String tra = generarTra(service);
        byte[] cms = firmarTra(tra, certificate, privateKey);
        String soapRequest = construirSolicitudLoginCms(Base64.getEncoder().encodeToString(cms));
        String soapResponse = enviarSoap(properties.getWsaa().getUrl(), soapRequest,
                properties.getWsaa().getConnectTimeout(), properties.getWsaa().getReadTimeout());
        return parsearRespuestaLoginCms(soapResponse, service, properties.getCuit());
    }

    public String generarTra(String service) {
        Instant now = clock.instant();
        OffsetDateTime generation = OffsetDateTime.ofInstant(now.minusSeconds(300), clock.getZone());
        OffsetDateTime expiration = OffsetDateTime.ofInstant(now.plusSeconds(600), clock.getZone());
        long uniqueId = now.getEpochSecond();

        StringBuilder xml = new StringBuilder(256);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<loginTicketRequest version=\"1.0\">");
        xml.append("<header>");
        xml.append("<uniqueId>").append(uniqueId).append("</uniqueId>");
        xml.append("<generationTime>").append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(generation)).append("</generationTime>");
        xml.append("<expirationTime>").append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(expiration)).append("</expirationTime>");
        xml.append("</header>");
        xml.append("<service>").append(escaparXml(service)).append("</service>");
        xml.append("</loginTicketRequest>");
        return xml.toString();
    }

    public byte[] firmarTra(String traXml, X509Certificate certificate, PrivateKey privateKey) {
        try {
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(privateKey);
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build())
                    .build(signer, certificate));
            generator.addCertificates(new JcaCertStore(Collections.singletonList(certificate)));
            CMSSignedData signedData = generator.generate(
                    new CMSProcessableByteArray(traXml.getBytes(StandardCharsets.UTF_8)), true);
            return signedData.getEncoded();
        } catch (Exception ex) {
            throw new ArcaWsaaException("No se pudo firmar el TRA para WSAA", ex);
        }
    }

    public String construirSolicitudLoginCms(String cmsBase64) {
        StringBuilder xml = new StringBuilder(cmsBase64.length() + 256);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<soapenv:Envelope xmlns:soapenv=\"").append(SOAP_ENV_NS)
                .append("\" xmlns:wsaa=\"").append(WSAA_NS).append("\">");
        xml.append("<soapenv:Header/>");
        xml.append("<soapenv:Body>");
        xml.append("<wsaa:loginCms><wsaa:in0>").append(cmsBase64).append("</wsaa:in0></wsaa:loginCms>");
        xml.append("</soapenv:Body></soapenv:Envelope>");
        return xml.toString();
    }

    String enviarSoap(String url, String soapXml, Duration connectTimeout, Duration readTimeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(safeDuration(readTimeout, Duration.ofSeconds(30)))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"\"")
                    .POST(HttpRequest.BodyPublishers.ofString(soapXml, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body() == null ? "" : response.body();
            if (status < 200 || status >= 300) {
                throw new ArcaCommunicationException(
                        "WSAA respondió con HTTP " + status + ": " + resumirMensaje(body), status, body);
            }
            return body;
        } catch (ArcaCommunicationException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ArcaCommunicationException("Timeout de comunicación con WSAA", ex);
        } catch (IOException ex) {
            throw new ArcaCommunicationException("Error de comunicación con WSAA", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ArcaCommunicationException("Comunicación con WSAA interrumpida", ex);
        } catch (IllegalArgumentException ex) {
            throw new ArcaConfigurationException("URL de WSAA inválida", ex);
        }
    }

    public WsaaTicket parsearRespuestaLoginCms(String soapResponse, String service, String cuit) {
        Document doc = parsearXml(soapResponse, "respuesta WSAA");
        Element fault = primerElementoPorNombreLocal(doc, "Fault");
        if (fault != null) {
            String faultString = textoHijoLocal(fault, "faultstring");
            String faultCode = textoHijoLocal(fault, "faultcode");
            String detail = textoDetalleFault(fault);
            String mensaje = "WSAA Fault"
                    + (StringUtils.hasText(faultCode) ? " [" + faultCode + "]" : "")
                    + (StringUtils.hasText(faultString) ? ": " + faultString : "")
                    + (StringUtils.hasText(detail) ? " - " + detail : "");
            throw new ArcaWsaaException(mensaje);
        }

        Element loginReturn = primerElementoPorNombreLocal(doc, "loginCmsReturn");
        if (loginReturn == null) {
            throw new ArcaWsaaException("La respuesta WSAA no contiene loginCmsReturn");
        }
        String innerXml = loginReturn.getTextContent();
        if (!StringUtils.hasText(innerXml)) {
            throw new ArcaWsaaException("WSAA devolvió loginCmsReturn vacío");
        }
        String normalized = innerXml.trim();
        if (normalized.contains("&lt;") || normalized.contains("&gt;")) {
            normalized = normalized.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
        }

        Document credentialsDoc = parsearXml(normalized, "credenciales WSAA");
        String token = textoHijoLocal(credentialsDoc.getDocumentElement(), "token");
        if (!StringUtils.hasText(token)) {
            token = textoPorNombreLocal(credentialsDoc, "token");
        }
        String sign = textoHijoLocal(credentialsDoc.getDocumentElement(), "sign");
        if (!StringUtils.hasText(sign)) {
            sign = textoPorNombreLocal(credentialsDoc, "sign");
        }
        String expirationTimeText = textoPorNombreLocal(credentialsDoc, "expirationTime");
        if (!StringUtils.hasText(token) || !StringUtils.hasText(sign) || !StringUtils.hasText(expirationTimeText)) {
            throw new ArcaWsaaException("La respuesta WSAA no contiene token/sign/expirationTime válidos");
        }
        Instant expiration = parsearInstant(expirationTimeText.trim());
        String source = textoPorNombreLocal(credentialsDoc, "source");
        String destination = textoPorNombreLocal(credentialsDoc, "destination");
        return new WsaaTicket(token.trim(), sign.trim(), expiration, service, source, destination, cuit);
    }

    public X509Certificate cargarCertificado(String location) {
        try (InputStream is = abrirRecurso(location, "certificado")) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(is);
        } catch (ArcaConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ArcaConfigurationException("No se pudo cargar el certificado X.509 configurado", ex);
        }
    }

    public PrivateKey cargarClavePrivada(String location) {
        asegurarProveedorBouncyCastle();
        try (InputStream is = abrirRecurso(location, "clave privada");
             PEMParser parser = new PEMParser(new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
            Object object = parser.readObject();
            if (object == null) {
                throw new ArcaConfigurationException("La clave privada no tiene un formato PEM soportado");
            }
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (object instanceof PEMKeyPair keyPair) {
                return converter.getKeyPair(keyPair).getPrivate();
            }
            if (object instanceof PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }
            if (object instanceof RSAPrivateKey rsaPrivateKey) {
                PrivateKeyInfo info = new PrivateKeyInfo(
                        new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                                org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.rsaEncryption,
                                org.bouncycastle.asn1.DERNull.INSTANCE),
                        rsaPrivateKey);
                return converter.getPrivateKey(info);
            }
            if (object instanceof PEMEncryptedKeyPair) {
                throw new ArcaConfigurationException("La clave privada PEM está encriptada y no se soporta en esta configuración");
            }
            throw new ArcaConfigurationException("Formato de clave privada no soportado: " + object.getClass().getSimpleName());
        } catch (ArcaConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ArcaConfigurationException("No se pudo cargar la clave privada configurada", ex);
        }
    }

    public void validarVigenciaCertificado(X509Certificate certificate) {
        try {
            certificate.checkValidity(java.util.Date.from(clock.instant()));
        } catch (CertificateExpiredException ex) {
            throw new ArcaConfigurationException("El certificado X.509 se encuentra vencido", ex);
        } catch (CertificateNotYetValidException ex) {
            throw new ArcaConfigurationException("El certificado X.509 aún no es vigente", ex);
        }
    }

    public void validarCorrespondenciaCertificadoClave(X509Certificate certificate, PrivateKey privateKey) {
        try {
            if (!(certificate.getPublicKey() instanceof RSAPublicKey publicKey)
                    || !(privateKey instanceof RSAPrivateCrtKey rsaPrivateKey)) {
                throw new ArcaConfigurationException(
                        "No se pudo verificar la correspondencia entre certificado y clave privada (se requiere par RSA compatible)");
            }
            if (!publicKey.getModulus().equals(rsaPrivateKey.getModulus())
                    || !publicKey.getPublicExponent().equals(rsaPrivateKey.getPublicExponent())) {
                throw new ArcaConfigurationException("La clave privada no corresponde al certificado X.509 configurado");
            }
        } catch (ArcaConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ArcaConfigurationException(
                    "No se pudo validar la correspondencia entre certificado y clave privada", ex);
        }
    }

    InputStream abrirRecurso(String location, String tipo) throws IOException {
        if (!StringUtils.hasText(location)) {
            throw new ArcaConfigurationException("La ruta de " + tipo + " es obligatoria");
        }
        Resource resource = resourceLoader.getResource(location);
        if (resource.exists()) {
            return resource.getInputStream();
        }
        Path path = Path.of(location);
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }
        throw new ArcaConfigurationException("No se encontró el recurso de " + tipo + " configurado");
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
            throw new ArcaWsaaException("No se pudo interpretar XML de " + contexto, ex);
        }
    }

    static Element primerElementoPorNombreLocal(Node root, String localName) {
        if (root == null) {
            return null;
        }
        if (root.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) root;
            if (localName.equals(el.getLocalName()) || localName.equals(el.getNodeName())) {
                return el;
            }
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Element found = primerElementoPorNombreLocal(children.item(i), localName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    static String textoPorNombreLocal(Node root, String localName) {
        Element el = primerElementoPorNombreLocal(root, localName);
        return el == null ? null : el.getTextContent();
    }

    static String textoHijoLocal(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                if (localName.equals(el.getLocalName()) || localName.equals(el.getNodeName())) {
                    return el.getTextContent();
                }
            }
        }
        return null;
    }

    static String textoDetalleFault(Element fault) {
        Element detail = primerElementoPorNombreLocal(fault, "detail");
        if (detail == null) {
            return null;
        }
        String text = detail.getTextContent();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    static Instant parsearInstant(String value) {
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ex2) {
                throw new ArcaWsaaException("Formato de expirationTime inválido en respuesta WSAA", ex2);
            }
        }
    }

    static String escaparXml(String value) {
        if (value == null) {
            return "";
        }
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

    static String resumirMensaje(String body) {
        if (!StringUtils.hasText(body)) {
            return "sin detalle";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > 200 ? compact.substring(0, 200) + "..." : compact;
    }

    private String cacheKey(String service) {
        String cuit = StringUtils.hasText(properties.getCuit()) ? properties.getCuit().trim() : "nocuit";
        String env = properties.getEnvironment() == null ? "env" : properties.getEnvironment().name();
        return env + "|" + cuit + "|" + service;
    }

    private void asegurarHomologacion() {
        if (properties.getEnvironment() != ArcaProperties.Environment.HOMOLOGACION) {
            throw new ArcaConfigurationException(
                    "La integración ARCA solo está habilitada para HOMOLOGACION en esta etapa");
        }
        String wsaaUrl = properties.getWsaa() != null ? properties.getWsaa().getUrl() : null;
        String wsfeUrl = properties.getWsfev1() != null ? properties.getWsfev1().getUrl() : null;
        if (esUrlProduccion(wsaaUrl) || esUrlProduccion(wsfeUrl)) {
            throw new ArcaConfigurationException(
                    "La configuración apunta a endpoints de producción; se requiere homologación");
        }
    }

    private static boolean esUrlProduccion(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("wsaa.afip.gov.ar") || lower.contains("servicios1.afip.gov.ar");
    }

    private static void asegurarProveedorBouncyCastle() {
        synchronized (BC_LOCK) {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
        }
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ArcaConfigurationException(message);
        }
        return value.trim();
    }

    private static Duration safeDuration(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    private record CachedTicket(WsaaTicket ticket) {
        boolean isValid(Instant now, Duration margin) {
            if (ticket == null || ticket.getExpirationTime() == null) {
                return false;
            }
            Instant safeNow = now.plus(margin == null ? Duration.ZERO : margin);
            return safeNow.isBefore(ticket.getExpirationTime());
        }
    }
}

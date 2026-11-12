package com.example.api_tp.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de integración con ARCA (ex AFIP) para el ambiente activo.
 * Los valores predeterminados apuntan a homologación.
 */
@ConfigurationProperties(prefix = "app.arca")
public class ArcaProperties {

    /** Ambiente lógico de la integración. Debe ser HOMOLOGACION en esta etapa. */
    private Environment environment = Environment.HOMOLOGACION;

    /** CUIT del emisor/representado (sin guiones). */
    private String cuit = "";

    /**
     * Condición IVA del emisor según código AFIP (1 = Responsable Inscripto).
     * El sistema solo contempla emisión como Responsable Inscripto.
     */
    private int condicionIvaEmisor;

    private final Wsaa wsaa = new Wsaa();
    private final Wsfev1 wsfev1 = new Wsfev1();
    private final Credentials credentials = new Credentials();

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public String getCuit() {
        return cuit;
    }

    public void setCuit(String cuit) {
        this.cuit = cuit;
    }

    public int getCondicionIvaEmisor() {
        return condicionIvaEmisor;
    }

    public void setCondicionIvaEmisor(int condicionIvaEmisor) {
        this.condicionIvaEmisor = condicionIvaEmisor;
    }

    public Wsaa getWsaa() {
        return wsaa;
    }

    public Wsfev1 getWsfev1() {
        return wsfev1;
    }

    public Credentials getCredentials() {
        return credentials;
    }

    public enum Environment {
        HOMOLOGACION,
        PRODUCCION
    }

    public static class Wsaa {
        /** Endpoint SOAP LoginCms de WSAA. Por defecto homologación. */
        private String url = "https://wsaahomo.afip.gov.ar/ws/services/LoginCms";
        /** Servicio solicitado en el TRA. */
        private String service = "wsfe";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        /** Margen de renovación antes del vencimiento del TA. */
        private Duration renewalMargin = Duration.ofMinutes(10);

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Duration getRenewalMargin() {
            return renewalMargin;
        }

        public void setRenewalMargin(Duration renewalMargin) {
            this.renewalMargin = renewalMargin;
        }
    }

    public static class Wsfev1 {
        /** Endpoint SOAP de WSFEv1. Por defecto homologación. */
        private String url = "https://wswhomo.afip.gov.ar/wsfev1/service.asmx";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Credentials {
        /** Ruta o ubicación del certificado X.509 (classpath:, file:, ruta absoluta). */
        private String certPath = "classpath:certs/FacturaTestUtn.crt";
        /** Ruta o ubicación de la clave privada (classpath:, file:, ruta absoluta). */
        private String keyPath = "classpath:certs/privada_test.key";

        public String getCertPath() {
            return certPath;
        }

        public void setCertPath(String certPath) {
            this.certPath = certPath;
        }

        public String getKeyPath() {
            return keyPath;
        }

        public void setKeyPath(String keyPath) {
            this.keyPath = keyPath;
        }
    }
}

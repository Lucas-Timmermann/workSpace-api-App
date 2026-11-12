package com.example.api_tp.client.arca.wsaa;

import java.time.Instant;
import java.util.Objects;


public final class WsaaTicket {
    private final String token;
    private final String sign;
    private final Instant expirationTime;
    private final String service;
    private final String source;
    private final String destination;
    private final String cuit;

    public WsaaTicket(String token, String sign, Instant expirationTime, String service,
            String source, String destination, String cuit) {
        this.token = Objects.requireNonNull(token, "token");
        this.sign = Objects.requireNonNull(sign, "sign");
        this.expirationTime = Objects.requireNonNull(expirationTime, "expirationTime");
        this.service = service;
        this.source = source;
        this.destination = destination;
        this.cuit = cuit;
    }

    public String getToken() {
        return token;
    }

    public String getSign() {
        return sign;
    }

    public Instant getExpirationTime() {
        return expirationTime;
    }

    public String getService() {
        return service;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getCuit() {
        return cuit;
    }
}

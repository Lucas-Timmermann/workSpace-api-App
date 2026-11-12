package com.example.api_tp.client.arca.wsfe;

import com.example.api_tp.client.arca.wsaa.WsaaTicket;


public class WsfeAuth {
    private final String token;
    private final String sign;
    private final long cuit;

    public WsfeAuth(String token, String sign, long cuit) {
        this.token = token;
        this.sign = sign;
        this.cuit = cuit;
    }

    public static WsfeAuth from(WsaaTicket ticket, String cuitString) {
        if (cuitString == null || cuitString.isBlank()) {
            throw new IllegalArgumentException("El CUIT es obligatorio para WSFEv1");
        }
        String digits = cuitString.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            throw new IllegalArgumentException("El CUIT configurado no es válido");
        }
        return new WsfeAuth(ticket.getToken(), ticket.getSign(), Long.parseLong(digits));
    }

    public String getToken() {
        return token;
    }

    public String getSign() {
        return sign;
    }

    public long getCuit() {
        return cuit;
    }
}

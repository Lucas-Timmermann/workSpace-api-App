package com.example.api_tp.client.arca.wsfe;


public class PuntoVentaArca {
    private final int numero;
    private final String emisionTipo;
    private final boolean bloqueado;

    public PuntoVentaArca(int numero, String emisionTipo, boolean bloqueado) {
        this.numero = numero;
        this.emisionTipo = emisionTipo;
        this.bloqueado = bloqueado;
    }

    public int getNumero() {
        return numero;
    }

    public String getEmisionTipo() {
        return emisionTipo;
    }

    public boolean isBloqueado() {
        return bloqueado;
    }
}

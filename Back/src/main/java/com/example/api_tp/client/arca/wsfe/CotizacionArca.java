package com.example.api_tp.client.arca.wsfe;

import java.math.BigDecimal;

/** Cotización devuelta por FEParamGetCotizacion. */
public class CotizacionArca {
    private final String monId;
    private final BigDecimal monCotiz;
    private final String fchCotiz;

    public CotizacionArca(String monId, BigDecimal monCotiz, String fchCotiz) {
        this.monId = monId;
        this.monCotiz = monCotiz;
        this.fchCotiz = fchCotiz;
    }

    public String getMonId() {
        return monId;
    }

    public BigDecimal getMonCotiz() {
        return monCotiz;
    }

    public String getFchCotiz() {
        return fchCotiz;
    }
}

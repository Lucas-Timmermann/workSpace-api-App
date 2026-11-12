package com.example.api_tp.client.arca.wsfe;


import java.util.List;

public class UltimoAutorizadoResult {
    private final int puntoVenta;
    private final int tipoComprobante;
    private final int cbteNro;
    private final List<WsfeMessage> errors;
    private final List<WsfeMessage> events;

    public UltimoAutorizadoResult(int puntoVenta, int tipoComprobante, int cbteNro,
            List<WsfeMessage> errors, List<WsfeMessage> events) {
        this.puntoVenta = puntoVenta;
        this.tipoComprobante = tipoComprobante;
        this.cbteNro = cbteNro;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.events = events == null ? List.of() : List.copyOf(events);
    }

    public int getPuntoVenta() {
        return puntoVenta;
    }

    public int getTipoComprobante() {
        return tipoComprobante;
    }

    public int getCbteNro() {
        return cbteNro;
    }

    public int getProximoCbteNro() {
        return cbteNro + 1;
    }

    public List<WsfeMessage> getErrors() {
        return errors;
    }

    public List<WsfeMessage> getEvents() {
        return events;
    }

    public boolean tieneErrores() {
        return !errors.isEmpty();
    }
}

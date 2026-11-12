package com.example.api_tp.client.arca.wsfe;


import java.util.ArrayList;
import java.util.List;

/** Solicitud de autorización FECAESolicitar (cabecera + comprobantes). */
public class FeCaeSolicitud {
    private int puntoVenta;
    private int tipoComprobante;
    private final List<FeCaeDetalle> comprobantes = new ArrayList<>();

    public int getPuntoVenta() {
        return puntoVenta;
    }

    public void setPuntoVenta(int puntoVenta) {
        this.puntoVenta = puntoVenta;
    }

    public int getTipoComprobante() {
        return tipoComprobante;
    }

    public void setTipoComprobante(int tipoComprobante) {
        this.tipoComprobante = tipoComprobante;
    }

    public List<FeCaeDetalle> getComprobantes() {
        return comprobantes;
    }

    public void addComprobante(FeCaeDetalle detalle) {
        if (detalle != null) {
            this.comprobantes.add(detalle);
        }
    }
}

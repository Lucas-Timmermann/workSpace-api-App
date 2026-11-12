package com.example.api_tp.service.arca;

import java.util.ArrayList;
import java.util.List;

import com.example.api_tp.client.arca.wsfe.WsfeMessage;


public class ArcaEmisionResultado {
    public enum Estado {
        APROBADA,
        RECHAZADA,
        INCIERTO
    }

    private Estado estado;
    private String cae;
    private String caeFechaVencimiento; // yyyyMMdd según WSFEv1
    private Long cbteDesde;
    private Long cbteHasta;
    private Integer puntoVenta;
    private Integer tipoComprobante;
    private String resultadoGlobal;
    private String resultadoDetalle;
    private final List<WsfeMessage> errores = new ArrayList<>();
    private final List<WsfeMessage> observaciones = new ArrayList<>();
    private final List<WsfeMessage> eventos = new ArrayList<>();
    private String mensaje;

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public String getCae() { return cae; }
    public void setCae(String cae) { this.cae = cae; }
    public String getCaeFechaVencimiento() { return caeFechaVencimiento; }
    public void setCaeFechaVencimiento(String caeFechaVencimiento) { this.caeFechaVencimiento = caeFechaVencimiento; }
    public Long getCbteDesde() { return cbteDesde; }
    public void setCbteDesde(Long cbteDesde) { this.cbteDesde = cbteDesde; }
    public Long getCbteHasta() { return cbteHasta; }
    public void setCbteHasta(Long cbteHasta) { this.cbteHasta = cbteHasta; }
    public Integer getPuntoVenta() { return puntoVenta; }
    public void setPuntoVenta(Integer puntoVenta) { this.puntoVenta = puntoVenta; }
    public Integer getTipoComprobante() { return tipoComprobante; }
    public void setTipoComprobante(Integer tipoComprobante) { this.tipoComprobante = tipoComprobante; }
    public String getResultadoGlobal() { return resultadoGlobal; }
    public void setResultadoGlobal(String resultadoGlobal) { this.resultadoGlobal = resultadoGlobal; }
    public String getResultadoDetalle() { return resultadoDetalle; }
    public void setResultadoDetalle(String resultadoDetalle) { this.resultadoDetalle = resultadoDetalle; }
    public List<WsfeMessage> getErrores() { return errores; }
    public List<WsfeMessage> getObservaciones() { return observaciones; }
    public List<WsfeMessage> getEventos() { return eventos; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
}

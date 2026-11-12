package com.example.api_tp.client.arca.wsfe;

import java.util.ArrayList;
import java.util.List;

public class CompConsultarResultado {
    private Integer concepto;
    private Integer docTipo;
    private Long docNro;
    private Long cbteDesde;
    private Long cbteHasta;
    private String cbteFch;
    private String impTotal;
    private String impNeto;
    private String impIva;
    private String resultado;
    private String codAutorizacion;
    private String fchVto;
    private String emisionTipo;
    private String monId;
    private String monCotiz;
    private final List<WsfeMessage> errors = new ArrayList<>();
    private final List<WsfeMessage> events = new ArrayList<>();
    private final List<WsfeMessage> observaciones = new ArrayList<>();

    public Integer getConcepto() { return concepto; }
    public void setConcepto(Integer concepto) { this.concepto = concepto; }
    public Integer getDocTipo() { return docTipo; }
    public void setDocTipo(Integer docTipo) { this.docTipo = docTipo; }
    public Long getDocNro() { return docNro; }
    public void setDocNro(Long docNro) { this.docNro = docNro; }
    public Long getCbteDesde() { return cbteDesde; }
    public void setCbteDesde(Long cbteDesde) { this.cbteDesde = cbteDesde; }
    public Long getCbteHasta() { return cbteHasta; }
    public void setCbteHasta(Long cbteHasta) { this.cbteHasta = cbteHasta; }
    public String getCbteFch() { return cbteFch; }
    public void setCbteFch(String cbteFch) { this.cbteFch = cbteFch; }
    public String getImpTotal() { return impTotal; }
    public void setImpTotal(String impTotal) { this.impTotal = impTotal; }
    public String getImpNeto() { return impNeto; }
    public void setImpNeto(String impNeto) { this.impNeto = impNeto; }
    public String getImpIva() { return impIva; }
    public void setImpIva(String impIva) { this.impIva = impIva; }
    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }
    public String getCodAutorizacion() { return codAutorizacion; }
    public void setCodAutorizacion(String codAutorizacion) { this.codAutorizacion = codAutorizacion; }
    public String getFchVto() { return fchVto; }
    public void setFchVto(String fchVto) { this.fchVto = fchVto; }
    public String getEmisionTipo() { return emisionTipo; }
    public void setEmisionTipo(String emisionTipo) { this.emisionTipo = emisionTipo; }
    public String getMonId() { return monId; }
    public void setMonId(String monId) { this.monId = monId; }
    public String getMonCotiz() { return monCotiz; }
    public void setMonCotiz(String monCotiz) { this.monCotiz = monCotiz; }
    public List<WsfeMessage> getErrors() { return errors; }
    public List<WsfeMessage> getEvents() { return events; }
    public List<WsfeMessage> getObservaciones() { return observaciones; }
}

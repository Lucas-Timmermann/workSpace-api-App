package com.example.api_tp.service.arca;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class ArcaComprobanteDatos {
    private int puntoVenta;
    private int tipoComprobante;
    private int concepto; // 1 productos, 2 servicios, 3 ambos
    private int docTipo;
    private long docNro;
    private long cbteDesde;
    private long cbteHasta;
    private String cbteFch; // yyyyMMdd
    private BigDecimal impTotal;
    private BigDecimal impTotConc = BigDecimal.ZERO;
    private BigDecimal impNeto;
    private BigDecimal impOpEx = BigDecimal.ZERO;
    private BigDecimal impIva;
    private BigDecimal impTrib = BigDecimal.ZERO;
    private String monId = "PES";
    private BigDecimal monCotiz = BigDecimal.ONE;
    private Integer condicionIvaReceptorId;
    private String fchServDesde;
    private String fchServHasta;
    private String fchVtoPago;
    private final List<AlicuotaIva> iva = new ArrayList<>();

    public int getPuntoVenta() { return puntoVenta; }
    public void setPuntoVenta(int puntoVenta) { this.puntoVenta = puntoVenta; }
    public int getTipoComprobante() { return tipoComprobante; }
    public void setTipoComprobante(int tipoComprobante) { this.tipoComprobante = tipoComprobante; }
    public int getConcepto() { return concepto; }
    public void setConcepto(int concepto) { this.concepto = concepto; }
    public int getDocTipo() { return docTipo; }
    public void setDocTipo(int docTipo) { this.docTipo = docTipo; }
    public long getDocNro() { return docNro; }
    public void setDocNro(long docNro) { this.docNro = docNro; }
    public long getCbteDesde() { return cbteDesde; }
    public void setCbteDesde(long cbteDesde) { this.cbteDesde = cbteDesde; }
    public long getCbteHasta() { return cbteHasta; }
    public void setCbteHasta(long cbteHasta) { this.cbteHasta = cbteHasta; }
    public String getCbteFch() { return cbteFch; }
    public void setCbteFch(String cbteFch) { this.cbteFch = cbteFch; }
    public BigDecimal getImpTotal() { return impTotal; }
    public void setImpTotal(BigDecimal impTotal) { this.impTotal = impTotal; }
    public BigDecimal getImpTotConc() { return impTotConc; }
    public void setImpTotConc(BigDecimal impTotConc) { this.impTotConc = impTotConc; }
    public BigDecimal getImpNeto() { return impNeto; }
    public void setImpNeto(BigDecimal impNeto) { this.impNeto = impNeto; }
    public BigDecimal getImpOpEx() { return impOpEx; }
    public void setImpOpEx(BigDecimal impOpEx) { this.impOpEx = impOpEx; }
    public BigDecimal getImpIva() { return impIva; }
    public void setImpIva(BigDecimal impIva) { this.impIva = impIva; }
    public BigDecimal getImpTrib() { return impTrib; }
    public void setImpTrib(BigDecimal impTrib) { this.impTrib = impTrib; }
    public String getMonId() { return monId; }
    public void setMonId(String monId) { this.monId = monId; }
    public BigDecimal getMonCotiz() { return monCotiz; }
    public void setMonCotiz(BigDecimal monCotiz) { this.monCotiz = monCotiz; }
    public Integer getCondicionIvaReceptorId() { return condicionIvaReceptorId; }
    public void setCondicionIvaReceptorId(Integer condicionIvaReceptorId) { this.condicionIvaReceptorId = condicionIvaReceptorId; }
    public String getFchServDesde() { return fchServDesde; }
    public void setFchServDesde(String fchServDesde) { this.fchServDesde = fchServDesde; }
    public String getFchServHasta() { return fchServHasta; }
    public void setFchServHasta(String fchServHasta) { this.fchServHasta = fchServHasta; }
    public String getFchVtoPago() { return fchVtoPago; }
    public void setFchVtoPago(String fchVtoPago) { this.fchVtoPago = fchVtoPago; }
    public List<AlicuotaIva> getIva() { return iva; }

    public static class AlicuotaIva {
        private int id;
        private BigDecimal baseImp;
        private BigDecimal importe;

        public AlicuotaIva() {}
        public AlicuotaIva(int id, BigDecimal baseImp, BigDecimal importe) {
            this.id = id; this.baseImp = baseImp; this.importe = importe;
        }
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public BigDecimal getBaseImp() { return baseImp; }
        public void setBaseImp(BigDecimal baseImp) { this.baseImp = baseImp; }
        public BigDecimal getImporte() { return importe; }
        public void setImporte(BigDecimal importe) { this.importe = importe; }
    }
}

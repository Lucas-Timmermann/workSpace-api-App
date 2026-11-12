package com.example.api_tp.client.arca.wsfe;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class FeCaeDetalle {
    private Integer concepto;
    private Integer docTipo;
    private Long docNro;
    private Long cbteDesde;
    private Long cbteHasta;
    private String cbteFch; // yyyyMMdd
    private BigDecimal impTotal;
    private BigDecimal impTotConc;
    private BigDecimal impNeto;
    private BigDecimal impOpEx;
    private BigDecimal impIva;
    private BigDecimal impTrib;
    private String fchServDesde;
    private String fchServHasta;
    private String fchVtoPago;
    private String monId;
    private BigDecimal monCotiz;
    private Integer condicionIvaReceptorId;
    private final List<AlicuotaIva> iva = new ArrayList<>();
    private final List<Tributo> tributos = new ArrayList<>();

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
    public String getFchServDesde() { return fchServDesde; }
    public void setFchServDesde(String fchServDesde) { this.fchServDesde = fchServDesde; }
    public String getFchServHasta() { return fchServHasta; }
    public void setFchServHasta(String fchServHasta) { this.fchServHasta = fchServHasta; }
    public String getFchVtoPago() { return fchVtoPago; }
    public void setFchVtoPago(String fchVtoPago) { this.fchVtoPago = fchVtoPago; }
    public String getMonId() { return monId; }
    public void setMonId(String monId) { this.monId = monId; }
    public BigDecimal getMonCotiz() { return monCotiz; }
    public void setMonCotiz(BigDecimal monCotiz) { this.monCotiz = monCotiz; }
    public Integer getCondicionIvaReceptorId() { return condicionIvaReceptorId; }
    public void setCondicionIvaReceptorId(Integer condicionIvaReceptorId) { this.condicionIvaReceptorId = condicionIvaReceptorId; }
    public List<AlicuotaIva> getIva() { return iva; }
    public List<Tributo> getTributos() { return tributos; }

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

    public static class Tributo {
        private int id;
        private String desc;
        private BigDecimal baseImp;
        private BigDecimal alic;
        private BigDecimal importe;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getDesc() { return desc; }
        public void setDesc(String desc) { this.desc = desc; }
        public BigDecimal getBaseImp() { return baseImp; }
        public void setBaseImp(BigDecimal baseImp) { this.baseImp = baseImp; }
        public BigDecimal getAlic() { return alic; }
        public void setAlic(BigDecimal alic) { this.alic = alic; }
        public BigDecimal getImporte() { return importe; }
        public void setImporte(BigDecimal importe) { this.importe = importe; }
    }
}

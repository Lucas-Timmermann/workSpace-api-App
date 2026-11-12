package com.example.api_tp.client.arca.wsfe;

import java.util.ArrayList;
import java.util.List;

public class FeCaeResultado {
    private String resultadoGlobal;
    private final List<FeCaeDetalleResultado> detalles = new ArrayList<>();
    private final List<WsfeMessage> errors = new ArrayList<>();
    private final List<WsfeMessage> events = new ArrayList<>();

    public String getResultadoGlobal() { return resultadoGlobal; }
    public void setResultadoGlobal(String resultadoGlobal) { this.resultadoGlobal = resultadoGlobal; }
    public List<FeCaeDetalleResultado> getDetalles() { return detalles; }
    public List<WsfeMessage> getErrors() { return errors; }
    public List<WsfeMessage> getEvents() { return events; }

    public boolean esAprobadoGlobal() { return "A".equalsIgnoreCase(resultadoGlobal); }
    public boolean esRechazadoGlobal() { return "R".equalsIgnoreCase(resultadoGlobal); }

    public static class FeCaeDetalleResultado {
        private Integer concepto;
        private Integer docTipo;
        private Long docNro;
        private Long cbteDesde;
        private Long cbteHasta;
        private String cbteFch;
        private String resultado; // A/R
        private String cae;
        private String caeFchVto;
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
        public String getResultado() { return resultado; }
        public void setResultado(String resultado) { this.resultado = resultado; }
        public String getCae() { return cae; }
        public void setCae(String cae) { this.cae = cae; }
        public String getCaeFchVto() { return caeFchVto; }
        public void setCaeFchVto(String caeFchVto) { this.caeFchVto = caeFchVto; }
        public List<WsfeMessage> getObservaciones() { return observaciones; }
        public boolean aprobado() { return "A".equalsIgnoreCase(resultado); }
    }
}

package com.example.api_tp.client.arca.wsfe;


public class ParametroArca {
    private final String id;
    private final String descripcion;

    public ParametroArca(String id, String descripcion) {
        this.id = id;
        this.descripcion = descripcion;
    }

    public String getId() {
        return id;
    }

    public String getDescripcion() {
        return descripcion;
    }
}

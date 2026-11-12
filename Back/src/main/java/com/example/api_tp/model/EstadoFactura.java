package com.example.api_tp.model;

public enum EstadoFactura {
    PENDIENTE,
    EN_PROCESO_ENVIO,
    AUTORIZADA,
    RECHAZADA,
    RESULTADO_DESCONOCIDO,
    ANULADA_LOCAL;

    public boolean esEditable() {
        return this == PENDIENTE || this == RECHAZADA;
    }

    public boolean esEnviable() {
        return this == PENDIENTE;
    }
}

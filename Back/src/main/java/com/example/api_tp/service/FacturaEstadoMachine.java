package com.example.api_tp.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.api_tp.model.EstadoFactura;

@Component
public class FacturaEstadoMachine {

    private static final Map<EstadoFactura, Set<EstadoFactura>> TRANSICIONES = new EnumMap<>(EstadoFactura.class);

    static {
        TRANSICIONES.put(EstadoFactura.PENDIENTE,
                EnumSet.of(EstadoFactura.EN_PROCESO_ENVIO, EstadoFactura.ANULADA_LOCAL));
        TRANSICIONES.put(EstadoFactura.EN_PROCESO_ENVIO,
                EnumSet.of(EstadoFactura.AUTORIZADA, EstadoFactura.RECHAZADA, EstadoFactura.RESULTADO_DESCONOCIDO));
        TRANSICIONES.put(EstadoFactura.RESULTADO_DESCONOCIDO,
                EnumSet.of(EstadoFactura.AUTORIZADA, EstadoFactura.RECHAZADA));
        TRANSICIONES.put(EstadoFactura.RECHAZADA,
                EnumSet.of(EstadoFactura.PENDIENTE));
        TRANSICIONES.put(EstadoFactura.AUTORIZADA, EnumSet.noneOf(EstadoFactura.class));
        TRANSICIONES.put(EstadoFactura.ANULADA_LOCAL, EnumSet.noneOf(EstadoFactura.class));
    }

    public void validarTransicion(EstadoFactura origen, EstadoFactura destino) {
        if (origen == null || destino == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de factura inválido");
        }
        if (origen == destino) {
            return;
        }
        Set<EstadoFactura> permitidos = TRANSICIONES.getOrDefault(origen, Set.of());
        if (!permitidos.contains(destino)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transición de estado no permitida: " + origen + " -> " + destino);
        }
    }

    public EstadoFactura parsear(String estado) {
        if (estado == null || estado.isBlank()) {
            return EstadoFactura.PENDIENTE;
        }
        try {
            return EstadoFactura.valueOf(estado.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            // compatibilidad con estados legacy
            if ("ANULADA".equalsIgnoreCase(estado.trim())) {
                return EstadoFactura.ANULADA_LOCAL;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de factura desconocido: " + estado);
        }
    }
}

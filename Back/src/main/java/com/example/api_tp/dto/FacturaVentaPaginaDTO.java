package com.example.api_tp.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacturaVentaPaginaDTO {
    private List<FacturaVentaDTO> contenido;
    private int pagina;
    private int tamanoPagina;
    private long totalElementos;
    private int totalPaginas;
}
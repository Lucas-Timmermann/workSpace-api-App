package com.example.api_tp.dto;

import java.util.List;

import lombok.Data;

@Data
public class EnvioArcaRequestDTO {
   
    private Boolean enviarTodas;
    private List<Long> ids;
}

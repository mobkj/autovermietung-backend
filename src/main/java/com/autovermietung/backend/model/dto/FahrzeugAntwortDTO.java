package com.autovermietung.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class FahrzeugAntwortDTO {

    private Long id;

    private String marke;
    private String modell;
    private String serie;

    private Integer baujahr;
    private Integer ps;
    private String getriebe;
    private String kraftstoff;

    private Integer sitze;
    private Integer tueren;
    private String farbe;

    private BigDecimal nettoPreisProTag;
    private Integer freiKmProTag;
    private BigDecimal kaution;

    private String bildUrl;
    private String status;
}


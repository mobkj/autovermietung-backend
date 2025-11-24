package com.autovermietung.backend.model.dto;

import com.autovermietung.backend.model.FahrzeugStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FahrzeugUpdateDTO {
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

    private FahrzeugStatus status;
}

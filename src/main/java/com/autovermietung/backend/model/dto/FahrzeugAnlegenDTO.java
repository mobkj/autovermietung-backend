package com.autovermietung.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FahrzeugAnlegenDTO {

    @NotBlank
    private String marke;
    @NotBlank private String modell;
    private String serie;

    private Integer baujahr;
    private Integer ps;
    private String getriebe;
    private String kraftstoff;

    private Integer sitze;
    private Integer tueren;
    private String farbe;

    @NotNull
    @Positive
    private BigDecimal nettoPreisProTag;

    @NotNull @PositiveOrZero
    private Integer freiKmProTag;

    private BigDecimal kaution;
}

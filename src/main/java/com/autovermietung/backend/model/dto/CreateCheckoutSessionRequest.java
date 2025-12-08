package com.autovermietung.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@AllArgsConstructor
public class CreateCheckoutSessionRequest {
    private Long buchungId;
    private Integer freieKmPaket;
    private Boolean bringService;
    private Boolean agbAccepted;
}


package com.autovermietung.backend.model.dto;

import lombok.Data;

@Data
public class CreateCheckoutSessionRequest {
    private Long buchungId;
    private Integer freieKmPaket;
    private Boolean bringService;
}


package com.autovermietung.backend.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BuchungAnlegenDTO {

    @NotNull
    private Long fahrzeugId;

    // optional – kannst du später auch aus dem Security-Kontext holen
    private Long userId;

    @NotBlank
    private String kundeName;

    @Email
    @NotBlank
    private String kundeEmail;

    private String kundePhone;

    @NotNull
    private LocalDateTime startDatum;

    @NotNull
    private LocalDateTime endDatum;

    // Bringservice (Fahrzeug liefern/abholen)
    private boolean bringService;
}

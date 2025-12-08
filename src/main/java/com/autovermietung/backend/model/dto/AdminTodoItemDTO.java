package com.autovermietung.backend.model.dto;

import java.time.LocalDateTime;

public record AdminTodoItemDTO(
        Long buchungId,
        String buchungsNummer,
        String fahrzeugName,
        String kundeName,
        String kundeEmail,   // 👈 neu
        String kundePhone,   // 👈 neu
        boolean bringService,
        String ort,
        LocalDateTime startDatum,
        long tageBis
) {}

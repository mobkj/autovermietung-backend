package com.autovermietung.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class BuchungAntwortDTO {

    private Long id;
    private String buchungsNummer;

    private Long fahrzeugId;
    private Long userId;

    private String kundeName;
    private String kundeEmail;
    private String kundePhone;

    private LocalDateTime startDatum;
    private LocalDateTime endDatum;

    private boolean bringService;

    // z.B. "RESERVIERT", "BEZAHLT", "STORNIERT"
    private String status;

    // optional – später beim Checkout/Payment
    private BigDecimal gesamtPreis;

    private LocalDateTime createdAt;

    // bis wann der Slot reserviert ist (für 5-Minuten-Hold)
    private LocalDateTime reserviertBis;
}

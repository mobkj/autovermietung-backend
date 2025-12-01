package com.autovermietung.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuchungPreisAntwortDTO {

    // Fürs Frontend zur Zuordnung
    private Long buchungId;
    private Long fahrzeugId;

    // Basis-Infos
    private int tage;               // Anzahl Miettage
    private Integer freieKmPaket;   // z.B. 150 / 300 / 500

    // ================
    // Netto-Beträge
    // ================
    private BigDecimal mietpreisNetto;          // tage * nettoPreisProTag
    private BigDecimal kmPaketAufpreisNetto;    // Aufpreis für gewähltes Km-Paket
    private BigDecimal bringServiceNetto;       // Bringservice (Netto)
    private BigDecimal gesamtNetto;             // Summe aller Netto-Bereiche

    // ================
    // Brutto-Beträge
    // ================
    private BigDecimal mietpreisBrutto;
    private BigDecimal kmPaketAufpreisBrutto;
    private BigDecimal bringServiceBrutto;
    private BigDecimal gesamtBrutto;

    // MwSt-Infos (optional, aber meist nice für Anzeige)
    private BigDecimal mwstBetrag;   // gesamtBrutto - gesamtNetto
    private BigDecimal mwstSatz;     // z.B. 0.19 (= 19%)
}



package com.autovermietung.backend.service;

import com.autovermietung.backend.model.Buchung;
import com.autovermietung.backend.model.Fahrzeug;
import com.autovermietung.backend.model.dto.BuchungPreisAntwortDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PreisBerechnungService {

    // Mehrwertsteuer: 19%
    private static final BigDecimal MWST_SATZ = new BigDecimal("0.19");

    // Aufpreise für das komplette Km-Paket (NICHT pro Tag!)
    // TODO: Werte später vom Kunden/DB/Config holen
    private Map<Integer, BigDecimal> kmPaketAufpreis = Map.of(
            150, BigDecimal.ZERO,
            300, new BigDecimal("50.00"),
            500, new BigDecimal("90.00")
    );

    /**
     * Haupt-Methode zur Preisberechnung:
     * - ermittelt Miettage
     * - berechnet Netto-Beträge
     * - rechnet Brutto (inkl. MwSt) aus
     */
    public BuchungPreisAntwortDTO berechnePreis(Buchung buchung, int freieKmPaket) {
        boolean bringService = buchung.isBringService();
        return berechnePreis(buchung, freieKmPaket, bringService);
    }

    // 🟢 Deine 3-Parameter Kern-Methode
    public BuchungPreisAntwortDTO berechnePreis(Buchung buchung, int freieKmPaket, boolean bringService) {
        Fahrzeug fahrzeug = buchung.getFahrzeug();

        int tage = berechneMietTage(buchung.getStartDatum(), buchung.getEndDatum());

        BigDecimal mietpreisNetto = fahrzeug.getNettoPreisProTag()
                .multiply(BigDecimal.valueOf(tage));

        BigDecimal kmAufpreisNetto = berechneKmPaketAufpreis(freieKmPaket);

        BigDecimal bringServiceNetto = bringService
                ? berechneBringServiceNetto(buchung)
                : BigDecimal.ZERO;

        BigDecimal gesamtNetto = mietpreisNetto
                .add(kmAufpreisNetto)
                .add(bringServiceNetto);

        BigDecimal mwstBetrag = gesamtNetto
                .multiply(MWST_SATZ)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal gesamtBrutto = gesamtNetto.add(mwstBetrag);

        BigDecimal mietpreisBrutto = berechneBrutto(mietpreisNetto);
        BigDecimal kmAufpreisBrutto = berechneBrutto(kmAufpreisNetto);
        BigDecimal bringServiceBrutto = berechneBrutto(bringServiceNetto);

        return BuchungPreisAntwortDTO.builder()
                .buchungId(buchung.getId())
                .fahrzeugId(fahrzeug.getId())
                .tage(tage)
                .freieKmPaket(freieKmPaket)
                .mietpreisNetto(mietpreisNetto)
                .kmPaketAufpreisNetto(kmAufpreisNetto)
                .bringServiceNetto(bringServiceNetto)
                .gesamtNetto(gesamtNetto)
                .mietpreisBrutto(mietpreisBrutto)
                .kmPaketAufpreisBrutto(kmAufpreisBrutto)
                .bringServiceBrutto(bringServiceBrutto)
                .gesamtBrutto(gesamtBrutto)
                .mwstBetrag(mwstBetrag)
                .mwstSatz(MWST_SATZ)
                .build();
    }

    private int berechneMietTage(LocalDateTime start, LocalDateTime ende) {
        long stunden = Duration.between(start, ende).toHours();
        if (stunden <= 0) {
            return 1;
        }
        long tage = (stunden + 23) / 24;
        return (int) tage;
    }

    private BigDecimal berechneKmPaketAufpreis(int freieKmPaket) {
        return kmPaketAufpreis.getOrDefault(freieKmPaket, BigDecimal.ZERO);
    }

    private BigDecimal berechneBringServiceNetto(Buchung buchung) {
        boolean weitWeg = false;
        return weitWeg ? new BigDecimal("500.00") : new BigDecimal("100.00");
    }

    private BigDecimal berechneBrutto(BigDecimal netto) {
        if (netto == null) {
            return BigDecimal.ZERO;
        }
        return netto
                .multiply(BigDecimal.ONE.add(MWST_SATZ))
                .setScale(2, RoundingMode.HALF_UP);
    }
}

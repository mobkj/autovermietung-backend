package com.autovermietung.backend.service;

import com.autovermietung.backend.model.*;
import com.autovermietung.backend.model.dto.BuchungAnlegenDTO;
import com.autovermietung.backend.model.dto.BuchungAntwortDTO;
import com.autovermietung.backend.repository.BuchungRepository;
import com.autovermietung.backend.repository.FahrzeugRepository;
import com.autovermietung.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BuchungService {

    private final BuchungRepository buchungRepo;
    private final FahrzeugRepository fahrzeugRepo;
    private final UserRepository userRepo;

    // =========================
    // BUCHUNG ANLEGEN
    // =========================
    public BuchungAntwortDTO anlegen(BuchungAnlegenDTO dto) {
        if (dto.getStartDatum() == null || dto.getEndDatum() == null) {
            throw new IllegalArgumentException("Start- und Enddatum sind erforderlich.");
        }

        if (dto.getStartDatum().isAfter(dto.getEndDatum())) {
            throw new IllegalArgumentException("Startdatum muss vor Enddatum liegen.");
        }

        Fahrzeug fahrzeug = fahrzeugRepo.findById(dto.getFahrzeugId())
                .orElseThrow(() -> new RuntimeException("Fahrzeug nicht gefunden"));

        User user = null;
        if (dto.getUserId() != null) {
            user = userRepo.findById(dto.getUserId())
                    .orElseThrow(() -> new RuntimeException("User nicht gefunden"));
        }

        LocalDateTime now = LocalDateTime.now();

        // =========================
        // 🔥 Doppelbuchung verhindern
        // =========================
        var overlaps = buchungRepo
                .findAllByFahrzeug_IdAndStartDatumLessThanEqualAndEndDatumGreaterThanEqual(
                        dto.getFahrzeugId(),
                        dto.getEndDatum(),
                        dto.getStartDatum()
                );

        boolean conflict = overlaps.stream().anyMatch(b -> {
            BuchungsStatus status = b.getStatus();

            // Stornierte blockieren nicht
            if (status == BuchungsStatus.STORNIERT) return false;

            // Bezahlte Buchungen blockieren immer
            if (status == BuchungsStatus.BEZAHLT) return true;

            // Reserviert blockiert, solange reserviertBis noch aktiv ist
            if (status == BuchungsStatus.RESERVIERT) {
                LocalDateTime reserviertBis = b.getReserviertBis();
                return reserviertBis == null || reserviertBis.isAfter(now);
            }

            // Fallback: im Zweifel blocken
            return true;
        });

        if (conflict) {
            throw new RuntimeException("Das Fahrzeug ist in diesem Zeitraum bereits gebucht oder reserviert.");
        }

        // =========================
        // Buchung speichern
        // =========================
        Buchung buchung = Buchung.builder()
                .fahrzeug(fahrzeug)
                .user(user)
                .kundeName(dto.getKundeName())
                .kundeEmail(dto.getKundeEmail())
                .kundePhone(dto.getKundePhone())
                .startDatum(dto.getStartDatum())
                .endDatum(dto.getEndDatum())
                .bringService(dto.isBringService())
                .status(BuchungsStatus.RESERVIERT)
                .reserviertBis(now.plusMinutes(5)) // 5-Minuten-Hold
                .build();

        Buchung saved = buchungRepo.save(buchung);
        if (saved.getBuchungsNummer() == null) {
            saved.setBuchungsNummer(generateBuchungsNummer(saved));
            saved = buchungRepo.save(saved);
        }
        return toDTO(saved);
    }

    public List<BuchungAntwortDTO> aktiveBuchungenFuerFahrzeug(Long fahrzeugId) {
        LocalDateTime now = LocalDateTime.now();
        List<BuchungsStatus> aktive = List.of(BuchungsStatus.RESERVIERT, BuchungsStatus.BEZAHLT);

        List<Buchung> list = buchungRepo.findAllByFahrzeug_IdAndStatusIn(fahrzeugId, aktive);

        return list.stream()
                // abgelaufene Reservierungen raus
                .filter(b -> !(b.getStatus() == BuchungsStatus.RESERVIERT
                        && b.getReserviertBis() != null
                        && b.getReserviertBis().isBefore(now)))
                .map(this::toDTO)
                .toList();
    }


    public BuchungAntwortDTO adminBlockieren(BuchungAnlegenDTO dto) {
        if (dto.getStartDatum() == null || dto.getEndDatum() == null) {
            throw new IllegalArgumentException("Start- und Enddatum sind erforderlich.");
        }

        if (dto.getStartDatum().isAfter(dto.getEndDatum())) {
            throw new IllegalArgumentException("Startdatum muss vor Enddatum liegen.");
        }

        Fahrzeug fahrzeug = fahrzeugRepo.findById(dto.getFahrzeugId())
                .orElseThrow(() -> new RuntimeException("Fahrzeug nicht gefunden"));

        LocalDateTime now = LocalDateTime.now();

        // Gleicher Konflikt-Check wie oben
        var overlaps = buchungRepo
                .findAllByFahrzeug_IdAndStartDatumLessThanEqualAndEndDatumGreaterThanEqual(
                        dto.getFahrzeugId(),
                        dto.getEndDatum(),
                        dto.getStartDatum()
                );

        boolean conflict = overlaps.stream().anyMatch(b -> {
            BuchungsStatus status = b.getStatus();

            if (status == BuchungsStatus.STORNIERT) return false;
            if (status == BuchungsStatus.BEZAHLT) return true;

            if (status == BuchungsStatus.RESERVIERT) {
                LocalDateTime reserviertBis = b.getReserviertBis();
                return reserviertBis == null || reserviertBis.isAfter(now);
            }

            return true;
        });

        if (conflict) {
            throw new RuntimeException("Das Fahrzeug ist in diesem Zeitraum bereits vergeben.");
        }

        // Admin-Block: RESERVIERT, aber ohne 5-Minuten-Hold
        Buchung buchung = Buchung.builder()
                .fahrzeug(fahrzeug)
                .user(null) // oder spezifischer Admin-User später
                .kundeName(dto.getKundeName() != null ? dto.getKundeName() : "Interne Reservierung")
                .kundeEmail(dto.getKundeEmail())
                .kundePhone(dto.getKundePhone())
                .startDatum(dto.getStartDatum())
                .endDatum(dto.getEndDatum())
                .bringService(dto.isBringService())
                .status(BuchungsStatus.RESERVIERT)
                .reserviertBis(null) // blockiert dauerhaft, bis storniert/bezahlt
                .build();

        Buchung saved = buchungRepo.save(buchung);

        if (saved.getBuchungsNummer() == null) {
            saved.setBuchungsNummer(generateBuchungsNummer(saved));
            saved = buchungRepo.save(saved);
        }
        return toDTO(saved);
    }



    // =========================
    // LISTEN: nach Fahrzeug / nach User
    // =========================
    public List<BuchungAntwortDTO> alleFuerFahrzeug(Long fahrzeugId) {
        return buchungRepo.findAllByFahrzeug_Id(fahrzeugId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<BuchungAntwortDTO> alleFuerUser(Long userId) {
        return buchungRepo.findAllByUser_Id(userId).stream()
                .map(this::toDTO)
                .toList();
    }

    public BuchungAntwortDTO stornieren(Long buchungId) {
        Buchung b = buchungRepo.findById(buchungId)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));

        if (b.getStatus() == BuchungsStatus.STORNIERT) {
            // schon storniert, einfach zurückgeben
            return toDTO(b);
        }

        b.setStatus(BuchungsStatus.STORNIERT);
        b.setReserviertBis(null); // blockiert nicht mehr

        Buchung saved = buchungRepo.save(b);
        return toDTO(saved);
    }





    // =========================
    // Mapping
    // =========================
    private BuchungAntwortDTO toDTO(Buchung b) {
        return new BuchungAntwortDTO(
                b.getId(),
                b.getBuchungsNummer(),
                b.getFahrzeug() != null ? b.getFahrzeug().getId() : null,
                b.getUser() != null ? b.getUser().getId() : null,
                b.getKundeName(),
                b.getKundeEmail(),
                b.getKundePhone(),
                b.getStartDatum(),
                b.getEndDatum(),
                b.isBringService(),
                b.getStatus().name(),
                b.getGesamtPreis(),
                b.getCreatedAt(),
                b.getReserviertBis()
        );
    }

    private String generateBuchungsNummer(Buchung buchung) {
        LocalDate heute = LocalDate.now();
        int year = heute.getYear();
        int month = heute.getMonthValue();

        // MZ-202511-000123 z.B.
        return String.format("MZ-%d%02d-%06d", year, month, buchung.getId());
    }


}

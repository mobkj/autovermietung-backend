package com.autovermietung.backend.service;

import com.autovermietung.backend.model.*;
import com.autovermietung.backend.model.dto.BuchungAnlegenDTO;
import com.autovermietung.backend.model.dto.BuchungAntwortDTO;
import com.autovermietung.backend.repository.BuchungRepository;
import com.autovermietung.backend.repository.FahrzeugRepository;
import com.autovermietung.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.autovermietung.backend.exception.ApiException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import com.autovermietung.backend.model.dto.AdminTodoItemDTO;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BuchungService {

    private final BuchungRepository buchungRepo;
    private final FahrzeugRepository fahrzeugRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;

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

        Fahrzeug fahrzeug = fahrzeugRepo.findByIdForUpdate(dto.getFahrzeugId())
                .orElseThrow(() -> new RuntimeException("Fahrzeug nicht gefunden"));


        User user = null;
        Role role = null;

        if (dto.getUserId() != null) {
            user = userRepo.findById(dto.getUserId())
                    .orElseThrow(() -> new RuntimeException("User nicht gefunden"));
            role = user.getRole();
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

        BuchungsStatus status;
        LocalDateTime reserviertBis;
        if (role == Role.ADMIN) {
            // 🔥 Admin blockt den Zeitraum sofort "hart"
            status = BuchungsStatus.BEZAHLT;
            reserviertBis = null;
        } else {
            // Normaler Kunde → weiche Reservierung mit 10-Minuten-Hold
            status = BuchungsStatus.RESERVIERT;
            reserviertBis = now.plusMinutes(10);
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
                .status(status)             // ✅ benutze die berechneten Werte
                .reserviertBis(reserviertBis)
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

        // Wir wollen alle aktiven Buchungen:
        // - BEZAHLT → immer
        // - RESERVIERT → nur wenn reserviertBis noch in der Zukunft oder null (Admin-Block)
        List<BuchungsStatus> aktive = List.of(
                BuchungsStatus.RESERVIERT,
                BuchungsStatus.BEZAHLT
        );

        List<Buchung> list = buchungRepo.findAllByFahrzeug_IdAndStatusIn(fahrzeugId, aktive);

        return list.stream()
                // abgelaufene Reservierungen rauswerfen
                .filter(b -> !(
                        b.getStatus() == BuchungsStatus.RESERVIERT
                                && b.getReserviertBis() != null
                                && b.getReserviertBis().isBefore(now)
                ))
                .map(this::toDTO)
                .toList();
    }




    // =========================
// BUCHUNGEN FÜR AKTUELLEN USER (aus JWT)
// =========================
    public List<BuchungAntwortDTO> alleFuerCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("Nicht eingeloggt.");
        }

        // In deinem JWT ist die Email als Subject gesetzt
        String email = auth.getName();

        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User zum Token nicht gefunden."));
        return buchungRepo.findAllByUser_Id(user.getId()).stream()
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

        Fahrzeug fahrzeug = fahrzeugRepo.findByIdForUpdate(dto.getFahrzeugId())
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

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User adminUser = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin nicht gefunden"));

        // Admin-Block: RESERVIERT, aber ohne 5-Minuten-Hold
        Buchung buchung = Buchung.builder()
                .fahrzeug(fahrzeug)
                .user(adminUser) // oder spezifischer Admin-User später
                .kundeName(dto.getKundeName() != null ? dto.getKundeName() : "Interne Reservierung")
                .kundeEmail(dto.getKundeEmail())
                .kundePhone(dto.getKundePhone())
                .startDatum(dto.getStartDatum())
                .endDatum(dto.getEndDatum())
                .bringService(dto.isBringService())
                .status(BuchungsStatus.BEZAHLT)
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

    public List<AdminTodoItemDTO> adminTodos(String range) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to;

        switch (range == null ? "" : range.toLowerCase()) {
            case "today", "heute" -> to = today.atTime(23, 59, 59);
            case "month", "monat" -> to = today.plusMonths(1).atTime(23, 59, 59);
            default -> to = today.plusWeeks(1).atTime(23, 59, 59); // week = default
        }

        List<Buchung> list = buchungRepo.findByStatusAndStartDatumBetweenOrderByStartDatumAsc(
                BuchungsStatus.BEZAHLT,
                from,
                to
        );

        return list.stream()
                .map(this::mapToAdminTodo)
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
                b.isAgbAccepted(),
                b.isBringService(),
                b.getStatus().name(),
                b.getGesamtPreis(),
                b.getCreatedAt(),
                b.getReserviertBis(),
                b.getRefundAmount(),     // ✅ neu
                b.getStorniertAm()
        );
    }

    public BuchungAntwortDTO stornierenUser(Long buchungId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ApiException("Nicht eingeloggt.");
        }

        String email = auth.getName();
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException("Benutzer nicht gefunden."));

        Buchung b = buchungRepo.findById(buchungId)
                .orElseThrow(() -> new ApiException("Buchung nicht gefunden."));

        if (b.getUser() == null || !b.getUser().getId().equals(user.getId())) {
            throw new ApiException("Du kannst nur deine eigenen Buchungen stornieren.");
        }

        // schon storniert → nichts machen
        if (b.getStatus() == BuchungsStatus.STORNIERT) {
            return toDTO(b);
        }

        Buchung saved;

        // Unbezahlte Reservierung -> einfach freigeben
        if (b.getStatus() == BuchungsStatus.RESERVIERT) {
            b.setStatus(BuchungsStatus.STORNIERT);
            b.setReserviertBis(null);
            b.setStorniertAm(LocalDateTime.now());
            saved = buchungRepo.save(b);
            sendStornoMailSafe(saved);
            return toDTO(saved);
        }

        // Bezahlte Buchung -> Refund nach Regel
        if (b.getStatus() == BuchungsStatus.BEZAHLT) {
            BigDecimal refundAmount = berechneStornoRefundBetrag(b);
            fuehreStripeRefundDurch(b, refundAmount);

            b.setStatus(BuchungsStatus.STORNIERT);
            b.setReserviertBis(null);
            b.setStorniertAm(LocalDateTime.now());
            saved = buchungRepo.save(b);
            sendStornoMailSafe(saved);
            return toDTO(saved);
        }

        // fallback
        b.setStatus(BuchungsStatus.STORNIERT);
        b.setReserviertBis(null);
        b.setStorniertAm(LocalDateTime.now());
        saved = buchungRepo.save(b);
        sendStornoMailSafe(saved);
        return toDTO(saved);
    }

    private void sendStornoMailSafe(Buchung buchung) {
        try {
            emailService.sendStornoBestaetigung(buchung);
        } catch (Exception mailEx) {
            System.out.println("[Mail] Fehler beim Senden der Storno-Bestätigung: " + mailEx.getMessage());
        }
    }



    private String generateBuchungsNummer(Buchung buchung) {
        LocalDate heute = LocalDate.now();
        int year = heute.getYear();
        int month = heute.getMonthValue();

        // MZ-202511-000123 z.B.
        return String.format("MZ-%d%02d-%06d", year, month, buchung.getId());
    }

    private BigDecimal berechneStornoRefundBetrag(Buchung b) {
        if (b.getGesamtPreis() == null || b.getStartDatum() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal full = b.getGesamtPreis().setScale(2, RoundingMode.HALF_UP);

        LocalDate startDate = b.getStartDatum().toLocalDate();
        LocalDate heute = LocalDate.now();

        long daysUntilStart = ChronoUnit.DAYS.between(heute, startDate);

        // 1) Basis-Erstattungsbetrag je nach Zeitpunkt der Stornierung
        BigDecimal baseRefund;
        if (daysUntilStart > 10) {
            // Mehr als 10 Tage vorher -> theoretisch volle Erstattung
            baseRefund = full;
        } else if (daysUntilStart >= 0) {
            // 0–10 Tage vorher -> nur 50 % Erstattung
            baseRefund = full.multiply(new BigDecimal("0.5"));
        } else {
            // Mietbeginn schon vorbei -> keine Erstattung
            return BigDecimal.ZERO;
        }

        // 2) Gebühren des Zahlungsanbieters ungefähr schätzen
        //    (z.B. ca. 2,5 % + 0,35 € auf den ursprünglichen Betrag)
        BigDecimal percentFee   = new BigDecimal("0.025"); // 2,5 %
        BigDecimal fixedFee     = new BigDecimal("0.35");  // 0,35 €
        BigDecimal minFee       = new BigDecimal("2.00");  // Mindestgebühr
        BigDecimal maxFee       = new BigDecimal("25.00"); // optionale Obergrenze

        BigDecimal feeEstimate = full.multiply(percentFee).add(fixedFee);

        // Servicegebühr begrenzen
        BigDecimal serviceFee = feeEstimate.max(minFee).min(maxFee);

        // 3) Tatsächlicher Refund = Basis-Erstattung minus Servicegebühr
        BigDecimal refund = baseRefund.subtract(serviceFee);

        if (refund.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }

        return refund.setScale(2, RoundingMode.HALF_UP);
    }





    private void fuehreStripeRefundDurch(Buchung b, BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // nichts zu erstatten
        }

        if (b.getStripePaymentIntentId() == null) {
            System.out.println("[Storno] Keine Stripe PaymentIntent ID vorhanden, kann nicht refunden.");
            return;
        }

        try {
            long amountInCents = refundAmount
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(b.getStripePaymentIntentId())
                    .setAmount(amountInCents)
                    .build();

            Refund refund = Refund.create(params);

            b.setRefundAmount(refundAmount);
            b.setStripeRefundId(refund.getId());   // 🔥 hier speichern wir die Refund-ID
            b.setStorniertAm(LocalDateTime.now());

            System.out.println("[Storno] Refund erstellt: " + refund.getId()
                    + " über " + refundAmount + " EUR");
        } catch (Exception e) {
            throw new ApiException("Die Rückerstattung über Stripe ist fehlgeschlagen: " + e.getMessage());
        }
    }


    public void abbrechenOhneStorno(Long buchungId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ApiException("Nicht eingeloggt.");
        }

        String email = auth.getName();
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ApiException("Benutzer nicht gefunden."));

        Buchung b = buchungRepo.findById(buchungId)
                .orElseThrow(() -> new ApiException("Buchung nicht gefunden."));

        // Nur eigene Buchung abbrechen
        if (b.getUser() == null || !b.getUser().getId().equals(user.getId())) {
            throw new ApiException("Du kannst nur deine eigenen Buchungen abbrechen.");
        }

        // WICHTIG: Nur unverbindliche Reservierungen (unbezahlt)
        if (b.getStatus() != BuchungsStatus.RESERVIERT) {
            throw new ApiException("Nur unverbindliche Reservierungen können abgebrochen werden.");
        }

        // Safety: falls da schon Payment-Infos drin sind → lieber stornieren
        if (b.getStripePaymentIntentId() != null || b.getGesamtPreis() != null) {
            throw new ApiException("Diese Buchung scheint bereits bezahlt zu sein. Bitte stornieren.");
        }

        // 👉 Komplett löschen – als hätte es die Buchung nie gegeben
        buchungRepo.delete(b);
    }


    private AdminTodoItemDTO mapToAdminTodo(Buchung b) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = b.getStartDatum();
        long tageBis = start != null
                ? ChronoUnit.DAYS.between(today, start.toLocalDate())
                : 0;

        // Fahrzeugname
        String fahrzeugName = "";
        if (b.getFahrzeug() != null) {
            var f = b.getFahrzeug();
            String base = (safe(f.getMarke()) + " " + safe(f.getModell())).trim();
            if (f.getSerie() != null && !f.getSerie().isBlank()) {
                fahrzeugName = (base + " " + f.getSerie()).trim();
            } else {
                fahrzeugName = base;
            }
        }

        // Kunde
        String kundeName;
        String kundeEmail = safe(b.getKundeEmail());
        String kundePhone = safe(b.getKundePhone());

        if (b.getUser() != null) {
            String fn = safe(b.getUser().getFirstName());
            String ln = safe(b.getUser().getLastName());
            kundeName = (fn + " " + ln).trim();

            if (kundeEmail.isBlank()) {
                kundeEmail = safe(b.getUser().getEmail());
            }
            if (kundePhone.isBlank()) {
                kundePhone = safe(b.getUser().getPhone());
            }
        } else {
            kundeName = safe(b.getKundeName());
        }

        // Ort
        String ort;
        if (b.isBringService()) {
            ort = buildCustomerAddressLine(b);
        } else {
            ort = "Mazari Autovermietung, Am Königsfloß 6, 55252 Mainz-Kastel";
        }

        return new AdminTodoItemDTO(
                b.getId(),
                safe(b.getBuchungsNummer()),
                fahrzeugName,
                kundeName,
                kundeEmail,
                kundePhone,
                b.isBringService(),
                ort,
                start,
                tageBis
        );
    }


    private String buildCustomerAddressLine(Buchung b) {
        if (b.getUser() == null) {
            return "";
        }
        var u = b.getUser();
        String street = (safe(u.getStreet()) + " " + safe(u.getHouseNumber())).trim();
        String city   = (safe(u.getPostalCode()) + " " + safe(u.getCity())).trim();
        String country = safe(u.getCountry());
        return (street + ", " + city + (country.isBlank() ? "" : ", " + country)).trim();
    }

    private String safe(Object v) {
        return v == null ? "" : v.toString().trim();
    }



}

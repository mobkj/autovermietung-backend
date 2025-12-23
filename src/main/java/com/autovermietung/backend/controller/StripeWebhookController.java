package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.Buchung;
import com.autovermietung.backend.model.BuchungsStatus;
import com.autovermietung.backend.model.User;
import com.autovermietung.backend.model.dto.BuchungPreisAntwortDTO;
import com.autovermietung.backend.repository.BuchungRepository;
import com.autovermietung.backend.service.EmailService;
import com.autovermietung.backend.service.PreisBerechnungService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final BuchungRepository buchungRepo;
    private final PreisBerechnungService preisBerechnungService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmailService emailService;

    @Value("${stripe.webhookSecret}")
    private String endpointSecret;

    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload
    ) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            System.out.println("[Stripe Webhook] Signature verification failed: " + e.getMessage());
            return ResponseEntity.badRequest().body("");
        }

        String type = event.getType();
        System.out.println("[Stripe Webhook] Event empfangen: " + type);

        if ("checkout.session.completed".equals(type)
                || "checkout.session.async_payment_succeeded".equals(type)) {

            try {
                JsonNode root = objectMapper.readTree(payload);
                JsonNode sessionNode = root.path("data").path("object");

                if (sessionNode.isMissingNode()) {
                    System.out.println("[Stripe Webhook] sessionNode fehlt in payload.");
                    return ResponseEntity.ok("");
                }

                String sessionId       = sessionNode.path("id").asText(null);
                String paymentStatus   = sessionNode.path("payment_status").asText(null);
                String paymentIntentId = sessionNode.path("payment_intent").asText(null);

                System.out.println("[Stripe Webhook] Raw Session ID: " + sessionId
                        + ", payment_status=" + paymentStatus);

                // Wenn die Session nicht wirklich bezahlt ist → nichts tun
                if (!"paid".equalsIgnoreCase(paymentStatus)) {
                    System.out.println("[Stripe Webhook] Zahlung nicht 'paid' → nichts zu tun.");
                    return ResponseEntity.ok("");
                }

                // ✅ HIER EINFÜGEN (payment_intent MUSS da sein – sonst Refund etc. kaputt)
                if (paymentIntentId == null || paymentIntentId.isBlank()) {
                    System.out.println("[Stripe Webhook] payment_intent fehlt trotz paid -> Abbruch");
                    return ResponseEntity.ok("");
                }

                // 3) Metadata auslesen (BuchungId, Km-Paket, Bringservice)
                JsonNode metadata = sessionNode.path("metadata");
                String buchungIdStr    = metadata.path("buchungId").asText(null);
                String kmPaketStr      = metadata.path("freieKmPaket").asText(null);
                String bringServiceStr = metadata.path("bringService").asText("false");
                boolean bringService   = Boolean.parseBoolean(bringServiceStr);

                if (buchungIdStr == null || kmPaketStr == null) {
                    System.out.println("[Stripe Webhook] Metadata unvollständig, breche ab.");
                    return ResponseEntity.ok("");
                }

                Long buchungId   = Long.valueOf(buchungIdStr);
                int freieKmPaket = Integer.parseInt(kmPaketStr);

                // 4) Buchung aus der DB holen (LOCK)
                Buchung buchung = buchungRepo.findByIdForUpdate(buchungId).orElse(null);

                if (buchung == null) {
                    System.out.println("[Stripe Webhook] Buchung " + buchungId + " nicht gefunden.");
                    return ResponseEntity.ok("");
                }

                // ✅ HIER (SessionId claimen / prüfen)
                if (buchung.getStripeSessionId() == null) {
                    buchung.setStripeSessionId(sessionId);
                    buchungRepo.save(buchung);
                } else if (sessionId != null && !buchung.getStripeSessionId().equals(sessionId)) {
                    System.out.println("[Stripe Webhook] Buchung " + buchungId + " hat andere sessionId -> ignore");
                    return ResponseEntity.ok("");
                }

                // ✅ HIER (Idempotenz: wenn PaymentIntent schon gesetzt -> ignore)
                if (buchung.getStripePaymentIntentId() != null) {
                    System.out.println("[Stripe Webhook] Buchung " + buchungId
                            + " hat bereits paymentIntent -> ignore.");
                    return ResponseEntity.ok("");
                }

                // 5) Idempotenz: wenn schon bezahlt oder storniert → nichts ändern
                if (buchung.getStatus() == BuchungsStatus.BEZAHLT
                        || buchung.getStatus() == BuchungsStatus.STORNIERT) {
                    System.out.println("[Stripe Webhook] Buchung " + buchungId
                            + " bereits im Status " + buchung.getStatus());
                    return ResponseEntity.ok("");
                }

                // Nur RESERVIERT-Buchungen dürfen an dieser Stelle auf BEZAHLT springen
                if (buchung.getStatus() != BuchungsStatus.RESERVIERT) {
                    System.out.println("[Stripe Webhook] Unerwarteter Status für Buchung " + buchungId
                            + ": " + buchung.getStatus() + " → Abbruch.");
                    return ResponseEntity.ok("");
                }

                // ✅ HIER EINFÜGEN (Reservierung darf NICHT abgelaufen sein)
                LocalDateTime now = LocalDateTime.now();
                if (buchung.getReserviertBis() != null && buchung.getReserviertBis().isBefore(now)) {
                    System.out.println("[Stripe Webhook] Reservierung abgelaufen -> ignore");
                    return ResponseEntity.ok("");
                }

                // 6) Optional: Email aus Metadata vs. Benutzer-Email prüfen (nur Logging)
                String metaEmail = metadata.path("kundeEmail").asText(null);
                if (metaEmail != null && buchung.getUser() != null) {
                    String userEmail = buchung.getUser().getEmail();
                    if (userEmail != null && !userEmail.equalsIgnoreCase(metaEmail)) {
                        System.out.println("[Stripe Webhook] Warnung: Email aus Metadata ("
                                + metaEmail + ") passt nicht zur Buchung-User-Email (" + userEmail + ").");
                    }
                }

                // 7) Rechnungsdaten aus Stripe / User bestimmen und in Buchung einfrieren
                try {
                    JsonNode customerDetails    = sessionNode.path("customer_details");
                    JsonNode billingAddressNode = customerDetails.path("address");

                    String stripeName  = customerDetails.path("name").asText(null);
                    String stripeEmail = customerDetails.path("email").asText(null);

                    String stripeLine1      = billingAddressNode.path("line1").asText(null);
                    String stripePostalCode = billingAddressNode.path("postal_code").asText(null);
                    String stripeCity       = billingAddressNode.path("city").asText(null);
                    String stripeCountry    = billingAddressNode.path("country").asText(null);

                    boolean hasStripeAddress =
                            (stripeLine1 != null && !stripeLine1.isBlank()) ||
                                    (stripePostalCode != null && !stripePostalCode.isBlank()) ||
                                    (stripeCity != null && !stripeCity.isBlank()) ||
                                    (stripeCountry != null && !stripeCountry.isBlank());

                    User user = buchung.getUser();

                    // 7.1) Name
                    String userFullName = "";
                    if (user != null) {
                        String fn = user.getFirstName() != null ? user.getFirstName().trim() : "";
                        String ln = user.getLastName() != null ? user.getLastName().trim() : "";
                        userFullName = (fn + " " + ln).trim();
                    }

                    String rechnungName;
                    if (stripeName != null && !stripeName.isBlank()) rechnungName = stripeName;
                    else if (!userFullName.isBlank()) rechnungName = userFullName;
                    else if (buchung.getKundeName() != null && !buchung.getKundeName().isBlank()) rechnungName = buchung.getKundeName();
                    else if (stripeEmail != null && !stripeEmail.isBlank()) rechnungName = stripeEmail;
                    else rechnungName = "Unbekannter Kunde";

                    // 7.2) Company
                    String rechnungCompany = null;
                    if (user != null && user.getCompanyName() != null && !user.getCompanyName().isBlank()) {
                        rechnungCompany = user.getCompanyName().trim();
                    }

                    // 7.3) Adresse
                    String rechnungStrasse;
                    String rechnungPlz;
                    String rechnungOrt;
                    String rechnungLand;

                    if (hasStripeAddress) {
                        rechnungStrasse = stripeLine1;
                        rechnungPlz     = stripePostalCode;
                        rechnungOrt     = stripeCity;
                        rechnungLand    = stripeCountry;
                    } else if (user != null) {
                        rechnungStrasse = buildStrasse(user);
                        rechnungPlz     = nullSafe(user.getPostalCode());
                        rechnungOrt     = nullSafe(user.getCity());
                        rechnungLand    = nullSafe(user.getCountry(), "DE");
                    } else {
                        rechnungStrasse = "";
                        rechnungPlz     = "";
                        rechnungOrt     = "";
                        rechnungLand    = "DE";
                    }

                    // speichern
                    buchung.setRechnungName(rechnungName);
                    buchung.setRechnungCompany(rechnungCompany);
                    buchung.setRechnungStrasse(rechnungStrasse);
                    buchung.setRechnungPlz(rechnungPlz);
                    buchung.setRechnungOrt(rechnungOrt);
                    buchung.setRechnungLand(rechnungLand);
                    buchung.setBringService(bringService);
                    buchung.setFreieKmPaket(freieKmPaket);

                    // ✅ HIER EINFÜGEN (PDF-SAFE Defaults)
                    if (buchung.getRechnungLand() == null || buchung.getRechnungLand().isBlank()) {
                        buchung.setRechnungLand("DE");
                    }
                    if (buchung.getRechnungName() == null || buchung.getRechnungName().isBlank()) {
                        buchung.setRechnungName("Unbekannter Kunde");
                    }

                } catch (Exception addrEx) {
                    System.out.println("[Stripe Webhook] Konnte Rechnungsadresse nicht auslesen: " + addrEx.getMessage());
                }

                // amount_total lesen
                long stripeAmountTotal = sessionNode.path("amount_total").asLong(-1);
                if (stripeAmountTotal <= 0) {
                    System.out.println("[Stripe Webhook] amount_total fehlt/ungueltig -> Abbruch");
                    return ResponseEntity.ok("");
                }

                // Erwarteten Betrag neu berechnen
                BuchungPreisAntwortDTO expectedPreis =
                        preisBerechnungService.berechnePreis(buchung, freieKmPaket, bringService);

                if (expectedPreis == null || expectedPreis.getGesamtBrutto() == null) {
                    System.out.println("[Stripe Webhook] Preisberechnung fehlgeschlagen (null) -> Abbruch");
                    return ResponseEntity.ok("");
                }

                long expectedCents = expectedPreis.getGesamtBrutto()
                        .multiply(new BigDecimal("100"))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();

                if (stripeAmountTotal != expectedCents) {
                    System.out.println("[Stripe Webhook] Betrag-Mismatch! Stripe=" + stripeAmountTotal
                            + " expected=" + expectedCents + " (Buchung " + buchungId + ")");
                    return ResponseEntity.ok("");
                }

                // ✅ FINAL: alles passt -> auf BEZAHLT setzen
                buchung.setStatus(BuchungsStatus.BEZAHLT);
                buchung.setGesamtPreis(expectedPreis.getGesamtBrutto());
                buchung.setReserviertBis(null);
                buchung.setStripeSessionId(sessionId);
                buchung.setStripePaymentIntentId(paymentIntentId);
                buchung.setAgbAccepted(true);

                buchungRepo.save(buchung);

                try {
                    emailService.sendPaymentConfirmation(buchung);
                } catch (Exception mailEx) {
                    System.out.println("[Stripe Webhook] Fehler beim Senden der Zahlungsbestätigung: " + mailEx.getMessage());
                }

            } catch (Exception e) {
                System.out.println("[Stripe Webhook] Fehler beim Verarbeiten: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return ResponseEntity.ok("");
    }


    // ===== Helper-Methoden =====

    private String buildStrasse(User u) {
        if (u == null) return "";
        String street = u.getStreet() != null ? u.getStreet().trim() : "";
        String house  = u.getHouseNumber() != null ? u.getHouseNumber().trim() : "";
        return (street + " " + house).trim();
    }

    private boolean safeEquals(String a, String b) {
        String aa = a == null ? "" : a.trim();
        String bb = b == null ? "" : b.trim();
        return aa.equalsIgnoreCase(bb);
    }

    private String nullSafe(String v) {
        return v == null ? "" : v.trim();
    }

    private String nullSafe(String v, String fallback) {
        String val = v == null ? "" : v.trim();
        return val.isEmpty() ? fallback : val;
    }
}

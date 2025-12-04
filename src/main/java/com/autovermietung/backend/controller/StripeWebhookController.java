package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.Buchung;
import com.autovermietung.backend.model.BuchungsStatus;
import com.autovermietung.backend.model.User;
import com.autovermietung.backend.model.dto.BuchungPreisAntwortDTO;
import com.autovermietung.backend.repository.BuchungRepository;
import com.autovermietung.backend.service.PreisBerechnungService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final BuchungRepository buchungRepo;
    private final PreisBerechnungService preisBerechnungService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${stripe.webhookSecret}")
    private String endpointSecret;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload
    ) {

        Event event;
        try {
            // Signatur prüfen
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
                // Payload JSON parsen
                JsonNode root = objectMapper.readTree(payload);
                JsonNode sessionNode = root.path("data").path("object");

                if (sessionNode.isMissingNode()) {
                    System.out.println("[Stripe Webhook] sessionNode fehlt in payload.");
                    return ResponseEntity.ok("");
                }

                String sessionId = sessionNode.path("id").asText(null);
                String paymentStatus = sessionNode.path("payment_status").asText(null);

                System.out.println("[Stripe Webhook] Raw Session ID: " + sessionId
                        + ", payment_status=" + paymentStatus);

                String paymentIntentId = sessionNode.path("payment_intent").asText(null);

                if (!"paid".equalsIgnoreCase(paymentStatus)) {
                    System.out.println("[Stripe Webhook] Zahlung nicht 'paid' → nichts zu tun.");
                    return ResponseEntity.ok("");
                }

                JsonNode metadata = sessionNode.path("metadata");
                String buchungIdStr = metadata.path("buchungId").asText(null);
                String kmPaketStr   = metadata.path("freieKmPaket").asText(null);
                String bringServiceStr = metadata.path("bringService").asText("false");
                boolean bringService = Boolean.parseBoolean(bringServiceStr);

                System.out.println("[Stripe Webhook] Metadata: buchungId="
                        + buchungIdStr + ", freieKmPaket=" + kmPaketStr);

                if (buchungIdStr == null || kmPaketStr == null) {
                    System.out.println("[Stripe Webhook] Metadata unvollständig, breche ab.");
                    return ResponseEntity.ok("");
                }

                Long buchungId = Long.valueOf(buchungIdStr);
                int freieKmPaket = Integer.parseInt(kmPaketStr);

                Buchung buchung = buchungRepo.findById(buchungId).orElse(null);
                if (buchung == null) {
                    System.out.println("[Stripe Webhook] Buchung " + buchungId + " nicht gefunden.");
                    return ResponseEntity.ok("");
                }


                // Idempotent: Wenn schon bezahlt/storniert, nichts ändern
                if (buchung.getStatus() == BuchungsStatus.BEZAHLT
                        || buchung.getStatus() == BuchungsStatus.STORNIERT) {
                    System.out.println("[Stripe Webhook] Buchung " + buchungId
                            + " bereits im Status " + buchung.getStatus());
                    return ResponseEntity.ok("");
                }

                if (buchung.getStatus() != BuchungsStatus.RESERVIERT) {
                    System.out.println("[Stripe Webhook] Unerwarteter Status für Buchung " + buchungId
                            + ": " + buchung.getStatus() + " → Abbruch.");
                    return ResponseEntity.ok("");
                }

// 🔐 Optional: Email aus Metadata vs. Benutzer-Email prüfen
                String metaEmail = metadata.path("kundeEmail").asText(null);
                if (metaEmail != null && buchung.getUser() != null) {
                    String userEmail = buchung.getUser().getEmail();
                    if (userEmail != null && !userEmail.equalsIgnoreCase(metaEmail)) {
                        System.out.println("[Stripe Webhook] Warnung: Email aus Metadata ("
                                + metaEmail + ") passt nicht zur Buchung-User-Email (" + userEmail + ").");
                        // Hier kannst du entscheiden:
                        // - Nur loggen und weitermachen (wie jetzt)
                        // - ODER abbrechen:
                        // return ResponseEntity.ok("");
                    }
                }

                // =========================================================
                // 🔍 Rechnungsadresse aus Stripe vs. Adresse des Users prüfen
                // =========================================================
                try {
                    JsonNode customerDetails = sessionNode.path("customer_details");
                    JsonNode billingAddressNode = customerDetails.path("address");

                    String stripeName   = customerDetails.path("name").asText(null);
                    String stripeEmail  = customerDetails.path("email").asText(null);

                    String stripeLine1      = billingAddressNode.path("line1").asText(null);
                    String stripeLine2      = billingAddressNode.path("line2").asText(null);
                    String stripePostalCode = billingAddressNode.path("postal_code").asText(null);
                    String stripeCity       = billingAddressNode.path("city").asText(null);
                    String stripeCountry    = billingAddressNode.path("country").asText(null);

                    boolean hasStripeAddress =
                            (stripeLine1 != null && !stripeLine1.isBlank()) ||
                                    (stripePostalCode != null && !stripePostalCode.isBlank()) ||
                                    (stripeCity != null && !stripeCity.isBlank()) ||
                                    (stripeCountry != null && !stripeCountry.isBlank());

                    if (hasStripeAddress && buchung.getUser() != null) {
                        User user = buchung.getUser();

                        String baseLine1 = buildStrasse(user);
                        String basePostal = nullSafe(user.getPostalCode());
                        String baseCity = nullSafe(user.getCity());
                        String baseCountry = nullSafe(user.getCountry(), "DE");

                        boolean differs =
                                !safeEquals(stripeLine1, baseLine1) ||
                                        !safeEquals(stripePostalCode, basePostal) ||
                                        !safeEquals(stripeCity, baseCity) ||
                                        !safeEquals(stripeCountry, baseCountry);

                        if (differs) {
                            System.out.println("[Stripe Webhook] ⚠ Abweichende Rechnungsadresse erkannt.");
                            System.out.println("  Adresse im System:");
                            System.out.println("    " + baseLine1);
                            System.out.println("    " + basePostal + " " + baseCity + " " + baseCountry);

                            System.out.println("  Adresse aus Stripe:");
                            System.out.println("    " + stripeLine1 + " " + stripeLine2);
                            System.out.println("    " + stripePostalCode + " " + stripeCity + " " + stripeCountry);
                            System.out.println("  Name (Stripe): " + stripeName);
                            System.out.println("  Email (Stripe): " + stripeEmail);

                            // =================================================
                            // HIER FÄNGT RECHNUNG-LOGIK AN (später implementieren)
                            //  - Diese Stripe-Adresse für Rechnungs-PDF merken
                            //  - Ggf. in Buchung/Rechnungs-Entity speichern
                            // =================================================

                        } else {
                            System.out.println("[Stripe Webhook] Rechnungsadresse entspricht der im System gespeicherten Adresse.");
                        }
                    } else {
                        System.out.println("[Stripe Webhook] Keine separate Rechnungsadresse von Stripe übermittelt oder kein User an Buchung.");
                    }
                } catch (Exception addrEx) {
                    System.out.println("[Stripe Webhook] Konnte Rechnungsadresse nicht auslesen: " + addrEx.getMessage());
                }

                // =========================
                // Preis final berechnen & Buchung updaten
                // =========================
                BuchungPreisAntwortDTO preis =
                        preisBerechnungService.berechnePreis(buchung, freieKmPaket, bringService);

                buchung.setBringService(bringService);
                buchung.setStatus(BuchungsStatus.BEZAHLT);
                buchung.setGesamtPreis(preis.getGesamtBrutto());
                buchung.setReserviertBis(null);
                buchung.setStripeSessionId(sessionId);
                buchung.setStripePaymentIntentId(paymentIntentId);
                buchungRepo.save(buchung);

                System.out.println("[Stripe Webhook] Buchung " + buchungId
                        + " auf BEZAHLT gesetzt. GesamtBrutto=" + preis.getGesamtBrutto());

                System.out.println("[Payment] Preis für Stripe: " + preis.getGesamtBrutto()
                        + " (freieKmPaket=" + freieKmPaket + ", bringService=" + bringService + ")");


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
        String house = u.getHouseNumber() != null ? u.getHouseNumber().trim() : "";
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

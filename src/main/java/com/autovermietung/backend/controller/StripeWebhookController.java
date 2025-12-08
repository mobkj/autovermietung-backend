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
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload
    ) {

        Event event;
        try {
            // 1) Webhook-Signatur von Stripe prüfen
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            System.out.println("[Stripe Webhook] Signature verification failed: " + e.getMessage());
            return ResponseEntity.badRequest().body("");
        }

        String type = event.getType();
        System.out.println("[Stripe Webhook] Event empfangen: " + type);

        // Nur auf erfolgreiche Checkout-Sessions reagieren
        if ("checkout.session.completed".equals(type)
                || "checkout.session.async_payment_succeeded".equals(type)) {
            try {
                // 2) Payload als JSON parsen und Session-Objekt holen
                JsonNode root = objectMapper.readTree(payload);
                JsonNode sessionNode = root.path("data").path("object");

                if (sessionNode.isMissingNode()) {
                    System.out.println("[Stripe Webhook] sessionNode fehlt in payload.");
                    return ResponseEntity.ok("");
                }

                String sessionId      = sessionNode.path("id").asText(null);
                String paymentStatus  = sessionNode.path("payment_status").asText(null);
                String paymentIntentId = sessionNode.path("payment_intent").asText(null);

                System.out.println("[Stripe Webhook] Raw Session ID: " + sessionId
                        + ", payment_status=" + paymentStatus);

                // Wenn die Session nicht wirklich bezahlt ist → nichts tun
                if (!"paid".equalsIgnoreCase(paymentStatus)) {
                    System.out.println("[Stripe Webhook] Zahlung nicht 'paid' → nichts zu tun.");
                    return ResponseEntity.ok("");
                }

                // 3) Metadata auslesen (BuchungId, Km-Paket, Bringservice)
                JsonNode metadata = sessionNode.path("metadata");
                String buchungIdStr     = metadata.path("buchungId").asText(null);
                String kmPaketStr       = metadata.path("freieKmPaket").asText(null);
                String bringServiceStr  = metadata.path("bringService").asText("false");
                boolean bringService    = Boolean.parseBoolean(bringServiceStr);

                System.out.println("[Stripe Webhook] Metadata: buchungId="
                        + buchungIdStr + ", freieKmPaket=" + kmPaketStr
                        + ", bringService=" + bringService);

                if (buchungIdStr == null || kmPaketStr == null) {
                    System.out.println("[Stripe Webhook] Metadata unvollständig, breche ab.");
                    return ResponseEntity.ok("");
                }

                Long buchungId      = Long.valueOf(buchungIdStr);
                int freieKmPaket    = Integer.parseInt(kmPaketStr);

                // 4) Buchung aus der DB holen
                Buchung buchung = buchungRepo.findById(buchungId).orElse(null);
                if (buchung == null) {
                    System.out.println("[Stripe Webhook] Buchung " + buchungId + " nicht gefunden.");
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

                    User user = buchung.getUser();

                    // Optionales Logging: Vergleich Stripe-Adresse vs. User-Adresse
                    if (hasStripeAddress && user != null) {
                        String baseLine1   = buildStrasse(user);
                        String basePostal  = nullSafe(user.getPostalCode());
                        String baseCity    = nullSafe(user.getCity());
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
                        } else {
                            System.out.println("[Stripe Webhook] Rechnungsadresse entspricht der im System gespeicherten Adresse.");
                        }
                    } else {
                        System.out.println("[Stripe Webhook] Keine separate Rechnungsadresse von Stripe übermittelt oder kein User an Buchung.");
                    }

                    // ===============================
                    // Rechnungsdaten bestimmen (immer)
                    // ===============================

                    // 7.1) Name für die Rechnung
                    String userFullName = "";
                    if (user != null) {
                        String fn = user.getFirstName() != null ? user.getFirstName().trim() : "";
                        String ln = user.getLastName() != null ? user.getLastName().trim() : "";
                        userFullName = (fn + " " + ln).trim();
                    }

                    String rechnungName;
                    if (stripeName != null && !stripeName.isBlank()) {
                        rechnungName = stripeName;
                    } else if (!userFullName.isBlank()) {
                        rechnungName = userFullName;
                    } else if (buchung.getKundeName() != null && !buchung.getKundeName().isBlank()) {
                        rechnungName = buchung.getKundeName();
                    } else if (stripeEmail != null && !stripeEmail.isBlank()) {
                        rechnungName = stripeEmail;
                    } else {
                        rechnungName = "Unbekannter Kunde";
                    }

                    // 7.2) Firmenname aus deinem System (falls hinterlegt)
                    String rechnungCompany = null;
                    if (user != null && user.getCompanyName() != null && !user.getCompanyName().isBlank()) {
                        rechnungCompany = user.getCompanyName().trim();
                    }

                    // 7.3) Rechnungsadresse – Stripe bevorzugt, sonst User-Adresse
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
                        // Notfall-Fallback
                        rechnungStrasse = "";
                        rechnungPlz     = "";
                        rechnungOrt     = "";
                        rechnungLand    = "DE";
                    }

                    // 7.4) In Buchung einfrieren – das nutzt du später fürs PDF
                    buchung.setRechnungName(rechnungName);
                    buchung.setRechnungCompany(rechnungCompany);
                    buchung.setRechnungStrasse(rechnungStrasse);
                    buchung.setRechnungPlz(rechnungPlz);
                    buchung.setRechnungOrt(rechnungOrt);
                    buchung.setRechnungLand(rechnungLand);

                } catch (Exception addrEx) {
                    System.out.println("[Stripe Webhook] Konnte Rechnungsadresse nicht auslesen: " + addrEx.getMessage());
                }

                // 8) Preis final berechnen und Buchung auf BEZAHLT setzen
                BuchungPreisAntwortDTO preis =
                        preisBerechnungService.berechnePreis(buchung, freieKmPaket, bringService);

                buchung.setBringService(bringService);
                buchung.setStatus(BuchungsStatus.BEZAHLT);
                buchung.setGesamtPreis(preis.getGesamtBrutto());
                buchung.setReserviertBis(null);               // Reservierungs-Timer löschen
                buchung.setStripeSessionId(sessionId);
                buchung.setStripePaymentIntentId(paymentIntentId);
                buchung.setAgbAccepted(true);

                buchungRepo.save(buchung);

                try {
                    emailService.sendPaymentConfirmation(buchung);
                } catch (Exception mailEx) {
                    System.out.println("[Stripe Webhook] Fehler beim Senden der Zahlungsbestätigung: " + mailEx.getMessage());
                }

                System.out.println("[Stripe Webhook] Buchung " + buchungId
                        + " auf BEZAHLT gesetzt. GesamtBrutto=" + preis.getGesamtBrutto());
                System.out.println("[Payment] Preis für Stripe: " + preis.getGesamtBrutto()
                        + " (freieKmPaket=" + freieKmPaket + ", bringService=" + bringService + ")");

            } catch (Exception e) {
                System.out.println("[Stripe Webhook] Fehler beim Verarbeiten: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Webhook immer mit 200 OK beantworten, damit Stripe zufrieden ist
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

package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.Buchung;
import com.autovermietung.backend.model.BuchungsStatus;
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

                // Preis final berechnen
                BuchungPreisAntwortDTO preis =
                        preisBerechnungService.berechnePreis(buchung, freieKmPaket);

                buchung.setStatus(BuchungsStatus.BEZAHLT);
                buchung.setGesamtPreis(preis.getGesamtBrutto());
                buchung.setReserviertBis(null);
                buchung.setStripeSessionId(sessionId);
                buchung.setStripePaymentIntentId(paymentIntentId);
                buchungRepo.save(buchung);

                System.out.println("[Stripe Webhook] Buchung " + buchungId
                        + " auf BEZAHLT gesetzt. GesamtBrutto=" + preis.getGesamtBrutto());

            } catch (Exception e) {
                System.out.println("[Stripe Webhook] Fehler beim Verarbeiten: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return ResponseEntity.ok("");
    }
}

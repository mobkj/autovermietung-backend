package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.Buchung;
import com.autovermietung.backend.model.BuchungsStatus;
import com.autovermietung.backend.model.dto.BuchungPreisAntwortDTO;
import com.autovermietung.backend.repository.BuchungRepository;
import com.autovermietung.backend.service.PreisBerechnungService;
import com.stripe.net.Webhook;
import jakarta.mail.Session;
import jdk.jfr.Event;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {
    /*

    private final BuchungRepository buchungRepo;
    private final PreisBerechnungService preisBerechnungService;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload
    ) {

        Event event;
        try {
            event = Webhook.constructEvent(
                    payload,
                    sigHeader,
                    endpointSecret
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);

            if (session != null) {
                String buchungIdStr = session.getMetadata().get("buchungId");
                Long buchungId = Long.valueOf(buchungIdStr);

                Buchung buchung = buchungRepo.findById(buchungId)
                        .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));

                // Preis final berechnen (damit du auch im Datensatz alles stehen hast)
                int freieKmPaket = Integer.parseInt(session.getMetadata().get("freieKmPaket"));
                BuchungPreisAntwortDTO preis =
                        preisBerechnungService.berechnePreis(buchung, freieKmPaket);

                // Buchung als BEZAHLT markieren & Gesamtpreis speichern
                buchung.setStatus(BuchungsStatus.BEZAHLT);
                buchung.setGesamtPreis(new BigDecimal(preis.getGesamtBrutto().toString()));
                buchungRepo.save(buchung);

                // TODO: hier PDF-Rechnung erzeugen & per Mail verschicken
                // generateInvoicePdf(buchung, preis, session);
            }
        }

        return ResponseEntity.ok("");
    }
    */
}

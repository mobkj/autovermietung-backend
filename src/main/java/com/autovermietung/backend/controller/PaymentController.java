package com.autovermietung.backend.controller;

import com.autovermietung.backend.exception.ApiException;
import com.autovermietung.backend.model.Buchung;
import com.autovermietung.backend.model.BuchungsStatus;
import com.autovermietung.backend.model.Role;
import com.autovermietung.backend.model.User;
import com.autovermietung.backend.model.dto.BuchungPreisAntwortDTO;
import com.autovermietung.backend.model.dto.CreateCheckoutSessionRequest;
import com.autovermietung.backend.repository.BuchungRepository;
import com.autovermietung.backend.repository.UserRepository;
import com.autovermietung.backend.service.PreisBerechnungService;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final BuchungRepository buchungRepo;
    private final PreisBerechnungService preisBerechnungService;
    private final UserRepository userRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    // TODO: für Prod in application-prod.yml auslagern
    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @PostConstruct
    public void init() {
        // Stripe-API-Key setzen
        Stripe.apiKey = stripeSecretKey;
    }

    /**
     * Body: { buchungId: number, freieKmPaket: number }
     *
     * 1. Holt die Buchung
     * 2. Prüft Rechte (Customer nur eigene Buchung, Admin alles)
     * 3. Prüft Status (nicht STORNIERT / BEZAHLT)
     * 4. Berechnet Preis
     * 5. Erzeugt Stripe Checkout Session
     */
    @PostMapping("/create-checkout-session")
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(
            @RequestBody CreateCheckoutSessionRequest request
    ) throws Exception {

        if (request.getBuchungId() == null) {
            throw new ApiException("Buchungs-ID fehlt.");
        }
        if (request.getFreieKmPaket() == null) {
            throw new ApiException("Kilometerpaket fehlt.");
        }

        // 1) Buchung laden
        Buchung buchung = buchungRepo.findById(request.getBuchungId())
                .orElseThrow(() -> new ApiException("Buchung wurde nicht gefunden."));

        // 2) Aktuellen User aus SecurityContext holen
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Benutzer im System nicht gefunden."));

        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        // 3) Wenn kein Admin: nur eigene Buchung darf bezahlt werden
        if (!isAdmin && !buchung.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException("Du darfst nur deine eigenen Buchungen bezahlen.");
        }

        // 4) Status checken – keine Zahlung, wenn schon bezahlt oder storniert
        if (buchung.getStatus() == BuchungsStatus.BEZAHLT) {
            throw new ApiException("Diese Buchung wurde bereits bezahlt.");
        }
        if (buchung.getStatus() == BuchungsStatus.STORNIERT) {
            throw new ApiException("Für eine stornierte Buchung kann keine Zahlung gestartet werden.");
        }

        // 5) Preis berechnen (inkl. Brutto)
        Integer freieKmPaketReq = request.getFreieKmPaket();
        if (freieKmPaketReq == null) {
            throw new ApiException("Kilometerpaket fehlt.");
        }
        int freieKmPaket = freieKmPaketReq; // explizit zu primitive int

        BuchungPreisAntwortDTO preis = preisBerechnungService
                .berechnePreis(buchung, freieKmPaket);

        // Stripe erwartet Betrag in CENT (Brutto)
        long amountInCents = preis.getGesamtBrutto()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        // 6) Metadaten – später im Webhook/Rechnung nützlich
        Map<String, String> metadata = new HashMap<>();
        metadata.put("buchungId", String.valueOf(buchung.getId()));
        metadata.put("buchungsNummer", buchung.getBuchungsNummer());
        metadata.put("fahrzeugId", String.valueOf(buchung.getFahrzeug().getId()));
        metadata.put("freieKmPaket", String.valueOf(request.getFreieKmPaket()));
        metadata.put("kundeEmail", buchung.getKundeEmail());

        String successUrl = frontendBaseUrl + "/checkout-success?session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = frontendBaseUrl + "/checkout-cancelled";

        // 7) Stripe Checkout-Session bauen
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("eur")
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Miete " +
                                                                        buchung.getFahrzeug().getMarke() + " " +
                                                                        buchung.getFahrzeug().getModell())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .putAllMetadata(metadata)
                .build();

        Session session = Session.create(params);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getId());
        response.put("checkoutUrl", session.getUrl());

        return ResponseEntity.ok(response);
    }
}

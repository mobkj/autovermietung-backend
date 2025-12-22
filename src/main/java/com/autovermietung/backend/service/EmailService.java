package com.autovermietung.backend.service;

import com.autovermietung.backend.model.Buchung;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.email.from}")
    private String from;

    @Value("${mailgun.api-key}")
    private String mailgunApiKey;

    @Value("${mailgun.domain}")
    private String mailgunDomain;

    // EU: https://api.eu.mailgun.net  | US: https://api.mailgun.net
    @Value("${mailgun.base-url:https://api.eu.mailgun.net}")
    private String mailgunBaseUrl;

    // ---------------------------------------------------
    // 1) Test-Mail
    // ---------------------------------------------------
    public void sendTestEmail(String to) {
        String subject = "Test-E-Mail aus der Autovermietung-App";

        String body = """
                Hey,

                das ist eine Test-E-Mail aus deiner Spring-Boot-Anwendung mit Mailgun.

                Wenn du diese Mail siehst, funktioniert dein Setup. 🎉
                
                %s
                """.formatted(buildFooter());

        sendViaMailgun(to, subject, body);
    }

    // ---------------------------------------------------
    // 2) Zahlungsbestätigung
    // ---------------------------------------------------
    public void sendPaymentConfirmation(Buchung buchung) {
        if (buchung.getKundeEmail() == null || buchung.getKundeEmail().isBlank()) return;

        LocalDateTime start = buchung.getStartDatum();
        LocalDateTime ende  = buchung.getEndDatum();

        String mietzeitraum = formatDateTimeRange(start, ende);
        String abholOderLieferInfo;

        if (buchung.isBringService()) {
            abholOderLieferInfo = """
                Sie haben den Bringservice angefordert.

                Das Fahrzeug wird am %s um %s
                an folgende Adresse geliefert:

                %s
                """.formatted(
                    formatDate(start),
                    formatTime(start),
                    buildCustomerAddress(buchung)
            );
        } else {
            abholOderLieferInfo = """
                Bitte holen Sie das Fahrzeug am %s um %s
                an folgender Adresse ab:

                Mazari Autovermietung
                Am Königsfloß 6
                55252 Mainz-Kastel
                Deutschland
                """.formatted(
                    formatDate(start),
                    formatTime(start)
            );
        }

        String subject = "Ihre Zahlung bei Mazari";

        String body = """
            Guten Tag,

            vielen Dank für Ihre Buchung bei Mazari Autovermietung.

            Ihre Zahlung für die folgende Buchung ist erfolgreich eingegangen:

            Buchungsnummer: %s
            Fahrzeug: %s %s
            Mietzeitraum: %s
            Gesamtbetrag (brutto): %s €

            Sie können Ihre Rechnung jederzeit in Ihrem Kundenbereich unter
            "Meine Buchungen" einsehen und herunterladen.

            Bitte lesen Sie vor einer möglichen Stornierung unsere AGB aufmerksam durch.

            Wichtige Hinweise zur Fahrzeugübergabe:
            - Bitte bringen Sie zur Fahrzeugübergabe Ihren gültigen Führerschein mit,
              damit wir Ihre Daten mit Ihrem Account abgleichen und den Mietvertrag
              abwickeln können.
            - Bitte halten Sie die vereinbarte Kaution bereit.
              Falls Sie die Kaution nicht zahlen möchten, können Sie alternativ Ihre
              Autoschlüssel als Sicherheit hinterlassen – sprechen Sie uns dazu einfach an.

            %s

            %s
            """.formatted(
                safe(buchung.getBuchungsNummer()),
                safe(buchung.getFahrzeug().getMarke()),
                safe(buchung.getFahrzeug().getModell()),
                mietzeitraum,
                safe(buchung.getGesamtPreis()),
                abholOderLieferInfo,
                buildFooter()
        );

        sendViaMailgun(buchung.getKundeEmail(), subject, body);
    }

    // ---------------------------------------------------
    // 3) Stornierungsbestätigung
    // ---------------------------------------------------
    public void sendStornoBestaetigung(Buchung buchung) {
        if (buchung.getKundeEmail() == null || buchung.getKundeEmail().isBlank()) return;

        LocalDateTime start = buchung.getStartDatum();
        LocalDateTime ende  = buchung.getEndDatum();

        String mietzeitraum = formatDateTimeRange(start, ende);

        String subject = "Ihre Stornierung bei Mazari";

        String body = """
            Guten Tag,

            wir bestätigen hiermit die Stornierung Ihrer Buchung bei Mazari Autovermietung.

            Buchungsnummer: %s
            Fahrzeug: %s %s
            Ursprünglicher Mietzeitraum: %s

            Ihre Buchung wurde erfolgreich storniert.

            Eine eventuelle Rückerstattung des Rechnungsbetrags wird in der Regel
            innerhalb von 5–10 Werktagen – abhängig von Ihrer Bank bzw. Ihrem
            Zahlungsanbieter – auf Ihrem ursprünglichen Zahlungsmittel gutgeschrieben.

            Es ist schade, dass Sie sich umentschieden haben – vielleicht
            dürfen wir Sie beim nächsten Mal wieder als Kunden begrüßen.

            %s
            """.formatted(
                safe(buchung.getBuchungsNummer()),
                safe(buchung.getFahrzeug().getMarke()),
                safe(buchung.getFahrzeug().getModell()),
                mietzeitraum,
                buildFooter()
        );

        sendViaMailgun(buchung.getKundeEmail(), subject, body);
    }

    // ---------------------------------------------------
    // Mailgun Sender (API)
    // ---------------------------------------------------
    private void sendViaMailgun(String to, String subject, String text) {
        String url = mailgunBaseUrl + "/v3/" + mailgunDomain + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("api", mailgunApiKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("from", from);
        form.add("to", to);
        form.add("subject", subject);
        form.add("text", text);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        ResponseEntity<String> res = restTemplate.postForEntity(url, request, String.class);

        if (!res.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Mailgun send failed: " + res.getStatusCode() + " -> " + res.getBody());
        }
    }

    // ---------------------------------------------------
    // Footer + Helper (DEIN CODE unverändert)
    // ---------------------------------------------------
    private String buildFooter() {
        return """
                Bei Fragen erreichen Sie uns jederzeit unter:

                E-Mail: info@mazariautovermietung.com
                Telefon: +49 152 02148802

                Mit freundlichen Grüßen
                Ihr Team Mazari Autovermietung
                """;
    }

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm 'Uhr'", Locale.GERMAN);

    private String formatDateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return "";
        return "%s %s – %s %s".formatted(
                formatDate(start),
                formatTime(start),
                formatDate(end),
                formatTime(end)
        );
    }

    private String formatDate(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DATE_FORMAT);
    }

    private String formatTime(LocalDateTime dt) {
        return dt == null ? "" : dt.format(TIME_FORMAT);
    }

    private String buildCustomerAddress(Buchung buchung) {
        if (buchung.getUser() != null) {
            var u = buchung.getUser();

            String nameLine   = (nullSafe(u.getFirstName()) + " " + nullSafe(u.getLastName())).trim();
            String streetLine = (nullSafe(u.getStreet()) + " " + nullSafe(u.getHouseNumber())).trim();
            String cityLine   = (nullSafe(u.getPostalCode()) + " " + nullSafe(u.getCity())).trim();
            String countryLine = nullSafe(u.getCountry());

            boolean hasAddress =
                    !streetLine.isBlank() || !cityLine.isBlank() || !countryLine.isBlank();

            if (hasAddress) {
                return """
                    %s
                    %s
                    %s
                    %s
                    """.formatted(
                        nameLine,
                        streetLine,
                        cityLine,
                        countryLine
                ).trim();
            }
        }

        String rStreet = nullSafe(buchung.getRechnungStrasse());
        String rPlz    = nullSafe(buchung.getRechnungPlz());
        String rOrt    = nullSafe(buchung.getRechnungOrt());
        String rLand   = nullSafe(buchung.getRechnungLand());
        String rName   = nullSafe(buchung.getRechnungName());

        boolean hasRechnungsAdresse =
                !rStreet.isBlank() || !rPlz.isBlank() || !rOrt.isBlank();

        if (hasRechnungsAdresse) {
            return """
                %s
                %s
                %s %s
                %s
                """.formatted(
                    rName,
                    rStreet,
                    rPlz,
                    rOrt,
                    rLand
            ).trim();
        }

        return "";
    }

    private String nullSafe(String v) {
        return v == null ? "" : v.trim();
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}

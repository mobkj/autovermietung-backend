package com.autovermietung.backend.service;

import com.autovermietung.backend.model.Buchung;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from}")
    private String from;

    // ---------------------------------------------------
    // 1) Test-Mail (kannst du behalten für lokale Tests)
    // ---------------------------------------------------
    public void sendTestEmail(String to) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("Test-E-Mail aus der Autovermietung-App");
        msg.setText("""
                Hey,

                das ist eine Test-E-Mail aus deiner Spring-Boot-Anwendung mit Mailgun.

                Wenn du diese Mail siehst, funktioniert dein SMTP-Setup. 🎉
                
                %s
                """.formatted(buildFooter()));

        mailSender.send(msg);
    }

    // ---------------------------------------------------
    // 2) Zahlungsbestätigung
    // ---------------------------------------------------
    public void sendPaymentConfirmation(Buchung buchung) {
        if (buchung.getKundeEmail() == null || buchung.getKundeEmail().isBlank()) {
            return;
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(buchung.getKundeEmail());
        msg.setSubject("Ihre Zahlung bei Mazari");

        LocalDateTime start = buchung.getStartDatum();
        LocalDateTime ende  = buchung.getEndDatum();

        String mietzeitraum = formatDateTimeRange(start, ende);
        String abholOderLieferInfo;

        if (buchung.isBringService()) {
            // Bringservice-Text
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
            // Abholung an Station
            abholOderLieferInfo = """
                Bitte holen Sie das Fahrzeug am %s um %s
                an folgender Adresse ab:

                Mazari Autovermietung
                MusterMann Straße 2
                65205 Wiesbaden-Erbenheim
                """.formatted(
                    formatDate(start),
                    formatTime(start)
            );
        }

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

        msg.setText(body);
        mailSender.send(msg);
    }


    // ---------------------------------------------------
    // 3) Stornierungsbestätigung
    // ---------------------------------------------------
    public void sendStornoBestaetigung(Buchung buchung) {
        if (buchung.getKundeEmail() == null || buchung.getKundeEmail().isBlank()) {
            return;
        }

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(buchung.getKundeEmail());
        msg.setSubject("Ihre Stornierung bei Mazari");

        LocalDateTime start = buchung.getStartDatum();
        LocalDateTime ende  = buchung.getEndDatum();

        String mietzeitraum = formatDateTimeRange(start, ende);

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

        msg.setText(body);
        mailSender.send(msg);
    }


    // ---------------------------------------------------
    // Footer + kleine Helper
    // ---------------------------------------------------
    private String buildFooter() {
        // TODO: Kontaktdaten anpassen
        return """
                Bei Fragen erreichen Sie uns jederzeit unter:

                E-Mail: info@mazari-autovermietung.de
                Telefon: +49 123 456789

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
        // 1) Versuch: Adresse aus dem verknüpften User (Registrierungsdaten)
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

        // 2) Versuch: Rechnungsadresse, die im Webhook eingefroren wurde
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

        // 3) Fallback: gar nichts Sinnvolles vorhanden
        return "";
    }


    private String nullSafe(String v) {
        return v == null ? "" : v.trim();
    }



    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}


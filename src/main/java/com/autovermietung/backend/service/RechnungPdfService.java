package com.autovermietung.backend.service;

import com.autovermietung.backend.model.Buchung;
import com.autovermietung.backend.model.Fahrzeug;
import com.autovermietung.backend.model.dto.BuchungPreisAntwortDTO;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RechnungPdfService {

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY);

    private final PreisBerechnungService preisBerechnungService;

    public byte[] createRechnungPdf(Buchung buchung) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, baos);

            doc.open();

            // ============ HEADER (Unternehmen) ============
            Paragraph firma = new Paragraph(
                    "Mazari Autovermietung\n" +
                            "Musterstraße 1\n" +
                            "65185 Wiesbaden\n\n",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)
            );
            doc.add(firma);

            doc.add(new Paragraph(" "));

            // ============ RECHNUNGSKOPF ============
            String rechnungsnr = buchung.getBuchungsNummer() != null
                    ? buchung.getBuchungsNummer()
                    : "B" + buchung.getId();

            Paragraph title = new Paragraph("Rechnung\n\n",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            doc.add(title);

            String rechnungsDatumStr = DATE_TIME_FMT.format(
                    buchung.getCreatedAt() != null ? buchung.getCreatedAt() : buchung.getStartDatum()
            );

            String buchungsDatumStr = buchung.getCreatedAt() != null
                    ? DATE_TIME_FMT.format(buchung.getCreatedAt())
                    : "-";

            Paragraph kopf = new Paragraph(
                    "Rechnungsnummer: " + rechnungsnr + "\n" +
                            "Rechnungsdatum: " + DATE_TIME_FMT.format(buchung.getCreatedAt() != null
                            ? buchung.getCreatedAt()
                            : buchung.getStartDatum()) + "\n" +
                            (buchung.getStripePaymentIntentId() != null
                                    ? "Zahlungsreferenz (Stripe PaymentIntent): " + buchung.getStripePaymentIntentId() + "\n"
                                    : "") +
                            (buchung.getStripeSessionId() != null
                                    ? "Stripe-Checkout-Session: " + buchung.getStripeSessionId() + "\n"
                                    : "") +
                            "\n",
                    FontFactory.getFont(FontFactory.HELVETICA, 11)
            );
            doc.add(kopf);


            // ============ RECHNUNGSADRESSE ============
            StringBuilder adr = new StringBuilder();
            if (buchung.getRechnungCompany() != null && !buchung.getRechnungCompany().isBlank()) {
                adr.append(buchung.getRechnungCompany()).append("\n");
            }
            if (buchung.getRechnungName() != null && !buchung.getRechnungName().isBlank()) {
                adr.append(buchung.getRechnungName()).append("\n");
            }
            if (buchung.getRechnungStrasse() != null && !buchung.getRechnungStrasse().isBlank()) {
                adr.append(buchung.getRechnungStrasse()).append("\n");
            }
            String plzOrt = "";
            if (buchung.getRechnungPlz() != null) plzOrt += buchung.getRechnungPlz() + " ";
            if (buchung.getRechnungOrt() != null) plzOrt += buchung.getRechnungOrt();
            if (!plzOrt.isBlank()) adr.append(plzOrt).append("\n");
            if (buchung.getRechnungLand() != null && !buchung.getRechnungLand().isBlank()) {
                adr.append(buchung.getRechnungLand()).append("\n");
            }

            Paragraph adrBlock = new Paragraph(adr.toString() + "\n",
                    FontFactory.getFont(FontFactory.HELVETICA, 11));
            doc.add(adrBlock);

            doc.add(new Paragraph(" "));

            // ============ LEISTUNGSBESCHREIBUNG (Textblock) ============
            Fahrzeug fzg = buchung.getFahrzeug();
            String fahrzeugText = fzg != null
                    ? ( (fzg.getMarke() != null ? fzg.getMarke() : "") + " " +
                    (fzg.getModell() != null ? fzg.getModell() : "") +
                    (fzg.getSerie() != null ? " " + fzg.getSerie() : "")
            ).trim()
                    : "Fahrzeug-ID " + (buchung.getFahrzeug() != null ? buchung.getFahrzeug().getId() : "-");

            Paragraph leistungHeader = new Paragraph("Leistungsbeschreibung\n",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
            doc.add(leistungHeader);

            Paragraph leistungMeta = new Paragraph(
                    "Mietfahrzeug: " + fahrzeugText + "\n" +
                            "Mietzeitraum: " + DATE_TIME_FMT.format(buchung.getStartDatum()) +
                            " bis " + DATE_TIME_FMT.format(buchung.getEndDatum()) + "\n" +
                    FontFactory.getFont(FontFactory.HELVETICA, 11)
            );
            doc.add(leistungMeta);

            // ============ PREISBERECHNUNG / POSITIONEN ============
            // Wir rekonstruieren das gewählte Km-Paket,
            // indem wir die drei Varianten durchrechnen und mit dem gespeicherten Gesamtpreis matchen.
            BuchungPreisAntwortDTO preisDto = null;
            int gebuchteKm = 150;
            BigDecimal targetBrutto = buchung.getGesamtPreis();

            int[] kmPakete = {150, 300, 500};
            for (int km : kmPakete) {
                BuchungPreisAntwortDTO candidate =
                        preisBerechnungService.berechnePreis(buchung, km, buchung.isBringService());
                if (targetBrutto != null
                        && candidate.getGesamtBrutto() != null
                        && candidate.getGesamtBrutto().setScale(2, RoundingMode.HALF_UP)
                        .compareTo(targetBrutto.setScale(2, RoundingMode.HALF_UP)) == 0) {
                    preisDto = candidate;
                    gebuchteKm = km;
                    break;
                }
                // Fallback, falls kein exaktes Match: letzter berechneter Wert
                preisDto = candidate;
            }

            if (preisDto == null) {
                // letzter Notfall: trotzdem irgendetwas rechnen
                preisDto = preisBerechnungService.berechnePreis(buchung, gebuchteKm, buchung.isBringService());
            }

            BigDecimal mietpreisNetto = nullSafe(preisDto.getMietpreisNetto());
            BigDecimal kmNetto = nullSafe(preisDto.getKmPaketAufpreisNetto());
            BigDecimal bringNetto = nullSafe(preisDto.getBringServiceNetto());
            BigDecimal gesamtNetto = nullSafe(preisDto.getGesamtNetto());
            BigDecimal mwstBetrag = nullSafe(preisDto.getMwstBetrag());
            BigDecimal gesamtBrutto = nullSafe(preisDto.getGesamtBrutto());
            BigDecimal mwstSatz = preisDto.getMwstSatz() != null
                    ? preisDto.getMwstSatz()
                    : new BigDecimal("0.19");

            int tage = preisDto.getTage();
            if (tage <= 0) {
                tage = 1;
            }


            BigDecimal mietEinzelpreis = mietpreisNetto.divide(
                    BigDecimal.valueOf(tage), 2, RoundingMode.HALF_UP
            );

            // ===== Tabelle: Positionen =====
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            PdfPTable posTable = new PdfPTable(5);
            posTable.setWidthPercentage(100);
            posTable.setWidths(new float[]{8, 40, 10, 20, 22});

            addHeaderCell(posTable, "Pos.", headerFont);
            addHeaderCell(posTable, "Beschreibung", headerFont);
            addHeaderCell(posTable, "Menge", headerFont);
            addHeaderCell(posTable, "Einzelpreis (netto)", headerFont);
            addHeaderCell(posTable, "Gesamt (netto)", headerFont);

            // Pos 1: Fahrzeugmiete
            addCell(posTable, "1", cellFont, Element.ALIGN_RIGHT);
            addCell(posTable, "Fahrzeugmiete für " + tage + " Tag(e)", cellFont, Element.ALIGN_LEFT);
            addCell(posTable, String.valueOf(tage), cellFont, Element.ALIGN_RIGHT);
            addCell(posTable, formatCurrency(mietEinzelpreis), cellFont, Element.ALIGN_RIGHT);
            addCell(posTable, formatCurrency(mietpreisNetto), cellFont, Element.ALIGN_RIGHT);

            // Pos 2: Kilometerpaket
            addCell(posTable, "2", cellFont, Element.ALIGN_RIGHT);
            addCell(posTable, "Kilometerpaket " + gebuchteKm + " km", cellFont, Element.ALIGN_LEFT);
            addCell(posTable, "1", cellFont, Element.ALIGN_RIGHT);
            addCell(posTable, formatCurrency(kmNetto), cellFont, Element.ALIGN_RIGHT);
            addCell(posTable, formatCurrency(kmNetto), cellFont, Element.ALIGN_RIGHT);

            // Pos 3: Bringservice (nur wenn > 0)
            if (bringNetto.compareTo(BigDecimal.ZERO) > 0) {
                addCell(posTable, "3", cellFont, Element.ALIGN_RIGHT);
                addCell(posTable, "Bringservice (Fahrzeuglieferung)", cellFont, Element.ALIGN_LEFT);
                addCell(posTable, "1", cellFont, Element.ALIGN_RIGHT);
                addCell(posTable, formatCurrency(bringNetto), cellFont, Element.ALIGN_RIGHT);
                addCell(posTable, formatCurrency(bringNetto), cellFont, Element.ALIGN_RIGHT);
            }

            doc.add(posTable);
            doc.add(new Paragraph(" "));

            // ===== Summen (rechtsbündig) =====
            PdfPTable sumTable = new PdfPTable(2);
            sumTable.setWidthPercentage(40);
            sumTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.setWidths(new float[]{50, 50});

            addSummaryRow(sumTable, "Zwischensumme (netto):", formatCurrency(gesamtNetto), cellFont, headerFont);
            String mwstLabel = "MwSt (" +
                    mwstSatz.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) +
                    " %):";
            addSummaryRow(sumTable, mwstLabel, formatCurrency(mwstBetrag), cellFont, headerFont);
            addSummaryRow(sumTable, "Gesamtbetrag (inkl. MwSt.):", formatCurrency(gesamtBrutto), cellFont, headerFont);

            doc.add(sumTable);
            doc.add(new Paragraph("\n"));

            // ============ FOOTER / HINWEISE ============
            Paragraph hinweis = new Paragraph(
                    "Vielen Dank für Ihre Buchung bei Mazari Autovermietung.\n" +
                            "Bitte bewahren Sie diese Rechnung für Ihre Unterlagen auf.\n\n",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10)
            );
            doc.add(hinweis);

            Paragraph agb = new Paragraph(
                    "Es gelten unsere allgemeinen Geschäftsbedingungen.",
                    FontFactory.getFont(FontFactory.HELVETICA, 8)
            );
            doc.add(agb);

            doc.close();

            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Fehler beim Erzeugen der Rechnung als PDF", e);
        }
    }

    // ===== Helpers =====

    private static void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5f);
        cell.setBackgroundColor(new Color(240, 244, 248));
        table.addCell(cell);
    }

    private static void addCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4f);
        table.addCell(cell);
    }

    private static void addSummaryRow(
            PdfPTable table,
            String label,
            String value,
            Font cellFont,
            Font labelFont
    ) {
        PdfPCell left = new PdfPCell(new Phrase(label, labelFont));
        left.setBorder(Rectangle.NO_BORDER);
        left.setHorizontalAlignment(Element.ALIGN_RIGHT);
        left.setPadding(3f);

        PdfPCell right = new PdfPCell(new Phrase(value, cellFont));
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setPadding(3f);

        table.addCell(left);
        table.addCell(right);
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String formatCurrency(BigDecimal v) {
        return String.format(Locale.GERMANY, "%,.2f €", v);
    }

    public byte[] createStornoRechnungPdf(Buchung buchung) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, baos);

            doc.open();

            // ============ HEADER (Unternehmen) ============
            Paragraph firma = new Paragraph(
                    "Mazari Autovermietung\nMusterstraße 1\n65185 Wiesbaden\n\n",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)
            );
            doc.add(firma);

            doc.add(new Paragraph(" "));

            // ============ STORNORECHNUNG KOPF ============
            String rechnungsnr = buchung.getBuchungsNummer() != null
                    ? buchung.getBuchungsNummer()
                    : "B" + buchung.getId();

            String stornoNummer = rechnungsnr + "-ST";

            Paragraph title = new Paragraph("Stornorechnung / Gutschrift\n\n",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            doc.add(title);

            // Rechnungsdatum = Stornodatum (oder jetzt, falls null)
            java.time.LocalDateTime stornoDatum =
                    buchung.getStorniertAm() != null ? buchung.getStorniertAm() : java.time.LocalDateTime.now();

            Paragraph kopf = new Paragraph(
                    "Stornorechnungsnummer: " + stornoNummer + "\n" +
                            "Bezug auf Rechnung / Buchung: " + rechnungsnr + "\n" +
                            "Stornodatum: " + DATE_TIME_FMT.format(stornoDatum) + "\n" +
                            (buchung.getStripePaymentIntentId() != null
                                    ? "Ursprüngliche Zahlungsreferenz (Stripe PaymentIntent): " + buchung.getStripePaymentIntentId() + "\n"
                                    : "") +
                            (buchung.getStripeRefundId() != null
                                    ? "Refund-Referenz (Stripe Refund): " + buchung.getStripeRefundId() + "\n"
                                    : "") +
                            "\n",
                    FontFactory.getFont(FontFactory.HELVETICA, 11)
            );
            doc.add(kopf);


            // ============ RECHNUNGSADRESSE ============
            StringBuilder adr = new StringBuilder();
            if (buchung.getRechnungCompany() != null && !buchung.getRechnungCompany().isBlank()) {
                adr.append(buchung.getRechnungCompany()).append("\n");
            }
            if (buchung.getRechnungName() != null && !buchung.getRechnungName().isBlank()) {
                adr.append(buchung.getRechnungName()).append("\n");
            }
            if (buchung.getRechnungStrasse() != null && !buchung.getRechnungStrasse().isBlank()) {
                adr.append(buchung.getRechnungStrasse()).append("\n");
            }
            String plzOrt = "";
            if (buchung.getRechnungPlz() != null) plzOrt += buchung.getRechnungPlz() + " ";
            if (buchung.getRechnungOrt() != null) plzOrt += buchung.getRechnungOrt();
            if (!plzOrt.isBlank()) adr.append(plzOrt).append("\n");
            if (buchung.getRechnungLand() != null && !buchung.getRechnungLand().isBlank()) {
                adr.append(buchung.getRechnungLand()).append("\n");
            }

            Paragraph adrBlock = new Paragraph(adr.toString() + "\n",
                    FontFactory.getFont(FontFactory.HELVETICA, 11));
            doc.add(adrBlock);

            doc.add(new Paragraph(" "));

            // ============ BUCHUNGSÜBERSICHT ============
            Fahrzeug fzg = buchung.getFahrzeug();

            String fahrzeugText = fzg != null
                    ? (fzg.getMarke() + " " + fzg.getModell() +
                    (fzg.getSerie() != null ? " " + fzg.getSerie() : ""))
                    : "Fahrzeug-ID " + (fzg != null ? fzg.getId() : buchung.getFahrzeug().getId());

            Paragraph buchHeader = new Paragraph("Buchungsübersicht\n",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
            doc.add(buchHeader);

            Paragraph buchInfo = new Paragraph(
                    "Mietfahrzeug: " + fahrzeugText + "\n" +
                            "Mietzeitraum: " + DATE_TIME_FMT.format(buchung.getStartDatum()) +
                            " bis " + DATE_TIME_FMT.format(buchung.getEndDatum()) + "\n" +
                            "Bringservice: " + (buchung.isBringService() ? "Ja" : "Nein") + "\n" +
                            "Buchungsnummer: " + rechnungsnr + "\n\n",
                    FontFactory.getFont(FontFactory.HELVETICA, 11)
            );
            doc.add(buchInfo);

            // ============ ZAHLUNG / STORNO: BERECHNUNG ============
            java.math.BigDecimal original = buchung.getGesamtPreis() != null
                    ? buchung.getGesamtPreis()
                    : java.math.BigDecimal.ZERO;

            java.math.BigDecimal refund = buchung.getRefundAmount() != null
                    ? buchung.getRefundAmount()
                    : java.math.BigDecimal.ZERO;

            java.math.BigDecimal fee = original.subtract(refund);
            if (fee.compareTo(java.math.BigDecimal.ZERO) < 0) {
                fee = java.math.BigDecimal.ZERO;
            }

            // Tage Abstand zwischen Stornodatum und Mietbeginn
            long daysUntilStart = 0;
            String regelText;
            if (buchung.getStartDatum() != null && stornoDatum != null) {
                java.time.LocalDate startDate = buchung.getStartDatum().toLocalDate();
                java.time.LocalDate stornoDate = stornoDatum.toLocalDate();
                daysUntilStart = java.time.temporal.ChronoUnit.DAYS.between(stornoDate, startDate);
            }

            if (daysUntilStart >= 10) {
                regelText = "Stornierung mehr als 10 Tage vor Mietbeginn – " +
                        "Erstattung des Mietpreises abzüglich Servicegebühr.";
            } else if (daysUntilStart >= 0) {
                regelText = "Stornierung innerhalb von 10 Tagen vor Mietbeginn – " +
                        "Erstattung von 50 % des Mietpreises abzüglich Servicegebühr.";
            } else {
                regelText = "Stornierung nach Mietbeginn – keine Erstattung laut Stornobedingungen.";
            }

            // ============ TABELLE: STORNOBERECHNUNG ============
            Paragraph stornoHeader = new Paragraph("Stornierung & Rückerstattung\n",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
            doc.add(stornoHeader);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(6f);
            table.setSpacingAfter(8f);
            table.setWidths(new float[]{2.5f, 1.5f});

            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            // Helfer für Zellen
            com.lowagie.text.pdf.PdfPCell cellLabel;
            com.lowagie.text.pdf.PdfPCell cellValue;

            // Ursprünglicher Betrag
            cellLabel = new com.lowagie.text.pdf.PdfPCell(new Phrase("Ursprünglicher Rechnungsbetrag (Brutto):", labelFont));
            cellLabel.setBorder(Rectangle.NO_BORDER);
            table.addCell(cellLabel);

            cellValue = new com.lowagie.text.pdf.PdfPCell(new Phrase(
                    String.format(Locale.GERMANY, "%.2f €", original), valueFont));
            cellValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellValue.setBorder(Rectangle.NO_BORDER);
            table.addCell(cellValue);

            // Servicegebühr
            cellLabel = new com.lowagie.text.pdf.PdfPCell(new Phrase("abzgl. Service-/Stornogebühr:", labelFont));
            cellLabel.setBorder(Rectangle.NO_BORDER);
            table.addCell(cellLabel);

            cellValue = new com.lowagie.text.pdf.PdfPCell(new Phrase(
                    String.format(Locale.GERMANY, "%.2f €", fee), valueFont));
            cellValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellValue.setBorder(Rectangle.NO_BORDER);
            table.addCell(cellValue);

            // Erstatteter Betrag
            cellLabel = new com.lowagie.text.pdf.PdfPCell(new Phrase("Erstatteter Betrag:", labelFont));
            cellLabel.setBorder(Rectangle.TOP);
            table.addCell(cellLabel);

            cellValue = new com.lowagie.text.pdf.PdfPCell(new Phrase(
                    String.format(Locale.GERMANY, "%.2f €", refund), valueFont));
            cellValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellValue.setBorder(Rectangle.TOP);
            table.addCell(cellValue);

            doc.add(table);

            // Kurzbeschreibung der angewendeten Regel
            Paragraph regelAbschnitt = new Paragraph(
                    "Angewendete Stornoregel:\n" +
                            regelText + "\n" +
                            "Stornierung am: " + DATE_TIME_FMT.format(stornoDatum) + "\n" +
                            "Mietbeginn am: " + DATE_TIME_FMT.format(buchung.getStartDatum()) + "\n" +
                            "Abstand bei Stornierung: " + daysUntilStart + " Tag(e) zum Mietbeginn.\n\n",
                    FontFactory.getFont(FontFactory.HELVETICA, 10)
            );
            doc.add(regelAbschnitt);

            // ============ FOOTER / HINWEISE ============
            Paragraph hinweis = new Paragraph(
                    "Die Rückerstattung erfolgt über denselben Zahlungsweg wie die ursprüngliche Zahlung.\n" +
                            "Die ausgewiesene Servicegebühr dient ausschließlich zur Deckung der Kosten\n" +
                            "unseres externen Zahlungsdienstleisters und stellt keinen zusätzlichen Gewinn dar.\n\n",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9)
            );
            doc.add(hinweis);

            Paragraph agb = new Paragraph(
                    "Es gelten die Stornobedingungen und Allgemeinen Geschäftsbedingungen (AGB) " +
                            "von Mazari Autovermietung.",
                    FontFactory.getFont(FontFactory.HELVETICA, 8)
            );
            doc.add(agb);

            doc.close();

            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Fehler beim Erzeugen der Stornorechnung als PDF", e);
        }
    }


}

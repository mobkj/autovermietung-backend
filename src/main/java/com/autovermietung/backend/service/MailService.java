package com.autovermietung.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class MailService {
/*c
    @Value("${mailgun.domain}")
    private String domain;

    @Value("${mailgun.apiKey}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendInvoiceMail(String to, String subject, String textBody, byte[] pdfBytes) {
        // Multipart-POST an Mailgun bauen
        // - from
        // - to
        // - subject
        // - text
        // - attachment (pdfBytes)
        // -> HTTP POST
        // Fehler sauber loggen, aber die Buchung nicht failen lassen, wenn Mail nicht klappt
    }
    */
}


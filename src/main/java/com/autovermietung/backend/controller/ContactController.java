// src/main/java/.../controller/ContactController.java
package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.dto.ContactRequest;
import com.autovermietung.backend.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {

    private final EmailService emailService;

    @PostMapping
    public ResponseEntity<?> send(@Valid @RequestBody ContactRequest req) {

        // Honeypot: wenn gefüllt -> still OK zurückgeben (Spam)
        if (req.getGotcha() != null && !req.getGotcha().isBlank()) {
            return ResponseEntity.ok(Map.of("ok", true));
        }

        emailService.sendContactMessage(
                req.getName(),
                req.getEmail(),
                req.getSubject(),
                req.getMessage()
        );

        return ResponseEntity.ok(Map.of("ok", true));
    }
}

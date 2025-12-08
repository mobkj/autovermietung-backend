package com.autovermietung.backend.controller;

import com.autovermietung.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test-email")
@RequiredArgsConstructor
public class TestEmailController {

    private final EmailService emailService;

    // GET /api/test-email?to=dein@mail.de
    @GetMapping
    public ResponseEntity<String> sendTest(@RequestParam String to) {
        emailService.sendTestEmail(to);
        return ResponseEntity.ok("Test-E-Mail wurde (theoretisch) gesendet an: " + to);
    }
}
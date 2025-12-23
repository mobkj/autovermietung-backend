package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.FahrzeugBild;
import com.autovermietung.backend.repository.FahrzeugBildRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fahrzeuge/bilder")
@RequiredArgsConstructor
public class FahrzeugBildController {

    private final FahrzeugBildRepository bildRepo;

    @Transactional(readOnly = true)
    @GetMapping("/{bildId}")
    public ResponseEntity<byte[]> bild(@PathVariable Long bildId) {
        FahrzeugBild bild = bildRepo.findById(bildId)
                .orElseThrow(() -> new RuntimeException("Bild nicht gefunden"));

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(bild.getContentType()))
                .body(bild.getData());
    }
}

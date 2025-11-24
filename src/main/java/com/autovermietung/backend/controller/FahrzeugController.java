package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.dto.FahrzeugAntwortDTO;
import com.autovermietung.backend.service.FahrzeugService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fahrzeuge")
@RequiredArgsConstructor
public class FahrzeugController {

    private final FahrzeugService service;

    /** Frontend bekommt nur AKTIVE Fahrzeuge */
    @GetMapping
    public List<FahrzeugAntwortDTO> alleAktiven() {
        return service.alleAktiven();
    }

    /** Frontend bekommt Fahrzeug nur, wenn AKTIV */
    @GetMapping("/{id}")
    public ResponseEntity<FahrzeugAntwortDTO> einsAktiv(@PathVariable Long id) {
        return service.einsAktiv(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

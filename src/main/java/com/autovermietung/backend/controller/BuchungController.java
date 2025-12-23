package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.dto.AdminTodoItemDTO;
import com.autovermietung.backend.model.dto.BuchungAnlegenDTO;
import com.autovermietung.backend.model.dto.BuchungAntwortDTO;
import com.autovermietung.backend.service.BuchungService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BuchungController {

    private final BuchungService buchungService;

    // =========================
    // PUBLIC: Buchung anlegen
    // =========================
    @PostMapping("/api/buchungen")
    public BuchungAntwortDTO anlegen(@Valid @RequestBody BuchungAnlegenDTO dto) {
        return buchungService.anlegen(dto);
    }

    // =========================
    // ADMIN: Buchungen nach Fahrzeug
    // =========================
    @GetMapping("/api/admin/buchungen/fahrzeug/{fahrzeugId}")
    public List<BuchungAntwortDTO> alleFuerFahrzeug(@PathVariable Long fahrzeugId) {
        return buchungService.alleFuerFahrzeug(fahrzeugId);
    }

    @GetMapping("/api/buchungen/me")
    public List<BuchungAntwortDTO> meineBuchungen() {
        return buchungService.alleFuerCurrentUser();
    }


    // =========================
// RESERVIERUNG ABBRECHEN (ohne Storno / Refund)
// =========================
    @DeleteMapping("/api/buchungen/{id}/abbrechen")
    public void abbrechenOhneStorno(@PathVariable Long id) {
        buchungService.abbrechenOhneStorno(id);
    }



    // =========================
    // ADMIN: Buchungen nach User
    // =========================
    @GetMapping("/api/admin/buchungen/user/{userId}")
    public List<BuchungAntwortDTO> alleFuerUser(@PathVariable Long userId) {
        return buchungService.alleFuerUser(userId);
    }

    // =========================
    // ADMIN: Fahrzeug blockieren (z.B. privat / Werkstatt)
    // =========================
    @PostMapping("/api/admin/buchungen/block")
    public BuchungAntwortDTO adminBlock(@Valid @RequestBody BuchungAnlegenDTO dto) {
        return buchungService.adminBlockieren(dto);
    }

    // PUBLIC / AUTH: Buchungen für ein Fahrzeug (für Kalender / Blocktage)
    @GetMapping("/api/buchungen/fahrzeug/{fahrzeugId}")
    public List<BuchungAntwortDTO> buchungenFuerFahrzeugKalender(@PathVariable Long fahrzeugId) {
        return buchungService.aktiveBuchungenFuerFahrzeug(fahrzeugId);
    }

    @GetMapping("/api/admin/buchungen/todo")
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<AdminTodoItemDTO> adminTodos(
            @RequestParam(name = "range", defaultValue = "week") String range
    ) {
        return buchungService.adminTodos(range);
    }


    // =========================
    // BUCHUNG STORNIEREN
    // =========================
    @PutMapping("/api/buchungen/{id}/stornieren")
    public BuchungAntwortDTO stornierenUser(@PathVariable Long id) {
        return buchungService.stornierenUser(id);
    }

    @PutMapping("/api/admin/buchungen/{id}/stornieren")
    public BuchungAntwortDTO stornierenAdmin(@PathVariable Long id) {
        return buchungService.stornieren(id);
    }
}

package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.Buchung;
import com.autovermietung.backend.model.Role;
import com.autovermietung.backend.model.User;
import com.autovermietung.backend.model.dto.BuchungPreisAntwortDTO;
import com.autovermietung.backend.repository.BuchungRepository;
import com.autovermietung.backend.service.PreisBerechnungService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/preise")
public class BuchungPreisController {

    private final BuchungRepository buchungRepo;
    private final PreisBerechnungService preisBerechnungService;

    /**
     * GET /api/preise/buchung/{id}?freieKmPaket=300
     *
     * - Frontend schickt:
     *   - Buchungs-ID
     *   - gewünschtes Km-Paket (z.B. 150, 300, 500)
     *
     * - Backend gibt vollständiges Preis-Breakdown (Netto/Brutto) zurück.
     * - Kann beliebig oft vom Frontend abgefragt werden → "reaktiv".
     */
    @GetMapping("/buchung/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_ADMIN')")
    public ResponseEntity<BuchungPreisAntwortDTO> berechnePreis(
            @PathVariable Long id,
            @RequestParam Integer freieKmPaket,
            @RequestParam(name = "bringService", defaultValue = "false") boolean bringService,
            Authentication authentication
    ) {
        Buchung buchung = buchungRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Buchung nicht gefunden"));

        String email = authentication.getName();
        User owner = buchung.getUser();

        boolean isOwner = owner != null && email.equalsIgnoreCase(owner.getEmail());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isOwner && !isAdmin) {
            throw new RuntimeException("Du darfst den Preis dieser Buchung nicht sehen.");
        }

        BuchungPreisAntwortDTO result =
                preisBerechnungService.berechnePreis(buchung, freieKmPaket, bringService);

        return ResponseEntity.ok(result);
    }


}

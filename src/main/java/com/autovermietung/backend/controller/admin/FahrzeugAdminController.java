package com.autovermietung.backend.controller.admin;

import com.autovermietung.backend.model.dto.FahrzeugAnlegenDTO;
import com.autovermietung.backend.model.dto.FahrzeugAntwortDTO;
import com.autovermietung.backend.model.dto.FahrzeugUpdateDTO;
import com.autovermietung.backend.service.FahrzeugService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/fahrzeuge")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
public class FahrzeugAdminController {

    private final FahrzeugService service;

    /** Admin legt ein Fahrzeug an */
    @PostMapping
    public FahrzeugAntwortDTO anlegen(@Valid @RequestBody FahrzeugAnlegenDTO dto) {
        return service.anlegen(dto);
    }

    /** Admin sieht alle Fahrzeuge (egal welcher Status) */
    @GetMapping
    public List<FahrzeugAntwortDTO> alle() {
        return service.alle();
    }

    /** Admin holt ein Fahrzeug per ID */
    @GetMapping("/{id}")
    public ResponseEntity<FahrzeugAntwortDTO> eins(@PathVariable Long id) {
        return service.eins(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Admin aktualisiert Stammdaten eines Fahrzeugs */
    @PutMapping("/{id}")
    public FahrzeugAntwortDTO update(@PathVariable Long id, @Valid @RequestBody FahrzeugUpdateDTO dto) {
        return service.update(id, dto);
    }

    // =========================================================================
    // BILDER: mehrere hinzufügen
    // =========================================================================

    /**
     * Mehrere Bilder zu einem Fahrzeug hinzufügen.
     * Frontend: FormData mit "files" (Array von Dateien)
     * POST /api/admin/fahrzeuge/{id}/bilder
     */
    @PostMapping("/{id}/bilder")
    public ResponseEntity<FahrzeugAntwortDTO> uploadBilder(
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files
    ) {
        try {
            if (files == null || files.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // optional: check, dass alles Bilder sind
            boolean hasNonImage = files.stream().anyMatch(f ->
                    f.getContentType() == null || !f.getContentType().startsWith("image/")
            );
            if (hasNonImage) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }

            FahrzeugAntwortDTO updated = service.bilderHinzufuegen(id, files);
            return ResponseEntity.ok(updated);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // =========================================================================
    // BILDER: ein einzelnes Bild ersetzen (z. B. Bild 2 von 4)
    // =========================================================================

    /**
     * Ein bestehendes Bild ersetzen.
     * PUT /api/admin/fahrzeuge/{fahrzeugId}/bilder/{bildId}
     * Frontend: FormData mit "file"
     */
    @PutMapping("/{fahrzeugId}/bilder/{bildId}")
    public ResponseEntity<FahrzeugAntwortDTO> updateBild(
            @PathVariable Long fahrzeugId,
            @PathVariable Long bildId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }

            FahrzeugAntwortDTO updated = service.bildErsetzen(fahrzeugId, bildId, file);
            return ResponseEntity.ok(updated);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (RuntimeException e) {
            // z. B. Fahrzeug oder Bild nicht gefunden
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

package com.autovermietung.backend.controller.admin;

import com.autovermietung.backend.model.dto.FahrzeugAnlegenDTO;
import com.autovermietung.backend.model.dto.FahrzeugAntwortDTO;
import com.autovermietung.backend.model.dto.FahrzeugUpdateDTO;
import com.autovermietung.backend.service.FahrzeugService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import java.util.List;

@RestController
@RequestMapping("/api/admin/fahrzeuge")
@RequiredArgsConstructor
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

    @PostMapping("/{id}/bild")
    public ResponseEntity<FahrzeugAntwortDTO> uploadBild(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // optional: content-type check (nur bilder)
            if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
            }

            // Ordner anlegen falls nicht da
            Path uploadDir = Paths.get("uploads/fahrzeuge");
            Files.createDirectories(uploadDir);

            // Dateiname bauen
            String original = Objects.requireNonNull(file.getOriginalFilename());
            String ext = original.contains(".")
                    ? original.substring(original.lastIndexOf("."))
                    : ".jpg";

            String filename = "fahrzeug-" + id + "-" + System.currentTimeMillis() + ext;

            // Datei speichern
            Path target = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), target);

            // URL in DB speichern
            String bildUrl = "/uploads/fahrzeuge/" + filename;
            FahrzeugAntwortDTO updated = service.updateBildUrl(id, bildUrl);

            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @PutMapping("/{id}")
    public FahrzeugAntwortDTO update(@PathVariable Long id, @RequestBody FahrzeugUpdateDTO dto) {
        return service.update(id, dto);
    }

    /** Admin holt ein Fahrzeug per ID */
    @GetMapping("/{id}")
    public ResponseEntity<FahrzeugAntwortDTO> eins(@PathVariable Long id) {
        return service.eins(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

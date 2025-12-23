package com.autovermietung.backend.service;

import com.autovermietung.backend.model.Fahrzeug;
import com.autovermietung.backend.model.FahrzeugBild;
import com.autovermietung.backend.model.FahrzeugStatus;
import com.autovermietung.backend.model.dto.FahrzeugAnlegenDTO;
import com.autovermietung.backend.model.dto.FahrzeugAntwortDTO;
import com.autovermietung.backend.model.dto.FahrzeugBildAntwortDTO;
import com.autovermietung.backend.model.dto.FahrzeugUpdateDTO;
import com.autovermietung.backend.repository.FahrzeugBildRepository;
import com.autovermietung.backend.repository.FahrzeugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import net.coobird.thumbnailator.Thumbnails;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FahrzeugService {

    private static final long MAX_UPLOAD_BYTES = 25L * 1024 * 1024;// 25 MB
    // ab dieser Größe wird komprimiert
    private static final long COMPRESS_THRESHOLD_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_WIDTH = 2560;
    private static final int MAX_HEIGHT = 2560;
    private final FahrzeugRepository repo;
    private final FahrzeugBildRepository bildRepo;


    // =========================================================================
    // FAHRZEUG ANLEGEN
    // =========================================================================
    public FahrzeugAntwortDTO anlegen(FahrzeugAnlegenDTO dto) {
        Fahrzeug f = new Fahrzeug();
        f.setMarke(dto.getMarke());
        f.setModell(dto.getModell());
        f.setSerie(dto.getSerie());
        f.setBaujahr(dto.getBaujahr());
        f.setPs(dto.getPs());
        f.setGetriebe(dto.getGetriebe());
        f.setKraftstoff(dto.getKraftstoff());
        f.setSitze(dto.getSitze());
        f.setTueren(dto.getTueren());
        f.setFarbe(dto.getFarbe());
        f.setNettoPreisProTag(dto.getNettoPreisProTag());
        f.setFreiKmProTag(dto.getFreiKmProTag());
        f.setKaution(dto.getKaution());

        Fahrzeug saved = repo.save(f);
        return toDTO(saved);
    }

    private byte[] toBytesWithOptionalCompression(MultipartFile file) throws IOException {
        long size = file.getSize();

        if (size <= COMPRESS_THRESHOLD_BYTES) {
            return file.getBytes();
        }

        try (var in = file.getInputStream();
             var out = new java.io.ByteArrayOutputStream()) {

            Thumbnails.of(in)
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .outputQuality(0.8f)
                    .toOutputStream(out);

            return out.toByteArray();
        }
    }



    // =========================================================================
    // LISTEN / EINZELNES FAHRZEUG
    // =========================================================================

    /** Für Admin: alle Fahrzeuge unabhängig vom Status */
    @Transactional(readOnly = true)
    public List<FahrzeugAntwortDTO> alle() {
        return repo.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /** Für Kunden/Frontend: nur AKTIVE Fahrzeuge */
    @Transactional(readOnly = true)
    public List<FahrzeugAntwortDTO> alleAktiven() {
        return repo.findAllByStatus(FahrzeugStatus.AKTIV).stream()
                .map(this::toDTO)
                .toList();
    }

    /** Für Admin: Fahrzeug per ID */
    @Transactional(readOnly = true)
    public Optional<FahrzeugAntwortDTO> eins(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    /** Für Kunden/Frontend: nur wenn AKTIV */
    @Transactional(readOnly = true)
    public Optional<FahrzeugAntwortDTO> einsAktiv(Long id) {
        return repo.findByIdAndStatus(id, FahrzeugStatus.AKTIV)
                .map(this::toDTO);
    }

    // =========================================================================
    // UPDATE FAHRZEUG-STAMMDATEN
    // =========================================================================

    @Transactional
    public FahrzeugAntwortDTO update(Long id, FahrzeugUpdateDTO dto) {
        Fahrzeug f = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Fahrzeug nicht gefunden"));

        f.setMarke(dto.getMarke());
        f.setModell(dto.getModell());
        f.setSerie(dto.getSerie());
        f.setBaujahr(dto.getBaujahr());
        f.setPs(dto.getPs());
        f.setGetriebe(dto.getGetriebe());
        f.setKraftstoff(dto.getKraftstoff());
        f.setSitze(dto.getSitze());
        f.setTueren(dto.getTueren());
        f.setFarbe(dto.getFarbe());
        f.setNettoPreisProTag(dto.getNettoPreisProTag());
        f.setFreiKmProTag(dto.getFreiKmProTag());
        f.setKaution(dto.getKaution());
        f.setStatus(dto.getStatus());

        Fahrzeug saved = repo.save(f);
        return toDTO(saved);
    }

    // =========================================================================
    // BILDER: NEUE HOCHLADEN (z. B. bis zu 4 Stück)
    // =========================================================================

    /**
     * Mehrere neue Bilder zu einem Fahrzeug hinzufügen.
     * Ordner: resources/static/fahrzeug{id}
     * Datei:  bild{sortierung}_{fahrzeugId}.ext  (z. B. bild1_5.jpg)
     *
     * Bild 1 (sortierung = 1) wird als Vorschau markiert.
     */
    @Transactional
    public FahrzeugAntwortDTO bilderHinzufuegen(Long fahrzeugId, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Keine Dateien übergeben");
        }

        Fahrzeug f = repo.findById(fahrzeugId)
                .orElseThrow(() -> new RuntimeException("Fahrzeug nicht gefunden"));

        int startIndex = f.getBilder().size() + 1;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            if (file.isEmpty()) throw new IllegalArgumentException("Eine der Dateien ist leer.");
            if (file.getSize() > MAX_UPLOAD_BYTES)
                throw new IllegalArgumentException("Eine Datei ist zu groß (max. 25 MB pro Bild).");

            int sortierung = startIndex + i;

            FahrzeugBild bild = new FahrzeugBild();
            bild.setFahrzeug(f);
            bild.setSortierung(sortierung);

            bild.setData(toBytesWithOptionalCompression(file));
            bild.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            bild.setOriginalFilename(file.getOriginalFilename());

            boolean istErstesBild = f.getBilder().isEmpty() && sortierung == 1;
            bild.setVorschau(istErstesBild);

            f.getBilder().add(bild);
        }

        Fahrzeug saved = repo.save(f);
        return toDTO(saved);
    }



    // =========================================================================
    // BILDER: EIN EINZELNES BILD ERSETZEN (z. B. 1 von 4)
    // =========================================================================

    /**
     * Ein bestehendes Bild ersetzen (z. B. nur Bild 3 austauschen).
     * Bild bleibt in derselben Sortierung (Position im Slider).
     */
    @Transactional
    public FahrzeugAntwortDTO bildErsetzen(Long fahrzeugId, Long bildId, MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Keine Datei übergeben.");
        if (file.getSize() > MAX_UPLOAD_BYTES)
            throw new IllegalArgumentException("Datei ist zu groß (max. 25 MB pro Bild).");

        Fahrzeug f = repo.findById(fahrzeugId)
                .orElseThrow(() -> new RuntimeException("Fahrzeug nicht gefunden"));

        FahrzeugBild bild = bildRepo.findById(bildId)
                .orElseThrow(() -> new RuntimeException("Bild nicht gefunden"));

        if (!bild.getFahrzeug().getId().equals(fahrzeugId)) {
            throw new RuntimeException("Bild gehört nicht zu diesem Fahrzeug");
        }

        bild.setData(toBytesWithOptionalCompression(file));
        bild.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        bild.setOriginalFilename(file.getOriginalFilename());

        bildRepo.save(bild);

        return toDTO(f);
    }





    private FahrzeugAntwortDTO toDTO(Fahrzeug f) {

        // HIER: Basis-URL, unter der die Bilder erreichbar sind
        List<FahrzeugBildAntwortDTO> bilder = bildRepo.findDtosByFahrzeugId(f.getId());

        return new FahrzeugAntwortDTO(
                f.getId(),
                f.getMarke(),
                f.getModell(),
                f.getSerie(),
                f.getBaujahr(),
                f.getPs(),
                f.getGetriebe(),
                f.getKraftstoff(),
                f.getSitze(),
                f.getTueren(),
                f.getFarbe(),
                f.getNettoPreisProTag(),
                f.getFreiKmProTag(),
                f.getKaution(),
                bilder,
                f.getStatus().name()
        );
    }

}

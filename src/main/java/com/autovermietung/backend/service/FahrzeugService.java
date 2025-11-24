package com.autovermietung.backend.service;

import com.autovermietung.backend.model.Fahrzeug;
import com.autovermietung.backend.model.FahrzeugStatus;
import com.autovermietung.backend.model.dto.FahrzeugAnlegenDTO;
import com.autovermietung.backend.model.dto.FahrzeugAntwortDTO;
import com.autovermietung.backend.model.dto.FahrzeugUpdateDTO;
import com.autovermietung.backend.repository.FahrzeugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FahrzeugService {

    private final FahrzeugRepository repo;

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

    /** Für Admin: alle Fahrzeuge unabhängig vom Status */
    public List<FahrzeugAntwortDTO> alle() {
        return repo.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /** Für Kunden/Frontend: nur AKTIVE Fahrzeuge */
    public List<FahrzeugAntwortDTO> alleAktiven() {
        return repo.findAllByStatus(FahrzeugStatus.AKTIV).stream()
                .map(this::toDTO)
                .toList();
    }

    /** Für Admin: Fahrzeug per ID */
    public Optional<FahrzeugAntwortDTO> eins(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    /** Für Kunden/Frontend: nur wenn AKTIV */
    public Optional<FahrzeugAntwortDTO> einsAktiv(Long id) {
        return repo.findByIdAndStatus(id, FahrzeugStatus.AKTIV)
                .map(this::toDTO);
    }

    public FahrzeugAntwortDTO updateBildUrl(Long id, String bildUrl) {
        Fahrzeug f = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Fahrzeug nicht gefunden"));

        f.setBildUrl(bildUrl);
        Fahrzeug saved = repo.save(f);

        return toDTO(saved);
    }
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
        if (dto.getBildUrl() != null && !dto.getBildUrl().isBlank()) {
            f.setBildUrl(dto.getBildUrl());
        }
        return toDTO(repo.save(f));
    }


    private FahrzeugAntwortDTO toDTO(Fahrzeug f) {
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
                f.getBildUrl(),
                f.getStatus().name()
        );
    }
}

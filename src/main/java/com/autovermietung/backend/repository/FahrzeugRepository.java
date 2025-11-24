package com.autovermietung.backend.repository;

import com.autovermietung.backend.model.Fahrzeug;
import com.autovermietung.backend.model.FahrzeugStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FahrzeugRepository extends JpaRepository<Fahrzeug, Long> {

    List<Fahrzeug> findAllByStatus(FahrzeugStatus status);

    Optional<Fahrzeug> findByIdAndStatus(Long id, FahrzeugStatus status);
}

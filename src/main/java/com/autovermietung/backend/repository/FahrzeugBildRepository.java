package com.autovermietung.backend.repository;

import com.autovermietung.backend.model.FahrzeugBild;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FahrzeugBildRepository extends JpaRepository<FahrzeugBild, Long> {

    List<FahrzeugBild> findAllByFahrzeugIdOrderBySortierungAsc(Long fahrzeugId);
}

package com.autovermietung.backend.repository;

import com.autovermietung.backend.model.Fahrzeug;
import com.autovermietung.backend.model.FahrzeugStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FahrzeugRepository extends JpaRepository<Fahrzeug, Long> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fahrzeug f where f.id = :id")
    Optional<Fahrzeug> findByIdForUpdate(@Param("id") Long id);

    List<Fahrzeug> findAllByStatus(FahrzeugStatus status);

    Optional<Fahrzeug> findByIdAndStatus(Long id, FahrzeugStatus status);
}

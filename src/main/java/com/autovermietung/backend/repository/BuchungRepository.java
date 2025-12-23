package com.autovermietung.backend.repository;

import com.autovermietung.backend.model.Buchung;
import com.autovermietung.backend.model.BuchungsStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BuchungRepository extends JpaRepository<Buchung, Long> {

    // Alle Buchungen für ein Fahrzeug (z.B. Admin / Übersicht)
    List<Buchung> findAllByFahrzeug_Id(Long fahrzeugId);

    // Alle Buchungen für einen User/Kunden
    List<Buchung> findAllByUser_Id(Long userId);

    // Optional: aktive (nicht abgelaufene) Reservierungen für Verfügbarkeitscheck
    List<Buchung> findAllByFahrzeug_IdAndStatusInAndReserviertBisAfter(
            Long fahrzeugId,
            List<BuchungsStatus> status,
            LocalDateTime now
    );

    List<Buchung> findAllByFahrzeug_IdAndStartDatumLessThanEqualAndEndDatumGreaterThanEqual(
            Long fahrzeugId,
            LocalDateTime endDatum,
            LocalDateTime startDatum
    );

    List<Buchung> findAllByFahrzeug_IdAndStatusIn(Long fahrzeugId, List<BuchungsStatus> status);

    List<Buchung> findByStatusAndStartDatumBetweenOrderByStartDatumAsc(
            BuchungsStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Buchung b where b.id = :id")
    Optional<Buchung> findByIdForUpdate(@Param("id") Long id);


}

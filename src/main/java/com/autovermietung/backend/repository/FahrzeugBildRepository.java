package com.autovermietung.backend.repository;

import com.autovermietung.backend.model.FahrzeugBild;
import com.autovermietung.backend.model.dto.FahrzeugBildAntwortDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FahrzeugBildRepository extends JpaRepository<FahrzeugBild, Long> {

    List<FahrzeugBild> findAllByFahrzeugIdOrderBySortierungAsc(Long fahrzeugId);

    @Query("""
        select new com.autovermietung.backend.model.dto.FahrzeugBildAntwortDTO(
            b.id,
            concat('/api/fahrzeuge/bilder/', b.id),
            b.vorschau,
            b.sortierung
        )
        from FahrzeugBild b
        where b.fahrzeug.id = :fahrzeugId
        order by b.sortierung asc
    """)
    List<FahrzeugBildAntwortDTO> findDtosByFahrzeugId(@Param("fahrzeugId") Long fahrzeugId);
}

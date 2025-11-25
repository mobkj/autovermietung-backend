package com.autovermietung.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fahrzeug_bild")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FahrzeugBild {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fahrzeug_id")
    private Fahrzeug fahrzeug;

    /** z. B. "bild1_5.jpg" – Ordner kommt über Fahrzeug-ID */
    private String dateiname;

    /** 1 = erstes Bild (Vorschau), 2, 3, … */
    private Integer sortierung;

    /** true = Vorschau-Bild (Cover) */
    private boolean vorschau;
}

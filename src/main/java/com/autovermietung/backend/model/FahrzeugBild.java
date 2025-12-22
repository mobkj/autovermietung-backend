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

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private String contentType; // "image/jpeg", "image/png", ...

    private String originalFilename; // optional

    private Integer sortierung;

    private boolean vorschau;
}

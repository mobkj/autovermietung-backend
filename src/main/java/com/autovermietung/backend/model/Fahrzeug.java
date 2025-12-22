package com.autovermietung.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="fahrzeug")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fahrzeug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String marke;
    private String modell;
    private String serie;

    private Integer baujahr;
    private Integer ps;
    private String getriebe;
    private String kraftstoff;

    private Integer sitze;
    private Integer tueren;
    private String farbe;

    @Column(nullable=false, precision=10, scale=2)
    private BigDecimal nettoPreisProTag;

    @Column(nullable=false)
    private Integer freiKmProTag;

    private BigDecimal kaution;

    @Enumerated(EnumType.STRING)
    private FahrzeugStatus status = FahrzeugStatus.AKTIV;

    @OneToMany(
            mappedBy = "fahrzeug",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("sortierung ASC")
    private List<FahrzeugBild> bilder = new ArrayList<>();
}



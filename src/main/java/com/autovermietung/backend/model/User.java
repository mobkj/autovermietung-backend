package com.autovermietung.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// @Entity sagt Spring das es eine Tabelle ist
@Entity
//Erstellt Tabelle in Postgres namens 'users'
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    // Persönliche Daten
    private String phone;

    // Adresse
    private String street;
    private String houseNumber;
    private String postalCode;
    private String city;
    private String country;

    // Professionelle Daten
    private String birthDate;
    private String driverLicenseNumber;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String companyName;


    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
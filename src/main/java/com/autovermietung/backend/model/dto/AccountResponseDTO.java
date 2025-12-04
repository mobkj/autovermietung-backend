package com.autovermietung.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor // Konstruktor mit allen Feldern
@NoArgsConstructor  // leerer Standardkonstruktor
public class AccountResponseDTO {

    private Long id;                // 1
    private String email;           // 2
    private String firstName;       // 3
    private String lastName;        // 4
    private String role;            // 5

    private String phone;           // 6
    private String street;          // 7
    private String houseNumber;     // 8
    private String postalCode;      // 9
    private String city;            // 10
    private String country;         // 11
    private String birthDate;       // 12
    private String driverLicenseNumber; // 13
    private String companyName;     // 14
}

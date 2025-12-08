package com.autovermietung.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminUserOverviewDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;

    private String birthday;
    private String driverLicenseNumber;

    // 🔽 neu dazu
    private String phone;
    private String street;
    private String houseNumber;
    private String postalCode;
    private String city;
    private String country;
    private String companyName;
}

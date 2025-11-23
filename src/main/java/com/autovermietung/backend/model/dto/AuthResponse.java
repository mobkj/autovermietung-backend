package com.autovermietung.backend.model.dto;

import com.autovermietung.backend.model.Role;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;

    private String phone;
    private String street;
    private String houseNumber;
    private String postalCode;
    private String city;
    private String country;
    private String birthDate;
    private String driverLicenseNumber;
    private String companyName;
}
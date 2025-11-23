package com.autovermietung.backend.model.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class RegisterRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String password;

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


// src/main/java/.../model/dto/ContactRequest.java
package com.autovermietung.backend.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactRequest {
    @NotBlank @Size(max = 120)
    private String name;

    @NotBlank @Email @Size(max = 180)
    private String email;

    @Size(max = 180)
    private String subject;

    @NotBlank @Size(max = 5000)
    private String message;

    // Honeypot (dein _gotcha)
    private String gotcha;
}

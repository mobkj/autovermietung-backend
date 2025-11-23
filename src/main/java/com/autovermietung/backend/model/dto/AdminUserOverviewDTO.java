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
}

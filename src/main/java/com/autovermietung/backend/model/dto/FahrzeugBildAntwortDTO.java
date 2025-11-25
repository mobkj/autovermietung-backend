package com.autovermietung.backend.model.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FahrzeugBildAntwortDTO {
    private Long id;
    private String url;       // komplette URL für das Frontend
    private boolean vorschau; // Cover?
    private Integer sortierung;
}

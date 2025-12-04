package com.autovermietung.backend.controller;

import com.autovermietung.backend.exception.ApiException;
import com.autovermietung.backend.model.User;
import com.autovermietung.backend.model.dto.AccountResponseDTO;
import com.autovermietung.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // =========================
    // GET /api/users/me
    // -> aktuellen User zurückgeben
    // =========================
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountResponseDTO> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException("Nicht eingeloggt.");
        }

        String emailFromToken = authentication.getName();

        User user = userRepository.findByEmail(emailFromToken)
                .orElseThrow(() -> new ApiException("Benutzer nicht gefunden."));

        AccountResponseDTO dto = toAccountResponseDTO(user);
        return ResponseEntity.ok(dto);
    }

    // =========================
    // PUT /api/users/me
    // -> eigenen Account / Adresse updaten
    // =========================
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountResponseDTO> updateCurrentUser(
            @RequestBody AccountResponseDTO dto,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException("Nicht eingeloggt.");
        }

        String emailFromToken = authentication.getName();

        User user = userRepository.findByEmail(emailFromToken)
                .orElseThrow(() -> new ApiException("Benutzer nicht gefunden."));

        // -------- Account-Daten (optional: nur wenn != null) --------
        if (dto.getFirstName() != null) {
            user.setFirstName(dto.getFirstName().trim());
        }
        if (dto.getLastName() != null) {
            user.setLastName(dto.getLastName().trim());
        }

        // ⚠ Email-Änderung – vorsichtig, aber hier erstmal erlaubt
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            user.setEmail(dto.getEmail().trim());
        }

        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone().trim());
        }

        if (dto.getBirthDate() != null) {
            user.setBirthDate(dto.getBirthDate().trim());
        }

        if (dto.getCompanyName() != null) {
            user.setCompanyName(dto.getCompanyName().trim());
        }

        if (dto.getDriverLicenseNumber() != null) {
            user.setDriverLicenseNumber(dto.getDriverLicenseNumber().trim());
        }

        // -------- Adresse --------
        if (dto.getStreet() != null) {
            user.setStreet(dto.getStreet().trim());
        }
        if (dto.getHouseNumber() != null) {
            user.setHouseNumber(dto.getHouseNumber().trim());
        }
        if (dto.getPostalCode() != null) {
            user.setPostalCode(dto.getPostalCode().trim());
        }
        if (dto.getCity() != null) {
            user.setCity(dto.getCity().trim());
        }
        if (dto.getCountry() != null) {
            user.setCountry(dto.getCountry().trim());
        }

        User saved = userRepository.save(user);

        AccountResponseDTO response = toAccountResponseDTO(saved);
        return ResponseEntity.ok(response);
    }

    // =========================
    // Helper-Mapping
    // =========================
    private AccountResponseDTO toAccountResponseDTO(User u) {
        return new AccountResponseDTO(
                u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getRole() != null ? u.getRole().name() : null,
                u.getPhone(),
                u.getStreet(),
                u.getHouseNumber(),
                u.getPostalCode(),
                u.getCity(),
                u.getCountry(),
                u.getBirthDate(),
                u.getDriverLicenseNumber(),
                u.getCompanyName()
        );
    }
}

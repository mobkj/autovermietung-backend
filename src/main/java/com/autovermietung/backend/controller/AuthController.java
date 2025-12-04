package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.User;
import com.autovermietung.backend.model.dto.AuthResponse;
import com.autovermietung.backend.model.dto.LoginRequest;
import com.autovermietung.backend.model.dto.RegisterRequest;
import com.autovermietung.backend.model.dto.VerifyPasswordRequest;
import com.autovermietung.backend.repository.UserRepository;
import com.autovermietung.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;


import java.util.Map;

// Erlaubt, dass die Klasse HTTP-Endpoints bereitstellt.
@RestController
//Alle Routen fangen mit /auth/... an.
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(
                request.getFirstName(),
                request.getLastName(),
                request.getEmail(),
                request.getPassword(),
                request.getPhone(),
                request.getStreet(),
                request.getHouseNumber(),
                request.getPostalCode(),
                request.getCity(),
                request.getCountry(),
                request.getBirthDate(),
                request.getDriverLicenseNumber(),
                request.getCompanyName()

        )
        );
    }


    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(
                request.getEmail(),
                request.getPassword()
        ));
    }

    @PostMapping("/verify-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> verifyPassword(
            @RequestBody VerifyPasswordRequest request,
            Authentication authentication
    ) {
        if (request == null || request.getPassword() == null || request.getPassword().isBlank()) {
            Map<String, Object> body = Map.of(
                    "success", false,
                    "message", "Passwort fehlt."
            );
            return ResponseEntity.badRequest().body(body);
        }

        String email = authentication.getName(); // kommt aus deinem JWT (Subject)

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Benutzer nicht gefunden."));

        boolean matches = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!matches) {
            Map<String, Object> body = Map.of(
                    "success", false,
                    "message", "Passwort ist falsch."
            );
            return ResponseEntity.status(401).body(body);
        }

        Map<String, Object> body = Map.of(
                "success", true
        );
        return ResponseEntity.ok(body);
    }


}

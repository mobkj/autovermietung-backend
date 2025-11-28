package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.User;
import com.autovermietung.backend.model.dto.AuthResponse;
import com.autovermietung.backend.model.dto.LoginRequest;
import com.autovermietung.backend.model.dto.RegisterRequest;
import com.autovermietung.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Erlaubt, dass die Klasse HTTP-Endpoints bereitstellt.
@RestController
//Alle Routen fangen mit /auth/... an.
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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

}

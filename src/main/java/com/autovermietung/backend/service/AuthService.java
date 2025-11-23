package com.autovermietung.backend.service;

import com.autovermietung.backend.exception.ApiException;
import com.autovermietung.backend.model.Role;
import com.autovermietung.backend.model.User;
import com.autovermietung.backend.model.dto.AuthResponse;
import com.autovermietung.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Regestrierung
    public AuthResponse register(String firstName, String lastName, String email, String password,
                                 String phone, String street, String houseNumber, String postalCode,
                                 String city, String country, String birthDate,
                                 String driverLicenseNumber, String companyName
    ) {

        if (userRepository.existsByEmail(email)) {
            throw new ApiException("Ein Benutzer mit dieser E-Mail existiert bereits.");
        }

        String hashedPassword = passwordEncoder.encode(password);

        User newUser = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .password(hashedPassword)
                .role(Role.CUSTOMER)
                .phone(phone)
                .street(street)
                .houseNumber(houseNumber)
                .postalCode(postalCode)
                .city(city)
                .country(country)
                .birthDate(birthDate)
                .driverLicenseNumber(driverLicenseNumber)
                .companyName(companyName)
                .build();

        User savedUser = userRepository.save(newUser);

        String token = jwtService.generateToken(savedUser);

        return AuthResponse.builder()
                .token(token)
                .userId(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .phone(savedUser.getPhone())
                .street(savedUser.getStreet())
                .houseNumber(savedUser.getHouseNumber())
                .postalCode(savedUser.getPostalCode())
                .city(savedUser.getCity())
                .country(savedUser.getCountry())
                .birthDate(savedUser.getBirthDate())
                .driverLicenseNumber(savedUser.getDriverLicenseNumber())
                .companyName(savedUser.getCompanyName())
                .build();
    }


    // Login
    public AuthResponse login(String email, String password) {

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            throw new ApiException("Benutzer wurde nicht gefunden.");
        }

        User user = userOpt.get();

        // Passwort prüfen
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ApiException("Das Passwort ist falsch.");
        }

        // Token generieren
        String token = jwtService.generateToken(user);

        // Antwort zurückgeben
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())

                .phone(user.getPhone())
                .street(user.getStreet())
                .houseNumber(user.getHouseNumber())
                .postalCode(user.getPostalCode())
                .city(user.getCity())
                .country(user.getCountry())
                .birthDate(user.getBirthDate())
                .driverLicenseNumber(user.getDriverLicenseNumber())
                .companyName(user.getCompanyName())
                .build();
    }
}
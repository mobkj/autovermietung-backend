package com.autovermietung.backend.service;

import com.autovermietung.backend.exception.ApiException;
import com.autovermietung.backend.model.Role;
import com.autovermietung.backend.model.User;
import com.autovermietung.backend.model.dto.AuthResponse;
import com.autovermietung.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // =======================================
    // 🔒 Login-Rate-Limiting (in-memory)
    // =======================================

    private static final int MAX_FAILED_ATTEMPTS = 6;
    private static final long WINDOW_MS = 60_000; // 1 Minute

    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    private static class LoginAttempt {
        int failedCount;
        long windowStart;
    }

    private LoginAttempt getLoginAttemptForEmail(String email) {
        return loginAttempts.computeIfAbsent(email, e -> {
            LoginAttempt la = new LoginAttempt();
            la.failedCount = 0;
            la.windowStart = System.currentTimeMillis();
            return la;
        });
    }

    private void assertLoginNotRateLimited(String email) {
        if (email == null) {
            return;
        }
        LoginAttempt attempt = getLoginAttemptForEmail(email);
        long now = System.currentTimeMillis();
        synchronized (attempt) {
            // Neues Fenster starten, wenn älter als WINDOW_MS
            if (now - attempt.windowStart > WINDOW_MS) {
                attempt.windowStart = now;
                attempt.failedCount = 0;
            }

            if (attempt.failedCount >= MAX_FAILED_ATTEMPTS) {
                throw new ApiException("Zu viele Login-Versuche. Bitte versuche es in einer Minute erneut.");
            }
        }
    }

    private void registerFailedLogin(String email) {
        if (email == null) {
            return;
        }
        LoginAttempt attempt = getLoginAttemptForEmail(email);
        long now = System.currentTimeMillis();
        synchronized (attempt) {
            if (now - attempt.windowStart > WINDOW_MS) {
                attempt.windowStart = now;
                attempt.failedCount = 0;
            }
            attempt.failedCount++;
        }
    }

    private void resetLoginAttempts(String email) {
        if (email == null) {
            return;
        }
        LoginAttempt attempt = getLoginAttemptForEmail(email);
        synchronized (attempt) {
            attempt.failedCount = 0;
            attempt.windowStart = System.currentTimeMillis();
        }
    }

    // =======================================
    // Registrierung
    // =======================================

    public AuthResponse register(String firstName, String lastName, String email, String password,
                                 String phone, String street, String houseNumber, String postalCode,
                                 String city, String country, String birthDate,
                                 String driverLicenseNumber, String companyName
    ) {

        String normalizedEmail = normalizeEmail(email);

        // 1) Validierung
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new ApiException("E-Mail ist erforderlich.");
        }

        // 2) Check: existiert schon?
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException("Es existiert bereits ein Account mit dieser E-Mail.");
        }

        String hashedPassword = passwordEncoder.encode(password);

        User newUser = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(normalizedEmail)
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

    // =======================================
    // Login
    // =======================================

    public AuthResponse login(String email, String password) {

        String normalizedEmail = normalizeEmail(email);

        // 🔒 Vor dem eigentlichen Login prüfen, ob rate-limited
        assertLoginNotRateLimited(normalizedEmail);

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            // Fehlversuch zählen (auch wenn der User nicht existiert → schützt gegen Bruteforce auf fremde Mails)
            registerFailedLogin(normalizedEmail);
            throw new ApiException("Benutzer wurde nicht gefunden.");
        }

        User user = userOpt.get();

        // Passwort prüfen
        if (!passwordEncoder.matches(password, user.getPassword())) {
            registerFailedLogin(normalizedEmail);
            throw new ApiException("Das Passwort ist falsch.");
        }

        // Erfolgreicher Login → Zähler zurücksetzen
        resetLoginAttempts(normalizedEmail);

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

    private String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

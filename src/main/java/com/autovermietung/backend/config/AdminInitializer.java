package com.autovermietung.backend.config;

import com.autovermietung.backend.model.Role;
import com.autovermietung.backend.model.User;
import com.autovermietung.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer {

    public AdminInitializer(UserRepository userRepository, PasswordEncoder encoder) {

        // Prüfen ob Admin existiert
        if (!userRepository.existsByEmail("admin@mazari.de")) {

            User admin = User.builder()
                    .firstName("Admin")
                    .lastName("Mazari")
                    .email("admin@mazari.de")
                    .password(encoder.encode("Admin123!")) // Passwort später anpassen!
                    .role(Role.ADMIN)
                    .build();

            userRepository.save(admin);

            System.out.println("⚠️ Admin account created: admin@mazari.de / Admin123!");
        }
    }
}

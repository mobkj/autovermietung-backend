package com.autovermietung.backend.config;

import com.autovermietung.backend.model.Role;
import com.autovermietung.backend.model.User;
import com.autovermietung.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class AdminBootstrap {

    @Value("${app.bootstrap.admin.email}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.password}")
    private String adminPassword;

    @Bean
    CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder encoder) {
        return args -> {

            // ✅ Admin
            if (!userRepository.existsByEmail(adminEmail)) {
                User admin = User.builder()
                        .firstName("Admin")
                        .lastName("Mazari")
                        .email(adminEmail)
                        .password(encoder.encode(adminPassword))
                        .driverLicenseNumber("L8KSJQOD")
                        .birthDate("10.09.2003")
                        .role(Role.ADMIN)
                        .build();

                userRepository.save(admin);
                System.out.println("✅ Admin created: " + adminEmail);
            }
        };
    }
}

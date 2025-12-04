package com.autovermietung.backend.config;

import java.util.List;


import com.autovermietung.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.stream.Collectors;

@EnableMethodSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // Öffentliche Endpunkte
                        .requestMatchers("/auth/login", "/auth/register").permitAll()
                        .requestMatchers("/uploads/**").permitAll()

                        // Stripe Webhook (muss ohne Auth erreichbar sein)
                        .requestMatchers("/api/stripe/webhook").permitAll()

                        // PUBLIC Fahrzeuge (ohne Login)
                        .requestMatchers(HttpMethod.GET, "/api/fahrzeuge/**").permitAll()

                        // Buchungen
                        // Jeder eingeloggte User darf Buchung anlegen
                        .requestMatchers(HttpMethod.POST, "/api/buchungen").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/buchungen/fahrzeug/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/buchungen/mw").authenticated()

                        // Admin Bereich
                        .requestMatchers(HttpMethod.POST, "/api/admin/**")
                        .hasAuthority("ROLE_ADMIN")

                        // alles andere → Login nötig (inkl. /auth/verify-password)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }




    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // localhost + Heimnetz (Handy/iPad)
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://localhost:8080",
                "http://192.168.178.128:5173",
                "https://ngan-unsettled-uninceptively.ngrok-free.dev"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);


        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

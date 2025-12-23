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
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

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


                        // ✅ CORS-Preflight immer erlauben
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()

                        // Öffentliche Endpunkte
                        .requestMatchers("/auth/login", "/auth/register").permitAll()
                        .requestMatchers("/uploads/**").permitAll()

                        // Stripe Webhook (muss ohne Auth erreichbar sein)
                        .requestMatchers("/api/stripe/webhook").permitAll()

                        // PUBLIC Fahrzeuge (ohne Login)
                        .requestMatchers(antMatcher("/api/fahrzeuge")).permitAll()
                        .requestMatchers(antMatcher("/api/fahrzeuge/**")).permitAll()


                        // Buchungen
                        // Jeder eingeloggte User darf Buchung anlegen
                        .requestMatchers(HttpMethod.POST, "/api/buchungen").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/buchungen/fahrzeug/**").authenticated()

                        // Admin Bereich
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")

                        .requestMatchers("/api/test-email/**").permitAll()

                        // alles andere → Login nötig (inkl. /auth/verify-password)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }




    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Frontend-Origins, die auf dein Backend zugreifen dürfen
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "https://autovermietung-frontend.vercel.app",
                "https://*.vercel.app" // optional: falls Preview-Deployments erlaubt sein sollen
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Authorization")); // optional, falls du Tokens in Response-Headers nutzt

        // Bei JWT im Authorization-Header brauchst du das NICHT zwingend.
        // Lass es nur true, wenn du wirklich Cookies/Session über CORS nutzt.
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }




    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

package com.autovermietung.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
@Component
public class VehicleImageStartupCleaner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        // Root-Ordner, den wir beim Start leeren wollen
        Path root = Paths.get("uploads/fahrzeuge");

        try {
            if (Files.exists(root)) {
                // Alle Dateien und Unterordner löschen (Kinder zuerst)
                System.out.println("FAhrzeug ordner gefunden");
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    log.warn("Konnte Datei/Ordner nicht löschen: {}", path, e);
                                }
                            });
                }
            }

            // Root-Folder wieder anlegen, damit dein Service weiter sauber arbeiten kann
            Files.createDirectories(root);
            log.info("Fahrzeug-Bilder-Ordner beim Start geleert und neu angelegt: {}", root.toAbsolutePath());

        } catch (IOException e) {
            log.error("Fehler beim Initialisieren des Fahrzeug-Bilder-Ordners", e);
        }
    }
}

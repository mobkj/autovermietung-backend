package com.autovermietung.backend.repository;

import com.autovermietung.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
// Repositories sind die Verbindung mit der Datenbank
public interface UserRepository extends JpaRepository<User, Long> {

    // Brauchen wir fürs login
    Optional<User> findByEmail(String email);

    // Braucen wir fürs regestrieren, email darf nicht doppelt exisitieren
    boolean existsByEmail(String email);
}

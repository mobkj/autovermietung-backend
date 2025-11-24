package com.autovermietung.backend.controller;

import com.autovermietung.backend.model.dto.AdminUserOverviewDTO;
import com.autovermietung.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Liste aller Benutzer (nur ADMIN)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminUserOverviewDTO> getAllUsers() {
        return userService.getAllUsersForAdmin();
    }

    /**
     * Einzelner Benutzer als DTO
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserOverviewDTO> getUserById(@PathVariable Long id) {
        return userService.getUserOverviewById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Benutzer löschen
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        return userService.deleteUser(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}

package com.autovermietung.backend.service;

import com.autovermietung.backend.model.dto.AdminUserOverviewDTO;
import com.autovermietung.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<AdminUserOverviewDTO> getAllUsersForAdmin() {
        return userRepository.findAll().stream()
                .map(u -> new AdminUserOverviewDTO(
                        u.getId(),
                        u.getFirstName(),
                        u.getLastName(),
                        u.getEmail(),
                        u.getRole().name()
                ))
                .toList();
    }

    public Optional<AdminUserOverviewDTO> getUserOverviewById(Long id) {
        return userRepository.findById(id)
                .map(u -> new AdminUserOverviewDTO(
                        u.getId(),
                        u.getFirstName(),
                        u.getLastName(),
                        u.getEmail(),
                        u.getRole().name()
                ));
    }

    public boolean deleteUser(Long id) {
        if (!userRepository.existsById(id)) return false;
        userRepository.deleteById(id);
        return true;
    }
}

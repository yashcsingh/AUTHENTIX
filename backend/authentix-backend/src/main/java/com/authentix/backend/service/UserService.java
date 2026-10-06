package com.authentix.backend.service;

import com.authentix.backend.dto.RegisterRequest;
import com.authentix.backend.entity.User;
import com.authentix.backend.exception.ConflictException;
import com.authentix.backend.exception.ResourceNotFoundException;
import com.authentix.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new ConflictException("Email already registered");
        }

        if (userRepository.existsByUserCode(request.getUserCode().trim())) {
            throw new ConflictException("User code already exists");
        }

        User user = new User();
        user.setUserCode(request.getUserCode().trim());
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());

        String role = request.getRole();
        if (role == null || role.isBlank()) {
            role = "CUSTOMER";
        }
        user.setRole(role.trim().toUpperCase());

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setQrCodeValue("AUTHENTIX://USER/" + user.getUserCode());
        user.setActive(true);

        return userRepository.save(user);
    }

    public User createUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ConflictException("Email already registered");
        }

        if (userRepository.existsByUserCode(user.getUserCode())) {
            throw new ConflictException("User code already exists");
        }

        if (user.getQrCodeValue() == null || user.getQrCodeValue().isBlank()) {
            user.setQrCodeValue("AUTHENTIX://USER/" + user.getUserCode());
        }

        if (user.getActive() == null) {
            user.setActive(true);
        }

        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            if (!user.getPasswordHash().startsWith("$2a$") && !user.getPasswordHash().startsWith("$2b$")) {
                user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
            }
        } else {
            user.setPasswordHash(passwordEncoder.encode("ChangeMe123!"));
        }

        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByUserCode(String userCode) {
        return userRepository.findByUserCode(userCode);
    }
}
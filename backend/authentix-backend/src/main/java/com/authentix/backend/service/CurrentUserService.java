package com.authentix.backend.service;

import com.authentix.backend.entity.User;
import com.authentix.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }

        String principal = authentication.getName();
        return userRepository.findByUserCode(principal)
                .or(() -> userRepository.findByEmail(principal));
    }

    public User getRequiredCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new RuntimeException("No authenticated user in context"));
    }
}

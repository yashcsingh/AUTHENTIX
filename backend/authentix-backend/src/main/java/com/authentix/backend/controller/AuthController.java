package com.authentix.backend.controller;

import com.authentix.backend.config.JwtService;
import com.authentix.backend.dto.AuthResponse;
import com.authentix.backend.dto.LoginRequest;
import com.authentix.backend.dto.RegisterRequest;
import com.authentix.backend.entity.User;
import com.authentix.backend.exception.BadRequestException;
import com.authentix.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            UserService userService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        if (request.getRole() != null && "ADMIN".equalsIgnoreCase(request.getRole().trim())) {
            throw new BadRequestException("Public registration cannot create an ADMIN account");
        }

        User user = userService.registerUser(request);
        String token = jwtService.generateToken(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new AuthResponse(
                        token,
                        user.getUserCode(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole()
                )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        String identifier = request.getEmail().trim();
        Optional<User> userOptional = userService.findByEmail(identifier.toLowerCase())
                .or(() -> userService.findByUserCode(identifier));

        if (userOptional.isEmpty()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userOptional.get();

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadCredentialsException("Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtService.generateToken(user);

        return ResponseEntity.ok(
                new AuthResponse(
                        token,
                        user.getUserCode(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole()
                )
        );
    }
}

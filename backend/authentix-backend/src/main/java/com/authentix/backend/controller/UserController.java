package com.authentix.backend.controller;

import com.authentix.backend.dto.LinkWalletRequest;
import com.authentix.backend.dto.RegisterRequest;
import com.authentix.backend.dto.UserResponse;
import com.authentix.backend.dto.VerifyWalletRequest;
import com.authentix.backend.dto.WalletChallengeRequest;
import com.authentix.backend.dto.WalletChallengeResponse;
import com.authentix.backend.entity.User;
import com.authentix.backend.service.CurrentUserService;
import com.authentix.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(
            UserService userService,
            CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid RegisterRequest request) {
        User user = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.fromEntity(user));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(
                userService.getAllUsers().stream()
                        .map(UserResponse::fromEntity)
                        .collect(Collectors.toList())
        );
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        User user = currentUserService.getRequiredCurrentUser();
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }

    @PutMapping("/me/wallet")
    public ResponseEntity<UserResponse> linkWallet(@RequestBody @Valid LinkWalletRequest request) {
        User user = currentUserService.getRequiredCurrentUser();
        User updated = userService.linkWallet(user, request.getWalletAddress());
        return ResponseEntity.ok(UserResponse.fromEntity(updated));
    }

    @PostMapping("/me/wallet/challenge")
    public ResponseEntity<WalletChallengeResponse> requestWalletChallenge(@RequestBody @Valid WalletChallengeRequest request) {
        User user = currentUserService.getRequiredCurrentUser();
        WalletChallengeResponse response = userService.generateWalletChallenge(user, request.getWalletAddress());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/wallet/verify")
    public ResponseEntity<UserResponse> verifyAndLinkWallet(@RequestBody @Valid VerifyWalletRequest request) {
        User user = currentUserService.getRequiredCurrentUser();
        User updated = userService.verifyAndLinkWallet(user, request);
        return ResponseEntity.ok(UserResponse.fromEntity(updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(UserResponse.fromEntity(userService.getUserById(id)));
    }
}
package com.authentix.backend.service;

import com.authentix.backend.crypto.SignatureVerifier;
import com.authentix.backend.dto.RegisterRequest;
import com.authentix.backend.dto.VerifyWalletRequest;
import com.authentix.backend.dto.WalletChallengeResponse;
import com.authentix.backend.entity.User;
import com.authentix.backend.entity.WalletVerificationChallenge;
import com.authentix.backend.exception.BadRequestException;
import com.authentix.backend.exception.ConflictException;
import com.authentix.backend.exception.ResourceNotFoundException;
import com.authentix.backend.repository.UserRepository;
import com.authentix.backend.repository.WalletVerificationChallengeRepository;
import com.authentix.backend.validation.WalletAddressValidator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Numeric;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final WalletVerificationChallengeRepository challengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserService(
            UserRepository userRepository,
            WalletVerificationChallengeRepository challengeRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.challengeRepository = challengeRepository;
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

    @Transactional
    public User linkWallet(User user, String rawWalletAddress) {
        if (user == null) {
            throw new BadRequestException("User cannot be null");
        }

        // Re-fetch managed user
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + user.getId()));

        if (managedUser.getWalletAddress() != null && !managedUser.getWalletAddress().isBlank()) {
            throw new ConflictException("Wallet address is already linked to this account and cannot be replaced");
        }

        String normalizedAddress = WalletAddressValidator.normalize(rawWalletAddress);

        if (userRepository.existsByWalletAddressIgnoreCase(normalizedAddress)) {
            throw new ConflictException("Wallet address is already registered by another user");
        }

        managedUser.setWalletAddress(normalizedAddress);
        return userRepository.save(managedUser);
    }

    @Transactional
    public WalletChallengeResponse generateWalletChallenge(User user, String rawWalletAddress) {
        if (user == null) {
            throw new BadRequestException("User cannot be null");
        }

        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + user.getId()));

        if (managedUser.getWalletAddress() != null && !managedUser.getWalletAddress().isBlank()) {
            throw new ConflictException("Wallet address is already linked to this account and cannot be replaced");
        }

        String normalizedAddress = WalletAddressValidator.normalize(rawWalletAddress);

        if (userRepository.existsByWalletAddressIgnoreCase(normalizedAddress)) {
            throw new ConflictException("Wallet address is already registered by another user");
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String nonce = Numeric.toHexStringNoPrefix(randomBytes);
        String challenge = "AUTHENTIX-VERIFY-" + nonce;
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        WalletVerificationChallenge challengeRecord = new WalletVerificationChallenge(
                managedUser,
                normalizedAddress,
                challenge,
                expiresAt
        );
        challengeRepository.save(challengeRecord);

        String message = "AUTHENTIX Verification: Please sign this challenge to verify ownership of wallet "
                + normalizedAddress + ": " + challenge;

        return new WalletChallengeResponse(challenge, normalizedAddress, expiresAt, message);
    }

    @Transactional
    public User verifyAndLinkWallet(User user, VerifyWalletRequest request) {
        if (user == null) {
            throw new BadRequestException("User cannot be null");
        }

        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + user.getId()));

        if (managedUser.getWalletAddress() != null && !managedUser.getWalletAddress().isBlank()) {
            throw new ConflictException("Wallet address is already linked to this account and cannot be replaced");
        }

        String normalizedAddress = WalletAddressValidator.normalize(request.getWalletAddress());

        WalletVerificationChallenge challengeRecord = challengeRepository.findByChallenge(request.getChallenge().trim())
                .orElseThrow(() -> new BadRequestException("Invalid or unknown verification challenge"));

        if (Boolean.TRUE.equals(challengeRecord.getUsed())) {
            throw new ConflictException("Challenge has already been used");
        }

        if (challengeRecord.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Challenge has expired");
        }

        if (!challengeRecord.getUser().getId().equals(managedUser.getId())) {
            throw new AccessDeniedException("Challenge does not belong to the authenticated user");
        }

        if (!challengeRecord.getWalletAddress().equalsIgnoreCase(normalizedAddress)) {
            throw new BadRequestException("Challenge was issued for a different wallet address");
        }

        String expectedMessage = "AUTHENTIX Verification: Please sign this challenge to verify ownership of wallet "
                + normalizedAddress + ": " + challengeRecord.getChallenge();

        boolean verified = SignatureVerifier.verify(expectedMessage, request.getSignature(), normalizedAddress)
                || SignatureVerifier.verify(challengeRecord.getChallenge(), request.getSignature(), normalizedAddress);

        if (!verified) {
            throw new BadRequestException("Cryptographic signature verification failed: signature does not match claimed wallet address");
        }

        challengeRecord.setUsed(true);
        challengeRepository.save(challengeRecord);

        if (userRepository.existsByWalletAddressIgnoreCase(normalizedAddress)) {
            throw new ConflictException("Wallet address is already registered by another user");
        }

        managedUser.setWalletAddress(normalizedAddress);
        return userRepository.save(managedUser);
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
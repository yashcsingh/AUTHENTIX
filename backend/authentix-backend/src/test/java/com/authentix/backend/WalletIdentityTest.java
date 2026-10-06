package com.authentix.backend;

import com.authentix.backend.config.JwtService;
import com.authentix.backend.crypto.SignatureVerifier;
import com.authentix.backend.dto.LinkWalletRequest;
import com.authentix.backend.dto.RegisterRequest;
import com.authentix.backend.dto.VerifyWalletRequest;
import com.authentix.backend.dto.WalletChallengeRequest;
import com.authentix.backend.entity.User;
import com.authentix.backend.entity.WalletVerificationChallenge;
import com.authentix.backend.repository.AssetRepository;
import com.authentix.backend.repository.AssetTransferRepository;
import com.authentix.backend.repository.UserRepository;
import com.authentix.backend.repository.WalletVerificationChallengeRepository;
import com.authentix.backend.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class WalletIdentityTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetTransferRepository transferRepository;

    @Autowired
    private WalletVerificationChallengeRepository challengeRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    // Standard test address and corresponding private key pair
    private static final String VALID_TEST_ADDRESS = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        challengeRepository.deleteAll();
        transferRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String signMessage(String message, ECKeyPair keyPair) {
        Sign.SignatureData signatureData = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), keyPair);
        byte[] sigBytes = new byte[65];
        System.arraycopy(signatureData.getR(), 0, sigBytes, 0, 32);
        System.arraycopy(signatureData.getS(), 0, sigBytes, 32, 32);
        System.arraycopy(signatureData.getV(), 0, sigBytes, 64, 1);
        return Numeric.toHexString(sigBytes);
    }

    // ==========================================
    // 1. BASIC WALLET MAPPING TESTS
    // ==========================================

    @Test
    @DisplayName("1. Authenticated user can link valid wallet")
    void testAuthenticatedUserCanLinkValidWallet() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W1", "Wallet User", "w1@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        LinkWalletRequest req = new LinkWalletRequest(VALID_TEST_ADDRESS);

        MvcResult result = mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(VALID_TEST_ADDRESS, json.get("walletAddress").asText());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(VALID_TEST_ADDRESS, updatedUser.getWalletAddress());
    }

    @Test
    @DisplayName("2. Unauthenticated user cannot link wallet")
    void testUnauthenticatedUserCannotLinkWallet() throws Exception {
        LinkWalletRequest req = new LinkWalletRequest(VALID_TEST_ADDRESS);

        mockMvc.perform(put("/api/users/me/wallet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. Invalid wallet prefix rejected with 400 Bad Request")
    void testInvalidWalletPrefixRejected() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W3", "User", "w3@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // Missing 0x prefix (40 chars)
        LinkWalletRequest req = new LinkWalletRequest("71C7656EC7ab88b098defB751B7401B5f6d8976F");

        mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("4. Invalid wallet length rejected with 400 Bad Request")
    void testInvalidWalletLengthRejected() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W4", "User", "w4@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // Too short (20 chars)
        LinkWalletRequest req = new LinkWalletRequest("0x71C7656EC7ab88b0");

        mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("5. Invalid hexadecimal characters rejected with 400 Bad Request")
    void testInvalidHexCharactersRejected() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W5", "User", "w5@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // Contains invalid hex chars 'ZZ'
        LinkWalletRequest req = new LinkWalletRequest("0xZZC7656EC7ab88b098defB751B7401B5f6d8976F");

        mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Empty or blank wallet address rejected with 400 Bad Request")
    void testEmptyWalletAddressRejected() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W6", "User", "w6@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        LinkWalletRequest req = new LinkWalletRequest("");

        mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("7. Duplicate wallet address rejected with 409 Conflict")
    void testDuplicateWalletAddressRejected() throws Exception {
        User user1 = userService.registerUser(new RegisterRequest("USR-W7-1", "User 1", "w71@example.com", "Password123!", "CUSTOMER"));
        User user2 = userService.registerUser(new RegisterRequest("USR-W7-2", "User 2", "w72@example.com", "Password123!", "CUSTOMER"));

        String token1 = jwtService.generateToken(user1);
        String token2 = jwtService.generateToken(user2);

        // User 1 links wallet
        mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkWalletRequest(VALID_TEST_ADDRESS))))
                .andExpect(status().isOk());

        // User 2 attempts to link the exact same wallet -> 409 Conflict
        MvcResult conflictResult = mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkWalletRequest(VALID_TEST_ADDRESS.toLowerCase()))))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(conflictResult.getResponse().getContentAsString());
        assertEquals("CONFLICT", json.get("error").asText());
    }

    @Test
    @DisplayName("8. Client-controlled userId cannot modify another user's wallet")
    void testClientCannotModifyAnotherUsersWallet() throws Exception {
        User victim = userService.registerUser(new RegisterRequest("USR-W8-V", "Victim", "w8v@example.com", "Password123!", "CUSTOMER"));
        User attacker = userService.registerUser(new RegisterRequest("USR-W8-A", "Attacker", "w8a@example.com", "Password123!", "CUSTOMER"));

        String attackerToken = jwtService.generateToken(attacker);

        // Attacker calls /me/wallet -> it ONLY updates attacker's wallet, never victim's
        mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkWalletRequest(VALID_TEST_ADDRESS))))
                .andExpect(status().isOk());

        User victimAfter = userRepository.findById(victim.getId()).orElseThrow();
        assertNull(victimAfter.getWalletAddress(), "Victim wallet must remain untouched");

        User attackerAfter = userRepository.findById(attacker.getId()).orElseThrow();
        assertEquals(VALID_TEST_ADDRESS, attackerAfter.getWalletAddress());
    }

    @Test
    @DisplayName("9. Existing linked wallet cannot be silently replaced -> 409 Conflict")
    void testExistingWalletCannotBeSilentlyReplaced() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W9", "User", "w9@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // First link succeeds
        mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkWalletRequest(VALID_TEST_ADDRESS))))
                .andExpect(status().isOk());

        // Second attempt to replace wallet address is rejected with 409 Conflict
        String secondAddress = "0x2c7536E3605D9C16a7a3D7b1898e529396a65c23";
        MvcResult result = mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkWalletRequest(secondAddress))))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("CONFLICT", json.get("error").asText());

        // Stored wallet remains the original one
        User current = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(VALID_TEST_ADDRESS, current.getWalletAddress());
    }

    @Test
    @DisplayName("10. Wallet address is returned by GET /api/users/me")
    void testWalletReturnedByGetMe() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W10", "User", "w10@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // Before linking -> walletAddress is null
        MvcResult res1 = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json1 = objectMapper.readTree(res1.getResponse().getContentAsString());
        assertTrue(json1.get("walletAddress").isNull());

        // Link wallet
        mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkWalletRequest(VALID_TEST_ADDRESS))))
                .andExpect(status().isOk());

        // After linking -> walletAddress is present
        MvcResult res2 = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json2 = objectMapper.readTree(res2.getResponse().getContentAsString());
        assertEquals(VALID_TEST_ADDRESS, json2.get("walletAddress").asText());
    }

    @Test
    @DisplayName("11. Password hash is never exposed in user API responses")
    void testPasswordHashRemainsHidden() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W11", "User", "w11@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        MvcResult result = mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkWalletRequest(VALID_TEST_ADDRESS))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(json.has("passwordHash"), "passwordHash must never be exposed");
        assertFalse(json.has("password"), "password must never be exposed");
    }

    @Test
    @DisplayName("12. Wallet normalization consistently converts to EIP-55 checksum")
    void testWalletNormalizationToEip55Checksum() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W12", "User", "w12@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // Submit all lowercase
        String lowercaseAddress = VALID_TEST_ADDRESS.toLowerCase();
        MvcResult result = mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkWalletRequest(lowercaseAddress))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        // Must be normalized to EIP-55 checksum format
        assertEquals(VALID_TEST_ADDRESS, json.get("walletAddress").asText());
    }

    @Test
    @DisplayName("13. Invalid EIP-55 checksum rejected when mixed-case address provided")
    void testInvalidEip55ChecksumRejected() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W13", "User", "w13@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // Corrupt the case of one character in VALID_TEST_ADDRESS ('71C' -> '71c' while other uppercase letters exist)
        String corruptedChecksumAddress = "0x71c7656EC7ab88b098defB751B7401B5f6d8976F";

        mockMvc.perform(put("/api/users/me/wallet")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LinkWalletRequest(corruptedChecksumAddress))))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // 2. CRYPTOGRAPHIC CHALLENGE & SIGNATURE TESTS
    // ==========================================

    @Test
    @DisplayName("14. Challenge generated with entropy, expiration, and user/wallet binding")
    void testChallengeGeneration() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W14", "User", "w14@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        WalletChallengeRequest req = new WalletChallengeRequest(VALID_TEST_ADDRESS);

        MvcResult result = mockMvc.perform(post("/api/users/me/wallet/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String challenge = json.get("challenge").asText();
        assertTrue(challenge.startsWith("AUTHENTIX-VERIFY-"));
        assertTrue(challenge.length() > 30, "Challenge must have sufficient cryptographic entropy");
        assertEquals(VALID_TEST_ADDRESS, json.get("walletAddress").asText());
        assertNotNull(json.get("expiresAt").asText());
        assertNotNull(json.get("message").asText());

        // Verify stored entity
        WalletVerificationChallenge saved = challengeRepository.findByChallenge(challenge).orElseThrow();
        assertEquals(user.getId(), saved.getUser().getId());
        assertEquals(VALID_TEST_ADDRESS, saved.getWalletAddress());
        assertFalse(saved.getUsed());
    }

    @Test
    @DisplayName("15. Expired challenge rejected with 400 Bad Request")
    void testExpiredChallengeRejected() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-W15", "User", "w15@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // Manually create expired challenge
        String challenge = "AUTHENTIX-VERIFY-expired123456";
        WalletVerificationChallenge expiredChallenge = new WalletVerificationChallenge(
                user,
                VALID_TEST_ADDRESS,
                challenge,
                LocalDateTime.now().minusMinutes(1)
        );
        challengeRepository.save(expiredChallenge);

        VerifyWalletRequest req = new VerifyWalletRequest(
                VALID_TEST_ADDRESS,
                challenge,
                "0x1111222233334444555566667777888899990000111122223333444455556666777788889999000011112222333344445555666677778888999900001111222233334444555566661b"
        );

        mockMvc.perform(post("/api/users/me/wallet/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("16. Challenge is single-use; replay is rejected with 409 Conflict")
    void testChallengeSingleUseReplayRejected() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String address = Keys.toChecksumAddress(Keys.getAddress(keyPair));

        User user = userService.registerUser(new RegisterRequest("USR-W16", "User", "w16@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // Request challenge
        MvcResult chalResult = mockMvc.perform(post("/api/users/me/wallet/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WalletChallengeRequest(address))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode chalJson = objectMapper.readTree(chalResult.getResponse().getContentAsString());
        String challenge = chalJson.get("challenge").asText();
        String message = chalJson.get("message").asText();

        // Sign challenge message
        String signature = signMessage(message, keyPair);

        // First verification -> 200 OK
        VerifyWalletRequest verifyReq = new VerifyWalletRequest(address, challenge, signature);
        mockMvc.perform(post("/api/users/me/wallet/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk());

        // Second verification with identical challenge and signature -> 409 Conflict
        mockMvc.perform(post("/api/users/me/wallet/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("17. Challenge bound to User A cannot be used by User B -> 403 Forbidden")
    void testChallengeBoundToCorrectUser() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String address = Keys.toChecksumAddress(Keys.getAddress(keyPair));

        User userA = userService.registerUser(new RegisterRequest("USR-W17-A", "User A", "w17a@example.com", "Password123!", "CUSTOMER"));
        User userB = userService.registerUser(new RegisterRequest("USR-W17-B", "User B", "w17b@example.com", "Password123!", "CUSTOMER"));

        String tokenA = jwtService.generateToken(userA);
        String tokenB = jwtService.generateToken(userB);

        // User A requests challenge
        MvcResult chalResult = mockMvc.perform(post("/api/users/me/wallet/challenge")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WalletChallengeRequest(address))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode chalJson = objectMapper.readTree(chalResult.getResponse().getContentAsString());
        String challenge = chalJson.get("challenge").asText();
        String message = chalJson.get("message").asText();
        String signature = signMessage(message, keyPair);

        // User B attempts to verify User A's challenge -> 403 Forbidden
        VerifyWalletRequest verifyReq = new VerifyWalletRequest(address, challenge, signature);
        mockMvc.perform(post("/api/users/me/wallet/verify")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("18. Challenge bound to Wallet A cannot authenticate Wallet B -> 400 Bad Request")
    void testChallengeBoundToCorrectWallet() throws Exception {
        ECKeyPair keyPairA = Keys.createEcKeyPair();
        ECKeyPair keyPairB = Keys.createEcKeyPair();
        String addressA = Keys.toChecksumAddress(Keys.getAddress(keyPairA));
        String addressB = Keys.toChecksumAddress(Keys.getAddress(keyPairB));

        User user = userService.registerUser(new RegisterRequest("USR-W18", "User", "w18@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        // User requests challenge for Wallet A
        MvcResult chalResult = mockMvc.perform(post("/api/users/me/wallet/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WalletChallengeRequest(addressA))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode chalJson = objectMapper.readTree(chalResult.getResponse().getContentAsString());
        String challenge = chalJson.get("challenge").asText();
        String message = chalJson.get("message").asText();

        // Sign with keypair B and submit address B with challenge issued for address A
        String signatureB = signMessage(message, keyPairB);
        VerifyWalletRequest verifyReq = new VerifyWalletRequest(addressB, challenge, signatureB);

        mockMvc.perform(post("/api/users/me/wallet/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("19. Valid cryptographic EIP-191 signature accepted and links wallet")
    void testValidCryptographicSignatureAccepted() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String address = Keys.toChecksumAddress(Keys.getAddress(keyPair));

        User user = userService.registerUser(new RegisterRequest("USR-W19", "User", "w19@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        MvcResult chalResult = mockMvc.perform(post("/api/users/me/wallet/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WalletChallengeRequest(address))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode chalJson = objectMapper.readTree(chalResult.getResponse().getContentAsString());
        String challenge = chalJson.get("challenge").asText();
        String message = chalJson.get("message").asText();

        String signature = signMessage(message, keyPair);

        MvcResult verifyResult = mockMvc.perform(post("/api/users/me/wallet/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyWalletRequest(address, challenge, signature))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode verifyJson = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        assertEquals(address, verifyJson.get("walletAddress").asText());

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(address, updatedUser.getWalletAddress());
    }

    @Test
    @DisplayName("20. Invalid cryptographic signature rejected with 400 Bad Request")
    void testInvalidCryptographicSignatureRejected() throws Exception {
        ECKeyPair keyPairReal = Keys.createEcKeyPair();
        ECKeyPair keyPairAttacker = Keys.createEcKeyPair();
        String claimedAddress = Keys.toChecksumAddress(Keys.getAddress(keyPairReal));

        User user = userService.registerUser(new RegisterRequest("USR-W20", "User", "w20@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        MvcResult chalResult = mockMvc.perform(post("/api/users/me/wallet/challenge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WalletChallengeRequest(claimedAddress))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode chalJson = objectMapper.readTree(chalResult.getResponse().getContentAsString());
        String challenge = chalJson.get("challenge").asText();
        String message = chalJson.get("message").asText();

        // Sign with attacker key instead of the key owning claimedAddress
        String forgedSignature = signMessage(message, keyPairAttacker);

        mockMvc.perform(post("/api/users/me/wallet/verify")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyWalletRequest(claimedAddress, challenge, forgedSignature))))
                .andExpect(status().isBadRequest());

        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertNull(unchangedUser.getWalletAddress(), "Wallet must not be linked when signature fails");
    }

    // ==========================================
    // 3. CONCURRENCY & RACE CONDITIONS
    // ==========================================

    @Test
    @DisplayName("21. Simultaneous wallet registration cannot associate one wallet with two users")
    void testConcurrentWalletRegistrationRace() throws Exception {
        User userA = userService.registerUser(new RegisterRequest("USR-W21-A", "User A", "w21a@example.com", "Password123!", "CUSTOMER"));
        User userB = userService.registerUser(new RegisterRequest("USR-W21-B", "User B", "w21b@example.com", "Password123!", "CUSTOMER"));

        String tokenA = jwtService.generateToken(userA);
        String tokenB = jwtService.generateToken(userB);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: User A registers VALID_TEST_ADDRESS
        executor.submit(() -> {
            try {
                startGate.await();
                MvcResult res = mockMvc.perform(put("/api/users/me/wallet")
                                .header("Authorization", "Bearer " + tokenA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LinkWalletRequest(VALID_TEST_ADDRESS))))
                        .andReturn();
                statusCodes.add(res.getResponse().getStatus());
            } catch (Exception e) {
                statusCodes.add(500);
            } finally {
                endGate.countDown();
            }
        });

        // Thread 2: User B simultaneously registers the exact same VALID_TEST_ADDRESS
        executor.submit(() -> {
            try {
                startGate.await();
                MvcResult res = mockMvc.perform(put("/api/users/me/wallet")
                                .header("Authorization", "Bearer " + tokenB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LinkWalletRequest(VALID_TEST_ADDRESS))))
                        .andReturn();
                statusCodes.add(res.getResponse().getStatus());
            } catch (Exception e) {
                statusCodes.add(500);
            } finally {
                endGate.countDown();
            }
        });

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Both concurrent registration threads must complete within timeout");
        assertEquals(2, statusCodes.size());

        // In a proper unique constraint and transaction isolation setup:
        // Exactly one should succeed with 200 OK and the other must be rejected with 409 Conflict
        assertTrue(statusCodes.contains(200), "One thread must succeed with 200 OK");
        assertTrue(statusCodes.contains(409), "The competing thread must be rejected with 409 Conflict");

        // Verify database integrity: exactly one user has the wallet address
        List<User> owners = userRepository.findAll().stream()
                .filter(u -> VALID_TEST_ADDRESS.equalsIgnoreCase(u.getWalletAddress()))
                .toList();
        assertEquals(1, owners.size(), "Exactly one user must own the wallet in the database");
    }
}

package com.authentix.backend;

import com.authentix.backend.config.JwtService;
import com.authentix.backend.dto.CreateAssetRequest;
import com.authentix.backend.dto.CreateTransferRequest;
import com.authentix.backend.dto.LoginRequest;
import com.authentix.backend.dto.RegisterRequest;
import com.authentix.backend.entity.Asset;
import com.authentix.backend.entity.AssetTransfer;
import com.authentix.backend.entity.User;
import com.authentix.backend.repository.AssetRepository;
import com.authentix.backend.repository.AssetTransferRepository;
import com.authentix.backend.repository.UserRepository;
import com.authentix.backend.service.AssetService;
import com.authentix.backend.service.AssetTransferService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ValidationAndExceptionHandlingTest {

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
    private UserService userService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetTransferService transferService;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        transferRepository.deleteAll();
        assetRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==========================================
    // REGISTRATION VALIDATION TESTS (1 to 9)
    // ==========================================

    @Test
    @DisplayName("1. Blank userCode produces 400 Bad Request with field error")
    void testBlankUserCode() throws Exception {
        RegisterRequest req = new RegisterRequest("", "Valid Name", "val@example.com", "Password123!", "CUSTOMER");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("VALIDATION_ERROR", json.get("error").asText());
        assertNotNull(json.get("validationErrors").get("userCode"));
    }

    @Test
    @DisplayName("2. Blank fullName produces 400 Bad Request with field error")
    void testBlankFullName() throws Exception {
        RegisterRequest req = new RegisterRequest("USR-001", "", "val@example.com", "Password123!", "CUSTOMER");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("VALIDATION_ERROR", json.get("error").asText());
        assertNotNull(json.get("validationErrors").get("fullName"));
    }

    @Test
    @DisplayName("3. Invalid email produces 400 Bad Request with field error")
    void testInvalidEmail() throws Exception {
        RegisterRequest req = new RegisterRequest("USR-001", "Valid Name", "not-an-email", "Password123!", "CUSTOMER");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("VALIDATION_ERROR", json.get("error").asText());
        assertNotNull(json.get("validationErrors").get("email"));
    }

    @Test
    @DisplayName("4. Blank password produces 400 Bad Request with field error")
    void testBlankPassword() throws Exception {
        RegisterRequest req = new RegisterRequest("USR-001", "Valid Name", "val@example.com", "", "CUSTOMER");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("VALIDATION_ERROR", json.get("error").asText());
        assertNotNull(json.get("validationErrors").get("password"));
    }

    @Test
    @DisplayName("5. Too-short password (<8 characters) produces 400 Bad Request with field error")
    void testTooShortPassword() throws Exception {
        RegisterRequest req = new RegisterRequest("USR-001", "Valid Name", "val@example.com", "short1", "CUSTOMER");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("VALIDATION_ERROR", json.get("error").asText());
        assertNotNull(json.get("validationErrors").get("password"));
    }

    @Test
    @DisplayName("6. Unsupported role produces 400 Bad Request with field error")
    void testUnsupportedRole() throws Exception {
        RegisterRequest req = new RegisterRequest("USR-001", "Valid Name", "val@example.com", "Password123!", "SUPERUSER");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("VALIDATION_ERROR", json.get("error").asText());
        assertNotNull(json.get("validationErrors").get("role"));
    }

    @Test
    @DisplayName("7. Public registration cannot create ADMIN role -> produces 400 Bad Request")
    void testPublicRegistrationCannotCreateAdmin() throws Exception {
        RegisterRequest req = new RegisterRequest("USR-ADMIN-REQ", "Admin Request", "admin.req@example.com", "Password123!", "ADMIN");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("BAD_REQUEST", json.get("error").asText());
        assertTrue(json.get("message").asText().contains("ADMIN"));
    }

    @Test
    @DisplayName("8. Duplicate email produces 409 Conflict")
    void testDuplicateEmailProducesConflict() throws Exception {
        RegisterRequest first = new RegisterRequest("USR-DUP-01", "User One", "dup.email@example.com", "Password123!", "CUSTOMER");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        RegisterRequest second = new RegisterRequest("USR-DUP-02", "User Two", "dup.email@example.com", "Password123!", "CUSTOMER");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(409, json.get("status").asInt());
        assertEquals("CONFLICT", json.get("error").asText());
        assertTrue(json.get("message").asText().contains("Email already registered"));
    }

    @Test
    @DisplayName("9. Duplicate userCode produces 409 Conflict")
    void testDuplicateUserCodeProducesConflict() throws Exception {
        RegisterRequest first = new RegisterRequest("USR-DUP-CODE", "User One", "first@example.com", "Password123!", "CUSTOMER");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        RegisterRequest second = new RegisterRequest("USR-DUP-CODE", "User Two", "second@example.com", "Password123!", "CUSTOMER");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(409, json.get("status").asInt());
        assertEquals("CONFLICT", json.get("error").asText());
        assertTrue(json.get("message").asText().contains("User code already exists"));
    }

    // ==========================================
    // LOGIN VALIDATION TESTS (10 to 12)
    // ==========================================

    @Test
    @DisplayName("10. Missing identifier on login produces 400 Bad Request")
    void testLoginMissingIdentifier() throws Exception {
        LoginRequest req = new LoginRequest("", "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("11. Missing password on login produces 400 Bad Request")
    void testLoginMissingPassword() throws Exception {
        LoginRequest req = new LoginRequest("user@example.com", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("12. Invalid credentials on login produces 401 Unauthorized")
    void testLoginInvalidCredentials() throws Exception {
        LoginRequest req = new LoginRequest("nonexistent@example.com", "WrongPassword123!");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(401, json.get("status").asInt());
    }

    // ==========================================
    // ASSET VALIDATION TESTS (13 to 18)
    // ==========================================

    @Test
    @DisplayName("13. Blank assetCode produces 400 Bad Request")
    void testBlankAssetCode() throws Exception {
        User creator = userService.registerUser(new RegisterRequest("USR-ASSET-01", "Creator", "cr1@example.com", "Password123!", "MANUFACTURER"));
        String token = jwtService.generateToken(creator);

        CreateAssetRequest req = new CreateAssetRequest("", "WATCH", "meta");

        MvcResult result = mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("VALIDATION_ERROR", json.get("error").asText());
        assertNotNull(json.get("validationErrors").get("assetCode"));
    }

    @Test
    @DisplayName("14. Blank assetType produces 400 Bad Request")
    void testBlankAssetType() throws Exception {
        User creator = userService.registerUser(new RegisterRequest("USR-ASSET-02", "Creator", "cr2@example.com", "Password123!", "MANUFACTURER"));
        String token = jwtService.generateToken(creator);

        CreateAssetRequest req = new CreateAssetRequest("ASSET-002", "", "meta");

        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("15. Oversized assetCode (>100 characters) produces 400 Bad Request")
    void testOversizedAssetCode() throws Exception {
        User creator = userService.registerUser(new RegisterRequest("USR-ASSET-03", "Creator", "cr3@example.com", "Password123!", "MANUFACTURER"));
        String token = jwtService.generateToken(creator);

        String longCode = "A".repeat(101);
        CreateAssetRequest req = new CreateAssetRequest(longCode, "WATCH", "meta");

        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("16. Unknown asset by ID produces 404 Not Found")
    void testUnknownAssetByIdProducesNotFound() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-ASSET-04", "User", "cr4@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        MvcResult result = mockMvc.perform(get("/api/assets/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(404, json.get("status").asInt());
        assertEquals("NOT_FOUND", json.get("error").asText());
    }

    @Test
    @DisplayName("17. Unknown asset by code produces 404 Not Found")
    void testUnknownAssetByCodeProducesNotFound() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-ASSET-05", "User", "cr5@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/assets/code/NON-EXISTENT-CODE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("18. Duplicate assetCode produces 409 Conflict")
    void testDuplicateAssetCodeProducesConflict() throws Exception {
        User creator = userService.registerUser(new RegisterRequest("USR-ASSET-06", "Creator", "cr6@example.com", "Password123!", "MANUFACTURER"));
        String token = jwtService.generateToken(creator);

        CreateAssetRequest first = new CreateAssetRequest("ASSET-DUP-001", "WATCH", "meta");
        mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        CreateAssetRequest duplicate = new CreateAssetRequest("ASSET-DUP-001", "WATCH", "meta");
        MvcResult result = mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(409, json.get("status").asInt());
        assertEquals("CONFLICT", json.get("error").asText());
        assertTrue(json.get("message").asText().contains("Asset code already exists"));
    }

    // ==========================================
    // TRANSFER VALIDATION TESTS (19 to 23)
    // ==========================================

    @Test
    @DisplayName("19. Invalid assetId (negative or null) produces 400 Bad Request")
    void testInvalidTransferAssetId() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-TRF-01", "Sender", "trf1@example.com", "Password123!", "MANUFACTURER"));
        String token = jwtService.generateToken(user);

        CreateTransferRequest req = new CreateTransferRequest(-5L, 2L, "TRF-CODE");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("20. Invalid receiver ID (negative) produces 400 Bad Request")
    void testInvalidTransferReceiverId() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-TRF-02", "Sender", "trf2@example.com", "Password123!", "MANUFACTURER"));
        String token = jwtService.generateToken(user);

        CreateTransferRequest req = new CreateTransferRequest(1L, -10L, "TRF-CODE");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("21. Missing transferCode produces 400 Bad Request")
    void testMissingTransferCode() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-TRF-03", "Sender", "trf3@example.com", "Password123!", "MANUFACTURER"));
        String token = jwtService.generateToken(user);

        CreateTransferRequest req = new CreateTransferRequest(1L, 2L, "");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("22. Unknown transfer produces 404 Not Found")
    void testUnknownTransferProducesNotFound() throws Exception {
        User user = userService.registerUser(new RegisterRequest("USR-TRF-04", "User", "trf4@example.com", "Password123!", "CUSTOMER"));
        String token = jwtService.generateToken(user);

        MvcResult result = mockMvc.perform(get("/api/transfers/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(404, json.get("status").asInt());
        assertEquals("NOT_FOUND", json.get("error").asText());
    }

    @Test
    @DisplayName("23. Confirming an already completed transfer produces 409 Conflict")
    void testConfirmAlreadyCompletedTransferProducesConflict() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-TRF-S", "Seller", "seller.trf@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-TRF-B", "Buyer", "buyer.trf@example.com", "Password123!", "CUSTOMER"));

        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-TRF-COMP", "PRODUCT", "meta"), seller);
        AssetTransfer transfer = transferService.createTransfer(asset.getId(), seller.getId(), buyer.getId(), "TRF-COMP-01");

        // Complete both confirmations
        transferService.confirmTransfer(transfer.getId(), seller.getId());
        transferService.confirmTransfer(transfer.getId(), buyer.getId());

        String buyerToken = jwtService.generateToken(buyer);

        // Attempting to confirm again once already completed
        MvcResult result = mockMvc.perform(post("/api/transfers/" + transfer.getId() + "/confirm")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(409, json.get("status").asInt());
        assertEquals("CONFLICT", json.get("error").asText());
        assertTrue(json.get("message").asText().contains("already completed"));
    }

    // ==========================================
    // ERROR RESPONSE SECURITY TESTS (24 to 28)
    // ==========================================

    @Test
    @DisplayName("24. Validation errors contain no stack trace")
    void testNoStackTraceInValidationErrors() throws Exception {
        RegisterRequest req = new RegisterRequest("", "", "", "", "");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("at org.springframework"));
        assertFalse(body.contains("at com.authentix"));
        assertFalse(body.contains("Exception"));
    }

    @Test
    @DisplayName("25. Error response contains no password hash")
    void testNoErrorResponseContainsPasswordHash() throws Exception {
        LoginRequest req = new LoginRequest("test@example.com", "WrongPassword123!");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("$2a$"));
        assertFalse(body.contains("$2b$"));
        assertFalse(body.contains("passwordHash"));
    }

    @Test
    @DisplayName("26. Error response contains no database credentials or JDBC strings")
    void testNoErrorResponseContainsDatabaseCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/999999")
                        .header("Authorization", "Bearer " + jwtService.generateToken(
                                userService.registerUser(new RegisterRequest("USR-SEC-01", "Sec User", "sec@example.com", "Password123!", "CUSTOMER")))))
                .andExpect(status().isNotFound())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("jdbc:"));
        assertFalse(body.contains("postgresql"));
        assertFalse(body.contains("password="));
    }

    @Test
    @DisplayName("27. Error response contains no private keys")
    void testNoErrorResponseContainsPrivateKeys() throws Exception {
        RegisterRequest req = new RegisterRequest("", "Name", "bad", "123", "BAD");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("BEGIN PRIVATE KEY"));
        assertFalse(body.contains("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"));
    }

    @Test
    @DisplayName("28. Error response does not expose internal Java class names")
    void testNoErrorResponseContainsJavaClassNames() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/888888")
                        .header("Authorization", "Bearer " + jwtService.generateToken(
                                userService.registerUser(new RegisterRequest("USR-SEC-02", "Sec User 2", "sec2@example.com", "Password123!", "CUSTOMER")))))
                .andExpect(status().isNotFound())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("ResourceNotFoundException"));
        assertFalse(body.contains("java.lang."));
    }

    // ==========================================
    // HEALTH ENDPOINT TEST
    // ==========================================

    @Test
    @DisplayName("Health endpoint /api/health is accessible without authentication")
    void testHealthEndpointAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}

package com.authentix.backend;

import com.authentix.backend.config.JwtService;
import com.authentix.backend.dto.LoginRequest;
import com.authentix.backend.dto.RegisterRequest;
import com.authentix.backend.entity.User;
import com.authentix.backend.repository.UserRepository;
import com.authentix.backend.service.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class SecurityFoundationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Password is hashed rather than stored plaintext")
    void testPasswordIsHashed() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "USR-HASH-01",
                "Alice Tester",
                "alice.hash@example.com",
                "PlaintextPass123!",
                "CUSTOMER"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("alice.hash@example.com").orElseThrow();

        assertNotEquals("PlaintextPass123!", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2a$") || user.getPasswordHash().startsWith("$2b$"));
        assertTrue(passwordEncoder.matches("PlaintextPass123!", user.getPasswordHash()));
    }

    @Test
    @DisplayName("2. Successful registration returns 201 and safe response without password")
    void testSuccessfulRegistration() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "USR-REG-01",
                "Bob Creator",
                "bob.reg@example.com",
                "SecureBobPass!",
                "MANUFACTURER"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(responseBody);

        assertNotNull(node.get("token"));
        assertEquals("USR-REG-01", node.get("userCode").asText());
        assertEquals("Bob Creator", node.get("fullName").asText());
        assertEquals("bob.reg@example.com", node.get("email").asText());
        assertEquals("MANUFACTURER", node.get("role").asText());
        assertNull(node.get("password"));
        assertNull(node.get("passwordHash"));
    }

    @Test
    @DisplayName("3. Duplicate registration (email and userCode) is rejected")
    void testDuplicateRegistrationRejected() throws Exception {
        RegisterRequest first = new RegisterRequest(
                "USR-DUP-01",
                "Original User",
                "dup@example.com",
                "Pass123!",
                "CUSTOMER"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Duplicate email
        RegisterRequest dupEmail = new RegisterRequest(
                "USR-DUP-02",
                "Second User",
                "dup@example.com",
                "Pass456!",
                "CUSTOMER"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupEmail)))
                .andExpect(status().isConflict());

        // Duplicate userCode
        RegisterRequest dupCode = new RegisterRequest(
                "USR-DUP-01",
                "Third User",
                "other@example.com",
                "Pass789!",
                "CUSTOMER"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupCode)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("4. Successful login returns a valid JWT")
    void testSuccessfulLoginReturnsJwt() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "USR-LOGIN-01",
                "Login User",
                "login@example.com",
                "MyPassword123!",
                "CUSTOMER"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("login@example.com", "MyPassword123!");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = node.get("token").asText();
        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals("USR-LOGIN-01", jwtService.extractUserCode(token));
    }

    @Test
    @DisplayName("5. Invalid password is rejected with 401 Unauthorized")
    void testInvalidPasswordRejected() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "USR-BADPASS-01",
                "User BadPass",
                "badpass@example.com",
                "CorrectPassword!",
                "CUSTOMER"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest wrongLogin = new LoginRequest("badpass@example.com", "WrongPassword!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongLogin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6. Inactive user cannot login")
    void testInactiveUserCannotLogin() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "USR-INACT-01",
                "Inactive User",
                "inactive@example.com",
                "Password123!",
                "CUSTOMER"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Deactivate user in DB
        User user = userRepository.findByEmail("inactive@example.com").orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("inactive@example.com", "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. Protected endpoint rejects unauthenticated request with 401")
    void testProtectedEndpointRejectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. Protected endpoint accepts valid JWT")
    void testProtectedEndpointAcceptsValidJwt() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "USR-AUTH-OK",
                "Auth User",
                "auth.ok@example.com",
                "Password123!",
                "CUSTOMER"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("auth.ok@example.com").orElseThrow();
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("9. Invalid JWT is rejected with 401")
    void testInvalidJwtRejected() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer invalid.token.signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("10. Expired JWT is rejected with 401")
    void testExpiredJwtRejected() throws Exception {
        User user = new User();
        user.setUserCode("USR-EXP-01");
        user.setFullName("Expired User");
        user.setEmail("expired@example.com");
        user.setRole("CUSTOMER");
        user.setPasswordHash(passwordEncoder.encode("Pass123!"));
        user.setQrCodeValue("AUTHENTIX://USER/USR-EXP-01");
        user.setActive(true);
        userRepository.save(user);

        // Generate token with negative duration (-10 seconds)
        String expiredToken = jwtService.generateToken(user, -10000L);

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. Authentication context contains the correct user")
    void testAuthenticationContextContainsCorrectUser() throws Exception {
        User user = new User();
        user.setUserCode("USR-CTX-01");
        user.setFullName("Context User");
        user.setEmail("context@example.com");
        user.setRole("ADMIN");
        user.setPasswordHash(passwordEncoder.encode("Pass123!"));
        user.setQrCodeValue("AUTHENTIX://USER/USR-CTX-01");
        user.setActive(true);
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        // Call protected endpoint to pass through JwtAuthenticationFilter and verify inside controller or test
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Validate directly that token claims map back to user via userCode
        String extractedUserCode = jwtService.extractUserCode(token);
        User resolvedUser = userRepository.findByUserCode(extractedUserCode).orElseThrow();
        assertEquals("USR-CTX-01", resolvedUser.getUserCode());
        assertEquals("context@example.com", resolvedUser.getEmail());
    }

    @Test
    @DisplayName("12. Role information is correctly represented in JWT and authority mapping")
    void testRoleInformationIsCorrectlyRepresented() throws Exception {
        RegisterRequest mfgReq = new RegisterRequest(
                "USR-ROLE-01",
                "Manufacturer One",
                "mfg@example.com",
                "Pass123!",
                "MANUFACTURER"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mfgReq)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail("mfg@example.com").orElseThrow();
        String token = jwtService.generateToken(user);

        assertEquals("MANUFACTURER", jwtService.extractRole(token));
    }
}

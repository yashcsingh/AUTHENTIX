package com.authentix.backend;

import com.authentix.backend.config.JwtService;
import com.authentix.backend.dto.CreateAssetRequest;
import com.authentix.backend.dto.CreateTransferRequest;
import com.authentix.backend.dto.RegisterRequest;
import com.authentix.backend.entity.Asset;
import com.authentix.backend.entity.User;
import com.authentix.backend.repository.AssetRepository;
import com.authentix.backend.repository.AssetTransferRepository;
import com.authentix.backend.repository.UserRepository;
import com.authentix.backend.service.AssetService;
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
class DtoApiHardeningTest {

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

    @Test
    @DisplayName("1 & 2. Create asset using authenticated user; creatorId query parameter is ignored")
    void testCreateAssetUsingAuthenticatedUser() throws Exception {
        User creator = userService.registerUser(new RegisterRequest(
                "USR-CREATOR-01",
                "Creator User",
                "creator@example.com",
                "Password123!",
                "MANUFACTURER"
        ));
        String token = jwtService.generateToken(creator);

        User attacker = userService.registerUser(new RegisterRequest(
                "USR-ATTACKER-01",
                "Attacker User",
                "attacker@example.com",
                "Password123!",
                "CUSTOMER"
        ));

        // Attempting to specify creatorId query param for another user (attacker)
        CreateAssetRequest request = new CreateAssetRequest(
                "ASSET-AUTH-001",
                "PHYSICAL_PRODUCT",
                "hash-meta-001"
        );

        MvcResult result = mockMvc.perform(post("/api/assets?creatorId=" + attacker.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());

        assertEquals("ASSET-AUTH-001", node.get("assetCode").asText());
        assertEquals("CREATED", node.get("status").asText());
        assertEquals("USR-CREATOR-01", node.get("creator").get("userCode").asText());
        assertEquals("USR-CREATOR-01", node.get("currentCustodian").get("userCode").asText());
    }

    @Test
    @DisplayName("3, 4, 5, 6, 7. Client cannot override creator, custodian, status, createdAt, or database ID")
    void testClientCannotOverrideServerFields() throws Exception {
        User user = userService.registerUser(new RegisterRequest(
                "USR-SAFE-01",
                "Safe User",
                "safe@example.com",
                "Password123!",
                "MANUFACTURER"
        ));
        String token = jwtService.generateToken(user);

        // Client attempts to send JSON with injected server fields
        String maliciousPayload = "{" +
                "\"assetCode\":\"ASSET-SAFE-001\"," +
                "\"assetType\":\"PHYSICAL_PRODUCT\"," +
                "\"metadataHash\":\"hash-001\"," +
                "\"id\":999999," +
                "\"status\":\"TRANSFERRED\"," +
                "\"currentCustodian\":{\"userCode\":\"USR-FAKE-01\"}," +
                "\"creator\":{\"userCode\":\"USR-FAKE-02\"}," +
                "\"createdAt\":\"2010-01-01T00:00:00\"" +
                "}";

        MvcResult result = mockMvc.perform(post("/api/assets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());

        assertNotEquals(999999L, node.get("id").asLong());
        assertEquals("CREATED", node.get("status").asText());
        assertEquals("USR-SAFE-01", node.get("creator").get("userCode").asText());
        assertEquals("USR-SAFE-01", node.get("currentCustodian").get("userCode").asText());
    }

    @Test
    @DisplayName("8 & 9. Transfer sender comes from authenticated user; fromUserId cannot be used for impersonation")
    void testTransferSenderComesFromAuthenticatedUser() throws Exception {
        User seller = userService.registerUser(new RegisterRequest(
                "USR-SELLER-01",
                "Seller User",
                "seller@example.com",
                "Password123!",
                "MANUFACTURER"
        ));
        User buyer = userService.registerUser(new RegisterRequest(
                "USR-BUYER-01",
                "Buyer User",
                "buyer@example.com",
                "Password123!",
                "CUSTOMER"
        ));
        User imposter = userService.registerUser(new RegisterRequest(
                "USR-IMPOSTER-01",
                "Imposter User",
                "imposter@example.com",
                "Password123!",
                "CUSTOMER"
        ));

        Asset asset = assetService.createAsset(
                new CreateAssetRequest("ASSET-TRF-001", "PHYSICAL_PRODUCT", "meta"),
                seller
        );

        // 1. Seller creates transfer to buyer
        String sellerToken = jwtService.generateToken(seller);
        CreateTransferRequest validReq = new CreateTransferRequest(
                asset.getId(),
                buyer.getId(),
                "TRF-CODE-001"
        );

        MvcResult result = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("TRF-CODE-001", node.get("transferCode").asText());
        assertEquals("USR-SELLER-01", node.get("fromUser").get("userCode").asText());
        assertEquals("USR-BUYER-01", node.get("toUser").get("userCode").asText());
        assertEquals("PENDING", node.get("status").asText());

        // 2. Imposter attempts to initiate transfer for the same asset
        String imposterToken = jwtService.generateToken(imposter);
        CreateTransferRequest imposterReq = new CreateTransferRequest(
                asset.getId(),
                buyer.getId(),
                "TRF-CODE-IMPOSTER"
        );

        // Must fail because imposter is not the current custodian
        MvcResult imposterRes = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + imposterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(imposterReq)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(imposterRes.getResponse().getContentAsString().contains("Sender is not the current custodian"));

        // 3. Impersonation attempt: Attacker attempts to inject fromUserId via body and query param
        String spoofPayload = "{" +
                "\"assetId\":" + asset.getId() + "," +
                "\"fromUserId\":" + seller.getId() + "," +
                "\"toUserId\":" + buyer.getId() + "," +
                "\"transferCode\":\"TRF-CODE-SPOOF\"" +
                "}";

        MvcResult spoofRes = mockMvc.perform(post("/api/transfers?fromUserId=" + seller.getId())
                        .header("Authorization", "Bearer " + imposterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofPayload))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(spoofRes.getResponse().getContentAsString().contains("Sender is not the current custodian"));
    }

    @Test
    @DisplayName("10 & 11. Transfer confirmation uses authenticated user; userId cannot impersonate another confirmer")
    void testTransferConfirmationUsesAuthenticatedUser() throws Exception {
        User seller = userService.registerUser(new RegisterRequest(
                "USR-CONF-SELLER",
                "Confirm Seller",
                "conf.seller@example.com",
                "Password123!",
                "MANUFACTURER"
        ));
        User buyer = userService.registerUser(new RegisterRequest(
                "USR-CONF-BUYER",
                "Confirm Buyer",
                "conf.buyer@example.com",
                "Password123!",
                "CUSTOMER"
        ));
        User outsider = userService.registerUser(new RegisterRequest(
                "USR-CONF-OUTSIDER",
                "Outsider",
                "conf.outsider@example.com",
                "Password123!",
                "CUSTOMER"
        ));

        Asset asset = assetService.createAsset(
                new CreateAssetRequest("ASSET-CONF-001", "PHYSICAL_PRODUCT", "meta"),
                seller
        );

        String sellerToken = jwtService.generateToken(seller);
        String buyerToken = jwtService.generateToken(buyer);
        String outsiderToken = jwtService.generateToken(outsider);

        // Create transfer
        MvcResult createRes = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransferRequest(asset.getId(), buyer.getId(), "TRF-CONF-001"))))
                .andExpect(status().isCreated())
                .andReturn();

        long transferId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // Outsider attempts to confirm (even passing seller's or buyer's userId in query params)
        MvcResult outsiderConfirm = mockMvc.perform(post("/api/transfers/" + transferId + "/confirm?userId=" + seller.getId())
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(outsiderConfirm.getResponse().getContentAsString().contains("User is not part of this transfer"));

        // Seller confirms
        MvcResult sellerConfirm = mockMvc.perform(post("/api/transfers/" + transferId + "/confirm")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sellerNode = objectMapper.readTree(sellerConfirm.getResponse().getContentAsString());
        assertTrue(sellerNode.get("fromUserConfirmed").asBoolean());
        assertFalse(sellerNode.get("toUserConfirmed").asBoolean());
        assertEquals("PENDING", sellerNode.get("status").asText());

        // Buyer confirms -> Status completes
        MvcResult buyerConfirm = mockMvc.perform(post("/api/transfers/" + transferId + "/confirm")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode buyerNode = objectMapper.readTree(buyerConfirm.getResponse().getContentAsString());
        assertTrue(buyerNode.get("fromUserConfirmed").asBoolean());
        assertTrue(buyerNode.get("toUserConfirmed").asBoolean());
        assertEquals("COMPLETED", buyerNode.get("status").asText());
    }

    @Test
    @DisplayName("12 & 13. Raw JPA entities and passwordHash are never returned by APIs")
    void testRawJpaEntitiesNotReturnedAndPasswordHashNeverExposed() throws Exception {
        User user = userService.registerUser(new RegisterRequest(
                "USR-CLEAN-01",
                "Clean User",
                "clean@example.com",
                "SuperSecretPassword!",
                "CUSTOMER"
        ));
        String token = jwtService.generateToken(user);

        // Test GET /api/users/me
        MvcResult meRes = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String meBody = meRes.getResponse().getContentAsString();
        assertFalse(meBody.contains("passwordHash"));
        assertFalse(meBody.contains("SuperSecretPassword!"));
        assertFalse(meBody.contains("hibernateLazyInitializer"));

        // Test GET /api/users/{id}
        MvcResult userRes = mockMvc.perform(get("/api/users/" + user.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String userBody = userRes.getResponse().getContentAsString();
        assertFalse(userBody.contains("passwordHash"));
        assertFalse(userBody.contains("hibernateLazyInitializer"));
    }

    @Test
    @DisplayName("14. Protected endpoints require valid JWT")
    void testProtectedEndpointsRequireJwt() throws Exception {
        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetCode\":\"A1\",\"assetType\":\"P1\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":1,\"toUserId\":2,\"transferCode\":\"T1\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/transfers/1/confirm"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("15. Appropriate roles are enforced: ADMIN role required for POST /api/users")
    void testRoleAuthorizationForAdminEndpoints() throws Exception {
        User customer = userService.registerUser(new RegisterRequest(
                "USR-CUST-ROLE",
                "Customer",
                "cust.role@example.com",
                "Pass123!",
                "CUSTOMER"
        ));
        String customerToken = jwtService.generateToken(customer);

        User admin = userService.registerUser(new RegisterRequest(
                "USR-ADMIN-ROLE",
                "Admin",
                "admin.role@example.com",
                "Pass123!",
                "ADMIN"
        ));
        String adminToken = jwtService.generateToken(admin);

        RegisterRequest newStaff = new RegisterRequest(
                "USR-STAFF-01",
                "Staff Member",
                "staff@example.com",
                "Pass123!",
                "CUSTOMER"
        );

        // Customer attempts admin creation -> 403 Forbidden
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStaff)))
                .andExpect(status().isForbidden());

        // Admin attempts admin creation -> 201 Created
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStaff)))
                .andExpect(status().isCreated());
    }
}

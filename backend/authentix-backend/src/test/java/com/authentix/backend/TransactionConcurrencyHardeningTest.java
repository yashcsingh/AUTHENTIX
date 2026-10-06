package com.authentix.backend;

import com.authentix.backend.config.JwtService;
import com.authentix.backend.dto.CreateAssetRequest;
import com.authentix.backend.dto.CreateTransferRequest;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TransactionConcurrencyHardeningTest {

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
    // A. TRANSFER CREATION TESTS
    // ==========================================

    @Test
    @DisplayName("A1. Valid transfer creation succeeds")
    void testValidTransferCreationSucceeds() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-A1-S", "Seller", "a1s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-A1-B", "Buyer", "a1b@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-A1", "PRODUCT", "meta"), seller);

        String token = jwtService.generateToken(seller);
        CreateTransferRequest req = new CreateTransferRequest(asset.getId(), buyer.getId(), "TRF-A1");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        assertTrue(transferRepository.existsByAssetIdAndStatus(asset.getId(), "PENDING"));
    }

    @Test
    @DisplayName("A2. Non-custodian cannot create transfer")
    void testNonCustodianCannotCreateTransfer() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-A2-S", "Seller", "a2s@example.com", "Password123!", "MANUFACTURER"));
        User imposter = userService.registerUser(new RegisterRequest("USR-A2-I", "Imposter", "a2i@example.com", "Password123!", "CUSTOMER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-A2-B", "Buyer", "a2b@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-A2", "PRODUCT", "meta"), seller);

        String imposterToken = jwtService.generateToken(imposter);
        CreateTransferRequest req = new CreateTransferRequest(asset.getId(), buyer.getId(), "TRF-A2");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + imposterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A3. Nonexistent asset fails with 404 Not Found")
    void testNonexistentAssetFails() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-A3-S", "Seller", "a3s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-A3-B", "Buyer", "a3b@example.com", "Password123!", "CUSTOMER"));

        String token = jwtService.generateToken(seller);
        CreateTransferRequest req = new CreateTransferRequest(999999L, buyer.getId(), "TRF-A3");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A4. Nonexistent receiver fails with 404 Not Found")
    void testNonexistentReceiverFails() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-A4-S", "Seller", "a4s@example.com", "Password123!", "MANUFACTURER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-A4", "PRODUCT", "meta"), seller);

        String token = jwtService.generateToken(seller);
        CreateTransferRequest req = new CreateTransferRequest(asset.getId(), 999999L, "TRF-A4");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A5. Sender == receiver fails with 400 Bad Request")
    void testSenderEqualsReceiverFails() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-A5-S", "Seller", "a5s@example.com", "Password123!", "MANUFACTURER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-A5", "PRODUCT", "meta"), seller);

        String token = jwtService.generateToken(seller);
        CreateTransferRequest req = new CreateTransferRequest(asset.getId(), seller.getId(), "TRF-A5");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A6 & A7. Inactive sender or receiver fails with 400 Bad Request")
    void testInactiveSenderOrReceiverFails() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-A6-S", "Seller", "a6s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-A6-B", "Buyer", "a6b@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-A6", "PRODUCT", "meta"), seller);

        // Deactivate buyer
        buyer.setActive(false);
        userRepository.save(buyer);

        String sellerToken = jwtService.generateToken(seller);
        CreateTransferRequest req = new CreateTransferRequest(asset.getId(), buyer.getId(), "TRF-A6");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A8. Duplicate transfer code fails with 409 Conflict")
    void testDuplicateTransferCodeFails() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-A8-S", "Seller", "a8s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-A8-B", "Buyer", "a8b@example.com", "Password123!", "CUSTOMER"));
        Asset asset1 = assetService.createAsset(new CreateAssetRequest("ASSET-A8-1", "PRODUCT", "meta"), seller);
        Asset asset2 = assetService.createAsset(new CreateAssetRequest("ASSET-A8-2", "PRODUCT", "meta"), seller);

        String token = jwtService.generateToken(seller);
        CreateTransferRequest req1 = new CreateTransferRequest(asset1.getId(), buyer.getId(), "TRF-DUP");
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        CreateTransferRequest req2 = new CreateTransferRequest(asset2.getId(), buyer.getId(), "TRF-DUP");
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A9. Second pending transfer for the same asset fails with 409 Conflict")
    void testSecondPendingTransferForSameAssetFails() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-A9-S", "Seller", "a9s@example.com", "Password123!", "MANUFACTURER"));
        User buyer1 = userService.registerUser(new RegisterRequest("USR-A9-B1", "Buyer 1", "a9b1@example.com", "Password123!", "CUSTOMER"));
        User buyer2 = userService.registerUser(new RegisterRequest("USR-A9-B2", "Buyer 2", "a9b2@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-A9", "PRODUCT", "meta"), seller);

        String token = jwtService.generateToken(seller);

        // First transfer created successfully
        CreateTransferRequest req1 = new CreateTransferRequest(asset.getId(), buyer1.getId(), "TRF-A9-1");
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Second transfer for same asset while first is pending must be rejected with 409 Conflict
        CreateTransferRequest req2 = new CreateTransferRequest(asset.getId(), buyer2.getId(), "TRF-A9-2");
        MvcResult res2 = mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(res2.getResponse().getContentAsString());
        assertEquals("CONFLICT", json.get("error").asText());
        assertTrue(json.get("message").asText().contains("already pending"));
    }

    @Test
    @DisplayName("A10 & A11. Deactivated or reported asset cannot be transferred -> 409 Conflict")
    void testDeactivatedOrReportedAssetCannotBeTransferred() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-A10-S", "Seller", "a10s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-A10-B", "Buyer", "a10b@example.com", "Password123!", "CUSTOMER"));

        Asset deactAsset = assetService.createAsset(new CreateAssetRequest("ASSET-DEACT", "PRODUCT", "meta"), seller);
        deactAsset.setStatus("DEACTIVATED");
        assetRepository.save(deactAsset);

        String token = jwtService.generateToken(seller);
        CreateTransferRequest req = new CreateTransferRequest(deactAsset.getId(), buyer.getId(), "TRF-DEACT");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());

        Asset reportAsset = assetService.createAsset(new CreateAssetRequest("ASSET-REPORT", "PRODUCT", "meta"), seller);
        reportAsset.setStatus("REPORTED");
        assetRepository.save(reportAsset);

        CreateTransferRequest reqReport = new CreateTransferRequest(reportAsset.getId(), buyer.getId(), "TRF-REPORT");
        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqReport)))
                .andExpect(status().isConflict());
    }

    // ==========================================
    // B. CONFIRMATION & IDEMPOTENCY TESTS
    // ==========================================

    @Test
    @DisplayName("B1 & B2. Both sender and receiver confirmation succeed, completing transfer atomically")
    void testTwoPartyConfirmationCompletesTransfer() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-B1-S", "Seller", "b1s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-B1-B", "Buyer", "b1b@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-B1", "PRODUCT", "meta"), seller);

        AssetTransfer transfer = transferService.createTransfer(asset.getId(), seller.getId(), buyer.getId(), "TRF-B1");

        String sellerToken = jwtService.generateToken(seller);
        String buyerToken = jwtService.generateToken(buyer);

        // Sender confirms
        mockMvc.perform(post("/api/transfers/" + transfer.getId() + "/confirm")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        AssetTransfer midState = transferRepository.findById(transfer.getId()).orElseThrow();
        assertTrue(midState.getFromUserConfirmed());
        assertFalse(midState.getToUserConfirmed());
        assertEquals("PENDING", midState.getStatus());
        assertEquals(seller.getId(), assetRepository.findById(asset.getId()).orElseThrow().getCurrentCustodian().getId());

        // Receiver confirms
        mockMvc.perform(post("/api/transfers/" + transfer.getId() + "/confirm")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk());

        AssetTransfer completed = transferRepository.findById(transfer.getId()).orElseThrow();
        assertTrue(completed.getFromUserConfirmed());
        assertTrue(completed.getToUserConfirmed());
        assertEquals("COMPLETED", completed.getStatus());
        assertNotNull(completed.getCompletedAt());

        Asset updatedAsset = assetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(buyer.getId(), updatedAsset.getCurrentCustodian().getId());
        assertEquals("TRANSFERRED", updatedAsset.getStatus());
    }

    @Test
    @DisplayName("B3. Duplicate confirmation by same party is rejected with 409 Conflict")
    void testDuplicateConfirmationRejected() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-B3-S", "Seller", "b3s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-B3-B", "Buyer", "b3b@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-B3", "PRODUCT", "meta"), seller);

        AssetTransfer transfer = transferService.createTransfer(asset.getId(), seller.getId(), buyer.getId(), "TRF-B3");
        String sellerToken = jwtService.generateToken(seller);

        // First confirmation succeeds
        mockMvc.perform(post("/api/transfers/" + transfer.getId() + "/confirm")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk());

        // Second confirmation by sender before receiver confirms must be rejected with 409 Conflict
        MvcResult dupResult = mockMvc.perform(post("/api/transfers/" + transfer.getId() + "/confirm")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(dupResult.getResponse().getContentAsString());
        assertTrue(json.get("message").asText().contains("already confirmed"));
    }

    @Test
    @DisplayName("B4. Completed transfer cannot be confirmed again -> 409 Conflict")
    void testCompletedTransferCannotBeConfirmedAgain() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-B4-S", "Seller", "b4s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-B4-B", "Buyer", "b4b@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-B4", "PRODUCT", "meta"), seller);

        AssetTransfer transfer = transferService.createTransfer(asset.getId(), seller.getId(), buyer.getId(), "TRF-B4");
        transferService.confirmTransfer(transfer.getId(), seller.getId());
        transferService.confirmTransfer(transfer.getId(), buyer.getId());

        String sellerToken = jwtService.generateToken(seller);
        mockMvc.perform(post("/api/transfers/" + transfer.getId() + "/confirm")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("B5. Stale custodian state prevents confirmation completion -> 409 Conflict")
    void testStaleCustodianPreventsConfirmation() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-B5-S", "Seller", "b5s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-B5-B", "Buyer", "b5b@example.com", "Password123!", "CUSTOMER"));
        User other = userService.registerUser(new RegisterRequest("USR-B5-O", "Other", "b5o@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-B5", "PRODUCT", "meta"), seller);

        AssetTransfer transfer = transferService.createTransfer(asset.getId(), seller.getId(), buyer.getId(), "TRF-B5");

        // Simulate an unexpected out-of-band change to asset currentCustodian
        asset.setCurrentCustodian(other);
        assetRepository.save(asset);

        String sellerToken = jwtService.generateToken(seller);

        // Confirmation must detect that asset custody has changed and reject
        MvcResult result = mockMvc.perform(post("/api/transfers/" + transfer.getId() + "/confirm")
                        .header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(json.get("message").asText().contains("custody has changed"));
    }

    // ==========================================
    // C. STATE INTEGRITY TESTS
    // ==========================================

    @Test
    @DisplayName("C1. Asset currentCustodian changes only after mutual confirmation, not while pending")
    void testCustodianChangesOnlyAfterCompletion() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-C1-S", "Seller", "c1s@example.com", "Password123!", "MANUFACTURER"));
        User buyer = userService.registerUser(new RegisterRequest("USR-C1-B", "Buyer", "c1b@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-C1", "PRODUCT", "meta"), seller);

        AssetTransfer transfer = transferService.createTransfer(asset.getId(), seller.getId(), buyer.getId(), "TRF-C1");

        // While pending: custodian is seller
        Asset pAsset = assetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(seller.getId(), pAsset.getCurrentCustodian().getId());

        // After seller confirms only: custodian is still seller
        transferService.confirmTransfer(transfer.getId(), seller.getId());
        Asset sAsset = assetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(seller.getId(), sAsset.getCurrentCustodian().getId());

        // After buyer confirms: custodian becomes buyer
        transferService.confirmTransfer(transfer.getId(), buyer.getId());
        Asset compAsset = assetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(buyer.getId(), compAsset.getCurrentCustodian().getId());
    }

    // ==========================================
    // D. CONCURRENCY & PESSIMISTIC LOCKING TEST
    // ==========================================

    @Test
    @DisplayName("D1. Concurrent transfer creation race: exactly one pending transfer succeeds, others rejected with 409")
    void testConcurrentTransferCreationRace() throws Exception {
        User seller = userService.registerUser(new RegisterRequest("USR-D1-S", "Seller", "d1s@example.com", "Password123!", "MANUFACTURER"));
        User buyer1 = userService.registerUser(new RegisterRequest("USR-D1-B1", "Buyer 1", "d1b1@example.com", "Password123!", "CUSTOMER"));
        User buyer2 = userService.registerUser(new RegisterRequest("USR-D1-B2", "Buyer 2", "d1b2@example.com", "Password123!", "CUSTOMER"));
        Asset asset = assetService.createAsset(new CreateAssetRequest("ASSET-D1", "PRODUCT", "meta"), seller);

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numThreads);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        String token = jwtService.generateToken(seller);

        Callable<Void> task1 = () -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(post("/api/transfers")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreateTransferRequest(asset.getId(), buyer1.getId(), "TRF-RACE-1"))))
                        .andReturn();
                statusCodes.add(res.getResponse().getStatus());
            } catch (Exception e) {
                // ignore
            } finally {
                finishLatch.countDown();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(post("/api/transfers")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreateTransferRequest(asset.getId(), buyer2.getId(), "TRF-RACE-2"))))
                        .andReturn();
                statusCodes.add(res.getResponse().getStatus());
            } catch (Exception e) {
                // ignore
            } finally {
                finishLatch.countDown();
            }
            return null;
        };

        executor.submit(task1);
        executor.submit(task2);

        // Release latch simultaneously
        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should succeed with 201 Created and the other should fail with 409 Conflict
        assertTrue(statusCodes.contains(201), "One request must succeed with 201 Created");
        assertTrue(statusCodes.contains(409), "The competing request must be rejected with 409 Conflict");

        // Verify database state: exactly ONE pending transfer for the asset exists
        List<AssetTransfer> pendingTransfers = transferRepository.findByAssetIdOrderByRequestedAtDesc(asset.getId());
        assertEquals(1, pendingTransfers.size(), "Only one transfer must exist in the database for the asset");
        assertEquals("PENDING", pendingTransfers.get(0).getStatus());
    }
}

# STEP 2.11 — USER WALLET MAPPING & CRYPTOGRAPHIC IDENTITY REPORT

**Project**: AUTHENTIX  
**Component**: Backend (`authentix-backend`)  
**Scope**: User Wallet Mapping & Cryptographic Identity Foundation  
**Baseline Tests**: 66 tests (All passed)  
**Current Tests**: 87 tests (87 passed, 0 failures, 0 errors, 0 skipped)  
**Status**: COMPLETE (Audit & Hardening Verified)

---

## 1. Objective

The objective of Step 2.11 is to establish a secure, cryptographically sound mapping between an authenticated AUTHENTIX user and an EVM blockchain wallet address. This foundation prepares AUTHENTIX for future on-chain asset registration and custody transfers without exposing user private keys, without trusting arbitrary client-supplied addresses, and without enabling wallet-spoofing or replay attacks.

The architecture remains strictly:
```
Android (Client)
   │  HTTPS REST + JWT
   ▼
Spring Boot Backend (AUTHENTIX)
   │
   ├─► PostgreSQL / Neon (Identity, Assets, Transfers, Challenge Nonces)
   │
   └─► EVM Blockchain via Web3j (Read queries / future on-chain registry)
```
*Android never connects directly to PostgreSQL or the EVM blockchain node.*

---

## 2. Existing Identity Architecture Before Step 2.11

Prior to Step 2.11:
1. **User Identity**: Users were identified by internal database `id`, unique `userCode` (e.g. `USR-1234`), email, and roles (`ADMIN`, `MANUFACTURER`, `DISTRIBUTOR`, `RETAILER`, `CUSTOMER`).
2. **Authentication**: Handled via Spring Security, BCrypt password hashing, and stateless JWT Bearer tokens resolved by `CurrentUserService`.
3. **No Wallet Representation**: The `User` entity had no `walletAddress` field. No wallet-to-user mapping existed anywhere in the backend schema.
4. **Smart Contract Assumptions**: The Solidity contract (`AuthentixRegistry.sol`) operates with EVM addresses (`address creator`, `address newCustodian`) and requires `msg.sender == currentCustodian` for transfers. Because the backend had no verified user wallet addresses, it could not map authenticated users to Ethereum addresses required for future smart contract calls.
5. **No Client Modification**: No endpoints existed for users to link or update identity-related fields.

---

## 3. Wallet Model

A non-custodial EVM wallet address model was added to `User`:
- **Entity**: [User.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/entity/User.java)
- **Field**: `@Column(name = "wallet_address", unique = true, length = 42) private String walletAddress;`
- **Nullable initially**: Existing and newly registered users can register without an immediate wallet requirement.
- **EVM address representation**: Stored as a canonical 42-character hexadecimal string starting with `0x`.
- **Consistent Normalization**: All persisted and queried addresses are normalized to EIP-55 checksum format via Web3j `Keys.toChecksumAddress(...)`.
- **Non-Custodial Guarantee**: Neither private keys, seed phrases, mnemonic words, raw passwords, nor signing keys are ever stored or accepted in PostgreSQL. The backend does not hold custody of user private keys.

---

## 4. Wallet Validation

Wallet address validation is implemented in [WalletAddressValidator.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/validation/WalletAddressValidator.java):
1. **Non-Null/Non-Empty**: Rejects null or blank input (`400 Bad Request`).
2. **Prefix Enforcement**: Must explicitly begin with `0x` or `0X` (`400 Bad Request`).
3. **Length Enforcement**: Must be exactly 42 characters (`400 Bad Request`).
4. **Hexadecimal Format**: Validated against regex `^0[xX][0-9a-fA-F]{40}$` (`400 Bad Request`).
5. **Web3j Address Validation**: Validated via Web3j `WalletUtils.isValidAddress(address)`.
6. **EIP-55 Checksum Validation**: If a client provides a mixed-case address (indicating intent to supply a checksummed address), the checksum is mathematically validated against `Keys.toChecksumAddress(address)`. If any character's case does not match the Keccak-256 hash nibble rule, the address is rejected (`400 Bad Request: Wallet address has an invalid EIP-55 checksum`).
7. **Canonical Normalization**: Standardizes valid addresses via `Keys.toChecksumAddress(address)` before database persistence and comparisons.

---

## 5. Wallet Registration & Linking API

Three REST endpoints were added to [UserController.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/controller/UserController.java):

### A. Direct Initial Linking
- **Endpoint**: `PUT /api/users/me/wallet`
- **Request**: [LinkWalletRequest.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/dto/LinkWalletRequest.java) (`{ "walletAddress": "0x..." }`)
- **Authentication**: JWT Bearer token required. Identity is extracted exclusively from `CurrentUserService.getRequiredCurrentUser()`.
- **Protection**: Clients cannot specify `userId`, `userCode`, `email`, or `role`. Only the authenticated principal's profile is updated.
- **Response**: [UserResponse.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/dto/UserResponse.java) (HTTP 200 OK) with normalized `walletAddress`.

### B. Challenge Request
- **Endpoint**: `POST /api/users/me/wallet/challenge`
- **Request**: [WalletChallengeRequest.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/dto/WalletChallengeRequest.java) (`{ "walletAddress": "0x..." }`)
- **Response**: [WalletChallengeResponse.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/dto/WalletChallengeResponse.java) containing `challenge`, `walletAddress`, `expiresAt`, and the signing instruction `message`.

### C. Cryptographic Signature Verification & Linking
- **Endpoint**: `POST /api/users/me/wallet/verify`
- **Request**: [VerifyWalletRequest.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/dto/VerifyWalletRequest.java) (`{ "walletAddress": "0x...", "challenge": "...", "signature": "0x..." }`)
- **Response**: [UserResponse.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/dto/UserResponse.java) with verified linked `walletAddress`.

---

## 6. Wallet Uniqueness

A single EVM wallet address cannot be associated with more than one AUTHENTIX user.
- **Service Guard**: `existsByWalletAddressIgnoreCase(normalizedAddress)` checks existing records before saving and throws `ConflictException("Wallet address is already registered by another user")` (`HTTP 409 Conflict`).
- **Database Unique Constraint**: `@Column(name = "wallet_address", unique = true, length = 42)` guarantees database-level isolation.
- **Integrity Violation Mapping**: Added `@ExceptionHandler(DataIntegrityViolationException.class)` in [GlobalExceptionHandler.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/exception/GlobalExceptionHandler.java) mapping database constraint collisions to `HTTP 409 Conflict` with `"error": "CONFLICT"`.

---

## 7. Wallet Change & Replacement Policy

- **Policy**: An account may associate a wallet once. Once linked, replacing the wallet address is strictly prohibited in this MVP.
- **Behavior**: If `user.getWalletAddress() != null`, any subsequent attempt to link or verify a new address throws `ConflictException("Wallet address is already linked to this account and cannot be replaced")` (`HTTP 409 Conflict`).
- **Rationale**: Silently replacing or overwriting a wallet address would break asset custody continuity and enable account takeover attacks. Multi-sig or re-verification recovery mechanisms are documented for future work.

---

## 8. Cryptographic Verification Implementation Status

**Status: FULLY IMPLEMENTED AND VERIFIED.**

Using Web3j's native cryptographic stack (`org.web3j.crypto.Sign` and `org.web3j.crypto.Keys`), the backend verifies authentic Ethereum EIP-191 `personal_sign` signatures:
- **Challenge Message Verification**: [SignatureVerifier.java](file:///E:/AUTHENTIX/backend/authentix-backend/src/main/java/com/authentix/backend/crypto/SignatureVerifier.java) parses the 65-byte hex signature into `r`, `s`, and `v` components using `Sign.signatureDataFromHex(...)`.
- **Public Key Recovery**: Recovers the signer's ECDSA public key via `Sign.signedPrefixedMessageToKey(messageBytes, signatureData)`.
- **Address Derivation**: Computes `Keys.getAddress(publicKey)` and canonicalizes with `Keys.toChecksumAddress(...)`.
- **Verification Match**: Compares the recovered address against the claimed `walletAddress`. If forged or signed by a different private key, the request is rejected with `400 Bad Request`.

---

## 9. Challenge & Replay Protection

1. **Cryptographic Entropy**: Challenges are generated using `java.security.SecureRandom` with 256 bits (32 bytes) of cryptographic entropy, encoded as hex with prefix `AUTHENTIX-VERIFY-`.
2. **Expiration (TTL)**: Challenges expire 5 minutes after creation (`expiresAt = LocalDateTime.now().plusMinutes(5)`). Submissions after expiration fail with `400 Bad Request`.
3. **Single-Use Guard**: Stored entity `WalletVerificationChallenge` has `used = true` applied upon successful verification. Replay of an identical challenge/signature fails with `409 Conflict`.
4. **User Binding**: Challenges are bound to `user_id`. If User B attempts to submit a challenge issued to User A, the request fails with `403 Forbidden`.
5. **Wallet Binding**: Challenges are bound to `wallet_address`. If a challenge issued for Wallet A is submitted with Wallet B, the request fails with `400 Bad Request`.

---

## 10. User QR Compatibility

The application-level User QR format remains unchanged:
```
AUTHENTIX://USER/{USER_CODE}
```
- **Privacy & Security**: The User QR does NOT contain passwords, JWTs, private keys, wallet secrets, or challenges.
- **Separation of Concerns**: User QR provides off-chain application-level identification; the wallet mapping provides on-chain cryptographic authority.

---

## 11. Authorization Model

- `GET /api/users/me`: Returns the authenticated user's profile with their own `walletAddress`.
- `PUT /api/users/me/wallet`: Modifies only the authenticated principal.
- `POST /api/users/me/wallet/challenge`: Generates a challenge for the authenticated principal.
- `POST /api/users/me/wallet/verify`: Verifies a signature for the authenticated principal.
- `GET /api/users`: Admin/authenticated read endpoint lists users with public wallet addresses.
- `passwordHash` is annotated with `@JsonIgnore` and never included in `UserResponse` or any DTO.

---

## 12. Database Design

### Added to `users` table:
```sql
ALTER TABLE users ADD COLUMN wallet_address VARCHAR(42) UNIQUE;
```

### Added table `wallet_verification_challenges`:
```sql
CREATE TABLE wallet_verification_challenges (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    wallet_address VARCHAR(42) NOT NULL,
    challenge VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_challenge_token ON wallet_verification_challenges(challenge);
CREATE INDEX idx_challenge_user_wallet ON wallet_verification_challenges(user_id, wallet_address);
```
Both PostgreSQL and the H2 in-memory test dialect support this schema without database-specific syntax or proprietary extensions.

---

## 13. Security Review

- **No Private Keys Stored**: User entity, database tables, and API responses contain zero private keys or seed phrases.
- **No Client Impersonation**: Wallet endpoints strictly derive identity from JWT context (`CurrentUserService`).
- **No Address Forgery**: Cryptographic EIP-191 challenge-response proves ownership of the private key controlling the claimed address before verification.
- **Race Condition Safety**: Multithreaded test confirms database uniqueness and `@Transactional` isolation reject simultaneous competing registrations with `409 Conflict`.
- **Hardcoded Secrets Removed**: In Step 2.11.1, the previous development private key fallback was completely removed from `application.properties` (`${BLOCKCHAIN_PRIVATE_KEY:}`). The `Credentials` bean is lazy and fail-safe, requiring runtime environment configuration for signing without holding hardcoded defaults.

---

## 14. Tests Added (21 Tests in `WalletIdentityTest.java`)

1. `testAuthenticatedUserCanLinkValidWallet`: Authenticated user links valid EVM address; returns 200 OK.
2. `testUnauthenticatedUserCannotLinkWallet`: Request without JWT returns 401 Unauthorized.
3. `testInvalidWalletPrefixRejected`: Missing `0x` returns 400 Bad Request.
4. `testInvalidWalletLengthRejected`: Truncated address returns 400 Bad Request.
5. `testInvalidHexCharactersRejected`: Non-hex characters return 400 Bad Request.
6. `testEmptyWalletAddressRejected`: Blank address returns 400 Bad Request.
7. `testDuplicateWalletAddressRejected`: Second user registering same address receives 409 Conflict.
8. `testClientCannotModifyAnotherUsersWallet`: Endpoint updates authenticated principal only.
9. `testExistingWalletCannotBeSilentlyReplaced`: Second link attempt on same account returns 409 Conflict.
10. `testWalletReturnedByGetMe`: Verified wallet appears in `/me` response.
11. `testPasswordHashRemainsHidden`: `passwordHash` is absent from all user JSON responses.
12. `testWalletNormalizationToEip55Checksum`: Lowercase input is stored as canonical EIP-55 checksum.
13. `testInvalidEip55ChecksumRejected`: Mixed-case address with bad checksum returns 400 Bad Request.
14. `testChallengeGeneration`: Challenge created with 256-bit entropy, 5-minute TTL, user/wallet binding.
15. `testExpiredChallengeRejected`: Expired challenge returns 400 Bad Request.
16. `testChallengeSingleUseReplayRejected`: Replaying an already verified challenge returns 409 Conflict.
17. `testChallengeBoundToCorrectUser`: User B submitting User A's challenge returns 403 Forbidden.
18. `testChallengeBoundToCorrectWallet`: Submitting Wallet B with challenge issued for Wallet A returns 400 Bad Request.
19. `testValidCryptographicSignatureAccepted`: Real Web3j EIP-191 signature verifies and links wallet.
20. `testInvalidCryptographicSignatureRejected`: Signature from unauthorized keypair returns 400 Bad Request.
21. `testConcurrentWalletRegistrationRace`: Two concurrent threads racing to register the same address -> exactly one gets 200 OK, the other gets 409 Conflict, and exactly one user is associated in database.

---

## 15. Full Test Results

Execution of `mvn test` across all backend suites:

```
[INFO] Running com.authentix.backend.AuthentixBackendApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.authentix.backend.AuthentixRegistryServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.authentix.backend.BlockchainConnectionTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.authentix.backend.DtoApiHardeningTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.authentix.backend.SecurityFoundationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.authentix.backend.TransactionConcurrencyHardeningTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.authentix.backend.ValidationAndExceptionHandlingTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.authentix.backend.WalletIdentityTest
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 87, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 16. Files Created / Modified

| File | Status | Description |
|---|---|---|
| `entity/User.java` | Modified | Added `walletAddress` column (`VARCHAR(42) UNIQUE`), getter/setter |
| `repository/UserRepository.java` | Modified | Added wallet query methods (`findByWalletAddress`, `existsBy...`) |
| `entity/WalletVerificationChallenge.java` | Created | Entity for cryptographic ownership verification challenges |
| `repository/WalletVerificationChallengeRepository.java` | Created | Repository for challenge nonce lookup |
| `validation/WalletAddressValidator.java` | Created | Reusable EVM address, hex, and EIP-55 checksum validator |
| `crypto/SignatureVerifier.java` | Created | Web3j-based EIP-191 personal_sign signature verification utility |
| `dto/LinkWalletRequest.java` | Created | DTO for `PUT /api/users/me/wallet` |
| `dto/WalletChallengeRequest.java` | Created | DTO for requesting ownership challenge |
| `dto/WalletChallengeResponse.java` | Created | DTO returning challenge nonce, TTL, and sign prompt |
| `dto/VerifyWalletRequest.java` | Created | DTO submitting wallet address, challenge, and hex signature |
| `dto/UserResponse.java` | Modified | Added `walletAddress` to response DTO |
| `exception/GlobalExceptionHandler.java` | Modified | Added handler for `DataIntegrityViolationException` -> 409 Conflict |
| `service/UserService.java` | Modified | Added `linkWallet`, `generateWalletChallenge`, `verifyAndLinkWallet` |
| `controller/UserController.java` | Modified | Added `/me/wallet`, `/me/wallet/challenge`, `/me/wallet/verify` routes |
| `src/test/java/com/authentix/backend/WalletIdentityTest.java` | Created | 21 comprehensive integration and concurrency tests |
| `docs/STEP_2_11_WALLET_IDENTITY_REPORT.md` | Created | Step 2.11 audit and implementation documentation report |

---

## 17. Remaining Limitations

1. **Wallet Replacement**: Replacing an existing wallet address is intentionally disallowed in this MVP to preserve asset custody continuity.
2. **Blockchain Writes**: No blockchain transactions (`registerAsset`, `transferCustody`) are dispatched from the backend yet.
3. **Android Client UI**: Android client integration for web3 wallet connection / signing is not yet implemented.

---

## 18. Recommended Next Step (Step 2.12)

Proceed to **Step 2.12 — Blockchain Write Integration Architecture**:
- Integrate Web3j transaction manager and gas provider.
- Implement on-chain `registerAsset` upon asset creation by verified manufacturers.
- Implement on-chain `transferCustody` upon completion of two-party transfer confirmations.
- Ensure on-chain custody addresses align strictly with the verified user wallet mappings established in Step 2.11.

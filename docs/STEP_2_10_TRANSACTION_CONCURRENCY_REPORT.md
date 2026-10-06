# STEP 2.10 — Business Transaction & Concurrency Hardening Report

**Project:** AUTHENTIX  
**Repository:** E:\AUTHENTIX  
**Phase:** Step 2.10 — Business Transaction & Concurrency Hardening  
**Date:** October 2026  
**Status:** COMPLETED & VERIFIED (66 tests passing, 0 failures, 0 errors)  

---

## 1. Objective

The primary objective of Step 2.10 is to harden the AUTHENTIX backend custody transfer workflow so that physical asset custody transitions are transactionally atomic, deterministic, and resilient against race conditions and concurrent transfer attempts. 

Key goals:
- Eliminate the possibility of creating multiple pending/active transfers for the same asset simultaneously.
- Eliminate race conditions where competing transfer requests or confirmations cause state corruption or split custody.
- Enforce strict custodian revalidation at confirmation time to prevent stale transfers from transferring assets whose custody changed unexpectedly.
- Enforce idempotency and deterministic rejection of duplicate confirmations.
- Ensure that the final transfer completion (updating `Asset.currentCustodian`, `Asset.status`, `AssetTransfer.status`, and `AssetTransfer.completedAt`) executes atomically inside a single database transaction.
- Preserve full test portability across both PostgreSQL/Neon and the local H2 test configuration.

---

## 2. Existing Transfer Workflow Before Changes

Prior to Step 2.10, the transfer workflow operated as follows:

1. **Transfer Creation (`POST /api/transfers`):**
   - Caller identity was resolved via `CurrentUserService`.
   - `Asset` was loaded using `assetRepository.findById(assetId)`.
   - Verified that `fromUser.getId().equals(asset.getCurrentCustodian().getId())`.
   - Verified that `fromUser` != `toUser`.
   - Saved `AssetTransfer` in `PENDING` status.
2. **Transfer Confirmation (`POST /api/transfers/{id}/confirm`):**
   - Loaded transfer via `transferRepository.findById(transferId)`.
   - Loaded caller via `userRepository.findById(userId)`.
   - If caller matched `fromUser`, set `fromUserConfirmed = true`.
   - If caller matched `toUser`, set `toUserConfirmed = true`.
   - If both `fromUserConfirmed` and `toUserConfirmed` evaluated to `true`, invoked `completeTransfer`:
     - `asset.setCurrentCustodian(transfer.getToUser())`
     - `asset.setStatus("TRANSFERRED")`
     - `transfer.setStatus("COMPLETED")`
     - `transfer.setCompletedAt(LocalDateTime.now())`

---

## 3. Problems Identified

The audit revealed the following concurrency and data-integrity vulnerabilities:

1. **Multiple Pending Transfers (Race & Logic Gap):**
   There was no query or constraint checking whether the asset already had an active `PENDING` transfer. A custodian could initiate multiple pending transfers for the same asset to different buyers (A -> B and A -> C). Furthermore, two concurrent requests arriving simultaneously could both read the asset and insert conflicting pending transfers.
2. **Missing Database Locking on Asset:**
   `assetRepository.findById(assetId)` did not acquire a row-level lock. Under concurrent HTTP requests, both threads could observe the asset with no pending transfers, causing a classic Time-of-Check to Time-of-Use (TOCTOU) race.
3. **No Custodian Revalidation at Confirmation Time:**
   `confirmTransfer` checked only `transfer.getFromUser().getId().equals(user.getId())`. It never checked whether `asset.getCurrentCustodian()` was *still* `transfer.getFromUser()`. If custody had shifted out-of-band or via an interleaved workflow, confirming a stale pending transfer would erroneously reassign the asset to the stale transfer's recipient.
4. **Non-Idempotent / Silent Duplicate Confirmations:**
   If a sender called confirm twice before the receiver confirmed, the second request silently succeeded without alerting the caller that they had already confirmed.
5. **No Asset Eligibility Checks:**
   Assets in `DEACTIVATED` or `REPORTED` (counterfeit) statuses were not blocked from initiating custody transfers.
6. **Inactive Participant Checks:**
   Deactivated user accounts were not verified during transfer initiation or confirmation.

---

## 4. Changes Implemented

1. **Pessimistic Write Locking:**
   - Added `@Lock(LockModeType.PESSIMISTIC_WRITE)` query method `findByIdWithLock(Long id)` to `AssetRepository`.
   - Added `@Lock(LockModeType.PESSIMISTIC_WRITE)` query method `findByIdWithLock(Long id)` to `AssetTransferRepository`.
2. **Pending Transfer Prevention:**
   - Added `transferRepository.existsByAssetIdAndStatus(assetId, "PENDING")`.
   - In `createTransfer`, acquires write lock on `Asset` and checks for existing pending transfers. Rejects with `ConflictException` (HTTP 409) if a pending transfer already exists.
3. **Custodian Revalidation at Confirmation Time:**
   - In `confirmTransfer`, acquires row-level locks on both the transfer and the associated asset.
   - Strictly validates `asset.getCurrentCustodian().getId().equals(transfer.getFromUser().getId())`. If false, rejects with `ConflictException` (HTTP 409).
4. **Deterministic Confirmation Idempotency:**
   - If the sender attempts to confirm again when `fromUserConfirmed == true`, rejects with `ConflictException("Sender has already confirmed this transfer")`.
   - If the receiver attempts to confirm again when `toUserConfirmed == true`, rejects with `ConflictException("Receiver has already confirmed this transfer")`.
5. **Asset Status Validation:**
   - Transferred assets are restricted to valid statuses (`CREATED`, `TRANSFERRED`, `ACTIVE`).
   - If status is `DEACTIVATED` or `REPORTED`, rejects with `ConflictException` (HTTP 409).
6. **Inactive Participant Checks:**
   - Rejects transfer creation if either sender or receiver is inactive (`BadRequestException`, HTTP 400).
   - Rejects confirmation if the confirming user is inactive (`BadRequestException`, HTTP 400).
7. **Atomic Completion:**
   - Single atomic transaction boundary ensures `asset.currentCustodian`, `asset.status`, `transfer.status`, and `transfer.completedAt` update together or roll back completely on failure.

---

## 5. Repository Locking Strategy

### Why Pessimistic Locking Was Chosen
In high-integrity asset tracking systems, custody of physical items must never split or be ambiguously assigned. Optimistic locking (`@Version`) would throw `OptimisticLockException` after concurrent requests execute, requiring manual retry logic on the client. 

Pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)` / `SELECT ... FOR UPDATE`):
- Forces competing requests for the same asset to serialize at the database level.
- Thread 1 acquires the lock on the `Asset` row, checks that no transfer is pending, inserts transfer T1, and commits.
- Thread 2 waits on the lock, and upon acquisition, immediately re-reads the updated state, detects T1 in `PENDING` status, and safely throws `ConflictException` (HTTP 409).
- Prevents dirty reads and non-repeatable reads across both PostgreSQL and H2.

### Minimal Lock Surface
- Only the specific asset being transferred is locked during creation (`AssetRepository.findByIdWithLock`).
- Only the specific transfer and its target asset are locked during confirmation (`AssetTransferRepository.findByIdWithLock` and `AssetRepository.findByIdWithLock`).
- Unrelated assets and user records remain completely unlocked, preventing lock contention across the rest of the application.

---

## 6. Transaction Boundaries

- Both `AssetTransferService` and `AssetService` are annotated with `@Transactional`.
- Every operation (`createTransfer`, `confirmTransfer`, `completeTransfer`) executes within an atomic database transaction.
- If any validation rule, constraint, or database operation fails, the transaction is marked for rollback, leaving the database state completely clean.

---

## 7. Pending-Transfer Protection

- Query: `transferRepository.existsByAssetIdAndStatus(asset.getId(), "PENDING")`
- Rule: An asset can have at most **one** active transfer in `PENDING` status at any given time.
- Behavior: Attempting to create a second transfer while one is pending results in HTTP 409 Conflict with message `"An active transfer is already pending for this asset"`.
- Clean Error Mapping: Handled by `GlobalExceptionHandler` and rendered in standard `ApiErrorResponse` format.

---

## 8. Custodian Revalidation

- During confirmation:
  ```java
  if (asset.getCurrentCustodian() == null ||
          !asset.getCurrentCustodian().getId().equals(transfer.getFromUser().getId())) {
      throw new ConflictException("Asset custody has changed; transfer sender is no longer the current custodian");
  }
  ```
- Protects against stale confirmations if custody was modified out-of-band.
- Prevents unauthorized transfer completion.

---

## 9. Confirmation Idempotency

- Double confirmation by the same party is explicitly detected and rejected:
  - Sender: `ConflictException("Sender has already confirmed this transfer")` -> HTTP 409
  - Receiver: `ConflictException("Receiver has already confirmed this transfer")` -> HTTP 409
- Re-confirming an already completed transfer:
  - `ConflictException("Transfer is already completed")` -> HTTP 409
- Re-confirming a cancelled transfer:
  - `ConflictException("Transfer has been cancelled")` -> HTTP 409
- Eliminates ambiguous silent successes and prevents corrupted double-execution.

---

## 10. State-Transition Rules

### Asset State Transitions
- Creation: Status initialized to `"CREATED"`, `currentCustodian` = creator.
- Transfer Initiated: Status remains unchanged, `currentCustodian` remains sender.
- Mutual Confirmation: Status transitions to `"TRANSFERRED"`, `currentCustodian` transitions to receiver.
- Prohibited from Transfer: Assets with status `"DEACTIVATED"` or `"REPORTED"`.

### Transfer State Transitions
- Creation: Initialized to status `"PENDING"`, `fromUserConfirmed = false`, `toUserConfirmed = false`.
- Sender Confirmation: `fromUserConfirmed = true`, status remains `"PENDING"`.
- Receiver Confirmation: `toUserConfirmed = true`, status remains `"PENDING"` (if sender has not confirmed).
- Final Confirmation: When both `fromUserConfirmed == true` and `toUserConfirmed == true`, status transitions to `"COMPLETED"`, `completedAt` populated.

---

## 11. Database Considerations

- **PostgreSQL / Neon vs H2 Fallback:**
  - Standard JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` is translated to `SELECT ... FOR UPDATE` by both Hibernate's PostgreSQL dialect and H2 dialect.
  - Zero database schema migrations or breaking changes were introduced.
  - No database-specific vendor functions were used in JPQL queries.
- **Indices & Performance:**
  - Foreign key columns (`asset_id`, `from_user_id`, `to_user_id`) and status columns are queried via standard Spring Data JPA method signatures.

---

## 12. Tests Added

A comprehensive test suite was created in [`TransactionConcurrencyHardeningTest.java`](file:///E:/AUTHENTIX/backend/authentix-backend/src/test/java/com/authentix/backend/TransactionConcurrencyHardeningTest.java) containing 15 test cases:

1. `testValidTransferCreationSucceeds`: Valid transfer creation succeeds with 201 Created.
2. `testNonCustodianCannotCreateTransfer`: Rejects creation if caller is not the asset's current custodian (400 Bad Request).
3. `testNonexistentAssetFails`: Rejects transfer for nonexistent asset ID (404 Not Found).
4. `testNonexistentReceiverFails`: Rejects transfer for nonexistent receiver user ID (404 Not Found).
5. `testSenderEqualsReceiverFails`: Rejects self-transfers (400 Bad Request).
6. `testInactiveSenderOrReceiverFails`: Rejects transfer if participant account is inactive (400 Bad Request).
7. `testDuplicateTransferCodeFails`: Rejects duplicate transfer codes (409 Conflict).
8. `testSecondPendingTransferForSameAssetFails`: Blocks creating a second pending transfer for an asset (409 Conflict).
9. `testDeactivatedOrReportedAssetCannotBeTransferred`: Blocks transfer of deactivated or reported assets (409 Conflict).
10. `testTwoPartyConfirmationCompletesTransfer`: Mutual confirmation atomically completes transfer and updates custodian.
11. `testDuplicateConfirmationRejected`: Rejects duplicate confirmations by the same party (409 Conflict).
12. `testCompletedTransferCannotBeConfirmedAgain`: Rejects confirmation attempts on completed transfers (409 Conflict).
13. `testStaleCustodianPreventsConfirmation`: Detects if custody changed out-of-band and blocks completion (409 Conflict).
14. `testCustodianChangesOnlyAfterCompletion`: Verifies custodian remains unchanged while pending and transitions only after completion.
15. `testConcurrentTransferCreationRace`: Multi-threaded integration test executing simultaneous transfer creation requests for the same asset. Verifies that exactly one thread succeeds (201 Created), competing thread is rejected (409 Conflict), and exactly one pending transfer exists in the database.

---

## 13. Full Test Results

Maven test execution results:
```
[INFO] Running com.authentix.backend.AuthentixBackendApplicationTests
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.625 s -- in com.authentix.backend.AuthentixBackendApplicationTests
[INFO] Running com.authentix.backend.AuthentixRegistryServiceTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.992 s -- in com.authentix.backend.AuthentixRegistryServiceTest
[INFO] Running com.authentix.backend.BlockchainConnectionTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.985 s -- in com.authentix.backend.BlockchainConnectionTest
[INFO] Running com.authentix.backend.DtoApiHardeningTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.887 s -- in com.authentix.backend.DtoApiHardeningTest
[INFO] Running com.authentix.backend.SecurityFoundationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.610 s -- in com.authentix.backend.SecurityFoundationTest
[INFO] Running com.authentix.backend.TransactionConcurrencyHardeningTest
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.421 s -- in com.authentix.backend.TransactionConcurrencyHardeningTest
[INFO] Running com.authentix.backend.ValidationAndExceptionHandlingTest
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.678 s -- in com.authentix.backend.ValidationAndExceptionHandlingTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 14. Security Review

- **IDOR / BOLA Prevention:** Preserved. Transfer sender and confirming user are strictly resolved via `CurrentUserService` from authenticated JWT claims.
- **Client-Controlled Custody:** Completely eliminated. The client cannot specify `fromUserId` or override the asset's current custodian.
- **Mass Assignment:** Blocked by Request DTOs.
- **Information Leakage:** Sanitized error responses via `ApiErrorResponse`. No passwords, tokens, hashes, SQL, or class names are leaked.
- **Race Condition Mitigations:** Verified by multi-threaded integration tests (`testConcurrentTransferCreationRace`).

---

## 15. Files Created / Modified

### Created
- `backend/authentix-backend/src/test/java/com/authentix/backend/TransactionConcurrencyHardeningTest.java`
- `docs/STEP_2_10_TRANSACTION_CONCURRENCY_REPORT.md`

### Modified
- `backend/authentix-backend/src/main/java/com/authentix/backend/repository/AssetRepository.java`
- `backend/authentix-backend/src/main/java/com/authentix/backend/repository/AssetTransferRepository.java`
- `backend/authentix-backend/src/main/java/com/authentix/backend/service/AssetTransferService.java`

---

## 16. Remaining Limitations & Distinctions

### Implemented & Verified by Automated Tests
- Single active pending transfer per asset enforcement.
- Pessimistic write locking on `Asset` and `AssetTransfer`.
- Stale custodian revalidation at confirmation time.
- Duplicate confirmation rejection for sender and receiver.
- Inactive user and asset status validations.
- Atomic transfer completion.
- Multi-threaded transfer creation race prevention.

### Not Implemented / Future Work
- **Blockchain Write Integration (Step 2.12):** On-chain custody updates upon `completeTransfer` remain to be implemented.
- **Wallet Address Mapping (Step 2.11):** Cryptographic user wallet mapping and signature verification.
- **Transfer Cancellation API:** Formal endpoint for cancellation with reason and timeout mechanisms.
- **Distributed Locking:** In multi-node/clustered microservice deployments, database-level pessimistic locking is sufficient for single-database setups; distributed caching locks (e.g. Redis Redlock) would be considered if database-independent distributed coordination is introduced in the future.

---

## 17. Recommended Next Step

Proceed to **Step 2.11 — User Wallet Mapping & Cryptographic Signatures** or **Step 2.12 — Blockchain Write Integration** as planned in the AUTHENTIX backend roadmap.

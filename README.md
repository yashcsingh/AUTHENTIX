# AUTHENTIX

Blockchain-Based Asset Authentication and Chain-of-Custody System.

---

## Hardened REST API Specification (Step 2.8)

All protected endpoints require an `Authorization: Bearer <JWT>` header. The authenticated caller's identity is resolved on the server via `CurrentUserService` and cannot be overridden by client request parameters or payloads.

### Authentication Endpoints (`/api/auth`)

#### `POST /api/auth/register`
- **Auth:** Public
- **Request Body:**
  ```json
  {
    "userCode": "USR-001",
    "fullName": "Alice Manufacturer",
    "email": "alice@example.com",
    "password": "Password123!",
    "role": "MANUFACTURER"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "token": "<JWT_TOKEN>",
    "userCode": "USR-001",
    "fullName": "Alice Manufacturer",
    "email": "alice@example.com",
    "role": "MANUFACTURER"
  }
  ```

#### `POST /api/auth/login`
- **Auth:** Public
- **Request Body:**
  ```json
  {
    "email": "alice@example.com",
    "password": "Password123!"
  }
  ```
- **Response (200 OK):** `AuthResponse` with JWT token and user profile summary.

---

### User Endpoints (`/api/users`)

#### `GET /api/users/me`
- **Auth:** Authenticated
- **Response (200 OK):** `UserResponse`
  ```json
  {
    "id": 1,
    "userCode": "USR-001",
    "fullName": "Alice Manufacturer",
    "email": "alice@example.com",
    "role": "MANUFACTURER",
    "qrCodeValue": "AUTHENTIX://USER/USR-001",
    "active": true
  }
  ```
  *(Note: `passwordHash` is never exposed)*

#### `GET /api/users` & `GET /api/users/{id}`
- **Auth:** Authenticated
- **Response (200 OK):** `List<UserResponse>` / `UserResponse`

#### `POST /api/users`
- **Auth:** Admin only (`@PreAuthorize("hasRole('ADMIN')")`)
- **Request Body:** `RegisterRequest`
- **Response (201 Created):** `UserResponse`

---

### Asset Endpoints (`/api/assets`)

#### `POST /api/assets`
- **Auth:** Authenticated (Caller is automatically set as `creator` and initial `currentCustodian`)
- **Identity Source:** Server-side Spring Security Context (`CurrentUserService`)
- **Request Body (`CreateAssetRequest`):**
  ```json
  {
    "assetCode": "ASSET-001",
    "assetType": "LUXURY_WATCH",
    "metadataHash": "0x123...abc"
  }
  ```
  *(Server rejects/ignores client attempts to provide `id`, `creator`, `currentCustodian`, `status`, or `createdAt`)*
- **Response (201 Created):** `AssetResponse`
  ```json
  {
    "id": 10,
    "assetCode": "ASSET-001",
    "assetType": "LUXURY_WATCH",
    "status": "CREATED",
    "qrCodeValue": "AUTHENTIX://PRODUCT/ASSET-001",
    "creator": {
      "userCode": "USR-001",
      "fullName": "Alice Manufacturer",
      "role": "MANUFACTURER"
    },
    "currentCustodian": {
      "userCode": "USR-001",
      "fullName": "Alice Manufacturer",
      "role": "MANUFACTURER"
    },
    "metadataHash": "0x123...abc",
    "createdAt": "2026-10-06T21:12:00"
  }
  ```

#### `GET /api/assets`, `GET /api/assets/{id}`, `GET /api/assets/code/{assetCode}`
- **Auth:** Authenticated
- **Response (200 OK):** `List<AssetResponse>` / `AssetResponse`

---

### Transfer Endpoints (`/api/transfers`)

#### `POST /api/transfers`
- **Auth:** Authenticated (Sender MUST be the current custodian of the asset)
- **Identity Source:** Server-side Spring Security Context (`CurrentUserService`)
- **Request Body (`CreateTransferRequest`):**
  ```json
  {
    "assetId": 10,
    "toUserId": 2,
    "transferCode": "TRF-9901"
  }
  ```
  *(Clients cannot supply `fromUserId`; sender identity is strictly derived from the caller's JWT)*
- **Response (201 Created):** `TransferResponse`
  ```json
  {
    "id": 5,
    "transferCode": "TRF-9901",
    "assetId": 10,
    "assetCode": "ASSET-001",
    "fromUser": {
      "userCode": "USR-001",
      "fullName": "Alice Manufacturer",
      "role": "MANUFACTURER"
    },
    "toUser": {
      "userCode": "USR-002",
      "fullName": "Bob Customer",
      "role": "CUSTOMER"
    },
    "status": "PENDING",
    "fromUserConfirmed": false,
    "toUserConfirmed": false,
    "requestedAt": "2026-10-06T21:15:00",
    "completedAt": null,
    "blockchainTransactionId": null
  }
  ```

#### `POST /api/transfers/{transferId}/confirm`
- **Auth:** Authenticated (Must be either the transfer sender or receiver)
- **Identity Source:** Server-side Spring Security Context (`CurrentUserService`)
- **Request Body:** Optional `ConfirmTransferRequest` (`{"note": "..."}`)
  *(Clients cannot supply `userId`; confirmer identity is strictly derived from the caller's JWT)*
- **Response (200 OK):** `TransferResponse` with updated confirmation status (`fromUserConfirmed`, `toUserConfirmed`, and `status: COMPLETED` once both parties confirm)

#### `GET /api/transfers/{transferId}` & `GET /api/transfers/asset/{assetId}`
- **Auth:** Authenticated
- **Response (200 OK):** `TransferResponse` / `List<TransferResponse>`

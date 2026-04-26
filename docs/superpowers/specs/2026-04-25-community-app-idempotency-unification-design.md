# Community App Idempotency Unification Design

## 1. Problem

The application currently has two public idempotency models.

Content write APIs use the shared HTTP idempotency model:

- client sends `Idempotency-Key`
- server scopes the key by `operation + user_id + idem_key`
- `IdempotencyGuard` owns first-execution, concurrent replay, successful replay, and cached response behavior
- database state is stored in `http_idempotency`

Wallet and market order APIs use a different model:

- client sends `requestId` in the request body
- wallet and market services look up business tables by raw `request_id`
- wallet and market tables enforce globally unique `request_id`
- `WalletLedgerService` also treats raw `requestId` as the global ledger idempotency key

This creates three defects:

1. public API semantics are inconsistent across write endpoints
2. two users can collide by choosing the same body `requestId`
3. wallet ledger replay can silently reuse an unrelated existing transaction when raw `requestId` collides

The third defect is the highest risk. A money-moving path must never return an existing `wallet_txn` unless the existing transaction matches the requested transaction type, business identity, amount, and entries.

## 2. Goals

1. Use one public HTTP idempotency contract for authenticated write APIs that create non-repeatable side effects.
2. Scope public idempotency by `operation + actor_user_id + Idempotency-Key`.
3. Preserve wallet and market replay-conflict behavior for mismatched request parameters.
4. Prevent wallet ledger from silently reusing an unrelated transaction.
5. Keep a backward-compatible migration path for existing clients that still send body `requestId`.
6. Make the database uniqueness model match the API contract.
7. Keep the change compatible with the market wallet saga command design, where wallet command ids are server-generated and based on durable business ids.

## 3. Non-Goals

This spec does not redesign all market write APIs. It covers the wallet user write APIs and market order creation path that currently expose body `requestId`.

This spec does not replace `IdempotencyGuard` storage with a new system. The shared guard remains the public HTTP idempotency entry point.

This spec does not implement the full market wallet saga command processor. It only requires that wallet command ids stop depending on client-supplied raw request ids.

This spec does not backfill historical rows into a new audit model. Existing rows remain readable.

This spec does not change admin wallet action idempotency. `wallet_admin_action.request_id` is treated as a server-generated audit/action id and remains globally unique.

## 4. Current Evidence

`IdempotencyGuard` defines the intended HTTP model:

- header name: `Idempotency-Key`
- missing key on required endpoints returns `400`
- concurrent same key returns `409`
- successful replay returns the cached response
- scope is `operation + userId + key`

`http_idempotency` enforces the same scope with:

```sql
unique key uk_http_idem (operation, user_id, idem_key)
```

Wallet currently accepts body `requestId` through:

- `CreateRechargeRequest.requestId`
- `CreateWithdrawRequest.requestId`
- `CreateTransferRequest.requestId`

Market order creation currently accepts body `requestId` through:

- `CreateMarketOrderRequest.requestId`

Wallet and market schema currently use global unique keys:

- `wallet_txn`: `unique key uk_wallet_txn_request (request_id)`
- `recharge_order`: `unique key uk_recharge_order_request (request_id)`
- `withdraw_order`: `unique key uk_withdraw_order_request (request_id)`
- `transfer_order`: `unique key uk_transfer_order_request (request_id)`
- `wallet_admin_action`: `unique key uk_wallet_admin_action_request (request_id)`
- `market_order`: `unique key uk_market_order_request (request_id)`

`WalletLedgerService.post(...)` currently returns an existing transaction by raw `requestId` without validating that the existing transaction matches the requested postings.

## 5. Design Choice

Adopt `Idempotency-Key` as the public HTTP idempotency key for the affected wallet and market endpoints.

Keep domain `request_id` columns as persisted business replay keys, but change their meaning:

- for public HTTP requests, `request_id` stores the effective public idempotency key
- business table uniqueness is scoped by the acting user
- wallet ledger transaction ids are server-generated canonical command ids, not raw client keys

Add request fingerprint validation to the shared HTTP idempotency model for financial and order APIs. This preserves the current wallet and market behavior where replaying the same key with different parameters returns a conflict instead of returning a cached response for a different operation attempt.

## 6. Alternatives Considered

### 6.1 Only Scope Database Unique Keys

Change wallet and market unique keys from raw `request_id` to `(user_id, request_id)` or `(buyer_user_id, request_id)`, but keep body `requestId` as the public contract.

This is the smallest database fix, but it leaves the API fragmented and still requires clients to learn endpoint-specific idempotency behavior.

### 6.2 Header-Only Cutover

Remove body `requestId` immediately and require `Idempotency-Key` on all affected endpoints.

This is the cleanest final API, but it is too abrupt for existing clients and tests that already send body `requestId`.

### 6.3 Header With Compatibility Fallback

Read `Idempotency-Key` first. During migration, accept body `requestId` only when the header is absent. Reject requests where both are present and different.

This is the recommended path. It unifies the target contract while providing a deterministic migration for existing clients.

## 7. Public API Contract

The following endpoints must require an effective idempotency key:

- `POST /api/wallet/recharges`
- `POST /api/wallet/withdrawals`
- `POST /api/wallet/transfers`
- `POST /api/market/orders`

The effective key is resolved as follows:

1. If `Idempotency-Key` is present and body `requestId` is absent, use `Idempotency-Key`.
2. If `Idempotency-Key` is absent and body `requestId` is present, use body `requestId` as a compatibility fallback.
3. If both are present and equal after trimming, use the trimmed value.
4. If both are present and different after trimming, return `400`.
5. If both are absent, return `400`.

New clients must use `Idempotency-Key`. Body `requestId` is deprecated for these endpoints.

The response may continue to include `requestId` for compatibility. Its value is the effective idempotency key used by the server.

## 8. Operation Names

Use stable operation names for the shared guard:

- wallet recharge: `wallet:recharge`
- wallet withdraw: `wallet:withdraw`
- wallet transfer: `wallet:transfer`
- market order creation: `market:create_order`

Operation names are internal server strings. Clients do not send them.

## 9. Request Fingerprint

Financial and order creation APIs must reject a replay where the same effective idempotency key is reused with different semantic request parameters.

Add an optional request fingerprint to `IdempotencyGuard`.

The fingerprint is a SHA-256 hash of a canonical semantic string. It is not the raw HTTP body. Field order, whitespace, and JSON formatting must not affect the fingerprint.

The fingerprint-aware overload also accepts a replay-conflict `ErrorCode`. Wallet endpoints pass `WalletErrorCode.REQUEST_REPLAY_CONFLICT`. Market order creation passes `MarketErrorCode.REQUEST_REPLAY_CONFLICT`. If no domain code is supplied, the guard uses a common `409` replay-conflict error.

Canonical fields:

- wallet recharge: `amount`
- wallet withdraw: `amount`
- wallet transfer: `toUserId`, `amount`
- market order creation: `listingId`, `quantity`, `addressId`

For `addressId`, represent null as an empty value in the canonical string.

Example canonical strings:

```text
wallet:recharge|amount=100
wallet:withdraw|amount=100
wallet:transfer|toUserId=0f7c7c9e-1b51-4e75-9fe5-d8fd8537f963|amount=100
market:create_order|listingId=7ff32d6a-2737-4e1f-a67a-8682c747ae2f|quantity=2|addressId=
```

`http_idempotency` must store the fingerprint with the processing record. When an existing record is found:

- same fingerprint and `SUCCESS`: return cached response
- same fingerprint and `PROCESSING`: return `409`
- different fingerprint: return the supplied replay-conflict error code

Content APIs may keep using the current guard overload without a fingerprint.

## 10. Database Model

### 10.1 HTTP Idempotency

Add a nullable `request_hash` column to `http_idempotency`.

The unique key remains:

```sql
unique key uk_http_idem (operation, user_id, idem_key)
```

Rows created by APIs that do not pass a fingerprint have `request_hash = null`.

Rows created by wallet and market order APIs have a non-null SHA-256 request hash.

### 10.2 Wallet Order Tables

Change public wallet order uniqueness from global raw `request_id` to actor-scoped uniqueness:

```sql
recharge_order: unique (user_id, request_id)
withdraw_order: unique (user_id, request_id)
transfer_order: unique (from_user_id, request_id)
```

Mapper lookups used for public replay must include the actor user id:

- recharge: `selectByUserIdAndRequestId(userId, requestId)`
- withdraw: `selectByUserIdAndRequestId(userId, requestId)`
- transfer: `selectByFromUserIdAndRequestId(fromUserId, requestId)`

Replay match checks still compare the full request semantics:

- recharge: user id and amount
- withdraw: user id and amount
- transfer: from user id, to user id, and amount

### 10.3 Market Order Table

Change market order uniqueness from global raw `request_id` to buyer-scoped uniqueness:

```sql
market_order: unique (buyer_user_id, request_id)
```

Mapper lookups used for public replay must include buyer user id:

- `selectByBuyerUserIdAndRequestId(buyerUserId, requestId)`
- `selectByBuyerUserIdAndRequestIdForUpdate(buyerUserId, requestId)`

Replay match checks still compare:

- buyer user id
- listing id
- quantity
- address id semantics for physical orders

### 10.4 Wallet Ledger Table

Keep `wallet_txn.request_id` globally unique.

Its meaning changes from "client-provided request id" to "server-generated canonical wallet command id".

Canonical wallet ledger request ids:

- recharge ledger: `wallet:recharge:<orderId>`
- withdraw hold: `wallet:withdraw:<orderId>:request`
- withdraw settle: `wallet:withdraw:<orderId>:settle`
- transfer ledger: `wallet:transfer:<orderId>`
- market escrow: `market-order:<orderId>:escrow`
- market release: `market-order:<orderId>:release`
- market refund: `market-order:<orderId>:refund`
- reward issue or delta: `wallet:reward:<sourceType>:<sourceId>:<direction>`

For rewards, `sourceId` is the stable internal source event or projection id currently supplied to `WalletRewardService` through its `requestId` argument. Callers must provide a namespaced internal id, not a raw public HTTP idempotency key.

The wallet ledger must validate existing transactions on replay. Returning an existing `WalletTxnResult` is allowed only when all of the following match:

- `txn_type`
- `biz_type`
- `biz_id`
- `amount`
- entry multiset of `(account_id, direction, amount)`

If any field differs, throw `WalletErrorCode.REQUEST_REPLAY_CONFLICT`.

## 11. Service Design

### 11.1 Shared Resolver

Add a small application-layer helper for resolving the effective idempotency key from header and body.

The helper returns:

- effective key
- whether the key came from header or body fallback

It does not call `IdempotencyGuard`. It only normalizes and validates the API input.

### 11.2 Wallet Controller and Application Service

Wallet controller methods read:

```java
@RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey
```

The application service resolves the effective key and calls `IdempotencyGuard` with:

- operation name
- current user id
- effective key
- request fingerprint
- domain replay-conflict error code
- response type
- supplier containing the existing wallet service call

Wallet domain services receive the effective key as their public replay key.

### 11.3 Wallet Domain Services

`RechargeService`, `WithdrawService`, and `TransferService` must create or load their order before posting ledger entries when a stable order id is needed for the canonical wallet command id.

Required shape:

1. validate request
2. create or load actor-scoped order by effective key
3. verify replay semantics
4. derive canonical ledger request id from order id
5. post ledger command
6. transition order status
7. return order response

`TransferService` currently posts to the ledger before inserting `transfer_order`. It must insert or load the transfer order first so the ledger request id can be `wallet:transfer:<orderId>`.

### 11.4 Market Order Service

Market order creation receives the effective key from the application service.

Required shape:

1. validate request
2. look up existing order by `(buyerUserId, effectiveKey)`
3. if existing, verify replay semantics and return it
4. generate `orderId` before wallet escrow is requested
5. use `market-order:<orderId>:escrow` for wallet escrow
6. insert `market_order.request_id = effectiveKey`
7. return the order response

If this is implemented before the full market wallet saga command design, the synchronous escrow call still uses the order-id-based canonical wallet request id. If the saga design is implemented first, this spec uses the saga command id rules unchanged.

### 11.5 Wallet Ledger Service

Introduce a ledger command object instead of passing only raw `requestId`, `txnType`, and postings.

Required fields:

- `requestId`
- `txnType`
- `bizType`
- `bizId`
- `postings`

`WalletLedgerService.post(command)` writes these fields to `wallet_txn`.

On existing `requestId`, it loads the existing transaction and entries, compares them against the command, and returns the existing result only if the semantic replay matches.

## 12. Error Semantics

Missing effective key returns `400`.

Header/body key mismatch returns `400`.

Concurrent same operation/user/key/fingerprint returns `409`.

Successful replay with same operation/user/key/fingerprint returns cached response.

Replay with same operation/user/key but different fingerprint returns the domain replay-conflict error code supplied to `IdempotencyGuard`.

Wallet ledger replay mismatch returns `409 REQUEST_REPLAY_CONFLICT`.

Database duplicate-key races are handled by reloading the scoped business row or canonical wallet transaction and applying the same replay validation.

## 13. Migration Plan

### 13.1 Phase 1: Safe Ledger Replay

Add semantic replay validation to `WalletLedgerService`.

This phase reduces the highest-risk defect even before the public API contract changes.

### 13.2 Phase 2: Guard Fingerprint Support

Add optional request fingerprint support to `IdempotencyGuard`, `IdempotencyStore`, `JdbcIdempotencyStore`, and `RedisIdempotencyStore`.

Existing content write APIs continue using the current overload.

### 13.3 Phase 3: Public API Compatibility Layer

Add effective-key resolution for wallet and market order creation.

Update controllers to read `Idempotency-Key`.

Keep body `requestId` accepted as fallback only when the header is absent.

### 13.4 Phase 4: Scoped Business Uniqueness

Add migration SQL to:

- add `http_idempotency.request_hash`
- drop global unique keys on public wallet and market order `request_id`
- add actor-scoped unique keys
- keep `wallet_txn.request_id` globally unique

Update `backend/community-app/src/test/resources/schema.sql` in the same shape.

### 13.5 Phase 5: Canonical Wallet Command IDs

Change wallet and market services to derive ledger request ids from durable server-side ids.

This phase removes client key collision risk from `wallet_txn`.

### 13.6 Phase 6: Documentation and Deprecation

Update:

- `docs/SYSTEM_DESIGN.md`
- `docs/SECURITY.md`
- `docs/business-logic/wallet-ledger-flow.md`
- `docs/business-logic/market-order-dispute-flow.md`

Docs must state:

- new clients use `Idempotency-Key`
- body `requestId` is compatibility-only
- wallet ledger request ids are server-generated canonical command ids
- replay mismatches return conflict

Body `requestId` removal is a separate cleanup release after fallback telemetry reaches zero for a full release cycle.

## 14. Testing Requirements

### 14.1 Idempotency Guard

Cover:

- missing key still returns `400`
- same operation/user/key/hash returns cached response after success
- same operation/user/key with different hash returns `409`
- processing same operation/user/key/hash returns `409`
- old overload without hash still supports content APIs

### 14.2 Wallet API

Cover each wallet endpoint:

- header-only request succeeds
- body-only request succeeds as compatibility fallback
- header and equal body request succeeds
- header and different body request returns `400`
- same user and same key with same body replays successfully
- same user and same key with different body returns `409`
- different users can use the same key without collision

### 14.3 Wallet Ledger

Cover:

- exact semantic replay returns existing transaction
- same request id with different txn type returns `REQUEST_REPLAY_CONFLICT`
- same request id with different amount returns `REQUEST_REPLAY_CONFLICT`
- same request id with different entry account returns `REQUEST_REPLAY_CONFLICT`
- transfer creates order before ledger post and uses `wallet:transfer:<orderId>`

### 14.4 Market Order

Cover:

- header-only order creation succeeds
- body-only order creation succeeds as compatibility fallback
- header/body mismatch returns `400`
- same buyer/key/body replays successfully
- same buyer/key with different quantity or listing returns `409`
- different buyers can use the same key without collision
- escrow wallet request id uses `market-order:<orderId>:escrow`

### 14.5 Migration

Cover schema tests or repository tests proving:

- actor-scoped duplicate keys are rejected
- same key for different actors is allowed
- `wallet_txn.request_id` remains globally unique
- `http_idempotency.request_hash` is persisted and read

## 15. Rollout and Compatibility

During compatibility, existing clients can continue sending body `requestId`.

Server logs should record fallback usage with:

- endpoint operation
- authenticated user id
- key source: `header`, `body_fallback`, or `header_body_equal`

Fallback usage should be observable before removing body `requestId`.

The final API contract is header-only for idempotency. Body `requestId` remains only in responses or is removed in a later API cleanup.

## 16. Acceptance Criteria

1. Wallet and market order public writes accept `Idempotency-Key`.
2. The affected endpoints no longer require body `requestId` for new clients.
3. Body `requestId` fallback is deterministic and rejects mismatched header/body keys.
4. Public idempotency scope is `operation + user + key`.
5. Reusing the same key with different wallet or market order parameters returns conflict.
6. Different users can reuse the same key without colliding in wallet or market order tables.
7. `WalletLedgerService` never returns an existing transaction unless the requested ledger command semantically matches it.
8. Market wallet command ids are based on order id, not client idempotency key.
9. Focused wallet, market, idempotency, and schema tests pass.
10. Docs describe one public idempotency contract and the temporary body fallback.

## 17. Implementation Order Recommendation

1. Add wallet ledger semantic replay validation.
2. Extend `IdempotencyGuard` with optional request fingerprint support.
3. Add effective-key resolver and wire wallet endpoints.
4. Change wallet business table lookup and uniqueness to actor-scoped keys.
5. Change wallet services to use canonical ledger command ids.
6. Wire market order creation to the resolver and guard.
7. Change market order lookup and uniqueness to buyer-scoped keys.
8. Change market escrow id to `market-order:<orderId>:escrow`.
9. Update tests.
10. Update docs.

This order fixes the most dangerous money-ledger behavior before broadening the public API surface.

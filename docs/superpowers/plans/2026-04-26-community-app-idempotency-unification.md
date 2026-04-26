# Community App Idempotency Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Unify wallet and market order public write idempotency on `Idempotency-Key`, keep body `requestId` as a compatibility fallback, and make wallet ledger replay validation semantic.

**Architecture:** Keep `IdempotencyGuard` as the public HTTP entry point and add optional request fingerprints to the existing store abstraction. Wallet and market application services resolve the effective public key, compute canonical request hashes, and call the guard before domain services. Domain tables store the effective public key with actor-scoped uniqueness, while wallet ledger rows use server-generated canonical command ids based on durable order ids.

**Tech Stack:** Spring Boot MVC, MyBatis XML mappers, H2 test schema, MySQL bootstrap SQL, JUnit 5, Mockito, AssertJ, Maven.

---

## File Map

### New Production Files

- `backend/community-app/src/main/java/com/nowcoder/community/infra/idempotency/EffectiveIdempotencyKey.java`
  Responsibility: immutable value object containing the resolved effective key and source.
- `backend/community-app/src/main/java/com/nowcoder/community/infra/idempotency/IdempotencyKeyResolver.java`
  Responsibility: trim header/body keys, reject mismatches or missing keys, and return `HEADER`, `BODY_FALLBACK`, or `HEADER_BODY_EQUAL`.
- `backend/community-app/src/main/java/com/nowcoder/community/infra/idempotency/RequestFingerprint.java`
  Responsibility: SHA-256 canonical string hashing used by financial and order APIs.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/WalletLedgerCommand.java`
  Responsibility: command object for wallet ledger posting with `requestId`, `txnType`, `bizType`, `bizId`, and postings.

### Modified Production Files

- `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyStore.java`
  Responsibility: add fingerprint-aware overloads while preserving existing content API methods.
- `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`
  Responsibility: add fingerprint-aware `executeRequired`, compare replay hashes before replaying cached responses, and emit replay-conflict errors.
- `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/JdbcIdempotencyStore.java`
  Responsibility: persist/read `request_hash` in `http_idempotency`.
- `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/RedisIdempotencyStore.java`
  Responsibility: encode/decode request hashes in Redis values without changing Redis keys.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/controller/WalletController.java`
  Responsibility: read `Idempotency-Key` for recharge, withdraw, and transfer.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletApplicationService.java`
  Responsibility: resolve effective keys, compute wallet fingerprints, call `IdempotencyGuard`.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateRechargeRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateWithdrawRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/dto/CreateTransferRequest.java`
  Responsibility: make body `requestId` optional for header-only clients.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletLedgerService.java`
  Responsibility: accept `WalletLedgerCommand`, write command biz fields, and validate semantic replays.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/RechargeService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WithdrawService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/TransferService.java`
  Responsibility: load by actor-scoped public key and use canonical ledger request ids based on order id.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketApplicationService.java`
  Responsibility: pass market biz ids into `WalletLedgerCommand`.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/mapper/RechargeOrderMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/mapper/WithdrawOrderMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/mapper/TransferOrderMapper.java`
- `backend/community-app/src/main/resources/mapper/recharge_order_mapper.xml`
- `backend/community-app/src/main/resources/mapper/withdraw_order_mapper.xml`
- `backend/community-app/src/main/resources/mapper/transfer_order_mapper.xml`
  Responsibility: add actor-scoped request lookups and status updates.
- `backend/community-app/src/main/java/com/nowcoder/community/market/controller/MarketController.java`
  Responsibility: read `Idempotency-Key` for order creation.
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketApplicationService.java`
  Responsibility: resolve effective key, compute market fingerprint, call `IdempotencyGuard`.
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/CreateMarketOrderRequest.java`
  Responsibility: make body `requestId` optional for header-only clients.
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
  Responsibility: use buyer-scoped request lookup and order-id-based escrow/refund wallet ids.
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketOrderMapper.java`
- `backend/community-app/src/main/resources/mapper/market_order_mapper.xml`
  Responsibility: add buyer-scoped request lookups.
- `backend/community-app/src/test/resources/schema.sql`
- `deploy/mysql/community/010_schema_shared.sql`
- `deploy/mysql/community/031_schema_growth_wallet.sql`
- `deploy/mysql/community/032_schema_growth_market.sql`
  Responsibility: add `http_idempotency.request_hash` and actor-scoped public request unique keys.
- `docs/SYSTEM_DESIGN.md`
- `docs/SECURITY.md`
- `docs/business-logic/wallet-ledger-flow.md`
- `docs/business-logic/market-order-dispute-flow.md`
  Responsibility: document the unified public contract, fallback period, request fingerprints, and canonical wallet command ids.

### Modified Test Files

- `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardStoreFailureTest.java`
- `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/JdbcIdempotencyStoreTest.java`
- `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/RedisIdempotencyStoreTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencyGuardSerializationFailureTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletLedgerServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/RechargeServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WithdrawServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/TransferServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`
- Mapper/schema persistence tests where assertions reference old global request uniqueness.

---

### Task 1: Harden Wallet Ledger Semantic Replay

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/wallet/model/WalletLedgerCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletLedgerService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletLedgerServiceTest.java`

- [x] **Step 1: Write failing wallet ledger replay tests**

Add tests to `WalletLedgerServiceTest`:

```java
@Test
void postShouldRejectReplayWithDifferentTxnType() {
    UUID userAccountId = service.ensureUserWallet(uuid(101));
    UUID systemAccountId = service.ensureSystemAccount("ORDER_ESCROW");
    service.post(new WalletLedgerCommand(
            "wallet:semantic:1",
            WalletTxnType.ORDER_ESCROW,
            WalletTxnType.ORDER_ESCROW.name(),
            "market-order:1",
            List.of(WalletPosting.debit(userAccountId, 100), WalletPosting.credit(systemAccountId, 100))
    ));

    assertThatThrownBy(() -> service.post(new WalletLedgerCommand(
            "wallet:semantic:1",
            WalletTxnType.ORDER_REFUND,
            WalletTxnType.ORDER_REFUND.name(),
            "market-order:1",
            List.of(WalletPosting.debit(systemAccountId, 100), WalletPosting.credit(userAccountId, 100))
    ))).isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT));
}

@Test
void postShouldRejectReplayWithDifferentEntryAccount() {
    UUID firstUserAccountId = service.ensureUserWallet(uuid(101));
    UUID secondUserAccountId = service.ensureUserWallet(uuid(202));
    UUID systemAccountId = service.ensureSystemAccount("ORDER_ESCROW");
    service.post(new WalletLedgerCommand(
            "wallet:semantic:2",
            WalletTxnType.ORDER_ESCROW,
            WalletTxnType.ORDER_ESCROW.name(),
            "market-order:2",
            List.of(WalletPosting.debit(firstUserAccountId, 100), WalletPosting.credit(systemAccountId, 100))
    ));

    assertThatThrownBy(() -> service.post(new WalletLedgerCommand(
            "wallet:semantic:2",
            WalletTxnType.ORDER_ESCROW,
            WalletTxnType.ORDER_ESCROW.name(),
            "market-order:2",
            List.of(WalletPosting.debit(secondUserAccountId, 100), WalletPosting.credit(systemAccountId, 100))
    ))).isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT));
}
```

- [x] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -pl community-app -am -Dtest=WalletLedgerServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `WalletLedgerCommand` does not exist and `WalletLedgerService.post(command)` is not implemented.

- [x] **Step 3: Implement command object and replay validation**

Implement `WalletLedgerCommand` as:

```java
public record WalletLedgerCommand(
        String requestId,
        WalletTxnType txnType,
        String bizType,
        String bizId,
        List<WalletPosting> postings
) {
}
```

In `WalletLedgerService`, keep the old overload and delegate:

```java
public WalletTxnResult post(String requestId, WalletTxnType txnType, List<WalletPosting> postings) {
    return post(new WalletLedgerCommand(requestId, txnType, txnType.name(), requestId, postings));
}
```

The new `post(WalletLedgerCommand command)` validates all command fields, persists `bizType` and `bizId`, and calls `ensureReplayMatches(existing, command)` before returning an existing transaction. `ensureReplayMatches` compares `txnType`, `bizType`, `bizId`, amount, and sorted entry specs of `accountId + direction + amount`; mismatches throw `WalletErrorCode.REQUEST_REPLAY_CONFLICT`.

- [x] **Step 4: Verify GREEN**

Run:

```bash
cd backend
mvn -pl community-app -am -Dtest=WalletLedgerServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

---

### Task 2: Add Idempotency Fingerprints To Guard And Stores

**Files:**
- Modify: common idempotency guard/store classes and tests listed in the file map.
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `deploy/mysql/community/010_schema_shared.sql`

- [x] **Step 1: Write failing guard/store tests**

Add tests proving:

```java
guard.executeRequired("wallet:recharge", USER_ID, "k1", "hash-a", WalletErrorCode.REQUEST_REPLAY_CONFLICT, String.class, () -> "OK");
```

stores `"hash-a"` while processing and success, and a subsequent call with `"hash-b"` throws `WalletErrorCode.REQUEST_REPLAY_CONFLICT` without invoking the supplier.

Add JDBC assertions that insert/update SQL includes `request_hash`, and Redis assertions that fingerprint-aware calls store `P\nhash-a` and `S\nhash-a\n"OK"` while old overloads still store `P` and `S\n...`.

- [x] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -pl community-common/common-idempotency,community-app -Dtest=IdempotencyGuardStoreFailureTest,JdbcIdempotencyStoreTest,RedisIdempotencyStoreTest,IdempotencyGuardSerializationFailureTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because fingerprint overloads and `request_hash` are missing.

- [x] **Step 3: Implement fingerprint-aware APIs**

Add default overloads to `IdempotencyStore`:

```java
default boolean tryAcquireProcessing(String operation, UUID userId, String key, String requestHash, Duration ttl) {
    return tryAcquireProcessing(operation, userId, key, ttl);
}

default void saveSuccess(String operation, UUID userId, String key, String requestHash, String successJson, Duration ttl) {
    saveSuccess(operation, userId, key, successJson, ttl);
}

record Entry(Status status, String successJson, String requestHash) {
    public Entry(Status status, String successJson) {
        this(status, successJson, null);
    }
}
```

In `IdempotencyGuard`, add:

```java
public <T> T executeRequired(String operation, UUID userId, String idempotencyKey, String requestHash,
                             ErrorCode replayConflictCode, Class<T> type, Supplier<T> supplier)
```

and route the current overload through an internal method with `requestHash = null`. Compare `existing.requestHash()` to the normalized request hash before `SUCCESS` or `PROCESSING` handling when a hash is supplied.

- [x] **Step 4: Update schemas**

Add nullable `request_hash varchar(64)` to `http_idempotency` in H2 and MySQL bootstrap SQL. Keep `uk_http_idem (operation, user_id, idem_key)`.

- [x] **Step 5: Verify GREEN**

Run the same Maven command from Step 2. Expected: PASS.

---

### Task 3: Resolve Effective Keys And Guard Wallet APIs

**Files:**
- Create infra idempotency helper classes listed in the file map.
- Modify wallet DTOs, controller, application service, and `WalletControllerTest`.

- [x] **Step 1: Write failing resolver and wallet controller tests**

Add tests for each wallet endpoint proving:

```java
.header(IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, "recharge:key-1")
.content("{\"amount\":1200}")
```

succeeds and delegates with effective key `recharge:key-1`; body-only succeeds as fallback; equal header/body succeeds; different header/body returns `400`; same key with different semantic body returns `409` from the guard.

- [x] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -pl community-app -am -Dtest=WalletControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the controller does not read `Idempotency-Key`, request DTOs still require body `requestId`, and the application service does not call the guard.

- [x] **Step 3: Implement resolver and wallet guard wiring**

Use `IdempotencyKeyResolver.resolve(headerKey, bodyRequestId)` in `WalletApplicationService`. Call:

```java
idempotencyGuard.executeRequired(
        "wallet:recharge",
        userId,
        effective.value(),
        RequestFingerprint.sha256("wallet:recharge|amount=" + request.getAmount()),
        WalletErrorCode.REQUEST_REPLAY_CONFLICT,
        CreateRechargeResponse.class,
        () -> rechargeService.complete(effective.value(), userId, request.getAmount())
);
```

Use canonical strings from the spec for withdraw and transfer. Log fallback source with operation, user id, and source value.

- [x] **Step 4: Verify GREEN**

Run the WalletControllerTest command. Expected: PASS.

---

### Task 4: Scope Wallet Business Keys And Use Canonical Ledger IDs

**Files:**
- Modify wallet domain services, mappers, mapper XML, schema SQL, and service tests.

- [x] **Step 1: Write failing wallet domain/schema tests**

Add tests proving:

```java
rechargeService.complete("same-key", uuid(101), 1200);
rechargeService.complete("same-key", uuid(202), 1300);
```

creates two `recharge_order` rows and two canonical `wallet_txn.request_id` values: `wallet:recharge:<orderId>`. Add equivalent withdraw and transfer tests. Add transfer assertion that the `transfer_order` row exists before ledger posting by verifying the mocked ledger receives `wallet:transfer:<orderId>`.

- [x] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -pl community-app -am -Dtest=RechargeServiceTest,WithdrawServiceTest,TransferServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because mappers still use global `request_id` lookups and ledger request ids are public keys.

- [x] **Step 3: Implement actor-scoped wallet lookups**

Add mapper methods:

```java
RechargeOrder selectByUserIdAndRequestId(@Param("userId") UUID userId, @Param("requestId") String requestId);
WithdrawOrder selectByUserIdAndRequestId(@Param("userId") UUID userId, @Param("requestId") String requestId);
TransferOrder selectByFromUserIdAndRequestId(@Param("fromUserId") UUID fromUserId, @Param("requestId") String requestId);
```

Use these methods in create/load/replay paths and duplicate-key reloads. Update `updateStatus` SQL to include actor id where the service has it.

- [x] **Step 4: Implement canonical wallet command ids**

Use:

```java
"wallet:recharge:" + order.getOrderId()
"wallet:withdraw:" + order.getOrderId() + ":request"
"wallet:withdraw:" + order.getOrderId() + ":settle"
"wallet:transfer:" + order.getOrderId()
```

Pass `WalletLedgerCommand` with `bizType = WalletTxnType.<TYPE>.name()` and `bizId = order.getOrderId().toString()`.

- [x] **Step 5: Update wallet schemas**

Change unique keys to `unique(user_id, request_id)` for recharge and withdraw, `unique(from_user_id, request_id)` for transfer. Keep `wallet_txn.request_id` globally unique and keep `wallet_admin_action.request_id` globally unique.

- [x] **Step 6: Verify GREEN**

Run the wallet service test command. Expected: PASS.

---

### Task 5: Guard Market Order Creation And Use Buyer-Scoped Keys

**Files:**
- Modify market DTO, controller, application service, order service, mapper/XML, schema SQL, controller/service tests.

- [x] **Step 1: Write failing market API/domain tests**

Add tests proving header-only market order creation succeeds through the guard, body fallback succeeds, header/body mismatch returns `400`, same buyer/key with changed listing or quantity returns `409`, different buyers can reuse the same key, and `escrowOrder` receives `market-order:<orderId>:escrow`.

- [x] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -pl community-app -am -Dtest=MarketControllerTest,MarketOrderServiceUnitTest,MarketOrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because market order creation still requires body `requestId`, uses global lookups, and escrow id is based on the public key.

- [x] **Step 3: Implement market guard wiring**

In `MarketApplicationService.createOrder`, resolve the effective key and call:

```java
idempotencyGuard.executeRequired(
        "market:create_order",
        buyerUserId,
        effective.value(),
        RequestFingerprint.sha256("market:create_order|listingId=" + request.getListingId()
                + "|quantity=" + request.getQuantity()
                + "|addressId=" + (request.getAddressId() == null ? "" : request.getAddressId())),
        MarketErrorCode.REQUEST_REPLAY_CONFLICT,
        MarketOrderResponse.class,
        () -> marketOrderService.createOrder(effective.value(), buyerUserId, request.getListingId(), request.getQuantity(), request.getAddressId())
);
```

- [x] **Step 4: Implement buyer-scoped market lookups and canonical escrow/refund ids**

Add mapper methods:

```java
MarketOrder selectByBuyerUserIdAndRequestId(@Param("buyerUserId") UUID buyerUserId, @Param("requestId") String requestId);
MarketOrder selectByBuyerUserIdAndRequestIdForUpdate(@Param("buyerUserId") UUID buyerUserId, @Param("requestId") String requestId);
```

Generate `orderId` before escrow and call:

```java
walletMarketActionApi.escrowOrder("market-order:" + orderId + ":escrow", buyerUserId, totalAmount, "market-order:" + orderId);
```

Update `cancelOrder` to use `"market-order:" + order.getOrderId() + ":refund"`.

- [x] **Step 5: Update market schema**

Change `market_order` unique key to `unique(buyer_user_id, request_id)` in H2 and MySQL bootstrap SQL.

- [x] **Step 6: Verify GREEN**

Run the market test command. Expected: PASS.

---

### Task 6: Documentation And Focused Regression Verification

**Files:**
- Modify docs listed in the file map.

- [x] **Step 1: Update docs**

Document these exact rules:

- New clients send `Idempotency-Key` for wallet recharges, withdrawals, transfers, and market order creation.
- Body `requestId` is compatibility-only and is used only when the header is absent.
- Header/body mismatches return `400`.
- Replays with changed semantic parameters return the wallet or market replay-conflict code.
- Wallet ledger request ids are server-generated canonical command ids.

- [x] **Step 2: Run focused verification**

Run:

```bash
cd backend
mvn -pl community-common/common-idempotency,community-app -am -Dtest=IdempotencyGuardStoreFailureTest,JdbcIdempotencyStoreTest,RedisIdempotencyStoreTest,IdempotencyGuardSerializationFailureTest,WalletControllerTest,WalletLedgerServiceTest,RechargeServiceTest,WithdrawServiceTest,TransferServiceTest,MarketControllerTest,MarketOrderServiceUnitTest,MarketOrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [x] **Step 3: Review spec coverage**

Check acceptance criteria from `docs/superpowers/specs/2026-04-25-community-app-idempotency-unification-design.md` against the diff. Confirm all criteria except eventual removal of body fallback are represented in code, tests, schemas, or docs.

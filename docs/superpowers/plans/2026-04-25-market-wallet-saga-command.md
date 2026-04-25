# Market Wallet Saga Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace synchronous market-to-wallet writes with a durable `market_wallet_action` command table and an explicit market order saga state machine.

**Architecture:** Market writes local order/dispute/inventory state and durable wallet commands in its own transaction. A dedicated processor claims wallet commands, calls `WalletMarketActionApi` outside the original market transaction, and advances order/dispute state with idempotent conditional updates. Wallet keeps its own ledger transaction boundary and gains stricter request replay validation.

**Tech Stack:** Spring Boot, Spring transactions, MyBatis XML mappers, JdbcTemplate tests, JUnit 5, Mockito, AssertJ, Maven, XXL Job

---

## File Map

### New Production Files

- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketWalletAction.java`
  Responsibility: persisted command row for market escrow, release, and refund actions.
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketWalletActionType.java`
  Responsibility: action type constants `ESCROW`, `RELEASE`, `REFUND`.
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketWalletActionStatus.java`
  Responsibility: action status constants `PENDING`, `PROCESSING`, `RETRYING`, `SUCCEEDED`, `CANCELLED`, `FAILED`, `DEAD`.
- `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketWalletActionResultType.java`
  Responsibility: result type constants `APPLIED`, `NOOP`.
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketWalletActionMapper.java`
  Responsibility: MyBatis mapper interface for command insert, claim, retry, success, no-op, cancel, failure, and recovery queries.
- `backend/community-app/src/main/resources/mapper/market_wallet_action_mapper.xml`
  Responsibility: SQL for `market_wallet_action`.
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketWalletActionService.java`
  Responsibility: enqueue deterministic escrow/release/refund commands and provide idempotent command state transitions.
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketWalletActionProcessor.java`
  Responsibility: claim due actions, invoke wallet API, classify errors, and delegate saga state advancement.
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderSagaService.java`
  Responsibility: conditional order/dispute/inventory transitions after wallet action results.
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketWalletActionRecoveryService.java`
  Responsibility: reconcile stuck action/order pairs and recover expired processing leases.
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketWalletActionProcessorHandler.java`
  Responsibility: XXL Job entrypoint to process due market wallet actions.
- `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketWalletActionRecoveryHandler.java`
  Responsibility: XXL Job entrypoint to run reconciliation.

### Modified Production Files

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletLedgerService.java`
  Responsibility: add replay validation and a `bizId`-aware `post` overload.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketApplicationService.java`
  Responsibility: pass wallet `bizId` into ledger posting.
- `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketOrder.java`
  Responsibility: accept new pending statuses through existing `status` string field.
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketOrderMapper.java`
  Responsibility: add conditional status/txn update methods for saga transitions.
- `backend/community-app/src/main/resources/mapper/market_order_mapper.xml`
  Responsibility: SQL for conditional order transitions and pending-state scans.
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketInventoryUnitMapper.java`
  Responsibility: add idempotent release/delivery updates usable by compensation.
- `backend/community-app/src/main/resources/mapper/market_inventory_unit_mapper.xml`
  Responsibility: SQL for idempotent inventory release and delivery updates.
- `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketDisputeMapper.java`
  Responsibility: add conditional dispute resolution update methods.
- `backend/community-app/src/main/resources/mapper/market_dispute_mapper.xml`
  Responsibility: SQL for pending and final dispute resolution.
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
  Responsibility: enqueue wallet commands instead of calling wallet directly.
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketDisputeService.java`
  Responsibility: record dispute decisions and enqueue wallet commands.
- `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java`
  Responsibility: expose pending statuses without treating them as missing/invalid.
- `backend/community-app/src/main/java/com/nowcoder/community/market/dto/MarketOrderResponse.java`
  Responsibility: continue returning raw status strings for pending states.
- `backend/community-app/src/main/java/com/nowcoder/community/market/api/model/MarketOrderAutoConfirmResult.java`
  Responsibility: keep the two-count response; document `completedCount` as queued release count in this implementation.
- `backend/community-app/src/test/resources/schema.sql`
  Responsibility: add test schema for `market_wallet_action`.
- `deploy/mysql/community/032_schema_growth_market.sql`
  Responsibility: add production bootstrap schema for `market_wallet_action`.
- `docs/business-logic/market-order-dispute-flow.md`
  Responsibility: document pending money states and processor-driven finalization.
- `docs/business-logic/wallet-ledger-flow.md`
  Responsibility: document stricter wallet replay validation.

### New Test Files

- `backend/community-app/src/test/java/com/nowcoder/community/market/mapper/MarketWalletActionMapperPersistenceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionProcessorTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderSagaServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionRecoveryServiceTest.java`

### Modified Test Files

- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletLedgerServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketDisputeServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandlerTest.java`

---

### Task 1: Harden Wallet Request Replay Validation

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletLedgerService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletLedgerServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketApplicationServiceTest.java`

- [ ] **Step 1: Add failing tests for replay mismatch**

Add these tests to `WalletLedgerServiceTest`:

```java
@Test
void postShouldRejectReplayWithDifferentTxnType() {
    UUID firstAccountId = accountService.ensureUserWallet(uuid(7));
    UUID secondAccountId = accountService.ensureSystemAccount("ORDER_ESCROW");

    ledgerService.post(
            "wallet:replay:type",
            WalletTxnType.ORDER_ESCROW,
            "market-order:1",
            List.of(WalletPosting.debit(firstAccountId, 100L), WalletPosting.credit(secondAccountId, 100L))
    );

    assertThatThrownBy(() -> ledgerService.post(
            "wallet:replay:type",
            WalletTxnType.ORDER_REFUND,
            "market-order:1",
            List.of(WalletPosting.debit(secondAccountId, 100L), WalletPosting.credit(firstAccountId, 100L))
    )).isInstanceOf(BusinessException.class)
            .hasMessageContaining("wallet request replay conflict");
}

@Test
void postShouldRejectReplayWithDifferentAmountOrBizId() {
    UUID firstAccountId = accountService.ensureUserWallet(uuid(7));
    UUID secondAccountId = accountService.ensureSystemAccount("ORDER_ESCROW");

    ledgerService.post(
            "wallet:replay:amount",
            WalletTxnType.ORDER_ESCROW,
            "market-order:1",
            List.of(WalletPosting.debit(firstAccountId, 100L), WalletPosting.credit(secondAccountId, 100L))
    );

    assertThatThrownBy(() -> ledgerService.post(
            "wallet:replay:amount",
            WalletTxnType.ORDER_ESCROW,
            "market-order:2",
            List.of(WalletPosting.debit(firstAccountId, 200L), WalletPosting.credit(secondAccountId, 200L))
    )).isInstanceOf(BusinessException.class)
            .hasMessageContaining("wallet request replay conflict");
}
```

- [ ] **Step 2: Run wallet tests to verify failure**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=WalletLedgerServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `WalletLedgerService.post` has no `bizId` overload and replay validation accepts any existing `requestId`.

- [ ] **Step 3: Implement wallet replay validation**

In `WalletLedgerService`, keep the current public method and add the overload:

```java
@Transactional
public WalletTxnResult post(String requestId, WalletTxnType txnType, List<WalletPosting> postings) {
    return post(requestId, txnType, requestId, postings);
}

@Transactional
public WalletTxnResult post(String requestId, WalletTxnType txnType, String bizId, List<WalletPosting> postings) {
    validateRequest(requestId, txnType, postings);
    if (bizId == null || bizId.isBlank()) {
        throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "bizId must not be blank");
    }
    requireBalanced(requestId, postings);

    WalletTxn existing = walletTxnMapper.selectByRequestId(requestId);
    if (existing != null) {
        ensureReplayMatches(existing, txnType, bizId, postings);
        return new WalletTxnResult(existing.getTxnId(), existing.getStatus());
    }

    WalletTxn txn = new WalletTxn();
    txn.setTxnId(idGenerator.next());
    txn.setRequestId(requestId);
    txn.setTxnType(txnType.name());
    txn.setBizType(txnType.name());
    txn.setBizId(bizId.trim());
    txn.setStatus(TXN_STATUS_PENDING);
    txn.setAmount(postings.stream().mapToLong(WalletPosting::amount).sum() / 2);
    insertTxnAndEntries(txn, postings);
    return new WalletTxnResult(txn.getTxnId(), TXN_STATUS_SUCCEEDED);
}
```

Add helper methods in the same class:

```java
private void ensureReplayMatches(WalletTxn existing, WalletTxnType txnType, String bizId, List<WalletPosting> postings) {
    long amount = postings.stream().mapToLong(WalletPosting::amount).sum() / 2;
    boolean matches = existing.getTxnType().equals(txnType.name())
            && existing.getBizId().equals(bizId.trim())
            && existing.getAmount() == amount;
    if (!matches) {
        throw new BusinessException(
                WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                "wallet request replay conflict: requestId=" + existing.getRequestId()
        );
    }
}
```

Keep the existing duplicate-insert catch path, but call `ensureReplayMatches` before returning the duplicate.

- [ ] **Step 4: Pass market wallet biz id into ledger**

In `WalletMarketApplicationService`, change the three calls:

```java
WalletTxnResult result = walletLedgerService.post(
        requestId,
        WalletTxnType.ORDER_ESCROW,
        bizId,
        List.of(
                WalletPosting.debit(walletAccountService.ensureUserWallet(buyerUserId), amount),
                WalletPosting.credit(walletAccountService.ensureSystemAccount(ESCROW_ACCOUNT_TYPE), amount)
        )
);
```

Apply the same `bizId` argument for `ORDER_RELEASE` and `ORDER_REFUND`.

- [ ] **Step 5: Run wallet verification**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=WalletLedgerServiceTest,WalletMarketApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletLedgerService.java \
        backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMarketApplicationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletLedgerServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletMarketApplicationServiceTest.java
git commit -m "feat: harden wallet request replay validation"
```

---

### Task 2: Add Market Wallet Action Persistence

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketWalletAction.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketWalletActionType.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketWalletActionStatus.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketWalletActionResultType.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketWalletActionMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/market_wallet_action_mapper.xml`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/mapper/MarketWalletActionMapperPersistenceTest.java`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `deploy/mysql/community/032_schema_growth_market.sql`

- [ ] **Step 1: Add failing persistence test**

Create `MarketWalletActionMapperPersistenceTest`:

```java
@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class MarketWalletActionMapperPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MarketWalletActionMapper mapper;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from market_wallet_action");
    }

    @Test
    void insertAndSelectByRequestIdShouldRoundTripAction() {
        MarketWalletAction action = new MarketWalletAction();
        action.setActionId(uuid(101));
        action.setOrderId(uuid(201));
        action.setActionType("ESCROW");
        action.setRequestId("market-order:" + uuid(201) + ":escrow");
        action.setWalletBizId("market-order:" + uuid(201));
        action.setActorUserId(uuid(9));
        action.setCounterpartyUserId(uuid(7));
        action.setAmount(12_900L);
        action.setStatus("PENDING");

        mapper.insert(action);

        MarketWalletAction loaded = mapper.selectByRequestId(action.getRequestId());
        assertThat(loaded.getActionId()).isEqualTo(action.getActionId());
        assertThat(loaded.getStatus()).isEqualTo("PENDING");
        assertThat(loaded.getResultType()).isNull();
    }

    @Test
    void claimDueShouldMovePendingActionToProcessingWithLease() {
        MarketWalletAction action = pendingAction("market-order:" + uuid(202) + ":release", "RELEASE");
        mapper.insert(action);

        int updated = mapper.claimProcessing(action.getActionId(), Instant.parse("2026-04-25T10:00:00Z"));

        assertThat(updated).isEqualTo(1);
        MarketWalletAction loaded = mapper.selectById(action.getActionId());
        assertThat(loaded.getStatus()).isEqualTo("PROCESSING");
        assertThat(loaded.getProcessingLeaseUntil()).isNotNull();
    }
}
```

- [ ] **Step 2: Run persistence test to verify failure**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketWalletActionMapperPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the entity, mapper, and schema do not exist.

- [ ] **Step 3: Add schema**

Add this table to both `backend/community-app/src/test/resources/schema.sql` and `deploy/mysql/community/032_schema_growth_market.sql` after `market_order`:

```sql
create table if not exists market_wallet_action (
  action_id binary(16) primary key,
  order_id binary(16) not null,
  dispute_id binary(16) default null,
  action_type varchar(16) not null,
  request_id varchar(96) not null,
  wallet_biz_id varchar(96) not null,
  actor_user_id binary(16) not null,
  counterparty_user_id binary(16) default null,
  amount bigint not null,
  status varchar(16) not null,
  result_type varchar(16) default null,
  wallet_txn_id binary(16) default null,
  failure_code varchar(64) default null,
  last_error varchar(255) default null,
  retry_count int not null default 0,
  next_retry_at timestamp null default null,
  processing_lease_until timestamp null default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  constraint uk_market_wallet_action_request unique (request_id)
);

create index if not exists idx_market_wallet_action_status_next on market_wallet_action(status, next_retry_at, action_id);
create index if not exists idx_market_wallet_action_order_type on market_wallet_action(order_id, action_type);
```

Add `delete from market_wallet_action;` to test cleanup sections before deleting `market_order` in affected tests.

- [ ] **Step 4: Add model constants and entity**

Create the constants as final classes with private constructors:

```java
public final class MarketWalletActionStatus {
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String RETRYING = "RETRYING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";
    public static final String DEAD = "DEAD";

    private MarketWalletActionStatus() {
    }
}
```

Use the same shape for `MarketWalletActionType` and `MarketWalletActionResultType`.

Create `MarketWalletAction` with fields matching the schema and Java types `UUID`, `String`, `long`, `int`, `Instant`, and `Date` as used by local mapper conventions. Use JavaBean getters/setters.

- [ ] **Step 5: Add mapper interface and XML**

`MarketWalletActionMapper` must include:

```java
int insert(MarketWalletAction action);
MarketWalletAction selectById(@Param("actionId") UUID actionId);
MarketWalletAction selectByRequestId(@Param("requestId") String requestId);
List<MarketWalletAction> selectDue(@Param("asOf") Instant asOf, @Param("limit") int limit);
int claimProcessing(@Param("actionId") UUID actionId, @Param("leaseUntil") Instant leaseUntil);
int markSucceeded(@Param("actionId") UUID actionId, @Param("walletTxnId") UUID walletTxnId, @Param("resultType") String resultType);
int markCancelled(@Param("actionId") UUID actionId, @Param("resultType") String resultType);
int markRetrying(@Param("actionId") UUID actionId, @Param("nextRetryAt") Instant nextRetryAt, @Param("lastError") String lastError);
int markFailed(@Param("actionId") UUID actionId, @Param("failureCode") String failureCode, @Param("lastError") String lastError);
int markDead(@Param("actionId") UUID actionId, @Param("lastError") String lastError);
int recoverExpiredProcessing(@Param("asOf") Instant asOf);
```

The claim SQL must be atomic:

```xml
<update id="claimProcessing">
    update market_wallet_action
    set status = 'PROCESSING',
        processing_lease_until = #{leaseUntil},
        update_time = current_timestamp
    where action_id = #{actionId, jdbcType=BINARY}
      and status in ('PENDING', 'RETRYING')
</update>
```

- [ ] **Step 6: Run persistence verification**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketWalletActionMapperPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/entity/MarketWalletAction.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketWalletActionType.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketWalletActionStatus.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/model/MarketWalletActionResultType.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketWalletActionMapper.java \
        backend/community-app/src/main/resources/mapper/market_wallet_action_mapper.xml \
        backend/community-app/src/test/java/com/nowcoder/community/market/mapper/MarketWalletActionMapperPersistenceTest.java \
        backend/community-app/src/test/resources/schema.sql \
        deploy/mysql/community/032_schema_growth_market.sql
git commit -m "feat: add market wallet action persistence"
```

---

### Task 3: Add Command Enqueue Service And Processor Skeleton

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketWalletActionService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketWalletActionProcessor.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionProcessorTest.java`

- [ ] **Step 1: Add failing unit tests for deterministic enqueue and claim**

In `MarketWalletActionServiceTest`, verify request ids:

```java
@Test
void enqueueEscrowShouldUseOrderIdBasedRequestId() {
    MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
    UuidV7Generator idGenerator = mock(UuidV7Generator.class);
    UUID actionId = uuid(1);
    UUID orderId = uuid(2);
    when(idGenerator.next()).thenReturn(actionId);

    MarketWalletActionService service = new MarketWalletActionService(mapper, idGenerator);
    service.enqueueEscrow(orderId, uuid(9), uuid(7), 12_900L);

    ArgumentCaptor<MarketWalletAction> captor = ArgumentCaptor.forClass(MarketWalletAction.class);
    verify(mapper).insert(captor.capture());
    assertThat(captor.getValue().getRequestId()).isEqualTo("market-order:" + orderId + ":escrow");
    assertThat(captor.getValue().getWalletBizId()).isEqualTo("market-order:" + orderId);
    assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
}
```

In `MarketWalletActionProcessorTest`, verify no-op handling for cancelled escrow:

```java
@Test
void processOneShouldNoopEscrowWhenSagaRejectsForwardAction() {
    MarketWalletActionMapper mapper = mock(MarketWalletActionMapper.class);
    MarketOrderSagaService sagaService = mock(MarketOrderSagaService.class);
    WalletMarketActionApi walletApi = mock(WalletMarketActionApi.class);
    MarketWalletAction action = escrowAction();
    when(mapper.claimProcessing(eq(action.getActionId()), any())).thenReturn(1);
    when(sagaService.canApplyEscrow(action.getOrderId())).thenReturn(false);

    MarketWalletActionProcessor processor = new MarketWalletActionProcessor(mapper, walletApi, sagaService, Clock.systemUTC());

    processor.processOne(action);

    verify(walletApi, never()).escrowOrder(any(), any(), anyLong(), any());
    verify(mapper).markCancelled(action.getActionId(), "NOOP");
}
```

- [ ] **Step 2: Run service tests to verify failure**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketWalletActionServiceTest,MarketWalletActionProcessorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because services do not exist.

- [ ] **Step 3: Implement `MarketWalletActionService`**

Public methods:

```java
MarketWalletAction enqueueEscrow(UUID orderId, UUID buyerUserId, UUID sellerUserId, long amount);
MarketWalletAction enqueueRelease(UUID orderId, UUID sellerUserId, UUID buyerUserId, long amount);
MarketWalletAction enqueueRefund(UUID orderId, UUID buyerUserId, UUID sellerUserId, long amount);
MarketWalletAction enqueueDisputeRefund(UUID orderId, UUID disputeId, UUID buyerUserId, UUID sellerUserId, long amount);
MarketWalletAction enqueueDisputeRelease(UUID orderId, UUID disputeId, UUID sellerUserId, UUID buyerUserId, long amount);
```

Each method must call a single private `enqueue` method that:

```java
private MarketWalletAction enqueue(UUID orderId,
                                   UUID disputeId,
                                   String actionType,
                                   UUID actorUserId,
                                   UUID counterpartyUserId,
                                   long amount) {
    String requestId = "market-order:" + orderId + ":" + actionType.toLowerCase(Locale.ROOT);
    MarketWalletAction existing = mapper.selectByRequestId(requestId);
    if (existing != null) {
        ensureReplayMatches(existing, orderId, disputeId, actionType, actorUserId, counterpartyUserId, amount);
        return existing;
    }
    MarketWalletAction action = new MarketWalletAction();
    action.setActionId(idGenerator.next());
    action.setOrderId(orderId);
    action.setDisputeId(disputeId);
    action.setActionType(actionType);
    action.setRequestId(requestId);
    action.setWalletBizId("market-order:" + orderId);
    action.setActorUserId(actorUserId);
    action.setCounterpartyUserId(counterpartyUserId);
    action.setAmount(amount);
    action.setStatus(MarketWalletActionStatus.PENDING);
    mapper.insert(action);
    return action;
}
```

- [ ] **Step 4: Implement processor skeleton**

`MarketWalletActionProcessor` must expose:

```java
public int processDue(int limit);
public boolean processOne(MarketWalletAction action);
```

The first version only claims and routes actions. It calls saga guards before wallet:

```java
if (MarketWalletActionType.ESCROW.equals(action.getActionType()) && !sagaService.canApplyEscrow(action.getOrderId())) {
    actionMapper.markCancelled(action.getActionId(), MarketWalletActionResultType.NOOP);
    sagaService.completeEscrowNoop(action.getOrderId());
    return true;
}
```

Use bounded retry classification:

```java
private boolean isRetryable(RuntimeException e) {
    return !(e instanceof BusinessException);
}
```

Business exceptions are handled by saga terminal failure methods for escrow, release, or refund.

- [ ] **Step 5: Run command service verification**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketWalletActionServiceTest,MarketWalletActionProcessorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketWalletActionService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketWalletActionProcessor.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionProcessorTest.java
git commit -m "feat: add market wallet command processor"
```

---

### Task 4: Implement Saga Transition Mapper Methods

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderSagaService.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderSagaServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketOrderMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/market_order_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketInventoryUnitMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/market_inventory_unit_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketListingMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/market_listing_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketDisputeMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/market_dispute_mapper.xml`

- [ ] **Step 1: Add failing saga transition tests**

Create tests that verify:

```java
@Test
void markEscrowSucceededShouldAdvanceOnlyFromEscrowPending() {
    seedOrder("ESCROW_PENDING");

    boolean advanced = sagaService.markEscrowSucceeded(orderId, escrowTxnId);

    assertThat(advanced).isTrue();
    assertThat(order(orderId).getStatus()).isEqualTo("ESCROWED");
    assertThat(order(orderId).getEscrowTxnId()).isEqualTo(escrowTxnId);
}

@Test
void markEscrowSucceededShouldNotOverwriteCancelledOrder() {
    seedOrder("ESCROW_CANCEL_PENDING");

    boolean advanced = sagaService.markEscrowSucceeded(orderId, escrowTxnId);

    assertThat(advanced).isFalse();
    assertThat(order(orderId).getEscrowTxnId()).isNull();
}

@Test
void markRefundSucceededShouldRestoreInventoryOnce() {
    seedEscrowedFiniteStockOrder("REFUND_PENDING");

    sagaService.markRefundSucceeded(orderId, refundTxnId);
    sagaService.markRefundSucceeded(orderId, refundTxnId);

    Integer stock = jdbcTemplate.queryForObject(
            "select stock_available from market_listing where listing_id = ?",
            Integer.class,
            listingId
    );
    assertThat(stock).isEqualTo(1);
}
```

- [ ] **Step 2: Run saga tests to verify failure**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketOrderSagaServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because saga service and conditional mapper methods do not exist.

- [ ] **Step 3: Add conditional order mapper methods**

Add to `MarketOrderMapper`:

```java
int markEscrowSucceeded(@Param("orderId") UUID orderId, @Param("escrowTxnId") UUID escrowTxnId);
int markEscrowFailed(@Param("orderId") UUID orderId);
int markReleasePending(@Param("orderId") UUID orderId);
int markReleaseSucceeded(@Param("orderId") UUID orderId, @Param("releaseTxnId") UUID releaseTxnId);
int markRefundPending(@Param("orderId") UUID orderId);
int markEscrowCancelPending(@Param("orderId") UUID orderId);
int markCancelledNoRefund(@Param("orderId") UUID orderId);
int markCancelledWithRefund(@Param("orderId") UUID orderId, @Param("refundTxnId") UUID refundTxnId);
int markDisputeRefundPending(@Param("orderId") UUID orderId);
int markDisputeReleasePending(@Param("orderId") UUID orderId);
```

Each SQL update must include the expected current status. Example:

```xml
<update id="markReleasePending">
    update market_order
    set status = 'RELEASE_PENDING',
        update_time = current_timestamp
    where order_id = #{orderId, jdbcType=BINARY}
      and status in ('DELIVERED', 'SHIPPED')
</update>
```

- [ ] **Step 4: Add idempotent inventory mapper methods**

Add methods:

```java
int releaseReservedByOrderIfNeeded(@Param("orderId") UUID orderId);
int markDeliveredByOrderIfReserved(@Param("orderId") UUID orderId, @Param("deliveredAt") Date deliveredAt);
```

Use SQL guarded by current status:

```xml
<update id="releaseReservedByOrderIfNeeded">
    update market_inventory_unit
    set status = 'AVAILABLE',
        reserved_order_id = null
    where reserved_order_id = #{orderId, jdbcType=BINARY}
      and status = 'RESERVED'
</update>
```

- [ ] **Step 5: Implement `MarketOrderSagaService`**

Core methods:

```java
boolean canApplyEscrow(UUID orderId);
void completeEscrowNoop(UUID orderId);
boolean markEscrowSucceeded(UUID orderId, UUID escrowTxnId);
void markEscrowTerminalFailed(UUID orderId, String reason);
boolean markReleaseSucceeded(UUID orderId, UUID releaseTxnId);
boolean markRefundSucceeded(UUID orderId, UUID refundTxnId);
```

`canApplyEscrow` must load the order and return true only for `ESCROW_PENDING`.

`markEscrowTerminalFailed` must:

1. conditionally move `ESCROW_PENDING` or `ESCROW_CANCEL_PENDING` to `ESCROW_FAILED` or `CANCELLED`
2. restore finite listing stock once
3. release preloaded reserved inventory once

`markRefundSucceeded` must:

1. set refund txn id using a conditional order status update
2. restore finite listing stock only when the order reaches `CANCELLED`
3. release preloaded reserved inventory only when needed

- [ ] **Step 6: Run saga verification**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketOrderSagaServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderSagaService.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderSagaServiceTest.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketOrderMapper.java \
        backend/community-app/src/main/resources/mapper/market_order_mapper.xml \
        backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketInventoryUnitMapper.java \
        backend/community-app/src/main/resources/mapper/market_inventory_unit_mapper.xml \
        backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketListingMapper.java \
        backend/community-app/src/main/resources/mapper/market_listing_mapper.xml \
        backend/community-app/src/main/java/com/nowcoder/community/market/mapper/MarketDisputeMapper.java \
        backend/community-app/src/main/resources/mapper/market_dispute_mapper.xml
git commit -m "feat: add market order saga transitions"
```

---

### Task 5: Move Order Creation To Escrow Pending

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceUnitTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`

- [ ] **Step 1: Update tests for pending escrow and processor finalization**

In `MarketOrderServiceTest`, add:

```java
@Autowired
private MarketWalletActionProcessor marketWalletActionProcessor;

@Test
void createOrderShouldReturnEscrowPendingAndProcessorShouldEscrow() {
    UUID sellerUserId = uuid(7);
    UUID buyerUserId = uuid(9);
    UUID listingId = seedPhysicalListing(sellerUserId);
    UUID addressId = seedAddress(buyerUserId, true);
    seedBuyerBalance(buyerUserId, 20_000L);

    MarketOrderResponse created = marketOrderService.createOrder("physical:req-pending", buyerUserId, listingId, 1, addressId);

    assertThat(created.status()).isEqualTo("ESCROW_PENDING");
    assertThat(jdbcTemplate.queryForObject("select count(*) from wallet_txn", Integer.class)).isZero();

    marketWalletActionProcessor.processDue(10);

    MarketOrderDetailResponse detail = marketQueryService.getOrderDetail(created.orderId(), buyerUserId);
    assertThat(detail.status()).isEqualTo("ESCROWED");
    assertThat(jdbcTemplate.queryForObject("select count(*) from wallet_txn where request_id = ?", Integer.class,
            "market-order:" + created.orderId() + ":escrow")).isEqualTo(1);
}
```

Update existing tests that ship, deliver, confirm, cancel, or dispute created orders to call `marketWalletActionProcessor.processDue(10)` immediately after `createOrder`.

- [ ] **Step 2: Run market order tests to verify failure**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceUnitTest,MarketOrderServiceTest,MarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `createOrder` still calls wallet synchronously and returns `ESCROWED`.

- [ ] **Step 3: Refactor `MarketOrderService.createOrder`**

Constructor dependencies change from wallet API to command service:

```java
private final MarketWalletActionService marketWalletActionService;
```

Create order with generated `orderId` before inventory reservation finalization:

```java
UUID orderId = idGenerator.next();
order.setOrderId(orderId);
order.setStatus(STATUS_ESCROW_PENDING);
order.setEscrowTxnId(null);
```

Replace synchronous escrow call with:

```java
marketWalletActionService.enqueueEscrow(
        order.getOrderId(),
        buyerUserId,
        listing.getSellerUserId(),
        totalAmount
);
```

For preloaded virtual orders, reserve inventory units for the order but do not mark delivered in `createOrder`. Delivery happens in `MarketOrderSagaService.markEscrowSucceeded`.

- [ ] **Step 4: Ensure replay returns existing pending order**

`ensureReplayMatches` remains valid. When an existing order is `ESCROW_PENDING`, `MarketOrderResponse.from(existing)` returns the pending status; it must not call wallet or enqueue a duplicate action.

- [ ] **Step 5: Run escrow creation verification**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceUnitTest,MarketOrderServiceTest,MarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketQueryService.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceUnitTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java
git commit -m "feat: enqueue market order escrow command"
```

---

### Task 6: Move Confirm And Auto Confirm To Release Pending

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandlerTest.java`

- [ ] **Step 1: Add failing release pending test**

```java
@Test
void confirmOrderShouldEnqueueReleaseWithoutWalletWriteInOrderTransaction() {
    UUID orderId = seedDeliveredVirtualOrder(sellerUserId, buyerUserId);

    MarketOrderResponse confirmed = marketOrderService.confirmOrder(orderId, buyerUserId);

    assertThat(confirmed.status()).isEqualTo("RELEASE_PENDING");
    assertThat(jdbcTemplate.queryForObject(
            "select count(*) from market_wallet_action where request_id = ?",
            Integer.class,
            "market-order:" + orderId + ":release"
    )).isEqualTo(1);

    marketWalletActionProcessor.processDue(10);

    assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("COMPLETED");
}
```

- [ ] **Step 2: Run confirm tests to verify failure**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceTest,MarketOrderAutoConfirmHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because confirm still calls wallet synchronously.

- [ ] **Step 3: Refactor `confirmOrder`**

Replace wallet release call with:

```java
int updated = marketOrderMapper.markReleasePending(orderId);
if (updated != 1) {
    return MarketOrderResponse.from(reloadOrder(orderId));
}
marketWalletActionService.enqueueRelease(orderId, order.getSellerUserId(), order.getBuyerUserId(), order.getTotalAmount());
return MarketOrderResponse.from(reloadOrder(orderId));
```

- [ ] **Step 4: Refactor auto confirm**

`autoConfirmDueOrders` must not wrap all due orders in one wallet transaction. For each due order:

```java
try {
    MarketOrder locked = requireOrderForUpdate(dueOrder.getOrderId());
    if (!Set.of(STATUS_DELIVERED, STATUS_SHIPPED).contains(locked.getStatus())) {
        skipped++;
        continue;
    }
    int updated = marketOrderMapper.markReleasePending(locked.getOrderId());
    if (updated == 1) {
        marketWalletActionService.enqueueRelease(
                locked.getOrderId(),
                locked.getSellerUserId(),
                locked.getBuyerUserId(),
                locked.getTotalAmount()
        );
        completed++;
    }
} catch (RuntimeException e) {
    skipped++;
}
```

If the current method-level `@Transactional` causes a batch-sized transaction, split per-order work into a new `MarketOrderAutoConfirmService.confirmOneDueOrder(UUID orderId, Date now)` with its own `@Transactional` public method.

- [ ] **Step 5: Run release verification**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceTest,MarketOrderAutoConfirmHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/infra/job/handlers/MarketOrderAutoConfirmHandlerTest.java
git commit -m "feat: enqueue market order release command"
```

---

### Task 7: Move Cancel And Dispute Resolution To Refund/Release Commands

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketDisputeService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketDisputeServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java`

- [ ] **Step 1: Add cancel, empty compensation, and hanging tests**

Add to `MarketOrderServiceTest`:

```java
@Test
void cancelEscrowedOrderShouldEnqueueRefundAndCompleteAfterProcessorRuns() {
    UUID orderId = seedEscrowedPhysicalOrder(sellerUserId, buyerUserId);

    MarketOrderResponse cancelled = marketOrderService.cancelOrder(orderId, buyerUserId);

    assertThat(cancelled.status()).isEqualTo("REFUND_PENDING");
    marketWalletActionProcessor.processDue(10);
    assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("CANCELLED");
    assertThat(walletAccountService.balanceOfUser(buyerUserId)).isEqualTo(20_000L);
}

@Test
void cancelEscrowPendingOrderShouldPreventLaterEscrowWalletCall() {
    UUID orderId = marketOrderService.createOrder("cancel:pending", buyerUserId, listingId, 1, addressId).orderId();

    MarketOrderResponse cancelled = marketOrderService.cancelOrder(orderId, buyerUserId);
    marketWalletActionProcessor.processDue(10);

    assertThat(cancelled.status()).isIn("ESCROW_CANCEL_PENDING", "CANCELLED");
    assertThat(jdbcTemplate.queryForObject("select count(*) from wallet_txn", Integer.class)).isZero();
    assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("CANCELLED");
}
```

Add to `MarketDisputeServiceTest`:

```java
@Test
void sellerAcceptedDisputeShouldRemainPendingUntilRefundProcessorSucceeds() {
    UUID orderId = seedShippedPhysicalOrder(sellerUserId, buyerUserId);
    MarketDisputeResponse dispute = marketDisputeService.openDispute(orderId, buyerUserId, "货不对板", "和描述不一致");

    MarketDisputeResponse accepted = marketDisputeService.sellerAcceptRefund(dispute.disputeId(), sellerUserId, "同意退款");

    assertThat(accepted.status()).isEqualTo("SELLER_ACCEPTED");
    assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("DISPUTE_REFUND_PENDING");
    marketWalletActionProcessor.processDue(10);
    assertThat(marketQueryService.getOrderDetail(orderId, buyerUserId).status()).isEqualTo("REFUNDED");
}
```

- [ ] **Step 2: Run refund/dispute tests to verify failure**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceTest,MarketDisputeServiceTest,AdminMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because cancel and dispute still call wallet synchronously.

- [ ] **Step 3: Refactor buyer cancel**

For `ESCROWED`, mark refund pending and enqueue refund:

```java
marketOrderMapper.markRefundPending(orderId);
marketWalletActionService.enqueueRefund(orderId, buyerUserId, order.getSellerUserId(), order.getTotalAmount());
```

For `ESCROW_PENDING`, use hanging prevention:

```java
marketOrderMapper.markEscrowCancelPending(orderId);
marketWalletActionService.cancelPendingEscrowIfPossible(orderId);
marketOrderSagaService.completeEscrowNoop(orderId);
```

If `cancelPendingEscrowIfPossible` cannot cancel because the action is `PROCESSING`, keep `ESCROW_CANCEL_PENDING`; the processor will either no-op before wallet or enqueue refund if wallet already applied.

- [ ] **Step 4: Refactor dispute refund/release**

`sellerAcceptRefund`:

```java
dispute.setStatus(DISPUTE_STATUS_SELLER_ACCEPTED);
dispute.setResolutionType(RESOLUTION_REFUND);
dispute.setResolvedAt(new Date());
marketDisputeMapper.update(dispute);
marketOrderMapper.markDisputeRefundPending(order.getOrderId());
marketWalletActionService.enqueueDisputeRefund(order.getOrderId(), disputeId, order.getBuyerUserId(), order.getSellerUserId(), order.getTotalAmount());
```

`adminResolveRefund` and `adminResolveRelease` follow the same pattern, but the final dispute resolution remains pending until `MarketOrderSagaService` marks the wallet result applied.

- [ ] **Step 5: Run refund/dispute verification**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketOrderServiceTest,MarketDisputeServiceTest,AdminMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketOrderService.java \
        backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketDisputeService.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketOrderServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketDisputeServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java
git commit -m "feat: enqueue market refund and dispute wallet commands"
```

---

### Task 8: Add Processor Jobs, Recovery, And Operational Checks

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketWalletActionRecoveryService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketWalletActionProcessorHandler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketWalletActionRecoveryHandler.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionRecoveryServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionProcessorTest.java`

- [ ] **Step 1: Add failing recovery tests**

Create `MarketWalletActionRecoveryServiceTest`:

```java
@Test
void recoverExpiredProcessingShouldReturnActionToRetrying() {
    seedProcessingActionWithExpiredLease(actionId, Instant.parse("2026-04-25T09:00:00Z"));

    int recovered = recoveryService.recoverExpiredProcessing(Instant.parse("2026-04-25T10:00:00Z"));

    assertThat(recovered).isEqualTo(1);
    assertThat(action(actionId).getStatus()).isEqualTo("RETRYING");
}

@Test
void reconcileWalletTxnWithoutSucceededActionShouldAdvanceRemainingMarketState() {
    seedRefundPendingOrder(orderId);
    seedRefundActionWithWalletTxn(orderId, refundTxnId, "PROCESSING");

    MarketWalletActionRecoveryResult result = recoveryService.reconcileOnce(50);

    assertThat(result.reconciledCount()).isEqualTo(1);
    assertThat(order(orderId).getStatus()).isEqualTo("CANCELLED");
    assertThat(actionByOrder(orderId, "REFUND").getStatus()).isEqualTo("SUCCEEDED");
}
```

- [ ] **Step 2: Run recovery tests to verify failure**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketWalletActionRecoveryServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because recovery service does not exist.

- [ ] **Step 3: Implement recovery service**

Expose:

```java
public MarketWalletActionRecoveryResult reconcileOnce(int limit);
public int recoverExpiredProcessing(Instant asOf);
```

`reconcileOnce` handles:

- action has `wallet_txn_id` but status is not `SUCCEEDED`: call corresponding saga success method and then mark action `SUCCEEDED`
- order is pending money state but has no action: create the missing action from order snapshot
- order is `ESCROW_CANCEL_PENDING` with cancelled/no-op escrow action: finalize `CANCELLED`

Return:

```java
public record MarketWalletActionRecoveryResult(int recoveredLeases, int reconciledCount, int skippedCount) {
}
```

- [ ] **Step 4: Add XXL job handlers**

`MarketWalletActionProcessorHandler`:

```java
@Component
public class MarketWalletActionProcessorHandler {
    static final String JOB_NAME = "marketWalletActionProcessor";

    private final MarketWalletActionProcessor processor;

    @XxlJob(JOB_NAME)
    public void process() {
        int processed = processor.processDue(50);
        String message = "[market-wallet-action] processed=" + processed;
        XxlJobHelper.log(message);
        XxlJobHelper.handleSuccess(message);
    }
}
```

`MarketWalletActionRecoveryHandler` follows the same style and calls `recoveryService.reconcileOnce(100)`.

- [ ] **Step 5: Run recovery/job verification**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=MarketWalletActionRecoveryServiceTest,MarketWalletActionProcessorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/market/service/MarketWalletActionRecoveryService.java \
        backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketWalletActionProcessorHandler.java \
        backend/community-app/src/main/java/com/nowcoder/community/infra/job/handlers/MarketWalletActionRecoveryHandler.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionRecoveryServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/market/service/MarketWalletActionProcessorTest.java
git commit -m "feat: add market wallet action recovery"
```

---

### Task 9: Update Documentation And Run Full Focused Verification

**Files:**
- Modify: `docs/business-logic/market-order-dispute-flow.md`
- Modify: `docs/business-logic/wallet-ledger-flow.md`
- Modify: `docs/superpowers/specs/2026-04-25-market-wallet-saga-command-design.md` only if implementation names diverge from the spec.

- [ ] **Step 1: Update business docs**

`market-order-dispute-flow.md` must state:

```markdown
资金动作不再在 `MarketOrderService` / `MarketDisputeService` 的同步事务内直接落钱包账本。
市场先写 `market_wallet_action`，订单进入 `ESCROW_PENDING`、`RELEASE_PENDING`、`REFUND_PENDING`
或争议 pending 状态；后台 processor 调钱包并回写最终状态。
```

`wallet-ledger-flow.md` must state:

```markdown
钱包 `requestId` 重放不是简单返回旧交易。重放必须匹配交易类型、业务 id、金额和账本分录语义；
不匹配时返回 `REQUEST_REPLAY_CONFLICT`，避免错误复用幂等键。
```

- [ ] **Step 2: Run focused service suite**

Run:

```bash
cd backend && mvn -pl community-app -am -Dtest=WalletLedgerServiceTest,WalletMarketApplicationServiceTest,MarketWalletActionMapperPersistenceTest,MarketWalletActionServiceTest,MarketWalletActionProcessorTest,MarketOrderSagaServiceTest,MarketWalletActionRecoveryServiceTest,MarketOrderServiceUnitTest,MarketOrderServiceTest,MarketDisputeServiceTest,MarketOrderAutoConfirmHandlerTest,MarketControllerTest,AdminMarketControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 3: Run compile check for community-app**

Run:

```bash
cd backend && mvn -pl community-app -am -DskipTests compile
```

Expected: PASS.

- [ ] **Step 4: Verify no synchronous market wallet calls remain**

Run:

```bash
rg -n "walletMarketActionApi\\.(escrowOrder|releaseOrder|refundOrder)" backend/community-app/src/main/java/com/nowcoder/community/market
```

Expected: no matches.

- [ ] **Step 5: Commit documentation and verification cleanup**

```bash
git add docs/business-logic/market-order-dispute-flow.md \
        docs/business-logic/wallet-ledger-flow.md \
        docs/superpowers/specs/2026-04-25-market-wallet-saga-command-design.md
git commit -m "docs: describe market wallet saga behavior"
```

---

## Self-Review Checklist

- Spec coverage:
  - Durable command table: Task 2
  - Saga state transitions: Task 4
  - Idempotent wallet replay: Task 1
  - Escrow pending flow: Task 5
  - Release pending and auto confirm: Task 6
  - Refund, empty compensation, and hanging prevention: Task 7
  - Compensation failure and recovery: Task 8
  - Documentation and no direct wallet calls: Task 9
- Placeholder scan:
  - The plan contains no open-ended marker text.
- Type consistency:
  - New action statuses use string constants in `MarketWalletActionStatus`.
  - New action types use string constants in `MarketWalletActionType`.
  - Processor and recovery services use `MarketOrderSagaService` for all market state advancement.

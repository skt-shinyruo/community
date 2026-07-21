# Market Wallet Action Lease Fencing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 `market_wallet_action` 的每次 processor claim 增加 UUID fencing token，使租约过期后的旧 worker 无法写入任何处理终态。

**Architecture:** application 为每次 claim 生成 `MarketWalletActionLease(actionId, token)`，repository/MyBatis 在 claim 时写 token，并让所有 processor-owned transition 以 `action_id + PROCESSING + lease_token` 做 CAS。Recovery 清除过期 token 后重新排队，并使用独立的 expected-status/transaction CAS 修复已产生 Wallet 交易的 action，避免伪造 processor 所有权。

**Tech Stack:** Java 21、Spring Boot、MyBatis、MySQL 8、H2、Flyway、SLF4J、JUnit 5、Mockito、Testcontainers、Maven。

## Global Constraints

- Wallet API 继续使用 `requestId` 保证远程调用幂等；fencing 不替代 Wallet 幂等或订单状态机。
- processor-owned succeeded、cancelled、retrying、failed、recovery-pending、dead 都必须匹配当前 lease token。
- affected rows 为 0 表示所有权丢失；旧 worker 立即停止且不得执行无条件 fallback。
- `cancelPendingEscrow` 保持 `PENDING/RETRYING -> CANCELLED` 自有 CAS，不接收 processing token。
- Recovery 不调用 processor fenced transition；它必须校验 expected status 和 wallet transaction/failure facts。
- V013 是 forward-only migration；不得修改 V001-V012 或 schema manifest。
- lease token 不得出现在日志、API、事件或 metric tag 中。

---

### Task 1: Community V013 Lease Token Schema

**Files:**

- Create: `backend/community-db-migrations/src/main/resources/db/migration/community/V013__add_market_wallet_action_lease_fencing.sql`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationLayoutTest.java`
- Modify: `backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java`
- Modify: `backend/community-app/src/test/resources/schema.sql`

**Interfaces:**

- Consumes: V012 schema and potentially stranded `PROCESSING` rows after old processors have stopped.
- Produces: nullable `lease_token binary(16)`, index `idx_market_wallet_action_processing_lease(status, processing_lease_until, action_id)`, and migrated stranded rows in immediately due `RETRYING` state.

- [ ] **Step 1: Add failing migration tests**

  Raise migration count/latest version to 13. Insert legacy PENDING and PROCESSING rows; after migration assert PENDING is unchanged and PROCESSING has:

  ```text
  status=RETRYING
  retry_count=old+1
  next_retry_at is not null
  processing_lease_until=null
  lease_token=null
  ```

  Assert the new column type/nullability and exact composite index order.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest='CommunityMigrationLayoutTest,CommunityMigrationTest' test
  ```

  Expected: V013, column and index are missing.

- [ ] **Step 3: Add migration and H2 schema parity**

  ```sql
  alter table market_wallet_action
    add column lease_token binary(16) null after processing_lease_until,
    add index idx_market_wallet_action_processing_lease(status, processing_lease_until, action_id);

  update market_wallet_action
  set status = 'RETRYING',
      retry_count = retry_count + 1,
      next_retry_at = current_timestamp,
      processing_lease_until = null,
      lease_token = null,
      update_time = current_timestamp
  where status = 'PROCESSING';
  ```

  Mirror the column/index in H2 test schema.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-db-migrations -am -Dtest='CommunityMigrationLayoutTest,CommunityMigrationTest' test
  git add backend/community-db-migrations/src/main/resources/db/migration/community/V013__add_market_wallet_action_lease_fencing.sql \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationLayoutTest.java \
          backend/community-db-migrations/src/test/java/com/nowcoder/community/migration/CommunityMigrationTest.java \
          backend/community-app/src/test/resources/schema.sql
  git commit -m "feat(migration): add wallet action lease fencing"
  ```

### Task 2: Domain Lease and Persistence Contract

**Files:**

- Create: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketWalletActionLease.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketWalletAction.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/repository/MarketWalletActionRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/infrastructure/persistence/dataobject/MarketWalletActionDataObject.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/infrastructure/persistence/mapper/MarketWalletActionMapper.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/infrastructure/persistence/MyBatisMarketWalletActionRepository.java`
- Modify: `backend/community-app/src/main/resources/mapper/market_wallet_action_mapper.xml`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/infrastructure/persistence/MarketWalletActionMapperPersistenceTest.java`

**Interfaces:**

- Consumes: `MarketWalletActionLease(UUID actionId, UUID token)` generated by processor application.
- Produces:

  ```java
  int claimProcessing(MarketWalletActionLease lease, Date leaseUntil);
  int markSucceeded(MarketWalletActionLease lease, UUID walletTxnId, String resultType);
  int markCancelled(MarketWalletActionLease lease, String resultType);
  int markRetrying(MarketWalletActionLease lease, Date nextRetryAt, String lastError);
  int markFailed(MarketWalletActionLease lease, String failureCode, String lastError);
  int markRecoveryPending(MarketWalletActionLease lease, UUID walletTxnId, String failureCode, String lastError);
  int markDead(MarketWalletActionLease lease, String lastError);
  int markRecoveredSucceeded(UUID actionId, String expectedStatus, UUID walletTxnId, String resultType);
  int rescheduleFailed(UUID actionId, String expectedFailureCode, Date nextRetryAt, String lastError);
  ```

- [ ] **Step 1: Write deterministic mapper fencing tests**

  Execute PENDING -> claim token A -> expire/recover -> claim token B. For each token-A terminal mapper method assert affected rows `0` and token B/status/deadline remain unchanged. Execute each transition with token B on a fresh row and assert affected rows `1`, expected state, and cleared token/deadline.

  Add recovery CAS tests where `markRecoveredSucceeded` succeeds only when both expected status and `wallet_txn_id` match, and `rescheduleFailed` succeeds only for the expected failure code with null wallet transaction.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=MarketWalletActionMapperPersistenceTest test
  ```

  Expected: mapper methods lack token arguments and stale worker writes succeed.

- [ ] **Step 3: Implement the lease value and SQL CAS**

  `MarketWalletActionLease` rejects null IDs/tokens. Add `leaseToken` to aggregate/data object/BaseColumns/insert mapping. Claim writes both token and deadline. Every processor transition includes:

  ```sql
  where action_id = #{lease.actionId, jdbcType=BINARY}
    and status = 'PROCESSING'
    and lease_token = #{lease.token, jdbcType=BINARY}
  ```

  Every successful terminal/retry transition clears `lease_token` and `processing_lease_until`. `recoverExpiredProcessing` keeps its deadline predicate, increments retry count, sets immediate retry, and clears both fields.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=MarketWalletActionMapperPersistenceTest test
  git add backend/community-app/src/main/java/com/nowcoder/community/market/domain \
          backend/community-app/src/main/java/com/nowcoder/community/market/infrastructure/persistence \
          backend/community-app/src/main/resources/mapper/market_wallet_action_mapper.xml \
          backend/community-app/src/test/java/com/nowcoder/community/market/infrastructure/persistence/MarketWalletActionMapperPersistenceTest.java
  git commit -m "fix(market): fence wallet action persistence transitions"
  ```

### Task 3: Processor Propagates Ownership and Stops on Lease Loss

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionProcessorApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionProcessorApplicationServiceTest.java`

**Interfaces:**

- Consumes: repository methods from Task 2 and a new UUID generated for every `processOne` attempt.
- Produces: all route/failure methods carry one immutable `MarketWalletActionLease`; CAS miss records a warning with action ID and transition, then returns without another state update.

- [ ] **Step 1: Write RED processor tests**

  Capture the claim token and assert the identical lease reaches success, cancelled, retry, failed, recovery-pending and dead paths. Mock each terminal update to return `0`; assert no second terminal method or fallback is called. Add a two-worker test where A blocks after Wallet success, recovery and B complete the action, then A returns and cannot overwrite B.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=MarketWalletActionProcessorApplicationServiceTest test
  ```

  Expected: current processor passes only action ID and never detects ownership loss.

- [ ] **Step 3: Thread the lease through every branch**

  At claim:

  ```java
  MarketWalletActionLease lease = new MarketWalletActionLease(action.getActionId(), UUID.randomUUID());
  if (walletActionRepository.claimProcessing(lease, leaseUntil) != 1) {
      return false;
  }
  ```

  Change `route`, `processEscrow`, and `handleFailure` to receive the lease. Centralize affected-row handling:

  ```java
  private boolean ownsTransition(MarketWalletActionLease lease, String transition, int updated) {
      if (updated == 1) return true;
      log.warn("[market-wallet-action] lease lost actionId={} transition={}", lease.actionId(), transition);
      return false;
  }
  ```

  Do not log `lease.token()`. A CAS miss is not retried with action ID only.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=MarketWalletActionProcessorApplicationServiceTest test
  git add backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionProcessorApplicationService.java \
          backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionProcessorApplicationServiceTest.java
  git commit -m "fix(market): stop wallet processors after lease loss"
  ```

### Task 4: Recovery Uses Independent Expected-Fact CAS

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationServiceTest.java`

**Interfaces:**

- Consumes: `markRecoveredSucceeded` and `rescheduleFailed` from Task 2.
- Produces: recovery never invokes processor-owned token methods and only reports reconciliation when its expected-fact CAS succeeds.

- [ ] **Step 1: Add RED recovery race tests**

  Mock a non-processing action with wallet transaction, advance the saga, then make `markRecoveredSucceeded` return `0`; assert result counts it as skipped. Cover matching update returning `1`. For repairable failed release/refund, assert `rescheduleFailed` receives action ID, failure code and due time; it must not call fenced `markRetrying`.

- [ ] **Step 2: Run RED**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=MarketWalletActionRecoveryApplicationServiceTest test
  ```

  Expected: current recovery calls the same unfenced methods as processor and treats zero-row updates as success.

- [ ] **Step 3: Use recovery-specific CAS outcomes**

  Pass `action.getStatus()` and `action.getWalletTxnId()` to `markRecoveredSucceeded`, return true only for affected row `1`, and use `rescheduleFailed` for no-transaction failed repairs. Keep `recoverExpiredProcessing(asOf)` unchanged except for token clearing implemented in Task 2.

- [ ] **Step 4: Run GREEN and commit**

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=MarketWalletActionRecoveryApplicationServiceTest test
  git add backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationService.java \
          backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketWalletActionRecoveryApplicationServiceTest.java
  git commit -m "fix(market): isolate wallet action recovery transitions"
  ```

### Task 5: Wallet Fencing Regression

**Files:**

- Test: all files changed in Tasks 1-4.

**Interfaces:**

- Consumes: V013 and all processor/recovery CAS contracts.
- Produces: deterministic evidence that token A can never overwrite token B.

- [ ] **Step 1: Run focused Market and migration tests**

  ```bash
  cd backend
  mvn test -pl :community-db-migrations,:community-app -am \
    -Dtest='CommunityMigration*Test,MarketWalletAction*Test,MarketOrderAutoConfirmHandlerTest'
  ```

  Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run architecture tests**

  ```bash
  cd backend
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  Expected: all architecture tests pass.

- [ ] **Step 3: Scan for unfenced processor SQL**

  ```bash
  rg -n 'where action_id = #\{actionId' backend/community-app/src/main/resources/mapper/market_wallet_action_mapper.xml
  git diff --check
  ```

  Expected: matches exist only for explicitly recovery-owned methods; processor transitions reference `lease.actionId` and `lease.token`.

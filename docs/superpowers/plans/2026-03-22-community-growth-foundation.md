# Community Growth Foundation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the dual-account foundation for community growth so the system keeps `user.score` as public growth value, adds a separate reward balance, records every change in ledgers, and routes all reward-producing events through one unified grant path.

**Architecture:** Keep the current user points leaderboard path compatible, but insert a new `growth` subdomain beside it. The new subdomain owns reward accounts, reward ledgers, grant records, and unified orchestration. Existing post/comment/like reward listeners and outbox handlers stop writing growth values directly and instead call a single orchestration service that writes a grant record, then applies account-level mutations atomically.

**Tech Stack:** Java 17, Spring Boot 3, Spring Transactions, MyBatis XML mappers, H2 test schema, existing local event + outbox infrastructure, Maven, JUnit 5

---

## File Structure Map

### Schema and persistence

- `deploy/mysql-init/010_schema.sql`
  Role: create `reward_account`, `reward_ledger`, `reward_grant_record`, and `admin_reward_adjustment`.
- `backend/community-app/src/test/resources/schema.sql`
  Role: mirror the production schema in H2 tests.
- `backend/community-app/src/main/resources/mapper/reward_account_mapper.xml`
- `backend/community-app/src/main/resources/mapper/reward_ledger_mapper.xml`
- `backend/community-app/src/main/resources/mapper/reward_grant_record_mapper.xml`
- `backend/community-app/src/main/resources/mapper/admin_reward_adjustment_mapper.xml`
  Role: SQL write/read primitives for the new growth foundation tables.

### New growth backend domain

- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardAccount.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardLedgerEntry.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardGrantRecord.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/AdminRewardAdjustment.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardAccountMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardLedgerMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardGrantRecordMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/AdminRewardAdjustmentMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardAccountService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardLedgerService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/GrowthAccountQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/GrowthSummaryResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/GrowthController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/security/GrowthSecurityRules.java`
  Role: new foundation domain for reward balances, grants, queries, and authenticated summary access.

### Existing compatibility path that must be migrated, not broken

- `backend/community-app/src/main/java/com/nowcoder/community/user/service/PointsService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/LeaderboardService.java`
  Role: existing score/level/leaderboard surfaces that must stay correct while the unified grant path is introduced.

### Tests

- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/RewardAccountServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/UnifiedGrantServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/GrowthControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsProjectionListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsOutboxHandlerTest.java`
  Role: prove lazy account creation, idempotent grants, compatibility of old points events, and authenticated summary reads.

---

### Task 1: Add The Schema And Mapper Layer For Growth Foundation

**Files:**
- Modify: `deploy/mysql-init/010_schema.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardAccount.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardLedgerEntry.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardGrantRecord.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/AdminRewardAdjustment.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardAccountMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardLedgerMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardGrantRecordMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/AdminRewardAdjustmentMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/reward_account_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/reward_ledger_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/reward_grant_record_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/admin_reward_adjustment_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/RewardAccountServiceTest.java`

- [ ] **Step 1: Write failing tests for lazy account creation and balance persistence**

  Cover:
  - reading a missing reward account returns zero balance view and creates the account on first mutation
  - a second insert attempt for the same account key does not create duplicates
  - reward ledger inserts keep `balance_after` consistent with the mutated account

- [ ] **Step 2: Run the targeted growth foundation tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=RewardAccountServiceTest test`

- [ ] **Step 3: Add schema and mapper primitives only**

  Implement:
  - production and H2 schema tables plus keys/indexes
  - mapper interfaces and XML SQL for account read/write, ledger append, grant insert, and admin adjustment append
  - optimistic locking or equivalent compare-and-set support on reward accounts

- [ ] **Step 4: Re-run the targeted test and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=RewardAccountServiceTest test`

- [ ] **Step 5: Checkpoint the diff for the persistence task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 2: Implement Unified Grant Orchestration And Account Services

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardAccountService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardLedgerService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/GrowthAccountQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/service/PointsService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/UnifiedGrantServiceTest.java`

- [ ] **Step 1: Write failing tests for grant idempotency and dual-account writes**

  Cover:
  - the same source event only creates one grant record
  - a growth-only grant updates `user.score` and `user_score_log`
  - a reward-balance-only grant updates `reward_account` and `reward_ledger`
  - a dual grant updates both accounts atomically
  - negative reward balance mutations are rejected before ledger append

- [ ] **Step 2: Run the targeted service tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=UnifiedGrantServiceTest test`

- [ ] **Step 3: Implement account-level services and unified orchestration**

  Implement:
  - `RewardAccountService` for lazy-create, debit, credit, freeze, release primitives
  - `RewardLedgerService` append-only ledger writes
  - `UnifiedGrantService` that inserts one `reward_grant_record` and then calls account-level services
  - `PointsService` reduced to growth-account application logic instead of direct business orchestration

- [ ] **Step 4: Re-run the targeted service tests and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=UnifiedGrantServiceTest test`

- [ ] **Step 5: Checkpoint the diff for the grant orchestration task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 3: Migrate Existing Points Projections To The Unified Grant Path

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/service/PointsService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsProjectionListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsOutboxHandlerTest.java`

- [ ] **Step 1: Write failing compatibility tests for post/comment/like growth events**

  Cover:
  - existing points listeners still award the same growth deltas for post/comment/like events
  - those events now create a unified grant record instead of bypassing the new foundation
  - duplicate outbox delivery remains idempotent end-to-end

- [ ] **Step 2: Run the targeted listener/handler tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=PointsProjectionListenerTest,PointsOutboxHandlerTest test`

- [ ] **Step 3: Route points listeners and outbox handlers through `UnifiedGrantService`**

  Implement:
  - listeners/handlers submit growth-only grants using source event id + type
  - `PointsService.applyPoints(...)` becomes an internal account application primitive or a compatibility wrapper only
  - keep response semantics and existing leaderboard behavior unchanged

- [ ] **Step 4: Re-run the targeted tests and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=PointsProjectionListenerTest,PointsOutboxHandlerTest test`

- [ ] **Step 5: Checkpoint the diff for the migration task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 4: Expose Authenticated Growth Summary APIs And Run Final Verification

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/GrowthSummaryResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/GrowthController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/security/GrowthSecurityRules.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/GrowthControllerTest.java`

- [ ] **Step 1: Write failing controller tests for authenticated summary access**

  Cover:
  - authenticated users can fetch current growth score, level, reward balance, and frozen balance
  - anonymous callers are rejected
  - missing reward account still returns zero-balance summary

- [ ] **Step 2: Run the targeted controller tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=GrowthControllerTest test`

- [ ] **Step 3: Implement the summary endpoint and security rule**

  Implement:
  - `GET /api/growth/summary`
  - response fields sufficient for later task-center and shop pages
  - security rule that keeps the endpoint authenticated without weakening existing public user/profile routes

- [ ] **Step 4: Run focused and broader verification**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=RewardAccountServiceTest,UnifiedGrantServiceTest,GrowthControllerTest,PointsProjectionListenerTest,PointsOutboxHandlerTest test`
  - `cd backend && mvn -pl community-app test`

- [ ] **Step 5: Checkpoint the diff for the foundation plan**

  Note: do not create a git commit unless the user explicitly asks for one.

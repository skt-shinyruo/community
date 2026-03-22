# Community Reward Shop And Redemption Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reward shop that consumes reward balance without affecting leaderboard growth score, supports automatic and manual fulfillment, tracks order history, and preserves full order/audit semantics.

**Architecture:** Introduce a catalog/order subdomain inside `growth`. Reward items remain immutable at the order boundary through snapshot fields. Redemption is a transactional workflow layered on top of the growth foundation: automatic items debit available balance and complete immediately; manual items move value from available to frozen balance and keep the order pending until fulfillment or cancellation. User-facing APIs and Vue views consume this workflow directly.

**Tech Stack:** Java 17, Spring Boot 3, Spring Transactions, MyBatis, H2, Vue 3, Vue Router, Vitest, Maven

---

## File Structure Map

### Schema and persistence

- `deploy/mysql-init/010_schema.sql`
- `backend/community-app/src/test/resources/schema.sql`
  Role: add `reward_item` and `reward_order`.
- `backend/community-app/src/main/resources/mapper/reward_item_mapper.xml`
- `backend/community-app/src/main/resources/mapper/reward_order_mapper.xml`
  Role: catalog and order persistence with immutable snapshots.

### Backend growth shop domain

- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardItem.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardOrder.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardItemMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardOrderMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardCatalogService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardRedemptionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardOrderQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/RewardShopController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/RewardItemResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/RewardOrderResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/RedeemRewardRequest.java`
  Role: user-facing reward shop catalog and order APIs.

### Frontend

- `frontend/src/api/services/rewardShopService.js`
- `frontend/src/views/RewardShopView.vue`
- `frontend/src/views/RewardOrderHistoryView.vue`
- `frontend/src/views/rewardShopState.js`
- `frontend/src/views/rewardShopState.test.js`
- `frontend/src/router/index.js`
- `frontend/src/router/navigation.js`
  Role: reward shop browsing, redemption, and order-history UI.

### Tests

- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/RewardRedemptionServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/RewardShopControllerTest.java`
- `frontend/src/views/rewardShopState.test.js`
  Role: prove automatic/manual redemption rules, snapshot persistence, and UI state mapping.

---

### Task 1: Add Catalog And Order Persistence With Snapshot Semantics

**Files:**
- Modify: `deploy/mysql-init/010_schema.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardItem.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/RewardOrder.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardItemMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardOrderMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/reward_item_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/reward_order_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/RewardRedemptionServiceTest.java`

- [ ] **Step 1: Write failing tests for item snapshots and order creation invariants**

  Cover:
  - redeeming an item stores immutable snapshot fields in the order row
  - later catalog edits do not mutate historical orders
  - duplicate order ids are not created for one redemption attempt
  - stock and limit checks reject invalid redemptions before balance movement

- [ ] **Step 2: Run the targeted shop tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=RewardRedemptionServiceTest test`

- [ ] **Step 3: Add schema tables and mapper SQL for items/orders**

  Implement:
  - item table with status, stock, per-user limit, fulfillment mode
  - order table with snapshot columns and status machine seed values
  - mapper queries for list, detail, insert order, and user order history

- [ ] **Step 4: Re-run the targeted test and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=RewardRedemptionServiceTest test`

- [ ] **Step 5: Checkpoint the diff for the persistence task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 2: Implement Transactional Redemption And Freeze Semantics

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardCatalogService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardRedemptionService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardOrderQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardAccountService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/RewardRedemptionServiceTest.java`

- [ ] **Step 1: Expand the failing tests to cover auto-vs-manual fulfillment**

  Cover:
  - automatic items debit available balance and complete the order in one transaction
  - manual items move balance from available to frozen and leave the order pending
  - cancelling a pending manual order releases frozen balance
  - refunding a fulfilled order restores available balance exactly once
  - reward-shop redemption never changes `user.score`

- [ ] **Step 2: Run the targeted redemption tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=RewardRedemptionServiceTest test`

- [ ] **Step 3: Implement the redemption workflow**

  Implement:
  - catalog listing/detail reads
  - one transactional redemption service with stock, quota, and balance checks
  - automatic-item direct debit path
  - manual-item freeze path
  - explicit order states and idempotent refund hooks for later admin use

- [ ] **Step 4: Re-run the targeted redemption tests and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=RewardRedemptionServiceTest test`

- [ ] **Step 5: Checkpoint the diff for the redemption-service task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 3: Add User APIs And The Reward Shop Frontend

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/RewardShopController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/RewardItemResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/RewardOrderResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/RedeemRewardRequest.java`
- Create: `frontend/src/api/services/rewardShopService.js`
- Create: `frontend/src/views/RewardShopView.vue`
- Create: `frontend/src/views/RewardOrderHistoryView.vue`
- Create: `frontend/src/views/rewardShopState.js`
- Create: `frontend/src/views/rewardShopState.test.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/router/navigation.js`

- [ ] **Step 1: Write failing tests for reward shop UI state mapping**

  Cover:
  - item cards derive `canRedeem`, `insufficientBalance`, and `soldOut` correctly
  - order rows map `PENDING`, `PROCESSING`, `FULFILLED`, `REFUNDED`, and `CANCELLED` to distinct UI labels

- [ ] **Step 2: Run the targeted frontend test and confirm RED**

  Run:
  - `cd frontend && npm test -- src/views/rewardShopState.test.js`

- [ ] **Step 3: Implement the user-facing reward shop API and pages**

  Implement:
  - `GET /api/growth/shop/items`
  - `GET /api/growth/shop/items/{itemId}`
  - `POST /api/growth/shop/redeem`
  - `GET /api/growth/shop/orders`
  - authenticated shop and order-history routes in the Vue app

- [ ] **Step 4: Re-run frontend verification**

  Run:
  - `cd frontend && npm test -- src/views/rewardShopState.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 5: Checkpoint the diff for the reward-shop UI task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 4: Run Cross-Layer Verification For The Reward Shop Subproject

**Files:**
- Verify only

- [ ] **Step 1: Run focused backend verification**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=RewardRedemptionServiceTest,RewardShopControllerTest test`

- [ ] **Step 2: Run focused frontend verification**

  Run:
  - `cd frontend && npm test -- src/views/rewardShopState.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 3: Run a broader backend module verification**

  Run:
  - `cd backend && mvn -pl community-app test`

- [ ] **Step 4: Checkpoint the diff for the reward-shop plan**

  Note: do not create a git commit unless the user explicitly asks for one.

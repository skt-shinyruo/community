# Community Growth Admin And Operations Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add administrator-facing growth/reward operations so staff can query user balances, perform audited adjustments, manage catalog items, process reward orders, and inspect growth-system health without using ad hoc SQL.

**Architecture:** Reuse the project’s existing admin governance style instead of inventing a second admin framework. Backend admin APIs stay behind admin-only routes and require reasoned actions plus audit writes. Frontend admin pages follow the current admin desk shell and split the problem into two operator surfaces: account adjustments/ledger inspection and catalog/order operations. Metrics stay lightweight and read from the growth domain rather than introducing a separate analytics subsystem.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security, MyBatis, existing admin route patterns, Vue 3, Vue Router, Vitest, Maven

---

## File Structure Map

### Backend admin APIs and services

- `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/AdminGrowthController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/AdminRewardOpsController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/AdminGrowthService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/AdminRewardOpsService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminGrowthUserResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminAdjustBalanceRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminRewardItemUpsertRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminRewardOrderActionRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminGrowthMetricsResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/security/GrowthSecurityRules.java`
  Role: secure admin-only read/write operations for growth and shop systems.

### Existing files to extend, not bypass

- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/security/UserSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardAccountService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardRedemptionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/AdminRewardAdjustmentMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardItemMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardOrderMapper.java`
  Role: existing governance and growth primitives that admin ops must call rather than reimplement.

### Frontend admin desk

- `frontend/src/api/services/adminGrowthService.js`
- `frontend/src/api/services/adminRewardOpsService.js`
- `frontend/src/views/GrowthAdminView.vue`
- `frontend/src/views/RewardOpsView.vue`
- `frontend/src/views/growthAdminState.js`
- `frontend/src/views/growthAdminState.test.js`
- `frontend/src/router/index.js`
- `frontend/src/router/navigation.js`
- `frontend/src/components/layout/RightPanel.vue`
  Role: admin desk UI for balance adjustments, ledgers, items, order queue, and lightweight metrics.

### Tests

- `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/AdminGrowthControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/AdminRewardOpsControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/AdminGrowthServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/AdminRewardOpsServiceTest.java`
- `frontend/src/views/growthAdminState.test.js`
  Role: prove admin permissions, audited balance changes, item lifecycle operations, order processing, and admin UI state logic.

---

### Task 1: Add Admin Query And Audited Adjustment APIs

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/AdminGrowthController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/AdminGrowthService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminGrowthUserResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminAdjustBalanceRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/security/GrowthSecurityRules.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/AdminGrowthControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/AdminGrowthServiceTest.java`

- [ ] **Step 1: Write failing tests for admin search, ledger read, and manual adjustment**

  Cover:
  - admin can search a user and receive current score, level, reward balance, and recent ledger summary
  - non-admin requests are rejected
  - manual adjustments require a non-blank reason and confirmation flag
  - each adjustment writes one audit/adjustment record with before/after values

- [ ] **Step 2: Run the targeted admin-growth tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=AdminGrowthControllerTest,AdminGrowthServiceTest test`

- [ ] **Step 3: Implement audited admin adjustment APIs**

  Implement:
  - admin search endpoint for growth/reward account state
  - admin adjustment endpoint for score or reward balance
  - read endpoints for recent ledgers and adjustment history
  - hard guardrails against silent no-reason mutations

- [ ] **Step 4: Re-run the targeted tests and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=AdminGrowthControllerTest,AdminGrowthServiceTest test`

- [ ] **Step 5: Checkpoint the diff for the admin-adjustment task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 2: Add Admin Item Management And Order Processing APIs

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/AdminRewardOpsController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/AdminRewardOpsService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminRewardItemUpsertRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminRewardOrderActionRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/AdminGrowthMetricsResponse.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/AdminRewardOpsControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/AdminRewardOpsServiceTest.java`

- [ ] **Step 1: Write failing tests for item lifecycle and order actions**

  Cover:
  - admin can create, edit, and deactivate reward items
  - admin can process manual orders to `FULFILLED`, `CANCELLED`, or `REFUNDED`
  - cancelling a pending manual order releases frozen balance
  - refunding a fulfilled order is idempotent
  - lightweight metrics endpoint returns counts suitable for an operator dashboard

- [ ] **Step 2: Run the targeted admin-reward tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=AdminRewardOpsControllerTest,AdminRewardOpsServiceTest test`

- [ ] **Step 3: Implement item and order operations**

  Implement:
  - item upsert/list endpoints
  - order queue and detail endpoints
  - fulfill/cancel/refund actions with audit and note capture
  - small metrics summary endpoint for queue size, refunds, and fulfillment health

- [ ] **Step 4: Re-run the targeted tests and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=AdminRewardOpsControllerTest,AdminRewardOpsServiceTest test`

- [ ] **Step 5: Checkpoint the diff for the order-ops task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 3: Build The Admin Desk Views And Navigation

**Files:**
- Create: `frontend/src/api/services/adminGrowthService.js`
- Create: `frontend/src/api/services/adminRewardOpsService.js`
- Create: `frontend/src/views/GrowthAdminView.vue`
- Create: `frontend/src/views/RewardOpsView.vue`
- Create: `frontend/src/views/growthAdminState.js`
- Create: `frontend/src/views/growthAdminState.test.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/components/layout/RightPanel.vue`

- [ ] **Step 1: Write failing admin UI state tests**

  Cover:
  - account rows derive adjustment affordances and risk labels correctly
  - order rows map pending/processing/fulfilled/refunded/cancelled states to operator actions correctly
  - item cards show inactive/low-stock/high-cost warnings correctly

- [ ] **Step 2: Run the targeted frontend test and confirm RED**

  Run:
  - `cd frontend && npm test -- src/views/growthAdminState.test.js`

- [ ] **Step 3: Implement admin pages and navigation**

  Implement:
  - one admin page for user account lookup, ledger inspection, and manual adjustments
  - one admin page for item management and order queue processing
  - admin-only routes such as `/admin/growth` and `/admin/rewards`
  - navigation and right-panel links that appear only for admins

- [ ] **Step 4: Re-run frontend verification**

  Run:
  - `cd frontend && npm test -- src/views/growthAdminState.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 5: Checkpoint the diff for the admin UI task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 4: Run Cross-Layer Verification For The Admin/Ops Subproject

**Files:**
- Verify only

- [ ] **Step 1: Run focused backend verification**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=AdminGrowthControllerTest,AdminGrowthServiceTest,AdminRewardOpsControllerTest,AdminRewardOpsServiceTest test`

- [ ] **Step 2: Run focused frontend verification**

  Run:
  - `cd frontend && npm test -- src/views/growthAdminState.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 3: Run a broader backend module verification**

  Run:
  - `cd backend && mvn -pl community-app test`

- [ ] **Step 4: Checkpoint the diff for the admin/ops plan**

  Note: do not create a git commit unless the user explicitly asks for one.

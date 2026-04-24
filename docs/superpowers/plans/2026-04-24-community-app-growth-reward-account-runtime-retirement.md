# Community App Growth Reward-Account Runtime Retirement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire the remaining legacy `growth` reward/account runtime surface so `wallet` is the only online balance runtime while the legacy reward tables remain in schema as historical data.

**Architecture:** Remove the isolated legacy runtime classes (`LegacyRewardAccount*`, `RewardAccountService`, `WalletMigrationService`, `GrowthGrantActionApi`, `UnifiedGrantService`) plus the reward-account mapper layer, then replace the old runtime-oriented tests with repository-level retirement assertions. Keep wallet-first runtime paths (`PointsProjectionService`, `TaskProgressApplicationService`, `TaskProgressService`) unchanged and update the docs to describe legacy reward/account as retained history rather than active runtime.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, JUnit 5, Maven

---

## Planned File Structure

- `backend/community-app/src/main/java/com/nowcoder/community/growth/api/query/LegacyRewardAccountQueryApi.java`
  Retire the legacy read boundary.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/api/model/LegacyRewardAccountView.java`
  Retire the legacy reward-account view model.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardAccountService.java`
  Retire the legacy reward-account runtime service.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMigrationService.java`
  Retire the now-unused legacy migration bridge.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/api/action/GrowthGrantActionApi.java`
  Retire the now-unused growth grant action boundary.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java`
  Retire the now-unused grant orchestration runtime.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardAccountMapper.java`
  Retire the legacy reward-account mapper.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardLedgerMapper.java`
  Retire the legacy reward-ledger mapper.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardGrantRecordMapper.java`
  Retire the legacy reward-grant-record mapper.
- `backend/community-app/src/main/resources/mapper/reward_account_mapper.xml`
  Retire the legacy reward-account MyBatis mapping.
- `backend/community-app/src/main/resources/mapper/reward_ledger_mapper.xml`
  Retire the legacy reward-ledger MyBatis mapping.
- `backend/community-app/src/main/resources/mapper/reward_grant_record_mapper.xml`
  Retire the legacy reward-grant-record MyBatis mapping.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/RewardAccountServiceTest.java`
  Remove the runtime test that only validates the retired reward-account service.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/mapper/RewardAccountMapperTest.java`
  Remove the mapper test that only validates the retired reward-account mapper.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/mapper/RewardLogMapperPersistenceTest.java`
  Remove the mapper persistence test that only validates retired reward ledger/grant mappers.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/UnifiedGrantServiceTest.java`
  Remove the runtime test that only validates the retired grant orchestration path.
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletLedgerServiceTest.java`
  Remove the legacy reward-account migration cases and the `WalletMigrationService` dependency.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`
  Extend the retirement assertions so the removed reward/account runtime must stay off the classpath and out of mapper resources.
- `docs/ARCHITECTURE.md`
  Record that the deferred growth reward/account runtime cleanup is complete.
- `docs/SYSTEM_DESIGN.md`
  Mirror the runtime-retirement status and wallet-only online balance boundary.
- `docs/business-logic/growth-task-grant-level-flow.md`
  Rewrite the legacy `UnifiedGrantService` section so the document matches current wallet-first runtime behavior.

---

### Task 1: Lock Reward-Account Runtime Retirement In Tests

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`

- [ ] **Step 1: Add failing retirement assertions for the reward/account runtime**

Add these assertions to `legacyGrowthControllersAndServicesShouldNotRemainOnClasspath()`:

```java
assertClassIsRetired("com.nowcoder.community.growth.api.query.LegacyRewardAccountQueryApi");
assertClassIsRetired("com.nowcoder.community.growth.api.model.LegacyRewardAccountView");
assertClassIsRetired("com.nowcoder.community.growth.service.RewardAccountService");
assertClassIsRetired("com.nowcoder.community.wallet.service.WalletMigrationService");
assertClassIsRetired("com.nowcoder.community.growth.api.action.GrowthGrantActionApi");
assertClassIsRetired("com.nowcoder.community.growth.service.UnifiedGrantService");
assertClassIsRetired("com.nowcoder.community.growth.mapper.RewardAccountMapper");
assertClassIsRetired("com.nowcoder.community.growth.mapper.RewardLedgerMapper");
assertClassIsRetired("com.nowcoder.community.growth.mapper.RewardGrantRecordMapper");
```

Add these assertions to `legacyRewardShopMapperResourcesShouldBeRemoved()`:

```java
assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_account_mapper.xml")).doesNotExist();
assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_ledger_mapper.xml")).doesNotExist();
assertThat(MODULE_ROOT.resolve("src/main/resources/mapper/reward_grant_record_mapper.xml")).doesNotExist();
```

- [ ] **Step 2: Run the retirement suite and confirm it fails**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=LegacyGrowthSurfaceRetirementTest test
```

Expected:

- RED
- the new retirement assertions fail because the legacy reward/account runtime still exists

- [ ] **Step 3: Checkpoint the retirement lock**

Note: do not create a git commit unless the user explicitly asks for one.

---

### Task 2: Remove The Legacy Reward-Account Runtime Surface

**Files:**
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/api/query/LegacyRewardAccountQueryApi.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/api/model/LegacyRewardAccountView.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardAccountService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMigrationService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/api/action/GrowthGrantActionApi.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardAccountMapper.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardLedgerMapper.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardGrantRecordMapper.java`
- Delete: `backend/community-app/src/main/resources/mapper/reward_account_mapper.xml`
- Delete: `backend/community-app/src/main/resources/mapper/reward_ledger_mapper.xml`
- Delete: `backend/community-app/src/main/resources/mapper/reward_grant_record_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`

- [ ] **Step 1: Remove the Java runtime classes and mappers**

Delete these files:

```text
backend/community-app/src/main/java/com/nowcoder/community/growth/api/query/LegacyRewardAccountQueryApi.java
backend/community-app/src/main/java/com/nowcoder/community/growth/api/model/LegacyRewardAccountView.java
backend/community-app/src/main/java/com/nowcoder/community/growth/service/RewardAccountService.java
backend/community-app/src/main/java/com/nowcoder/community/wallet/service/WalletMigrationService.java
backend/community-app/src/main/java/com/nowcoder/community/growth/api/action/GrowthGrantActionApi.java
backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java
backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardAccountMapper.java
backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardLedgerMapper.java
backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/RewardGrantRecordMapper.java
```

- [ ] **Step 2: Remove the retired MyBatis XML resources**

Delete these files:

```text
backend/community-app/src/main/resources/mapper/reward_account_mapper.xml
backend/community-app/src/main/resources/mapper/reward_ledger_mapper.xml
backend/community-app/src/main/resources/mapper/reward_grant_record_mapper.xml
```

- [ ] **Step 3: Run the retirement suite and verify it turns GREEN**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=LegacyGrowthSurfaceRetirementTest test
```

Expected:

- PASS
- the retired reward/account runtime is absent from the classpath and mapper resources

- [ ] **Step 4: Checkpoint the runtime retirement diff**

Note: do not create a git commit unless the user explicitly asks for one.

---

### Task 3: Remove Legacy-Only Tests And Keep Wallet-First Tests Green

**Files:**
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/RewardAccountServiceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/mapper/RewardAccountMapperTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/mapper/RewardLogMapperPersistenceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/UnifiedGrantServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletLedgerServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/service/PointsProjectionServiceIntegrationTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/service/WalletLedgerServiceTest.java`

- [ ] **Step 1: Delete tests that only validate the retired runtime**

Delete:

```text
backend/community-app/src/test/java/com/nowcoder/community/growth/service/RewardAccountServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/growth/mapper/RewardAccountMapperTest.java
backend/community-app/src/test/java/com/nowcoder/community/growth/mapper/RewardLogMapperPersistenceTest.java
backend/community-app/src/test/java/com/nowcoder/community/growth/service/UnifiedGrantServiceTest.java
```

- [ ] **Step 2: Remove legacy migration coverage from `WalletLedgerServiceTest`**

Make these edits in `WalletLedgerServiceTest`:

```java
// remove this field
@Autowired
private WalletMigrationService migrationService;

// remove these setup lines if they are only legacy cleanup
jdbcTemplate.update("delete from reward_ledger");
jdbcTemplate.update("delete from reward_account");

// delete these test methods
void migrateOpeningBalanceShouldCreateOneOpeningTxnFromLegacyRewardAccount() { ... }
void migrateUserShouldCarryFrozenOnlyLegacyBalanceIntoMigrationHold() { ... }
void migrateUserShouldCarryBothAvailableAndFrozenLegacyBalances() { ... }
```

- [ ] **Step 3: Run the focused wallet-first regression suite**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=TaskProgressApplicationServiceTest,TaskProgressServiceTest,TaskProgressServiceUnitTest,PointsProjectionServiceIntegrationTest,WalletLedgerServiceTest,LegacyGrowthSurfaceRetirementTest test
```

Expected:

- PASS
- no test still depends on the retired reward/account runtime

- [ ] **Step 4: Checkpoint the test cleanup**

Note: do not create a git commit unless the user explicitly asks for one.

---

### Task 4: Update Docs To Reflect Runtime Retirement

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Modify: `docs/business-logic/growth-task-grant-level-flow.md`
- Modify: `docs/superpowers/specs/2026-04-24-community-app-growth-reward-account-runtime-retirement-design.md`

- [ ] **Step 1: Mark the new spec as ready for implementation**

Update the spec header:

```markdown
**Status:** Implementation plan completed
```

- [ ] **Step 2: Update architecture status**

Adjust `docs/ARCHITECTURE.md` so the `growth` domain note says the deferred reward/account legacy surface cleanup is complete and wallet is the only online balance runtime.

- [ ] **Step 3: Update system design status**

Adjust `docs/SYSTEM_DESIGN.md` so the deferred application-layer scope no longer lists growth reward/account runtime cleanup as pending.

- [ ] **Step 4: Rewrite the legacy grant section in the growth business-logic doc**

Replace the `UnifiedGrantService`-based runtime description with wording that matches the current online behavior:

```text
- task-progress rewards issue directly through wallet reward actions
- points projection writes directly through wallet reward actions
- reward_account / reward_ledger / reward_grant_record are retained historical tables, not active runtime surfaces
```

- [ ] **Step 5: Run doc-adjacent regression**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=LegacyGrowthSurfaceRetirementTest,ControllerBoundaryArchTest,DomainBoundaryArchTest,ListenerBoundaryArchTest test
```

Expected:

- PASS
- docs and retirement assertions match the enforced runtime state

---

### Task 5: Run Full Verification

**Files:**
- Test: `backend/community-app`

- [ ] **Step 1: Run the full module suite**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app test
```

Expected:

- PASS
- `BUILD SUCCESS`
- the retired reward/account runtime no longer appears in the compiled module

- [ ] **Step 2: Review the final diff**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git status --short
```

Expected:

- only the intended runtime retirement, test cleanup, and documentation updates remain in the worktree

---

## Self-Review

- Spec coverage: the plan covers runtime class removal, mapper/resource removal, test retirement, retirement assertions, doc updates, and full verification from the approved spec.
- Placeholder scan: no `TODO`, `TBD`, or deferred implementation text remains inside the task steps.
- Type consistency: the plan only references existing class names and paths from the current worktree and does not introduce new runtime abstractions.

# Community App Growth Reward-Account Runtime Retirement Design

**Date:** 2026-04-24
**Status:** Implemented in current worktree
**Owner:** Codex

---

## 1. Goal

Finish the last deferred long-term application-layer cleanup in `backend/community-app/` by retiring the remaining legacy `growth` reward/account runtime surface and making `wallet` the only online balance source of truth.

This design is intentionally narrower than a full wallet schema cleanup. The target is:

- no online runtime path reads or writes the legacy `reward_account` / `reward_ledger` / `reward_grant_record` surface
- `wallet` remains the only active balance and posting runtime
- historical tables can stay in schema as retained legacy data

---

## 2. Scope And Non-Goals

### 2.1 In Scope

- retirement of the remaining legacy reward/account runtime classes in `growth`
- retirement of the wallet migration bridge that still reads legacy reward balances
- retirement of the legacy reward/account mappers and mapper XML resources
- retirement of tests that only validate the removed runtime surface
- documentation updates needed to state that reward/account is a retired legacy surface

### 2.2 Out Of Scope

- physical schema removal of `reward_account`, `reward_ledger`, or `reward_grant_record`
- historical data migration or archival
- redesign of current wallet posting behavior
- redesign of task-progress semantics
- reintroduction of any bridge service that keeps the legacy surface alive

### 2.3 Non-Goals

- do not add a new compatibility layer around legacy reward data
- do not move history into a new audit store in this slice
- do not widen this work into unrelated `wallet`, `market`, or `ops` refactors

---

## 3. Current State

### 3.1 What Is Already True

Most online reward-related flows have already moved away from the legacy reward-account runtime:

- `PointsProjectionService` writes directly through `WalletRewardActionApi`
- `TaskProgressService` auto-issues rewards through `WalletRewardActionApi`
- `TaskProgressApplicationService` is already the owner write entry for growth task-progress

That means the active runtime is already wallet-first for the main write paths.

### 3.2 What Still Remains

The remaining legacy runtime surface is now mostly isolated:

- `LegacyRewardAccountQueryApi`
- `LegacyRewardAccountView`
- `RewardAccountService`
- `WalletMigrationService`
- `GrowthGrantActionApi`
- `UnifiedGrantService`
- `RewardAccountMapper`
- `RewardLedgerMapper`
- `RewardGrantRecordMapper`

These pieces no longer represent the desired steady-state architecture.

### 3.3 Why They Are Still Legacy Surface

The retained classes fall into three categories:

1. `RewardAccountService` and `LegacyRewardAccountQueryApi`
   - keep the old reward-account object model alive
   - expose `reward_account` as if it were still an online owner runtime

2. `WalletMigrationService`
   - still reads `LegacyRewardAccountQueryApi`
   - but currently has no `main/java` caller and only survives as a migration bridge plus test fixture

3. `GrowthGrantActionApi` and `UnifiedGrantService`
   - still model grant bookkeeping around `reward_grant_record`
   - but currently have no `main/java` consumers

This is now closer to dead bridge code than to an active online collaboration boundary.

### 3.4 Existing Long-Term Direction

The wallet design already established the intended end state:

- `reward_account` should only be a migration source, not the primary online balance model
- `reward_ledger` and `reward_grant_record` should remain historical audit data, not active online write targets
- new wallet and reward flows should not dual-write legacy reward tables

This design closes the remaining mismatch between that long-term direction and the code still left on the runtime classpath.

---

## 4. Design Options

### 4.1 Recommended: Runtime Retirement, Schema Retention

Retire the legacy reward/account runtime surface from `community-app`, while keeping the underlying legacy tables physically present in schema as historical data.

This gives the desired runtime end state without mixing in destructive schema cleanup.

### 4.2 Read-Only Bridge Retention

Keep `LegacyRewardAccountQueryApi` and `WalletMigrationService` as a read-only compatibility bridge, while retiring the mutable runtime services.

This is smaller, but still keeps the legacy reward-account model alive in online runtime code and does not fully finish the deferred scope.

### 4.3 Full Runtime And Schema Deletion

Remove both runtime code and legacy tables in one pass.

This is too wide for the current worktree because it mixes runtime cleanup, data lifecycle, and schema migration into one change.

**Decision:** choose **4.1**.

---

## 5. Target Runtime State

### 5.1 Online Balance Source Of Truth

After this work, the only online balance source of truth is `wallet`.

The active reward-related write paths remain:

- `PointsProjectionService -> WalletRewardActionApi`
- `TaskProgressApplicationService -> TaskProgressService -> WalletRewardActionApi`

No online growth reward/account runtime remains alongside these paths.

### 5.2 Classes To Retire

Retire the following runtime classes:

- `com.nowcoder.community.growth.api.query.LegacyRewardAccountQueryApi`
- `com.nowcoder.community.growth.api.model.LegacyRewardAccountView`
- `com.nowcoder.community.growth.service.RewardAccountService`
- `com.nowcoder.community.wallet.service.WalletMigrationService`
- `com.nowcoder.community.growth.api.action.GrowthGrantActionApi`
- `com.nowcoder.community.growth.service.UnifiedGrantService`

These classes should not remain available on the runtime classpath after the rollout.

### 5.3 Mappers And Resources To Retire

Retire the corresponding persistence surface:

- `RewardAccountMapper`
- `RewardLedgerMapper`
- `RewardGrantRecordMapper`
- `mapper/reward_account_mapper.xml`
- `mapper/reward_ledger_mapper.xml`
- `mapper/reward_grant_record_mapper.xml`

The goal is not merely “stop using” them. They should be removed so the retired runtime surface cannot silently return.

### 5.4 Data Boundary After Retirement

The tables remain physically present:

- `reward_account`
- `reward_ledger`
- `reward_grant_record`

But they are downgraded to:

- historical data
- audit/reference data
- no longer read or written by active `community-app` runtime code

This is a runtime retirement, not a schema destruction project.

---

## 6. Testing Strategy

### 6.1 Tests To Retire

Retire tests that only prove the removed runtime surface still exists:

- `RewardAccountServiceTest`
- `RewardAccountMapperTest`
- `RewardLogMapperPersistenceTest`
- `UnifiedGrantServiceTest`
- the legacy reward-account migration cases inside `WalletLedgerServiceTest`

These tests are validating legacy runtime behavior that the new design explicitly removes.

### 6.2 Tests To Keep

Keep and use current wallet-first tests as the functional safety net:

- `TaskProgressApplicationServiceTest`
- `TaskProgressServiceTest`
- `TaskProgressServiceUnitTest`
- `PointsProjectionServiceIntegrationTest`
- active wallet tests for recharge, transfer, withdraw, market, and admin ops

These tests already protect the intended online runtime.

### 6.3 Retirement Assertions

Expand `LegacyGrowthSurfaceRetirementTest` so it also proves the reward/account legacy runtime has exited:

- retired classes are not loadable from the classpath
- retired mapper XML resources no longer exist
- schema assertions continue to distinguish between “retired runtime surface” and “tables intentionally retained for history”

The retirement test becomes the explicit repository-level guardrail for this final slice.

---

## 7. Documentation Changes

### 7.1 Architecture Documents

Update:

- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`

to say that the previously deferred `growth reward/account legacy surface` cleanup is complete.

The documents should describe:

- wallet is the only online balance runtime
- the legacy reward/account surface is retired from runtime
- legacy tables are still retained as history

### 7.2 Business-Logic Documents

Update:

- `docs/business-logic/growth-task-grant-level-flow.md`

so it no longer describes `UnifiedGrantService` as the current online points projection path.

It should reflect the actual runtime:

- task-progress rewards flow through wallet reward actions
- points projection flows directly through wallet reward actions
- legacy reward-account grant orchestration has been retired

### 7.3 Wallet Design Alignment

Where needed, align the wording in wallet-oriented design documents with the now-implemented reality:

- old reward-account tables remain retained legacy data
- active code paths no longer use them

The purpose is to remove any ambiguity between “legacy tables still exist” and “legacy runtime is still active”.

---

## 8. Implementation Order

### 8.1 Step 1: Remove Runtime Surface

Delete the retired runtime classes, mappers, and mapper XML resources.

This is the decisive change that turns the deferred scope into a retired surface instead of a dormant one.

### 8.2 Step 2: Remove Legacy-Only Tests

Delete or rewrite tests that only validate the retired runtime.

Then extend the retirement test so the removed surface is guarded by explicit assertions.

### 8.3 Step 3: Update Docs

Update architecture and business-logic docs after the runtime and tests have converged.

This ordering avoids documenting a state that has not yet been enforced in code.

---

## 9. Risks And Mitigations

### 9.1 Risk: Hidden Runtime Dependency Still Exists

A forgotten caller could still depend on one of the retired services.

Mitigation:

- remove the classes rather than leaving them deprecated
- run focused regression plus full module tests
- use retirement assertions to prove the surface is gone

### 9.2 Risk: Documentation Confuses History With Runtime

Because the legacy tables remain in schema, readers may assume the runtime still uses them.

Mitigation:

- explicitly distinguish runtime retirement from schema retention
- update `ARCHITECTURE.md`, `SYSTEM_DESIGN.md`, and growth business-logic docs together

### 9.3 Risk: Scope Expands Into Data Lifecycle Work

It is easy to conflate “stop using the old runtime” with “physically remove historical data”.

Mitigation:

- keep schema deletion and archival out of scope
- treat this slice as runtime convergence only

---

## 10. Verification Plan

### 10.1 Focused Verification

Run focused suites covering:

- `LegacyGrowthSurfaceRetirementTest`
- `TaskProgressApplicationServiceTest`
- `TaskProgressServiceTest`
- `TaskProgressServiceUnitTest`
- `PointsProjectionServiceIntegrationTest`
- impacted wallet tests that remain part of the online runtime

### 10.2 Full Module Verification

Run:

```bash
cd backend
mvn -pl community-app test
```

Success means:

- the module still builds and tests green
- no retired reward/account runtime classes remain
- active wallet-first reward flows still pass

---

## 11. Success Criteria

This deferred scope is considered complete when all of the following are true:

- legacy reward/account runtime classes are removed from `community-app`
- legacy reward/account mapper interfaces and XML resources are removed
- no active runtime test still depends on legacy reward-account behavior
- retirement tests explicitly guard the removed surface
- docs no longer describe reward/account as an active online runtime
- `community-app` regression remains green

At that point, the long-term application-layer design no longer has an open deferred reward/account runtime cleanup inside this worktree.

# BBS Reliability P1 Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the remaining P1 governance surface for the BBS reliability platform without expanding into P2/P3 optimization work.

**Architecture:** Extend the existing `ops` governance domain for bounded replay, compensation triggers, audit, and hot-cache governance. `ops.controller` enters only `ops.application`; cross-domain repair and cache operations go through owner `api.query` / `api.action` contracts, and technical outbox operations go through ops-owned ports.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Security, MyBatis/JDBC, Redis via existing cache adapters, Micrometer, JUnit 5, Mockito, Spring MockMvc, ArchUnit, Maven.

## Global Constraints

- This plan covers P1 only.
- Do not add P2/P3 cache optimization features such as TTL jitter, single-flight rebuilds, rank-version redesign, or pressure-test automation.
- `ops` must not become owner of content, social, search, notice, growth, or hot-feed business facts.
- Governance must not directly modify business facts.
- `controller`, listener, handler, bridge, enqueuer, and job code may call only same-domain `*ApplicationService`.
- `ops.application` must call foreign owner domains only through `api.query`, `api.action`, or `api.model`.
- `ops.application` must not depend on owner `application`, `domain`, `infrastructure`, mapper, or dataobject packages.
- All `/api/ops/**` endpoints require `ROLE_ADMIN`.
- Mutating governance operations require a non-blank `reason`.
- Batch replay must require topic, `DEAD` status, created time range, and bounded limit.
- Plans and specs live under `docs/superpowers`; handbook docs live under `docs/handbook`.

---

## File Structure

### Ops Audit And Metrics

- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/GovernanceAuditPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/GovernanceMetrics.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/RecordGovernanceAuditCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/GovernanceAuditResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/domain/model/GovernanceAction.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/domain/model/GovernanceResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/persistence/MyBatisGovernanceAuditRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/persistence/dataobject/GovernanceAuditDataObject.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/persistence/mapper/GovernanceAuditMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/governance_audit_mapper.xml`
- Modify: `deploy/mysql/community/010_schema_shared.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/observability/ReliabilityGovernanceMetrics.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/persistence/MyBatisGovernanceAuditRepositoryTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/observability/ReliabilityGovernanceMetricsTest.java`

### Batch Outbox Replay

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxGovernancePort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/ReplayOutboxBatchCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxBatchReplayResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxBatchReplayItemResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/OutboxOpsController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxBatchReplayRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxBatchReplayResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxBatchReplayItemResponse.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/OutboxOpsControllerTest.java`

### Compensation Trigger Governance

- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/CompensationGovernanceApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/CompensationTriggerPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/TriggerCompensationCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/CompensationTriggerResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/CompensationOpsController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/TriggerCompensationRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/CompensationTriggerResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/compensation/DefaultCompensationTriggerAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxLeaseRecoveryPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/OutboxLeaseRecoveryResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxLeaseRecoveryAdapter.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/application/CompensationGovernanceApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/CompensationOpsControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/compensation/DefaultCompensationTriggerAdapterTest.java`

### Hot-Cache Owner APIs

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/query/HotFeedCacheGovernanceQueryApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/action/HotFeedCacheGovernanceActionApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/HotFeedCacheStatusView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/HotFeedCachePrewarmRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/HotFeedCachePrewarmResultView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/HotFeedDegradationSignalView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/UpdateHotFeedDegradationSignalRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/HotFeedCacheGovernanceApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/PrewarmHotFeedCacheCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/UpdateHotFeedDegradationSignalCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/HotFeedCacheStatusResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/HotFeedCachePrewarmResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/HotFeedDegradationSignalResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/HotFeedCacheGovernanceApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostFeedCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/HotFeedCacheGovernanceApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/HotFeedCacheGovernanceApiAdapterTest.java`

### Hot-Cache Ops Surface

- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/HotCacheGovernanceApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/GetHotCacheStatusCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/PrewarmHotCacheCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/command/UpdateHotCacheDegradationCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/HotCacheStatusResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/HotCachePrewarmResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/HotCacheDegradationSignalResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/HotCacheOpsController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/HotCacheStatusResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/HotCachePrewarmRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/HotCachePrewarmResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/HotCacheDegradationRequest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/HotCacheDegradationResponse.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/application/HotCacheGovernanceApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/HotCacheOpsControllerTest.java`

### Architecture And Documentation

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`
- Modify: `docs/handbook/reliability.md`
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/observability.md`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/*ArchTest.java`

---

### Task 1: Governance Audit And Metrics Foundation

**Files:**
- Create the ops audit port, command, result, domain enums, MyBatis repository, mapper, dataobject, and migration listed above.
- Modify `ReliabilityGovernanceMetrics` to implement the broader `GovernanceMetrics` interface.
- Test audit persistence and metric tags.

**Interfaces:**
- Produces `GovernanceAuditPort.record(RecordGovernanceAuditCommand command): GovernanceAuditResult`.
- Produces `GovernanceMetrics.recordOutboxBatchReplay(String topic, String result, long count)`.
- Produces `GovernanceMetrics.recordGovernanceAction(String action, String result)`.
- Produces `GovernanceMetrics.recordHotCacheGovernance(String operation, String result, String scope)`.
- Produces `GovernanceMetrics.recordCompensationTrigger(String jobName, String result)`.

- [ ] **Step 1: Write failing audit repository test**

Add `MyBatisGovernanceAuditRepositoryTest` that inserts one audit command for `OUTBOX_REPLAY_BATCH` through the MyBatis-backed repository and asserts the stored row contains actor, action, target, reason, result, summary JSON, trace id, and created time.

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=MyBatisGovernanceAuditRepositoryTest
```

Expected: compilation fails because audit classes and migration do not exist.

- [ ] **Step 2: Add migration and repository**

Create `ops_governance_audit` with indexed columns `action`, `actor_user_id`, `target_type`, `target_id`, `result`, and `created_at`. Store request and summary as sanitized JSON text.

- [ ] **Step 3: Extend metrics tests**

Extend `ReliabilityGovernanceMetricsTest` to assert the new metric families and bounded labels.

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=ReliabilityGovernanceMetricsTest
```

Expected: compilation fails until `GovernanceMetrics` and new metric methods exist.

- [ ] **Step 4: Implement metrics**

Keep existing replay metric behavior and add the new counters. Clamp tag values to existing bounded lengths and do not include actor ids, outbox ids, event ids, board ids, or trace ids.

- [ ] **Step 5: Verify task**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=MyBatisGovernanceAuditRepositoryTest,ReliabilityGovernanceMetricsTest
```

Expected: all selected tests pass.

### Task 2: Bounded Batch Outbox Replay

**Files:**
- Modify existing outbox governance application/controller files.
- Create batch command, result, and DTO files listed in the file structure.

**Interfaces:**
- Produces `OutboxGovernanceApplicationService.replayBatch(ReplayOutboxBatchCommand command): OutboxBatchReplayResult`.
- Produces `POST /api/ops/outbox/replay-batch`.

- [ ] **Step 1: Write failing application tests**

Extend `OutboxGovernanceApplicationServiceTest` with:

- rejects blank topic
- rejects non-`DEAD` status
- rejects missing created range
- rejects invalid range
- rejects `limit > 500`
- partially replays eligible rows and returns rejected row details
- writes one parent audit and row-level audit summaries
- records batch metrics

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxGovernanceApplicationServiceTest
```

Expected: compilation fails until batch command/result APIs exist.

- [ ] **Step 2: Implement batch command/result**

`ReplayOutboxBatchCommand` must normalize topic/status/reason and expose `safeLimit()` with max `500`.

`OutboxBatchReplayResult` must include:

- `topic`
- `requestedCount`
- `replayedCount`
- `rejectedCount`
- `notRequeuedCount`
- `result`
- `items`

Each item result must include:

- `outboxId`
- `eventId`
- `topic`
- `beforeStatus`
- `afterStatus`
- `replayed`
- `result`
- `message`

- [ ] **Step 3: Implement application behavior**

Use existing `findEvents` and single-row replay decision logic. Do not call handlers. Do not add a raw SQL bulk update, because per-row decision, audit, and result reporting are required.

- [ ] **Step 4: Write failing controller tests**

Extend `OutboxOpsControllerTest` to cover:

- non-admin rejected
- admin batch replay request binds actor from JWT
- invalid request returns `400`
- response includes aggregate and item rows

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxOpsControllerTest
```

Expected: compilation fails until the controller endpoint and DTOs exist.

- [ ] **Step 5: Implement controller endpoint**

Add `POST /api/ops/outbox/replay-batch`. Request body must not accept actor id.

- [ ] **Step 6: Verify task**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxGovernanceApplicationServiceTest,OutboxOpsControllerTest
```

Expected: selected tests pass.

### Task 3: Compensation Trigger Governance

**Files:**
- Create compensation application, port, command/result, controller, DTO, and adapter files listed above.
- Add outbox lease recovery result and store method if the existing store only exposes lease recovery internally.

**Interfaces:**
- Produces `CompensationGovernanceApplicationService.trigger(TriggerCompensationCommand command): CompensationTriggerResult`.
- Produces `POST /api/ops/compensations/{jobName}/trigger`.
- Produces allow-listed job names:
  - `outboxRecoverExpiredLeases`
  - `searchPostProjectionRepair`
  - `hotFeedProjectionRepair`
  - `growthTaskProjectionRepair`
  - `noticeProjectionRepair`

- [ ] **Step 1: Write failing application tests**

Add `CompensationGovernanceApplicationServiceTest` for:

- unknown job rejected
- blank reason rejected
- invalid limit rejected
- known job delegates to port
- audit and metrics recorded on accepted trigger
- failed trigger records failed metric and audit

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=CompensationGovernanceApplicationServiceTest
```

Expected: compilation fails until compensation governance classes exist.

- [ ] **Step 2: Implement application and allowlist**

Keep the allowlist in application code or configuration with tests. Do not route arbitrary job names to Spring beans.

- [ ] **Step 3: Implement adapter**

`DefaultCompensationTriggerAdapter` may call:

- outbox technical lease recovery through an ops-owned port/store method
- foreign owner `api.action` contracts for projection repair jobs when those owner actions exist

If an owner action API is missing in this P1 slice, return `SKIPPED` with an explicit message, record audit/metrics, and do not perform business repair from `ops`. Adding a new owner repair action requires a follow-up owner-domain spec and tests.

- [ ] **Step 4: Add controller tests**

Add `CompensationOpsControllerTest` for admin security, actor binding, valid request, and invalid request.

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=CompensationOpsControllerTest
```

Expected: compilation fails until controller and DTOs exist.

- [ ] **Step 5: Verify task**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=CompensationGovernanceApplicationServiceTest,CompensationOpsControllerTest,DefaultCompensationTriggerAdapterTest
```

Expected: selected tests pass.

### Task 4: Content Owner Hot-Cache Governance APIs

**Files:**
- Create content `api.query`, `api.action`, and `api.model` contracts listed above.
- Create `HotFeedCacheGovernanceApplicationService`.
- Modify `PostFeedCache` and `RedisPostFeedCache` only for the minimum status/degradation methods needed.

**Interfaces:**
- Produces `HotFeedCacheGovernanceQueryApi.getStatus(scope, boardId): HotFeedCacheStatusView`.
- Produces `HotFeedCacheGovernanceQueryApi.getDegradationSignal(): HotFeedDegradationSignalView`.
- Produces `HotFeedCacheGovernanceActionApi.prewarm(HotFeedCachePrewarmRequest request): HotFeedCachePrewarmResultView`.
- Produces `HotFeedCacheGovernanceActionApi.updateDegradationSignal(UpdateHotFeedDegradationSignalRequest request): HotFeedDegradationSignalView`.

- [ ] **Step 1: Write failing content application tests**

Add `HotFeedCacheGovernanceApplicationServiceTest` for:

- global status reads rank version and item count
- board status requires board id
- prewarm global writes rank version and hot entries using current content facts
- prewarm board writes board hot entries
- degradation signal can be set and cleared
- invalid limit rejected

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=HotFeedCacheGovernanceApplicationServiceTest
```

Expected: compilation fails until owner API/application types exist.

- [ ] **Step 2: Implement content application service**

Use content owner repositories and existing cache ports. Keep prewarm limit capped at `500`. Do not add P3 optimizations.

- [ ] **Step 3: Implement content API adapter**

Create `HotFeedCacheGovernanceApiAdapter` in `content.infrastructure.api`. It implements both content governance APIs and delegates to `HotFeedCacheGovernanceApplicationService`.

- [ ] **Step 4: Verify task**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=HotFeedCacheGovernanceApplicationServiceTest,HotFeedCacheGovernanceApiAdapterTest
```

Expected: selected tests pass.

### Task 5: Ops Hot-Cache Governance Surface

**Files:**
- Create ops hot-cache application, command/result, controller, and DTO files listed above.

**Interfaces:**
- Produces `GET /api/ops/hot-cache/status`.
- Produces `POST /api/ops/hot-cache/prewarm`.
- Produces `GET /api/ops/hot-cache/degradation`.
- Produces `POST /api/ops/hot-cache/degradation`.

- [ ] **Step 1: Write failing ops application tests**

Add `HotCacheGovernanceApplicationServiceTest` for:

- status delegates to content query API
- prewarm delegates to content action API
- degradation update delegates to content action API
- board scope requires board id
- mutating requests require reason
- audit and metrics recorded

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=HotCacheGovernanceApplicationServiceTest
```

Expected: compilation fails until ops hot-cache classes exist.

- [ ] **Step 2: Implement ops application**

Inject only content `api.query` and `api.action` contracts. Do not inject content application services.

- [ ] **Step 3: Write failing controller tests**

Add `HotCacheOpsControllerTest` for:

- non-admin rejected
- admin can query status
- admin can prewarm
- admin can query degradation
- admin can set and clear degradation signal
- invalid board scope request returns `400`

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=HotCacheOpsControllerTest
```

Expected: compilation fails until controller and DTOs exist.

- [ ] **Step 4: Implement controller**

All endpoints stay under `/api/ops/hot-cache/**`. Actor id comes from authentication for mutating calls.

- [ ] **Step 5: Verify task**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=HotCacheGovernanceApplicationServiceTest,HotCacheOpsControllerTest
```

Expected: selected tests pass.

### Task 6: Architecture Guardrails

**Files:**
- Modify ArchUnit tests listed above.

**Interfaces:**
- Produces guardrails for new ops, content API, and DTO boundaries.

- [ ] **Step 1: Extend controller boundary tests**

Ensure `ops.controller` still cannot depend on owner packages except ops application/result/command and controller DTOs.

- [ ] **Step 2: Extend DDD layering tests**

Ensure `ops.application` does not depend on owner application/domain/infrastructure packages and may depend only on owner `api.query`, `api.action`, and `api.model`.

- [ ] **Step 3: Extend DTO boundary tests**

Ensure application commands/results do not expose HTTP transport types and owner `api.*` contracts do not import `contracts.event`.

- [ ] **Step 4: Verify task**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: all architecture tests pass.

### Task 7: Documentation And Final Verification

**Files:**
- Modify handbook docs listed above.

**Interfaces:**
- Produces operator-facing docs for all P1 governance endpoints and metrics.

- [ ] **Step 1: Update reliability handbook**

Add sections for:

- bounded batch outbox replay
- compensation trigger semantics
- hot-cache governance semantics
- governance audit rules

- [ ] **Step 2: Update operations handbook**

Add runbooks for:

- selecting a safe batch replay range
- triaging partial batch replay
- triggering compensation jobs
- hot-cache status/prewarm/degradation response

- [ ] **Step 3: Update observability handbook**

Add metric contracts:

```text
community_outbox_batch_replay_total{topic,result}
community_governance_action_total{action,result}
community_hot_cache_governance_total{operation,result,scope}
community_compensation_trigger_total{job.name,result}
```

- [ ] **Step 4: Run final verification**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest,OutboxGovernanceApplicationServiceTest,OutboxOpsControllerTest,CompensationGovernanceApplicationServiceTest,CompensationOpsControllerTest,DefaultCompensationTriggerAdapterTest,HotFeedCacheGovernanceApplicationServiceTest,HotFeedCacheGovernanceApiAdapterTest,HotCacheGovernanceApplicationServiceTest,HotCacheOpsControllerTest,MyBatisGovernanceAuditRepositoryTest,ReliabilityGovernanceMetricsTest'
git diff --check -- docs/superpowers docs/handbook
```

Expected: all selected tests pass and Markdown has no whitespace errors.

## Acceptance Checklist

- [ ] Batch outbox replay requires topic, `DEAD`, created range, limit, and reason.
- [ ] Batch replay returns aggregate and per-row results.
- [ ] Batch replay requeues only by normal outbox worker path.
- [ ] Compensation trigger endpoint accepts only allow-listed jobs.
- [ ] Compensation trigger delegates to owner APIs or technical ports, not owner internals.
- [ ] Hot-cache status/prewarm/degradation goes through content owner APIs.
- [ ] Mutating governance calls write audit records.
- [ ] Metrics use bounded labels and no ids.
- [ ] `/api/ops/**` security remains `ROLE_ADMIN`.
- [ ] ArchUnit confirms DDD layering.
- [ ] Handbook docs describe runbooks and metrics.

## Self-Review

Spec coverage:

- Bounded batch replay is covered by Task 2.
- Compensation triggers are covered by Task 3.
- Hot-cache status, prewarm, and degradation are covered by Tasks 4 and 5.
- Audit and metrics are covered by Task 1 and consumed by Tasks 2, 3, and 5.
- Security, testing, and documentation are covered by Tasks 6 and 7.

Scope check:

- No P2/P3 cache optimization or business slice work is included.
- No direct owner fact mutation from `ops` is included.
- The plan keeps `ops` as governance, not owner of BBS facts.

Type consistency:

- `GovernanceAuditPort` and `GovernanceMetrics` are produced before services consume them.
- Content hot-cache APIs are produced before `ops` hot-cache application consumes them.
- Batch replay uses existing outbox query and replay primitives rather than a new worker path.

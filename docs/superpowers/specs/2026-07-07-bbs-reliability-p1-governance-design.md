# BBS Reliability P1 Governance Design

## Context

This spec covers only the remaining P1 governance surface for the BBS reliability platform. It is based on current `origin/main` at commit `df22c6e0`, where the first reliability wave has already landed.

Current main already includes:

- outbox governance query primitives in `common-outbox`
- `/api/ops/outbox/backlog`
- `/api/ops/outbox/events`
- single-event `/api/ops/outbox/events/{outboxId}/replay`
- `/api/ops/projections/lag`
- `community_outbox_replay_total{topic,result}`
- `community_cache_requests_total{cache="hot_feed",result,scope}`
- hot-feed fallback warming on read miss
- replay regression tests for search, notice, growth, and hot-feed projection
- ArchUnit guardrails for `ops.controller`, `ops.application`, and `ops.domain`

The remaining P1 work is governance surface completion, not a new reliability platform rewrite.

## Goals

1. Add bounded batch outbox replay with explicit scope and limit.
2. Add compensation job trigger governance that delegates to owner-domain capabilities.
3. Add hot-cache governance for status, prewarm, and degradation signals.
4. Add durable governance audit and richer bounded metrics for replay, compensation, and cache governance.
5. Update security, tests, and runbooks for the new P1 endpoints.

## Non-Goals

This spec does not cover P2 or P3 work:

- no new business slice implementation
- no new high-concurrency optimization such as TTL jitter or single-flight rebuild work
- no rank-version switching redesign
- no pressure test suite expansion
- no direct repair of business facts from `ops`
- no new `ops` owner domain for BBS facts

## Architecture Constraints

All implementation must keep the existing DDD Tactical Layering rules:

```text
ops.controller
  -> ops.application
      -> ops domain governance model
      -> ops application-owned technical ports
      -> foreign owner-domain api.query / api.action for owner-owned compensation
          -> owner ApplicationService
```

Rules:

- `ops.controller` can only call same-domain `ops.application`.
- `ops.application` can use `common-outbox` only through application-owned ports.
- `ops.application` can call owner domains only through `api.query` / `api.action`.
- `ops` must not import owner `application`, `domain`, `infrastructure`, mapper, or dataobject types.
- Owner compensation must decide inside owner `ApplicationService`; `ops` only requests a run.
- Governance may update technical state such as outbox rows, cache flags, and governance audit records.
- Governance must not directly modify posts, comments, likes, notices, growth progress, or search documents.

## Scope

### 1. Bounded Batch Outbox Replay

Add a batch replay command under the existing outbox governance surface.

Endpoint:

```text
POST /api/ops/outbox/replay-batch
```

Required input:

- `actorUserId` from authentication
- `topic`
- `status`, which must be `DEAD`
- `createdFrom`
- `createdTo`
- `limit`
- `reason`

Validation:

- `topic` must be non-blank.
- `status` must be exactly `DEAD`.
- `createdFrom` and `createdTo` must both be present.
- `createdFrom` must be before or equal to `createdTo`.
- `limit` must be between `1` and `500`.
- `reason` must be non-blank.
- The topic must have a registered outbox handler.

Behavior:

1. Find matching outbox rows using the existing governance query path.
2. Apply the same replay decision used for single-event replay to each row.
3. Requeue eligible `DEAD` rows to `PENDING`.
4. Do not call handlers directly.
5. Return a per-row result and aggregate counts.
6. Record one audit event for the batch and one child audit event per row.
7. Record metrics for total rows, replayed rows, rejected rows, and not-requeued rows.

Batch replay is allowed to be partially successful. Rejected rows must remain `DEAD` and must explain why they were not requeued.

### 2. Compensation Job Trigger Governance

Add a small allow-listed compensation trigger surface under `ops`.

Endpoint:

```text
POST /api/ops/compensations/{jobName}/trigger
```

The first P1 allowlist is:

- `outboxRecoverExpiredLeases`
- `searchPostProjectionRepair`
- `hotFeedProjectionRepair`
- `growthTaskProjectionRepair`
- `noticeProjectionRepair`

The allowlist is intentionally narrow. Adding a new job requires updating the spec, plan, tests, and runbook.

Behavior:

1. `ops.controller` binds request data and actor identity.
2. `CompensationGovernanceApplicationService` validates `jobName`, `limit`, and `reason`.
3. The service routes to an application-owned port or a foreign owner `api.action`.
4. Owner `ApplicationService` decides what can be repaired.
5. The result contains `jobName`, `accepted`, `processedCount`, `repairedCount`, `skippedCount`, and `result`.
6. Governance audit and metrics are recorded.

Important boundary:

- `outboxRecoverExpiredLeases` is a technical outbox runtime action and can use an ops-owned technical port.
- Search, hot-feed, growth, and notice repair triggers must go through the published `api.action` contract of the domain that owns the repair capability. `ops` must not directly call their application services or repositories.
- If a projection repair job is allow-listed but its owner action is not configured in P1, the governance trigger returns `SKIPPED`, records audit/metrics, and performs no business repair.

### 3. Hot-Cache Governance

Add governance entry points for hot-feed cache state, prewarm, and degradation signals.

Endpoints:

```text
GET  /api/ops/hot-cache/status?scope=global|board&boardId=<uuid?>
POST /api/ops/hot-cache/prewarm
GET  /api/ops/hot-cache/degradation
POST /api/ops/hot-cache/degradation
```

The owner-side contracts live under `content.api.query`, `content.api.action`, and `content.api.model`.

Status response:

- `scope`
- `boardId`
- `rankVersion`
- `itemCount`
- `summaryCacheAvailable`
- `degraded`
- `degradedReason`
- `lastPrewarmAt`

Prewarm request:

- `scope`
- optional `boardId`
- `limit`, capped at `500`
- `reason`

Prewarm behavior:

1. Content owner loads current eligible posts.
2. Content owner writes hot-feed cache entries and summary cache.
3. Content owner returns counts and degradation state.
4. `ops` records audit and metrics.

Degradation signal behavior:

- `GET` returns current manual and observed degradation state.
- `POST` sets or clears a manual degradation signal through content owner API.
- The signal is operational state, not business fact.
- Reads may use this signal to choose fallback behavior, but content facts remain authoritative.

### 4. Governance Audit

Add durable audit records for governance actions.

Audit fields:

- `id`
- `action`
- `actor_user_id`
- `target_type`
- `target_id`
- `scope`
- `reason`
- `request_json`
- `result`
- `summary_json`
- `trace_id`
- `created_at`

Actions:

- `OUTBOX_REPLAY_SINGLE`
- `OUTBOX_REPLAY_BATCH`
- `COMPENSATION_TRIGGER`
- `HOT_CACHE_STATUS`
- `HOT_CACHE_PREWARM`
- `HOT_CACHE_DEGRADATION_SIGNAL`

Audit rules:

- Every mutating governance operation must write an audit record.
- Read-only status calls may record low-volume audit only if configured; by default they should not create rows.
- Audit must not store raw payload bodies, Redis keys, tokens, cookies, authorization headers, or user-generated content.
- `target_id` must be a stable governance target id, not a high-cardinality metric label.

### 5. Metrics

Keep existing metrics and add bounded P1 metrics.

Existing:

```text
community_outbox_replay_total{topic,result}
community_cache_requests_total{cache="hot_feed",result,scope}
```

New:

```text
community_outbox_batch_replay_total{topic,result}
community_governance_action_total{action,result}
community_hot_cache_governance_total{operation,result,scope}
community_compensation_trigger_total{job.name,result}
```

Allowed result values:

- `ACCEPTED`
- `REPLAYED`
- `PARTIAL`
- `REJECTED`
- `NOT_REQUEUED`
- `FAILED`
- `DEGRADED`
- `SKIPPED`

Forbidden labels:

- user id
- outbox id
- event id
- board id
- trace id
- raw topic values outside the registered handler topic string
- timestamps
- Redis keys

### 6. Security

All new endpoints stay under `/api/ops/**` and require `ROLE_ADMIN`.

Request rules:

- Actor identity is taken from authentication, never from request body.
- Mutating operations require non-blank `reason`.
- Batch replay requires explicit time bounds and limit.
- Unknown compensation `jobName` is rejected.
- Hot-cache board operations require a `boardId`.
- Validation failures return `400`.
- Missing resource returns `404`.
- Replay conflict or not-requeueable rows return a successful batch response with per-row rejection details, unless the whole request is invalid.

### 7. Documentation Updates

Update:

- `docs/handbook/reliability.md`
- `docs/handbook/operations.md`
- `docs/handbook/observability.md`

The docs must describe:

- batch replay safety rules
- compensation trigger allowlist
- hot-cache status/prewarm/degradation runbook
- audit fields
- metric names and labels

## Acceptance Criteria

### Functional

- Admin can replay a bounded batch of `DEAD` outbox rows by topic and created time range.
- Batch replay never calls handlers directly.
- Batch replay returns aggregate and per-row results.
- Admin can trigger allow-listed compensation jobs.
- Compensation trigger paths do not directly mutate owner business facts from `ops`.
- Admin can query hot-cache status.
- Admin can prewarm global or board hot cache.
- Admin can set and clear hot-cache degradation signal.
- Governance audit is written for all mutating governance actions.
- New metrics are emitted with bounded labels.

### Boundary

- `ops.controller` depends only on `ops.application` and DTO/result types.
- `ops.application` does not depend on owner `application`, `domain`, `infrastructure`, mapper, or dataobject packages.
- Owner APIs do not import `ops` types.
- New content/search/notice/growth owner APIs use `api.query`, `api.action`, and `api.model`.
- No synchronous `api.*` contract imports `contracts.event`.

### Testing

Required test coverage:

- application unit tests for batch replay validation and partial success
- controller tests for admin access and request binding
- infrastructure tests for audit persistence and metrics
- owner API adapter tests for hot-cache governance
- compensation trigger routing tests
- ArchUnit tests for new `ops` and owner API boundaries
- handbook diff check

Verification commands:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest,OutboxGovernanceApplicationServiceTest,OutboxOpsControllerTest,CompensationGovernanceApplicationServiceTest,CompensationOpsControllerTest,HotCacheGovernanceApplicationServiceTest,HotCacheOpsControllerTest,GovernanceAuditRepositoryTest,ReliabilityGovernanceMetricsTest'
git diff --check -- docs/superpowers docs/handbook
```

## Explicit P1 Boundary

If implementation work discovers missing lower-level cache primitives, only add the minimum needed to support status, prewarm, and degradation governance. Do not add P3 cache optimization features such as TTL jitter, single-flight rebuilds, payload migration, rank version cutover redesign, or load-test automation in this P1 plan.

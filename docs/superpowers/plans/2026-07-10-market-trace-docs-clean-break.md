# Market, Trace, Documentation, And Final Clean Break Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Market's legacy address-snapshot reconstruction and the legacy MDC trace key, then make runtime configuration, deployment contracts, handbook documentation, and full-repository verification describe one clean current architecture.

**Architecture:** Market keeps its two-layer request idempotency and database replay lookup, but validates physical replay only against the order's persisted address ID. Trace context owns only `trace.id` and `span.id`; transport/business fields named `traceId` remain unchanged. A final repository contract script and handbook pass remove stale rollout/dual-path descriptions after the preceding three implementation plans.

**Tech Stack:** Java 17, Spring Boot, SLF4J MDC, Logback, MyBatis, MySQL/H2, JUnit 5, Mockito, AssertJ, Maven, Vitest, Bash, Markdown.

## Global Constraints

- Execute this plan last, after all other 2026-07-10 clean-break plans.
- Preserve HTTP `Idempotency-Key`, `(buyer_user_id, request_id)` uniqueness, pre-lock replay lookup, post-listing-lock replay lookup, and duplicate-insert recovery.
- A matching existing order returns before listing active/stock validation, including sold-out and concurrent-retry cases.
- Replay mismatch still returns `MarketErrorCode.REQUEST_REPLAY_CONFLICT`.
- A physical order replay compares request `addressId` directly with persisted `addressIdSnapshot`; a missing persisted snapshot is a conflict and never triggers an address repository lookup.
- New physical orders still require and persist a complete address snapshot; virtual orders still do not require an address snapshot.
- MDC owns only `trace.id` and `span.id`; remove constant/key `traceId` from all MDC write/save/restore/clear paths.
- Preserve HTTP JSON `Result.traceId`, error payloads, outbox/database `trace_id`, Java result fields, event-envelope `traceId`, WebSocket connection trace values, and human-readable log message tokens named `traceId`.
- Handbook files remain under `docs/handbook`; specs/plans remain under `docs/superpowers`.
- Historical 2026-07-07 specs/plans are not rewritten as current implementation, but the superseded spec receives an explicit link to the 2026-07-10 design.
- Do not restore root legacy packages, old virtual-market surfaces, the retired `GET /api/posts` feed-list route, or Content body-format migration logic.
- Development databases, Kafka topics, outbox rows, Redis projection/presence state, and browser bundles are rebuilt together; no in-place migration is supplied.

---

## File Structure

### Strict Market Replay

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/domain/model/MarketOrder.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceUnitTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/domain/model/MarketOrderTest.java`

### Canonical Trace MDC

- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContext.java`
- Modify: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/trace/TraceContextScope.java`
- Modify: `backend/community-common/common-core/src/test/java/com/nowcoder/community/common/trace/TraceContextSnapshotTest.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/AccessLogWebFilter.java`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/AccessLogWebFilterTest.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- Create: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImWebSocketMdcContractTest.java`
- Modify: `backend/community-common/common-observability/src/main/resources/logback/community-observability.xml`
- Create: `backend/community-common/common-core/src/test/java/com/nowcoder/community/common/trace/LegacyTraceMdcRetirementTest.java`

### Configuration, Deployment, And Documentation

- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-app/src/test/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/test/resources/application-test.yml`
- Modify: `deploy/nacos/config/community-kafka-policy.yaml`
- Modify: `deploy/nacos/config/im-realtime.yaml`
- Modify: `deploy/tests/nacos_config_seed.sh`
- Modify: `deploy/tests/topology_single_cluster.sh`
- Modify (created by Plan 1): `deploy/tests/projection_version_baseline_contract.sh`
- Modify (created by Plan 2): `deploy/tests/kafka_topics_contract.sh`
- Modify (created by Plan 3): `deploy/tests/community_backbone_topics_contract.sh`
- Create: `deploy/tests/development_clean_break_contract.sh`
- Modify: `docs/superpowers/specs/2026-07-07-bbs-reliability-p2-core-slices-design.md`
- Modify: `docs/handbook/architecture.md`
- Modify: `docs/handbook/system-design.md`
- Modify: `docs/handbook/business-flows.md`
- Modify: `docs/handbook/integration-contracts.md`
- Modify: `docs/handbook/reliability.md`
- Modify: `docs/handbook/data-and-storage.md`
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/security.md`
- Modify: `docs/handbook/testing.md`
- Modify: `docs/handbook/core-logic/async-event-backbone.md`
- Modify: `docs/handbook/core-logic/im-core-runtime.md`
- Modify: `docs/handbook/business-logic/im.md`
- Modify: `docs/handbook/business-logic/growth.md`
- Modify: `docs/handbook/business-logic/content.md`
- Modify: `docs/handbook/business-logic/notice-search-analytics-ops.md`
- Modify: `docs/handbook/business-logic/core-classes/im.md`
- Modify: `docs/handbook/business-logic/core-classes/growth.md`
- Modify: `docs/handbook/business-logic/core-classes/notice-search-analytics-ops.md`
- Modify: `docs/handbook/business-logic/workflows/im-session-messaging.md`
- Modify: `docs/handbook/business-logic/workflows/growth-reward-level.md`
- Modify: `docs/handbook/business-logic/workflows/notice-search-analytics-ops.md`
- Modify: `docs/handbook/core-logic-index.md`
- Modify: `docs/handbook/core-logic-coverage-audit.md`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/common/outbox/JdbcOutboxEventStoreGovernanceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/outbox/JdbcOutboxGovernanceAdapterTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/outbox/OutboxProjectionLagAdapterTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/persistence/MyBatisGovernanceAuditRepositoryTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/ops/application/OutboxGovernanceApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/observability/ReliabilityGovernanceMetricsTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/OutboxOpsControllerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/ProjectionOpsControllerTest.java`

---

### Task 1: Remove Legacy Market Address Reconstruction Without Weakening Idempotency

**Files:**
- Modify the five Market files listed above.

**Interfaces:**
- Changes `MarketOrder.assertReplayMatches(UUID buyerUserId, UUID listingId, int quantity, UUID addressId, MarketAddressSnapshot suppliedAddressSnapshot)` to `assertReplayMatches(UUID buyerUserId, UUID listingId, int quantity, UUID addressId)`.
- Deletes `MarketOrderApplicationService.replayAddressSnapshot(...)`.
- Preserves `createOrder` public signatures and result/error contracts.

- [ ] **Step 1: Write failing strict physical replay tests**

In `MarketOrderTest`, replace the fallback-snapshot test with:

```java
@Test
void physicalReplayWithMissingPersistedAddressIdShouldConflict() {
    MarketOrder order = MarketOrder.place(physicalPlacement());
    order.setAddressIdSnapshot(null);

    assertThatThrownBy(() -> order.assertReplayMatches(uuid(4), uuid(2), 1, uuid(5)))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));
}
```

Update the matching-success and quantity/listing/address-mismatch calls to the four-argument signature.

In `MarketOrderApplicationServiceUnitTest`, add two physical existing-order cases. One has `addressIdSnapshot=requestAddressId` and returns the order; the other has `addressIdSnapshot=null` and throws replay conflict. In both cases:

```java
verifyNoInteractions(marketAddressMapper);
verify(marketListingMapper, never()).selectByIdForUpdate(any());
```

Run:

```bash
(cd backend && mvn test -pl :community-app -Dtest=MarketOrderTest,MarketOrderApplicationServiceUnitTest)
```

Expected: compilation/test failure because replay still accepts a supplied address snapshot and queries the address repository.

- [ ] **Step 2: Make domain replay compare only persisted state**

Change the domain method to:

```java
public void assertReplayMatches(
        UUID buyerUserId,
        UUID listingId,
        int quantity,
        UUID addressId
) {
    if (!Objects.equals(this.buyerUserId, buyerUserId)
            || !Objects.equals(this.listingId, listingId)
            || this.quantity != quantity
            || !addressMatchesReplay(addressId)) {
        throw new BusinessException(
                MarketErrorCode.REQUEST_REPLAY_CONFLICT,
                "market order request replay conflict: requestId=" + requestId);
    }
}

private boolean addressMatchesReplay(UUID addressId) {
    if (!goodsType().isPhysical()) {
        return true;
    }
    return addressIdSnapshot != null && Objects.equals(addressIdSnapshot, addressId);
}
```

Do not compare receiver name/phone/region fields during replay: `addressIdSnapshot` is the immutable request identity, while the full snapshot remains persisted for fulfillment.

- [ ] **Step 3: Delete the application fallback from all replay branches**

Change the pre-lock, post-listing-lock, and duplicate-insert branches to the same call:

```java
existing.assertReplayMatches(buyerUserId, listingId, quantity, addressId);
```

Delete `replayAddressSnapshot`. Keep `requireActiveAddress` for creation of a new physical order; this is current validation, not replay fallback.

- [ ] **Step 4: Re-run the existing concurrency and sold-out replay coverage**

Ensure these existing behaviors still pass without loosening assertions:

```text
createOrderShouldReturnExistingReplayAfterListingLockEvenIfListingIsAlreadySoldOut
createOrderShouldReloadExistingOrderWhenInsertHitsDuplicateRequestId
concurrentReplayShouldReturnCommittedOrderEvenIfListingTurnsSoldOutBeforeRetryContinues
createOrderShouldRejectReplayWhenListingDoesNotMatchExistingOrder
createOrderShouldRejectReplayWhenPhysicalAddressDoesNotMatchExistingOrder
```

Run:

```bash
(cd backend && mvn test -pl :community-app -Dtest=MarketOrderApplicationServiceTest,MarketOrderApplicationServiceUnitTest,MarketOrderTest,MarketControllerTest)
```

Expected: PASS.

- [ ] **Step 5: Search and commit Market semantics**

```bash
rg -n 'replayAddressSnapshot|suppliedAddressSnapshot' backend/community-app/src/main backend/community-app/src/test --glob '*.java'
git add backend/community-app/src/main/java/com/nowcoder/community/market backend/community-app/src/test/java/com/nowcoder/community/market
git commit -m "refactor(market): require persisted replay address snapshot"
```

Expected: search has no match; request replay tests remain green.

### Task 2: Remove The Legacy `traceId` MDC Key

**Files:**
- Modify/create the Trace, gateway, IM realtime, and Logback files listed above.

**Interfaces:**
- `TraceContext` retains `MDC_KEY_TRACE_ID = "trace.id"` and `MDC_KEY_SPAN_ID = "span.id"` only.
- `TraceContextScope` saves/restores only ThreadLocal trace ID plus canonical trace/span MDC values.
- Gateway and IM event logs temporarily set/restore canonical `trace.id` only.

- [ ] **Step 1: Write failing common-core retirement and scope tests**

Create `LegacyTraceMdcRetirementTest`:

```java
@Test
void traceContextShouldNotExposeLegacyMdcConstant() {
    assertThatThrownBy(() -> TraceContext.class.getDeclaredField("MDC_KEY_" + "LEGACY_TRACE_ID"))
            .isInstanceOf(NoSuchFieldException.class);
}

@Test
void traceContextShouldNotMutateForeignLegacyNamedMdcEntry() {
    MDC.put("trace" + "Id", "foreign-value");
    TraceContext.set("11111111111111111111111111111111", "00f067aa0ba902b7");
    TraceContext.clear();
    assertThat(MDC.get("trace" + "Id")).isEqualTo("foreign-value");
    MDC.remove("trace" + "Id");
}
```

Rewrite `TraceContextSnapshotTest` assertions so nested scopes prove exact save/restore of `trace.id`, `span.id`, and the ThreadLocal only. Remove all references to the legacy constant.

Run:

```bash
(cd backend && mvn test -pl :community-common-core -Dtest=TraceContextSnapshotTest,LegacyTraceMdcRetirementTest)
```

Expected: FAIL because the constant and mutation paths still exist.

- [ ] **Step 2: Reduce `TraceContext` and `TraceContextScope`**

Delete `MDC_KEY_LEGACY_TRACE_ID`. `TraceContext.set` becomes:

```java
TraceId.set(t);
MDC.put(MDC_KEY_TRACE_ID, t);
String normalizedSpanId = TraceIdCodec.normalizeSpanId(spanId);
if (normalizedSpanId == null) {
    MDC.remove(MDC_KEY_SPAN_ID);
} else {
    MDC.put(MDC_KEY_SPAN_ID, normalizedSpanId);
}
```

`clear()` removes only canonical trace/span keys and the ThreadLocal. Remove `previousLegacyMdcTraceId` from the scope field, constructor, `open`, and `close`; preserve existing close ordering for OTel scope/span cleanup.

- [ ] **Step 3: Make gateway and IM log adapters canonical**

In `AccessLogWebFilter`, delete legacy constant, previous-value capture, put/remove, and restore. Preserve the log message argument named `traceId`; it is readable message data, not MDC.

Update `AccessLogWebFilterTest` to seed/restore only `TraceContext.MDC_KEY_TRACE_ID`, while continuing to assert structured JSON contains `trace.id`/`span.id` and no top-level `traceId`.

In `ImWebSocketHandler`, import `TraceContext` and set:

```java
private static final String MDC_TRACE_ID = TraceContext.MDC_KEY_TRACE_ID;
```

Create `ImWebSocketMdcContractTest` that reflectively reads `MDC_TRACE_ID` and asserts it equals `"trace.id"` and not `"traceId"`.

- [ ] **Step 4: Delete obsolete Logback filtering**

Remove only:

```xml
<excludeMdcKeyName>traceId</excludeMdcKeyName>
```

from `community-observability.xml`. Keep the MDC provider and text pattern's canonical `trace.id`/`span.id` fields. The old exclusion is unnecessary once no application writer creates that key.

- [ ] **Step 5: Verify and commit trace cleanup**

```bash
(cd backend && mvn test -pl :community-common-core,:community-common-observability,:community-gateway,:im-realtime -am -Dtest=TraceContextSnapshotTest,LegacyTraceMdcRetirementTest,AccessLogWebFilterTest,ImWebSocketMdcContractTest)
rg -n 'MDC_KEY_LEGACY_TRACE_ID|MDC_TRACE_ID\s*=\s*"traceId"|MDC\.(put|remove|get)\([^\n]*"traceId"|excludeMdcKeyName>traceId' backend --glob '*.java' --glob '*.xml' --glob '!**/target/**'
git add backend/community-common/common-core backend/community-common/common-observability backend/community-gateway backend/community-im/im-realtime
git commit -m "refactor(trace): remove legacy mdc key"
```

Expected: tests pass and the search has no match. Do not broaden the search to all `traceId` fields.

### Task 3: Reconcile Runtime Configuration And Retirement Fixtures

**Files:**
- Modify the residual runtime/deployment/test fixtures listed above.

**Interfaces:**
- Runtime configuration exposes only strict IM schema/projection behavior, routed Kafka fanout, mandatory Redis presence/worker slot, canonical owner topics, and `projection.im.policy`.

- [ ] **Step 1: Run the residual inventory searches**

Run from the repository root:

```bash
rg -n 'room-fanout\.(mode|transport)|IM_ROOM_FANOUT_(MODE|TRANSPORT)|owner-flush-interval|target-path|target-timeout|room-presence\.enabled|IM_ROOM_PRESENCE_ENABLED|worker-inbox-slot[^\n]*:0' backend deploy --glob '!**/target/**'
rg -n 'events\.publisher|EVENTS_PUBLISHER|projection\.(search\.post|growth\.task|user\.reward\.comment)|growth\.task\.(post-published|comment-created|like-created|like-removed)' backend/community-app/src/main backend/community-app/src/test/resources deploy --glob '!**/target/**'
rg -n 'projection\.(search\.post|growth\.task|user\.reward\.comment)' backend/community-app/src/test/java/com/nowcoder/community/ops backend/community-app/src/test/java/com/nowcoder/community/common/outbox --glob '*.java'
rg -n 'schemaVersionOrCurrent|legacyCompatibleVersionFloor|fromEpochMillis|@(user_policy|social_block|im_membership)_seed_version|CurrentSchemaVersionFilter' backend deploy --glob '!**/target/**'
```

Expected before cleanup: only residual configuration and generic governance fixtures missed by Plans 1-3. The independent `user_security_seed_version` timestamp domain is valid and intentionally excluded. Any production-code match means return to the owning plan rather than documenting around it.

- [ ] **Step 2: Remove residual config and update Nacos assertions**

Delete every obsolete property found above. Preserve explicit current properties:

```yaml
events:
  outbox:
    enabled: true

im:
  room-fanout:
    worker-inbox-slot: ${IM_ROOM_FANOUT_WORKER_INBOX_SLOT}
    routed-command-topic: im.command.room-fanout-routed
    publish-timeout: PT1S
```

`deploy/tests/nacos_config_seed.sh` must assert canonical owner/outbox/Kafka topics and IM routed/presence settings, and must no longer assert Search/Growth/User-reward secondary topics or publisher rollback selectors.

- [ ] **Step 3: Replace stale generic outbox-topic test fixtures**

The ops/governance tests may use retired topics only as arbitrary strings. Replace those examples with live topics without changing governance behavior:

```text
projection.search.post -> eventbus.content
projection.growth.task.post -> projection.im.policy
```

Replace every retired topic literal in the nine governance/ops test files listed in File Structure. Use `eventbus.content` for general owner-outbox backlog/replay examples and `projection.im.policy` where a second distinct live outbox topic is required. Update expected strings, meter tags, request JSON, and handler catalog assertions consistently. Do not create fake handlers for retired topics.

Run:

```bash
(cd backend && mvn test -pl :community-app -Dtest=JdbcOutboxEventStoreGovernanceTest,JdbcOutboxGovernanceAdapterTest,OutboxProjectionLagAdapterTest,MyBatisGovernanceAuditRepositoryTest,OutboxGovernanceApplicationServiceTest,ProjectionGovernanceApplicationServiceTest,ReliabilityGovernanceMetricsTest,OutboxOpsControllerTest,ProjectionOpsControllerTest)
rg -n 'projection\.(search\.post|growth\.task|user\.reward\.comment)' backend/community-app/src/test/java/com/nowcoder/community/ops backend/community-app/src/test/java/com/nowcoder/community/common/outbox --glob '*.java'
```

Expected: tests pass and the search has no match.

- [ ] **Step 4: Create a final clean-break contract script**

Create `deploy/tests/development_clean_break_contract.sh` with `set -euo pipefail`. It must fail if exact retired runtime/config symbols appear under production sources, Nacos, compose, or handbook:

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
retired='CurrentSchemaVersionFilter|schemaVersionOrCurrent|legacyCompatibleVersionFloor|RoomPersistedLegacyConsumer|RoomFanoutOwnerCoalescer|HttpRoomFanoutDispatcher|RoomFanoutTargetController|NoopRoomPresenceDirectory|owner-flush-interval|target-path|target-timeout|IM_ROOM_FANOUT_(MODE|TRANSPORT|OWNER_FLUSH_INTERVAL|TARGET_PATH|TARGET_TIMEOUT)|IM_ROOM_PRESENCE_ENABLED|worker-inbox-slot[^\n]*:0|events\.publisher|EVENTS_PUBLISHER|projection\.(search\.post|growth\.task|user\.reward\.comment)|GrowthTaskProgressActionApi|MDC_KEY_LEGACY_TRACE_ID'
if rg -n "${retired}" "${REPO_ROOT}/backend" --glob '**/src/main/**' --glob '!**/target/**' \
    || rg -n "${retired}" \
        "${REPO_ROOT}/backend/community-app/src/test/resources" \
        "${REPO_ROOT}/backend/community-im/im-realtime/src/test/resources" \
    || rg -n "${retired}" \
        "${REPO_ROOT}/deploy/nacos" \
        "${REPO_ROOT}/deploy/compose.runtime.services.single.yml" \
        "${REPO_ROOT}/deploy/compose.runtime.services.cluster.yml" \
        "${REPO_ROOT}/docs/handbook"; then
  echo "retired compatibility surface remains" >&2
  exit 1
fi

im_yaml=(
  "${REPO_ROOT}/backend/community-im/im-realtime/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-im/im-realtime/src/test/resources/application-test.yml"
  "${REPO_ROOT}/deploy/nacos/config/im-realtime.yaml"
)
retired_im_yaml='(?m)^  room-fanout:\r?\n(?:^    .*\r?\n)*?^    (mode|transport|owner-flush-interval|target-path|target-timeout):|^  room-presence:\r?\n(?:^    .*\r?\n)*?^    enabled:'
if rg -n -U "${retired_im_yaml}" "${im_yaml[@]}"; then
  echo "retired IM YAML switch remains" >&2
  exit 1
fi

rg -Fq 'projection.im.policy' "${REPO_ROOT}/deploy/nacos/config/community-kafka-policy.yaml"
rg -Fq 'projection.im.policy' "${REPO_ROOT}/docs/handbook"
for topic in content.events social.events user.events; do
  rg -Fq "${topic}" "${REPO_ROOT}/deploy/scripts/bootstrap-kafka-topics.sh"
  rg -Fq "${topic}" "${REPO_ROOT}/docs/handbook"
done
rg -Fq 'im.command.room-fanout-routed' "${REPO_ROOT}/deploy/nacos/config/im-realtime.yaml"
rg -Fq 'im.command.room-fanout-routed' "${REPO_ROOT}/deploy/scripts/bootstrap-kafka-topics.sh"
rg -Fq 'im.command.room-fanout-routed' "${REPO_ROOT}/docs/handbook"
rg -q 'schemaVersion.{0,12}1' "${REPO_ROOT}/docs/handbook/business-logic/im.md"
"${REPO_ROOT}/deploy/tests/topology_single_cluster.sh" >/dev/null
```

These assertions require `projection.im.policy`, all three owner topics, `im.command.room-fanout-routed`, explicit schema version `1` documentation, and the unique single/cluster inbox-slot topology.

Run `chmod +x deploy/tests/development_clean_break_contract.sh` before its first execution.

- [ ] **Step 5: Run deployment contracts and commit**

```bash
./deploy/tests/nacos_config_seed.sh
./deploy/tests/topology_single_cluster.sh
./deploy/tests/projection_version_baseline_contract.sh
./deploy/tests/kafka_topics_contract.sh
./deploy/tests/community_backbone_topics_contract.sh
./deploy/tests/development_clean_break_contract.sh
git add backend/community-app/src/test backend/community-app/src/main/resources backend/community-app/src/test/resources deploy
git commit -m "chore(config): remove compatibility rollout surfaces"
```

Expected: all scripts exit `0`.

### Task 4: Rewrite Handbook SSOT For The Clean Architecture

**Files:**
- Modify all documentation files listed above.

**Interfaces:**
- Handbook describes current behavior only; historical rationale remains in linked specs.

- [ ] **Step 1: Mark the old reliability spec as superseded**

Immediately below the title of `2026-07-07-bbs-reliability-p2-core-slices-design.md`, add:

```markdown
> **Superseded for the current implementation:** The Search/Growth secondary projection paths and publisher rollout switches in this historical design were retired by [Development Clean Break Design](2026-07-10-development-clean-break-design.md). Keep this file as decision history; do not use those sections as the current runtime contract.
```

- [ ] **Step 2: Rewrite IM contract, projection, and fanout sections**

Across `business-logic/im.md`, `core-logic/im-core-runtime.md`, `integration-contracts.md`, `reliability.md`, `data-and-storage.md`, and IM core class/workflow indexes, state exactly:

```text
- every IM wire payload writes numeric schemaVersion 1;
- only numeric 1 is accepted; missing/null/non-numeric/non-positive/future values fail;
- unknown v1 fields are ignored;
- projection delta/entry versions are explicit positive owner versions;
- snapshot watermark is required, boxed, non-negative, and may be 0;
- room fanout is Redis presence + shared owner consumer + Kafka worker inbox only;
- local connection/index state remains authoritative during Redis retry;
- worker slots are mandatory: single 0, cluster 0/1/2;
- target delivery is state-idempotent at-least-once, not cross-restart exactly-once.
```

Remove legacy/shadow/HTTP target and timestamp-derived/backfill descriptions. Update class tables from deleted owner coalescer/controller classes to the routed owner service, Kafka dispatcher, target service, and Redis presence directory actually implemented by Plan 2.

- [ ] **Step 3: Rewrite event backbone, Search, Growth, and User reward sections**

Across architecture/system design, async backbone, flows, integration, reliability, operations/testing, domain pages, core classes/workflows, and indexes, document one flow:

```text
owner transaction -> eventbus.<owner> -> owner outbox handler
-> <owner>.events -> consumer Kafka listener -> consumer ApplicationService
```

List `projection.im.policy` as the only retained internal projection outbox from this cleanup. Explain deterministic source-event idempotency and the source-topic `.dlq` policy. Remove local publisher rollback instructions, Search/Growth/User-reward secondary topics/classes, and Growth synchronous task action API. Keep Growth's Kafka backbone, task event log dedupe, and like-removal rollback.

- [ ] **Step 4: Preserve explicit non-compatibility and valid current behavior**

Ensure the handbook still says:

```text
- unified Market supports physical and virtual goods; only the old virtual-market surface is retired;
- Market buyer/requestId replay is business idempotency, including sold-out/concurrent retry;
- physical replay never reconstructs a missing address snapshot;
- Content provides no body-format migration path;
- root legacy service/entity/mapper/app packages and the old posts list route are retired;
- transport/business traceId fields remain valid while MDC uses trace.id/span.id only.
```

Delete any residual `GET /api/posts` feed-list route description while preserving post detail/write routes.

- [ ] **Step 5: Update indexes and run documentation contract**

Remove deleted classes from `core-logic-index.md` and `core-logic-coverage-audit.md`; add new IM policy application service/port/adapter and routed presence/fanout classes where appropriate. Make every core-class/workflow link point to an existing file/heading.

Run:

```bash
./deploy/tests/development_clean_break_contract.sh
rg -n 'projection\.(search\.post|growth\.task|user\.reward\.comment)|GrowthTaskProgressActionApi|RoomPersistedLegacyConsumer|RoomFanoutTargetController|schemaVersion <= 0|缺失.*schemaVersion.*按.*1|timestamp-derived' docs/handbook
```

Expected: contract passes and the search has no match.

- [ ] **Step 6: Commit handbook convergence**

```bash
git add docs/handbook docs/superpowers/specs/2026-07-07-bbs-reliability-p2-core-slices-design.md deploy/tests/development_clean_break_contract.sh
git commit -m "docs: align handbook with development clean break"
```

### Task 5: Run Final Repository Verification

**Files:**
- No planned production changes; fix only regressions caused by the four 2026-07-10 plans.

**Interfaces:**
- Verifies the four plans together satisfy the clean-break design and repository DDD guardrails.

- [ ] **Step 1: Run focused contract and trace modules**

```bash
(cd backend && mvn test -pl :im-common,:im-core,:im-realtime,:community-common-core,:community-common-observability,:community-gateway -am)
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run community-app architecture and full tests**

```bash
(cd backend && mvn test -pl :community-app -Dtest='*ArchTest')
(cd backend && mvn test -pl :community-app)
```

Expected: both builds succeed. The first command is mandatory after the listener-boundary guardrail change.

- [ ] **Step 3: Run frontend IM tests**

```bash
(cd frontend && npm test -- imRealtimeClient.test.js)
```

Expected: PASS; outbound/inbound schema version behavior is strict.

- [ ] **Step 4: Run all deployment contracts**

```bash
./deploy/tests/nacos_config_seed.sh
./deploy/tests/topology_single_cluster.sh
./deploy/tests/projection_version_baseline_contract.sh
./deploy/tests/kafka_topics_contract.sh
./deploy/tests/community_backbone_topics_contract.sh
./deploy/tests/development_clean_break_contract.sh
```

Expected: all commands exit `0`.

- [ ] **Step 5: Run the complete backend reactor**

```bash
(cd backend && mvn test)
```

Expected: BUILD SUCCESS. Do not claim completion if a failure is merely unrelated without first proving it predates these changes.

- [ ] **Step 6: Perform final source/config searches**

```bash
rg -n 'CurrentSchemaVersionFilter|schemaVersionOrCurrent|legacyCompatibleVersionFloor|RoomPersistedLegacyConsumer|RoomFanoutOwnerCoalescer|HttpRoomFanoutDispatcher|RoomFanoutTargetController|NoopRoomPresenceDirectory|events\.publisher|EVENTS_PUBLISHER|PostOutbox(Enqueuer|Handler)|CommentRewardOutbox|GrowthTaskProgressActionApi|TaskProgressOutbox|MDC_KEY_LEGACY_TRACE_ID' backend --glob '**/src/main/**' --glob '!**/target/**'
rg -n 'CurrentSchemaVersionFilter|schemaVersionOrCurrent|legacyCompatibleVersionFloor|RoomPersistedLegacyConsumer|RoomFanoutOwnerCoalescer|HttpRoomFanoutDispatcher|RoomFanoutTargetController|NoopRoomPresenceDirectory|events\.publisher|EVENTS_PUBLISHER|PostOutbox(Enqueuer|Handler)|CommentRewardOutbox|GrowthTaskProgressActionApi|TaskProgressOutbox|MDC_KEY_LEGACY_TRACE_ID' backend/community-app/src/test/resources backend/community-im/im-realtime/src/test/resources
rg -n 'CurrentSchemaVersionFilter|schemaVersionOrCurrent|legacyCompatibleVersionFloor|RoomPersistedLegacyConsumer|RoomFanoutOwnerCoalescer|HttpRoomFanoutDispatcher|RoomFanoutTargetController|NoopRoomPresenceDirectory|events\.publisher|EVENTS_PUBLISHER|PostOutbox(Enqueuer|Handler)|CommentRewardOutbox|GrowthTaskProgressActionApi|TaskProgressOutbox|MDC_KEY_LEGACY_TRACE_ID' deploy/nacos docs/handbook
rg -n 'projection\.(search\.post|growth\.task|user\.reward\.comment)' backend/community-app/src/main deploy/nacos docs/handbook --glob '!**/target/**'
```

Expected: no match. Historical specs/plans and retirement tests are intentionally outside this production/current-doc search.

- [ ] **Step 7: Record development-state rebuild requirements**

In the implementation handoff, state that the next local deployment must recreate MySQL/Kafka/Redis/outbox/projection state and rebuild the frontend bundle before mixing traffic. Do not run destructive volume deletion automatically; use the repository's normal environment rebuild command only with explicit operator authorization.

- [ ] **Step 8: Commit verification-only fixes if needed**

```bash
git add backend frontend deploy docs/handbook
git commit -m "test: verify development clean break"
```

Skip the commit when final verification required no changes.

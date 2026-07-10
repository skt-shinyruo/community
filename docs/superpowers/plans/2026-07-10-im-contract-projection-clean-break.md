# IM Contract And Projection Clean Break Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every IM wire payload explicitly declare schema version `1` and make every IM projection update use an explicit positive owner version.

**Architecture:** `im-common` remains the single wire-contract boundary and rejects invalid schema/projection versions in record constructors, with a schema-specific Jackson deserializer preventing scalar coercion. Owner services allocate monotonic versions; realtime consumers compare only those versions, while snapshot watermarks remain required non-negative values.

**Tech Stack:** Java 17, Jackson, Spring Boot, Spring Kafka, Spring WebFlux, MyBatis, MySQL/H2, JUnit 5, AssertJ, Vitest, Maven, npm.

## Global Constraints

- Execute this plan before the room-fanout, event-backbone, and final cleanup plans dated 2026-07-10.
- Every IM command, event, projection DTO, WebSocket frame, and `RoomFanoutCommand` writes numeric `"schemaVersion": 1`.
- Missing, `null`, non-numeric, `0`, negative, and future schema versions are rejected; no value is mapped to the current version.
- Unknown JSON fields remain ignored within schema version `1`.
- `RoomMemberChanged`, `UserMessagingPolicyChanged`, `UserBlockRelationChanged`, and their snapshot entries require explicit positive `version` values.
- Snapshot `snapshotHighWatermark` stays boxed `Long`, is required and non-negative, and accepts explicit `0` for an empty source.
- `occurredAtEpochMillis` remains observational data and never affects projection ordering.
- Development databases, outbox rows, Kafka data, and local projection state are rebuilt; this plan provides no legacy-state migration.
- Preserve `buyer + requestId` Market idempotency and all non-MDC transport fields named `traceId`; they are outside this plan.
- Do not introduce a v2 branch. A future v2 requires an explicit DTO or dispatch branch.

---

## File Structure

### Strict IM Schema Contract

- Modify: `backend/community-im/im-common/pom.xml`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImContractVersions.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImSchemaVersion.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImSchemaVersionDeserializer.java`
- Delete: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/CurrentSchemaVersionFilter.java`
- Modify: every Java record returned by `rg -l '@ImSchemaVersion' backend/community-im/im-common/src/main/java`
- Modify: `backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/JsonContractsTest.java`
- Modify: `backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/ImCommonContractRetirementTest.java`

### WebSocket And Browser Boundaries

- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImFrameCodec.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandlerContractVersionTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java`
- Modify: `frontend/src/im/imRealtimeClient.js`
- Modify: `frontend/src/im/imRealtimeClient.test.js`

### Explicit Projection Versions

- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/ProjectionVersions.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMemberChanged.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/UserMessagingPolicyChanged.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/UserBlockRelationChanged.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/RoomMembershipEntry.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserMessagingPolicyEntry.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserBlockRelationEntry.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/RoomMembershipSnapshot.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserMessagingPolicySnapshot.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserBlockRelationSnapshot.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/MembershipProjectionService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicyProjectionService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/MembershipSnapshotClient.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicySnapshotClient.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/projection/MembershipProjectionServiceTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/projection/PolicyProjectionServiceTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/projection/SnapshotClientContractVersionTest.java`

### Owner Version Production And Baselines

- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/infrastructure/persistence/MyBatisRoomMemberRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaRoomMemberChangePublisher.java`
- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/application/RoomApplicationServiceTest.java`
- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/kafka/KafkaRoomMemberChangePublisherTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicySnapshotApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/im/application/ImPolicySnapshotApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepositoryTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisBlockRepositoryTest.java`
- Modify: `deploy/mysql/community/020_schema_identity.sql`
- Modify: `deploy/mysql/community/050_schema_social.sql`
- Modify: `deploy/mysql/community/070_schema_im_core.sql`
- Modify: `deploy/mysql/community/090_seed_identity.sql`
- Create: `deploy/tests/projection_version_baseline_contract.sh`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `backend/community-im/im-core/src/test/resources/schema.sql`

---

### Task 1: Reject Non-V1 Schema Values In Every IM Record

**Files:**
- Modify/create/delete the strict IM schema contract files listed above.
- Update all `@ImSchemaVersion` records to call the strict validator from their canonical constructor.
- Test all versioned records through `JsonContractsTest` rather than sampling one record per category.

**Interfaces:**
- Produces `ImContractVersions.requireSupportedSchemaVersion(int schemaVersion): int`.
- Produces `ImSchemaVersionDeserializer extends JsonDeserializer<Integer>`.
- Preserves `@ImJsonContract` and its `@JsonIgnoreProperties(ignoreUnknown = true)` behavior.

- [ ] **Step 1: Replace compatibility expectations with failing strict-contract tests**

In `JsonContractsTest`, replace the legacy/missing and non-positive acceptance tests with a table-driven test over every class in the existing `shouldRejectUnsupportedFutureSchemaVersionForEveryVersionedRecord` list, including `RoomFanoutCommand`. For each generic valid JSON fixture assert:

```java
assertSchemaRejected(withoutField(validJson, "schemaVersion"), contractType);
assertSchemaRejected(withField(validJson, "schemaVersion", null), contractType);
assertSchemaRejected(withField(validJson, "schemaVersion", 0), contractType);
assertSchemaRejected(withField(validJson, "schemaVersion", -1), contractType);
assertSchemaRejected(withField(validJson, "schemaVersion", 2), contractType);
assertSchemaRejected(withField(validJson, "schemaVersion", "1"), contractType);
```

Also serialize each fixture and assert `objectMapper.readTree(json).get("schemaVersion").intValue() == 1`, then keep the existing unknown-field tests with explicit schema `1`.

Run:

```bash
(cd backend && mvn test -pl :im-common -Dtest=JsonContractsTest)
```

Expected: FAIL because missing/non-positive schema values are still normalized and schema `1` is omitted on write.

- [ ] **Step 2: Add the strict schema validator and deserializer**

Move `jackson-databind` in `im-common/pom.xml` out of test scope. Replace the compatibility method with:

```java
public static int requireSupportedSchemaVersion(int schemaVersion) {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
        throw new ImUnsupportedSchemaVersionException(schemaVersion, CURRENT_SCHEMA_VERSION);
    }
    return schemaVersion;
}
```

Make `@ImSchemaVersion` include only the strict deserializer:

```java
@JacksonAnnotationsInside
@JsonDeserialize(using = ImSchemaVersionDeserializer.class)
public @interface ImSchemaVersion {
}
```

Implement `ImSchemaVersionDeserializer.deserialize` so it accepts only `JsonToken.VALUE_NUMBER_INT`, obtains an exact `int`, and calls `requireSupportedSchemaVersion`. Override `getNullValue` to throw `new ImUnsupportedSchemaVersionException(0, CURRENT_SCHEMA_VERSION)`. Delete `CurrentSchemaVersionFilter`; do not add any `JsonInclude` replacement.

- [ ] **Step 3: Make every canonical record constructor strict**

Mechanically replace `schemaVersionOrCurrent` with `requireSupportedSchemaVersion` in every record returned by:

```bash
rg -l '@ImSchemaVersion' backend/community-im/im-common/src/main/java
```

Do not remove `@ImJsonContract`, and do not add a no-version JSON creator. Convenience Java constructors must continue to pass the appropriate constant (`KAFKA_COMMAND_SCHEMA_VERSION`, `KAFKA_EVENT_SCHEMA_VERSION`, `PROJECTION_SCHEMA_VERSION`, or `WS_FRAME_VERSION`) explicitly.

- [ ] **Step 4: Add a retirement guard and verify the common module**

Extend `ImCommonContractRetirementTest` with:

```java
assertClassRetired(cn("com.nowcoder.community.im.common.", "CurrentSchemaVersion", "Filter"));
assertThatThrownBy(() -> ImContractVersions.requireSupportedSchemaVersion(0))
        .isInstanceOf(ImUnsupportedSchemaVersionException.class);
```

Run:

```bash
(cd backend && mvn test -pl :im-common)
```

Expected: PASS; serialized records contain numeric schema `1`, invalid schema values fail, and unknown fields remain readable.

- [ ] **Step 5: Commit strict common contracts**

```bash
git add backend/community-im/im-common
git commit -m "refactor(im): require explicit schema version"
```

### Task 2: Enforce Schema Before WebSocket Dispatch And In The Browser Client

**Files:**
- Modify the WebSocket and frontend files listed above.

**Interfaces:**
- Produces `ImFrameCodec.requireSupportedSchemaVersion(JsonNode node): void`.
- Browser outbound `_send` always adds `schemaVersion: 1` after the command object is supplied.
- Browser emits `protocolError` and closes with code `1002` for an invalid inbound frame before any business event callback.

- [ ] **Step 1: Write failing server ordering tests**

Extend `ImWebSocketHandlerContractVersionTest` with parameterized missing, `null`, `0`, `-1`, string `"1"`, and `2` frames. Use frames with no `type`, an unauthenticated connection, and an unready projection, then assert the first result is always:

```java
assertThat(reject.path("reasonCode").asText()).isEqualTo("unsupported_schema_version");
verifyNoInteractions(projectionSyncCoordinator, commandIngressService);
```

Extend `ImRealtimeWebSocketIntegrationTest` with a missing-schema connect frame and assert no session is bound and the protocol reject is returned.

Run:

```bash
(cd backend && mvn test -pl :im-realtime -am -Dtest=ImWebSocketHandlerContractVersionTest,ImRealtimeWebSocketIntegrationTest)
```

Expected: FAIL because type, auth, or readiness is currently checked before the top-level schema.

- [ ] **Step 2: Validate top-level schema immediately after JSON parsing**

Implement `ImFrameCodec.requireSupportedSchemaVersion` with exact JSON token checks: `node.get("schemaVersion")` must be non-null, integral, convertible to `int`, and equal to `1`. Missing/null/non-integral values throw `ImUnsupportedSchemaVersionException(0, 1)`; other integers retain their actual value.

In `ImWebSocketHandler`, call it immediately after `readTree` and before reading `type`, checking connection state, or checking projection readiness. On failure call `rejectAndClose(..., "unsupported_schema_version", ...)` and return `Mono.empty()`. Keep canonical record-constructor validation as defense in depth.

- [ ] **Step 3: Write failing browser contract tests**

Extend `imRealtimeClient.test.js` to capture connect/private/room sends and assert all three equal `expect.objectContaining({ schemaVersion: 1 })`. For each invalid inbound value (`undefined`, `null`, `0`, `-1`, `2`, `'1'`) assert:

```javascript
expect(protocolErrors).toHaveLength(1)
expect(businessEvents).toHaveLength(0)
expect(ws.close).toHaveBeenCalledWith(1002, 'unsupported_schema_version')
```

Run:

```bash
(cd frontend && npm test -- imRealtimeClient.test.js)
```

Expected: FAIL because outbound frames omit schema and inbound frames are dispatched without validation.

- [ ] **Step 4: Implement browser schema handling**

Add `const IM_SCHEMA_VERSION = 1`. Serialize outbound frames as:

```javascript
this.ws.send(JSON.stringify({ ...(obj || {}), schemaVersion: IM_SCHEMA_VERSION }))
```

At the start of `onmessage`, require a parsed plain object whose `schemaVersion === IM_SCHEMA_VERSION`. Otherwise emit:

```javascript
this.emitter.emit('protocolError', { reasonCode: 'unsupported_schema_version' })
this.ws?.close?.(1002, 'unsupported_schema_version')
return
```

Only after this branch may the client mutate auth state or emit `connected`, `reject`, `sendRejected`, or the message type.

- [ ] **Step 5: Verify and commit WebSocket boundaries**

```bash
(cd backend && mvn test -pl :im-realtime -am -Dtest=ImWebSocketHandlerContractVersionTest,ImRealtimeWebSocketIntegrationTest)
(cd frontend && npm test -- imRealtimeClient.test.js)
git add backend/community-im/im-realtime frontend/src/im
git commit -m "refactor(im): enforce websocket schema version"
```

Expected: both Maven tests and Vitest pass.

### Task 3: Remove Timestamp-Derived Projection Ordering

**Files:**
- Modify the projection DTO, service, client, and tests listed above.

**Interfaces:**
- Produces `ProjectionVersions.requirePositive(Long version, String fieldName): long`.
- Produces `ProjectionVersions.requireNonNegative(Long watermark, String fieldName): long`.
- Produces `ProjectionVersions.snapshotEntryVersion(Long entryVersion, long snapshotHighWatermark): long` as `max(required entry version, watermark)`.
- `MembershipProjectionService.applyRoomMemberChanged(RoomMemberChanged): boolean` and policy apply methods retain their public signatures.

- [ ] **Step 1: Write failing DTO validation tests**

In `JsonContractsTest`, add missing/null/zero/negative `version` cases for all three delta types and all three entry types. Add missing/null/negative watermark cases for all snapshots, plus this valid empty snapshot:

```java
RoomMembershipSnapshot empty = objectMapper.readValue("""
        {"schemaVersion":1,"entries":[],"nextRoomId":null,"nextUserId":null,
         "hasMore":false,"snapshotHighWatermark":0}
        """, RoomMembershipSnapshot.class);
assertEquals(0L, empty.snapshotHighWatermark());
```

Run `(cd backend && mvn test -pl :im-common -Dtest=JsonContractsTest)`; expect FAIL because null versions and watermarks are accepted.

- [ ] **Step 2: Collapse `ProjectionVersions` to explicit helpers**

Delete `LOGICAL_BITS`, `MAX_EPOCH_MILLIS`, `fromEpochMillis`, `snapshotHighWatermarkFromEpochMillis`, `nextEventVersion`, and `resolve`. Retain `max`/`minPositiveOrZero` only if a non-legacy caller remains after `rg`; otherwise delete them. Implement:

```java
public static long requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0L) {
        throw new IllegalArgumentException(fieldName + " must be positive");
    }
    return value;
}

public static long requireNonNegative(Long value, String fieldName) {
    if (value == null || value < 0L) {
        throw new IllegalArgumentException(fieldName + " must be non-negative");
    }
    return value;
}

public static long snapshotEntryVersion(Long entryVersion, long watermark) {
    return Math.max(requirePositive(entryVersion, "version"), watermark);
}
```

- [ ] **Step 3: Enforce DTO invariants and remove legacy constructors**

In delta and entry canonical constructors call `requirePositive(version, "version")`. Remove the three delta overloads and three entry overloads that supply `null` version. In each snapshot constructor call `requireNonNegative(snapshotHighWatermark, "snapshotHighWatermark")`; remove overloads that omit the watermark. Keep `occurredAtEpochMillis` nullable only on snapshot entries and observational everywhere.

- [ ] **Step 4: Write failing projection-order tests**

Rewrite `MembershipProjectionServiceTest` and `PolicyProjectionServiceTest` with small explicit versions (`10`, `11`, `12`) and deliberately inverted timestamps. Assert version `11` wins even when its timestamp is earlier, version `10` is stale even when its timestamp is later, snapshot entry version is merged with watermark, and explicit watermark `0` replaces an empty initial snapshot.

Run:

```bash
(cd backend && mvn test -pl :im-realtime -am -Dtest=MembershipProjectionServiceTest,PolicyProjectionServiceTest)
```

Expected: FAIL until all `ProjectionVersions.resolve` calls are removed.

- [ ] **Step 5: Make realtime projections and snapshot clients explicit**

Use `event.version()` directly for deltas. Use `ProjectionVersions.snapshotEntryVersion(entry.version(), snapshot.snapshotHighWatermark())` for snapshot entries. `currentVersion(UserMessagingPolicyEntry)` returns its already-validated `version` directly.

In both snapshot clients, delete the entry/timestamp fallback loops. Return the first page's required watermark even when it is `0`; if no page is returned, fail with `IllegalStateException("projection snapshot returned no pages")`. Add a consistency check that every page uses the first page watermark so pagination cannot combine different snapshots.

Extend `SnapshotClientContractVersionTest` so missing schema, missing entry version, and missing watermark all fail the refresh; keep future-schema coverage.

- [ ] **Step 6: Verify and commit projection consumers**

```bash
(cd backend && mvn test -pl :im-common,:im-realtime -am -Dtest=JsonContractsTest,MembershipProjectionServiceTest,PolicyProjectionServiceTest,SnapshotClientContractVersionTest)
git add backend/community-im/im-common backend/community-im/im-realtime
git commit -m "refactor(im): require explicit projection versions"
```

Expected: selected tests pass and `rg 'fromEpochMillis|snapshotHighWatermarkFromEpochMillis|ProjectionVersions\.resolve' community-im` has no production match.

### Task 4: Start Owner Projection Counters At One

**Files:**
- Modify owner production, tests, and SQL baselines listed above.

**Interfaces:**
- Membership allocation remains transaction-scoped: lock counter row, compute `current + 1`, update, then persist fact/log.
- User policy, social block, and membership owner events expose positive versions; empty snapshot watermarks are `0`.

- [ ] **Step 1: Add failing owner-version tests**

In `RoomApplicationServiceTest`, reset `im_membership_version_counter.current_version` to `0`, create one membership fact, and assert its version and watermark are exactly `1`. Extend `KafkaRoomMemberChangePublisherTest` to assert version `0` throws before enqueue.

In `MyBatisUserRepositoryTest` and `MyBatisBlockRepositoryTest`, retain/strengthen exact first-version assertions after resetting their counters to `0`. Add assertions that snapshot mapping never creates an entry with version `0`.

Run:

```bash
(cd backend && mvn test -pl :im-core,:community-app -am -Dtest=RoomApplicationServiceTest,KafkaRoomMemberChangePublisherTest,MyBatisUserRepositoryTest,MyBatisBlockRepositoryTest,ImPolicySnapshotApplicationServiceTest)
```

Expected: the membership test fails with a timestamp-shifted value and any null-to-zero policy path fails DTO construction after Task 3.

- [ ] **Step 2: Remove membership version floor and validate publishers**

Delete `LEGACY_COMPATIBLE_LOGICAL_BITS` and `legacyCompatibleVersionFloor()` from `MyBatisRoomMemberRepository`. After `SELECT ... FOR UPDATE`, compute exactly:

```java
long next = Math.addExact(current == null ? 0L : current, 1L);
mapper.updateMembershipVersion(MEMBERSHIP_VERSION_COUNTER_ID, next);
return next;
```

Call `ProjectionVersions.requirePositive(version, "version")` before `KafkaRoomMemberChangePublisher` constructs either event. In `ImPolicyEventDispatchApplicationService`, read `version` with `requiredLongValue` so a missing value is an error rather than `null`; Task 3's event constructor enforces positivity.

- [ ] **Step 3: Make snapshots fail on impossible owner versions**

In `ImPolicySnapshotApplicationService`, require every non-empty `UserModerationStateView.version()` and `SocialBlockRelationView.version()` to be positive. Do not map a missing state to version `0`; `decidePrivateMessage` may handle missing users, but snapshot scan results with missing identity/version are invalid owner data and must throw.

- [ ] **Step 4: Replace timestamp-domain SQL initialization**

In `020_schema_identity.sql`, delete `@user_policy_seed_version` and its timestamp update. Initialize `user_policy_version_counter` from the maximum explicit row version, with `0` for an empty table. Keep the independent security-version logic unchanged.

In `090_seed_identity.sql`, insert demo users with explicit policy versions `1`, `2`, `3`, and advance `user_policy_version_counter` to `3` after the insert.

Use this exact counter advancement so the seed and retirement contract agree:

```sql
insert into user_policy_version_counter(id, current_version)
values (1, 3)
on duplicate key update current_version = greatest(current_version, values(current_version));
```

In `050_schema_social.sql`, delete `@social_block_seed_version` and timestamp backfill; initialize the counter from `max(version)` or `0`. In `070_schema_im_core.sql`, do the same for membership and remove every `* 4096`. Keep test schemas initialized to `0`.

- [ ] **Step 5: Add SQL retirement assertions**

Create `deploy/tests/projection_version_baseline_contract.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
identity="${REPO_ROOT}/deploy/mysql/community/020_schema_identity.sql"
social="${REPO_ROOT}/deploy/mysql/community/050_schema_social.sql"
membership="${REPO_ROOT}/deploy/mysql/community/070_schema_im_core.sql"
demo="${REPO_ROOT}/deploy/mysql/community/090_seed_identity.sql"

retired_projection_seeds='@(user_policy|social_block|im_membership)_seed_version'
if rg -n "${retired_projection_seeds}" "${identity}" "${social}" "${membership}"; then
  echo "timestamp-derived projection seed remains" >&2
  exit 1
fi
if rg -n '\*\s*4096' "${social}" "${membership}"; then
  echo "timestamp-domain social or membership version remains" >&2
  exit 1
fi

# Identity security_version intentionally retains its independent timestamp domain.
grep -F '@user_security_seed_version' "${identity}"
grep -F 'policy_version' "${demo}"
grep -F 'values (1, 3)' "${demo}"
```

Run:

```bash
chmod +x deploy/tests/projection_version_baseline_contract.sh
./deploy/tests/projection_version_baseline_contract.sh
```

Expected: PASS. The check deliberately permits the unrelated `user_security_seed_version` and its `* 4096` calculation required by the existing security-version logic.

- [ ] **Step 6: Verify and commit owner versions**

```bash
(cd backend && mvn test -pl :im-core,:community-app -am -Dtest=RoomApplicationServiceTest,KafkaRoomMemberChangePublisherTest,MyBatisUserRepositoryTest,MyBatisBlockRepositoryTest,ImPolicySnapshotApplicationServiceTest)
./deploy/tests/projection_version_baseline_contract.sh
git add backend/community-im/im-core backend/community-app/src/main/java/com/nowcoder/community/im backend/community-app/src/test deploy/mysql/community deploy/tests/projection_version_baseline_contract.sh
git commit -m "refactor(im): reset projection version domain"
```

Expected: first owner versions are `1`, all emitted projection versions are positive, and baseline SQL contains no timestamp-derived projection version.

### Task 5: Verify The IM Contract And Projection Slice

**Files:**
- No production changes expected; fix only failures caused by Tasks 1-4.

**Interfaces:**
- Confirms all modules agree on strict schema and explicit owner versions before later plans refactor fanout and event ingress.

- [ ] **Step 1: Run backend slice tests**

```bash
(cd backend && mvn test -pl :im-common,:im-core,:im-realtime,:community-app -am)
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run frontend tests and build**

```bash
(cd frontend && npm test -- imRealtimeClient.test.js)
(cd frontend && npm run build)
./deploy/tests/projection_version_baseline_contract.sh
```

Expected: all three commands exit `0`.

- [ ] **Step 3: Run compatibility-retirement searches**

```bash
rg -n 'CurrentSchemaVersionFilter|schemaVersionOrCurrent|fromEpochMillis|snapshotHighWatermarkFromEpochMillis|legacyCompatibleVersionFloor|ProjectionVersions\.resolve' backend frontend
rg -n '"schemaVersion"\s*:\s*(0|-1|2)' frontend/src backend/community-im --glob '!**/test/**' --glob '!**/target/**'
```

Expected: no production compatibility implementation matches; only intentional rejection fixtures may match under tests.

- [ ] **Step 4: Commit verification-only fixes if needed**

```bash
git add backend frontend
git commit -m "test(im): lock strict contract clean break"
```

Skip the commit when verification required no changes.

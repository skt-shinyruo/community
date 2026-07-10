# Routed Room Fanout And Presence Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the room-fanout rollout matrix with one reliable Redis-presence and Kafka owner/target path.

**Architecture:** A shared owner consumer resolves active workers from Redis and publishes one routed command to each worker's fixed Kafka inbox partition. Local connection state is authoritative and serialized per room; Redis stores a rebuildable single-key lease directory whose failures are retried without undoing correct local state.

**Tech Stack:** Java 17, Spring Boot, Spring Kafka, Spring Data Redis, Nacos Discovery, Reactor, Micrometer, JUnit 5, Mockito, AssertJ, Docker Compose YAML, Bash, Maven.

## Global Constraints

- Execute after `2026-07-10-im-contract-projection-clean-break.md` and before the event-backbone/final plans.
- Keep topic `im.command.room-fanout-routed` with exactly `64` partitions.
- Keep owner group `im-realtime-room-fanout-owner`, default owner concurrency `3`, and target group `im-realtime-room-fanout-target`.
- Keep presence TTL `PT30S`, heartbeat `PT10S`, and worker-directory cache TTL `PT0.5S`.
- Rename the Kafka acknowledgement bound to `im.room-fanout.publish-timeout`, default `PT1S`.
- Remove legacy, shadow, HTTP, and no-op fanout paths; there is no mode, transport, or fallback switch.
- A worker inbox slot is required, must be in `[0, 63]`, and has no `:0` runtime fallback.
- Single topology uses slot `0`; three-worker cluster uses distinct slots `0`, `1`, and `2`.
- Target delivery is state-only at-least-once. In-memory `sourceEventId` dedupe is process-local and must never suppress retry after a failed enqueue.
- Local `WsConnection` and `RoomLocalIndex` state is authoritative; Redis presence is derived and rebuildable.
- Membership duplicate/stale deliveries reconcile local connections from current projection state.
- Redis failure never rolls back correct local state and one room failure never blocks another room's reconciliation.
- Redis room presence uses one sorted-set key per room: member is worker ID and score is lease expiry epoch milliseconds.
- `im.event.room-persisted.dlq` has `12` partitions; every other IM realtime source DLQ matches its source partition count.

---

## File Structure

### Routed-Only Fanout Surface

- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomPersistedLegacyConsumer.java`
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/HttpRoomFanoutDispatcher.java`
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetController.java`
- Delete: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/HttpRoomFanoutDispatcherTest.java`
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutOwnerCoalescer.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutOwnerService.java`
- Rename: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutOwnerCoalescerTest.java` -> `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutOwnerServiceTest.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomPersistedOwnerConsumer.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/KafkaRoomFanoutDispatcher.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetConsumer.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutMetrics.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutProperties.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutConfiguration.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/KafkaRoomFanoutDispatcherTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutConfigurationTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutConsumerAnnotationTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetConsumerTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetServiceTest.java`

### Worker Routing And Configuration

- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RealtimeWorkerEndpoint.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RealtimeWorkerDirectory.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RealtimeWorkerDirectoryTest.java`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`
- Modify: `backend/community-im/im-realtime/src/test/resources/application-test.yml`
- Modify: `deploy/nacos/config/im-realtime.yaml`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/config/NacosImRealtimeBindingTest.java`

### Presence Consistency

- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/NoopRoomPresenceDirectory.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomPresenceConfiguration.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomPresenceProperties.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomPresenceDirectory.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RedisRoomPresenceDirectory.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomLocalIndex.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomLocalPresenceService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomPresenceHeartbeat.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/WsConnection.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/MembershipProjectionService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/EventConsumers.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/presence/RedisRoomPresenceDirectoryTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/presence/RoomLocalIndexMetricsTest.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/presence/RoomLocalPresenceServiceTest.java`
- Create: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/kafka/EventConsumersMembershipReconciliationTest.java`

### Security, Deployment, And Retirement

- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityConfig.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityIntegrationTest.java`
- Create: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutRetirementTest.java`
- Modify: `deploy/compose.runtime.services.single.yml`
- Modify: `deploy/compose.runtime.services.cluster.yml`
- Modify: `deploy/scripts/bootstrap-kafka-topics.sh`
- Modify: `deploy/tests/topology_single_cluster.sh`
- Create: `deploy/tests/kafka_topics_contract.sh`

---

### Task 1: Collapse Fanout To One Owner/Target Path

**Files:**
- Delete/create/modify the routed-only fanout files listed above.

**Interfaces:**
- Produces `RoomFanoutOwnerService.routeAndDispatch(RoomMessagePersistedEvent event): void`.
- Preserves `RoomFanoutDispatcher.dispatch(RoomFanoutCommand command): void` with `KafkaRoomFanoutDispatcher` as the only implementation.
- `RoomPersistedOwnerConsumer.onRoomPersisted` always calls the owner service.

- [ ] **Step 1: Write failing routed-only bean and retirement tests**

Rewrite `RoomFanoutConfigurationTest` and `RoomFanoutConsumerAnnotationTest` to assert the owner and target consumers have no `ConditionalOnExpression`, the target partition placeholder is exactly `${im.room-fanout.worker-inbox-slot}`, and the owner retains its shared group/concurrency. Add `RoomFanoutRetirementTest` assertions:

```java
assertRetired("com.nowcoder.community.im.realtime.fanout.RoomPersistedLegacyConsumer");
assertRetired("com.nowcoder.community.im.realtime.fanout.HttpRoomFanoutDispatcher");
assertRetired("com.nowcoder.community.im.realtime.fanout.RoomFanoutTargetController");
assertRetired("com.nowcoder.community.im.realtime.fanout.RoomFanoutOwnerCoalescer");
assertRetired("com.nowcoder.community.im.realtime.presence.NoopRoomPresenceDirectory");
```

Run:

```bash
(cd backend && mvn test -pl :im-realtime -Dtest=RoomFanoutConfigurationTest,RoomFanoutConsumerAnnotationTest,RoomFanoutRetirementTest)
```

Expected: FAIL because the old classes and conditions still exist.

- [ ] **Step 2: Replace the owner coalescer with a synchronous owner service**

Delete the ticker, pending maps, queues, shadow branch, and retry coalescing. `routeAndDispatch` validates the event, calls `routingService.routesFor(roomId, seq)`, attempts every route, records the first exception, and throws after all targets were attempted:

```java
RuntimeException firstFailure = null;
for (RoomFanoutRoute route : routes) {
    try {
        dispatcher.dispatch(commandFor(event, route));
        metrics.commandSent();
    } catch (RuntimeException failure) {
        metrics.routeFailed();
        if (firstFailure == null) firstFailure = failure;
    }
}
if (firstFailure != null) {
    throw new IllegalStateException(
            "room fanout routed dispatch failed: roomId=" + event.roomId() + ", seq=" + event.seq(),
            firstFailure);
}
```

An empty active-worker set is a successful no-op with `emptyTargetSet` metric. Route discovery failure and any publish failure escape the owner listener so `KafkaConfig` retries the original persisted event.

- [ ] **Step 3: Make owner, Kafka dispatcher, and target consumer unconditional**

Remove all mode/transport conditions and `@Primary`. `RoomPersistedOwnerConsumer` depends on `RoomFanoutOwnerService`, increments `ownerEventConsumed`, and calls `routeAndDispatch`. `RoomFanoutTargetConsumer` remains pinned to its one configured partition and treats only `ACCEPTED`/`DUPLICATE` as success.

Remove legacy/shadow counters from `RoomFanoutMetrics`; retain owner consumed, routes planned, command sent, empty target set, route failed, target accepted/duplicate/rejected metrics already used by the routed path.

- [ ] **Step 4: Replace owner tests and preserve partial-progress behavior**

Rename the owner test and remove flush/shadow cases. Add direct tests for: no active worker; two successful workers; first worker fails but second is still attempted; directory failure propagates; stable source event ID and original created time are copied into every command.

Run:

```bash
(cd backend && mvn test -pl :im-realtime -Dtest=RoomFanoutOwnerServiceTest,RoomFanoutConsumerAnnotationTest,RoomFanoutConfigurationTest)
```

Expected: PASS.

- [ ] **Step 5: Commit routed-only owner surface**

```bash
git add backend/community-im/im-realtime/src/main backend/community-im/im-realtime/src/test
git commit -m "refactor(im): make room fanout routed only"
```

### Task 2: Make Worker Inbox Routing Required And Kafka-Specific

**Files:**
- Modify worker routing, properties, configuration, and tests listed above.

**Interfaces:**
- `RealtimeWorkerEndpoint` becomes `record RealtimeWorkerEndpoint(String workerId, int roomFanoutInboxSlot)`.
- `RoomFanoutProperties.normalizedWorkerInboxSlot(): int` throws for missing/out-of-range values.
- `RoomFanoutProperties.normalizedPublishTimeout(): Duration` defaults to `PT1S`.

- [ ] **Step 1: Write failing property and directory tests**

Rewrite `RealtimeWorkerDirectoryTest` so host, port, protocol, and URI are irrelevant. Each discovery instance must expose non-blank worker ID metadata and a parseable slot in `[0, partitions)`. Assert missing worker ID, missing slot, malformed slot, out-of-range slot, duplicate worker ID, and duplicate slot each throw `IllegalStateException` during `find`.

In `RoomFanoutConfigurationTest`, bind `RoomFanoutProperties` with no slot and assert startup failure; bind slot `0` with `64` partitions and assert success.

Run:

```bash
(cd backend && mvn test -pl :im-realtime -Dtest=RealtimeWorkerDirectoryTest,RoomFanoutConfigurationTest,KafkaRoomFanoutDispatcherTest)
```

Expected: FAIL because slot `0` is implicit and directory parsing skips malformed endpoints.

- [ ] **Step 2: Remove rollout/HTTP properties**

Delete `mode`, `transport`, `ownerFlushInterval`, `targetPath`, `targetTimeout`, their getters/setters, and all normalization predicates. Add `Duration publishTimeout = Duration.ofSeconds(1)`. Change `workerInboxSlot` from primitive `int` to boxed `Integer` so absence is distinguishable, and implement:

```java
public int normalizedWorkerInboxSlot() {
    int partitions = normalizedRoutedCommandPartitions();
    if (workerInboxSlot == null || workerInboxSlot < 0 || workerInboxSlot >= partitions) {
        throw new IllegalStateException(
                "im.room-fanout.worker-inbox-slot is required and must be between 0 and " + (partitions - 1));
    }
    return workerInboxSlot;
}
```

`RoomFanoutConfiguration` keeps only a startup validator that invokes this method. Presence wiring is mandatory in Task 4, so delete the old routed/no-op guard.

- [ ] **Step 3: Reduce discovery endpoints and fail closed**

Replace URI construction with strict metadata parsing. `endpointFrom(ServiceInstance)` throws descriptive `IllegalStateException` for every malformed `im-realtime-worker` instance rather than returning `Optional.empty()`. Build `RealtimeWorkerEndpoint(workerId, slot)` and check worker/slot uniqueness before returning an immutable snapshot.

Update `KafkaRoomFanoutDispatcher` to use `normalizedPublishTimeout()` and the endpoint's primitive slot. Keep broker acknowledgement waiting and interrupt restoration.

- [ ] **Step 4: Remove all runtime slot fallbacks in Java/YAML**

Use this exact target annotation:

```java
partitions = "${im.room-fanout.worker-inbox-slot}"
```

In service and Nacos YAML use `${IM_ROOM_FANOUT_WORKER_INBOX_SLOT}` without `:0`, and publish discovery metadata from `${im.room-fanout.worker-inbox-slot}` without a fallback. Keep explicit slot `0` in `application-test.yml`, which is test configuration rather than a runtime fallback. Rename `target-timeout` to `publish-timeout` everywhere.

- [ ] **Step 5: Verify and commit worker routing**

```bash
(cd backend && mvn test -pl :im-realtime -Dtest=RealtimeWorkerDirectoryTest,RoomFanoutConfigurationTest,KafkaRoomFanoutDispatcherTest,NacosImRealtimeBindingTest)
rg -n 'worker-inbox-slot:.*:0|worker-inbox-slot:0|target-timeout|target-path|room-fanout\.mode|room-fanout\.transport' backend/community-im --glob '**/src/main/**' --glob '!**/target/**'
rg -n 'worker-inbox-slot:.*:0|worker-inbox-slot:0|target-timeout|target-path|room-fanout\.mode|room-fanout\.transport' deploy/nacos/config/im-realtime.yaml
git add backend/community-im/im-realtime deploy/nacos/config/im-realtime.yaml
git commit -m "refactor(im): require unique fanout inbox slots"
```

Expected: tests pass and the search has no match.

### Task 3: Make Target Dedupe Retry-Safe

**Files:**
- Modify `RoomFanoutTargetService.java` and `RoomFanoutTargetServiceTest.java`.

**Interfaces:**
- Preserves `RoomFanoutTargetService.apply(RoomFanoutCommand): RoomFanoutTargetResult`.
- Process-local duplicate suppression happens only after, or is rolled back when, `RoomFanoutCoalescer.markRoomUpdated` fails.

- [ ] **Step 1: Add the failing enqueue-failure test**

Configure `roomFanoutCoalescer.markRoomUpdated` to throw once, call `apply` twice with the same command, and assert the first call throws while the second returns `ACCEPTED` and invokes enqueue twice. Retain the existing successful duplicate test, which must invoke enqueue once.

Run `(cd backend && mvn test -pl :im-realtime -Dtest=RoomFanoutTargetServiceTest)`; expect FAIL because the source ID remains reserved after the first exception.

- [ ] **Step 2: Roll back an unsuccessful reservation**

Reserve with `putIfAbsent`, perform `markRoomUpdated` in a `try`, and on failure call `acceptedSourceEventIds.remove(sourceEventId, Boolean.TRUE)` before rethrowing. Add to the eviction-order queue only after successful enqueue. Document in the class that the cache suppresses only process-local duplicates and that room/seq max coalescing provides state idempotency across restart.

- [ ] **Step 3: Verify and commit target semantics**

```bash
(cd backend && mvn test -pl :im-realtime -Dtest=RoomFanoutTargetServiceTest,RoomFanoutTargetConsumerTest)
git add backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetService.java backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout
git commit -m "fix(im): allow fanout target enqueue retry"
```

Expected: PASS.

### Task 4: Replace Presence With A Single-Key Lease Directory

**Files:**
- Delete/modify the presence files and tests listed above.

**Interfaces:**
- Preserves `RoomPresenceDirectory.activate(UUID, String)`, `deactivate(UUID, String)`, and `activeWorkerIds(UUID)`.
- `RedisRoomPresenceDirectory` receives a package-private `Clock` constructor for deterministic tests.
- `RoomPresenceConfiguration` always creates the Redis directory from `StringRedisTemplate`; no missing-bean fallback exists.

- [ ] **Step 1: Write failing sorted-set tests**

Rewrite `RedisRoomPresenceDirectoryTest` with mocked `ZSetOperations<String,String>` and a fixed clock. Assert activate executes exactly one `add(key, workerId, now + ttlMillis)`, deactivate executes exactly one `remove(key, workerId)`, and read first calls `removeRangeByScore(key, -INF, now)` then `rangeByScore(key, now, +INF)`. Verify no set/value/delete operation is used.

Run `(cd backend && mvn test -pl :im-realtime -Dtest=RedisRoomPresenceDirectoryTest)`; expect FAIL against the current multi-key implementation.

- [ ] **Step 2: Implement the one-key lease**

Use key `${prefix}room:${roomId}:workers`. Activation upserts the worker with expiry score; deactivation removes it; read removes expired scores and returns normalized active members from the same sorted set. Use `Math.addExact(clock.millis(), ttl.toMillis())`, and propagate Redis errors to the local reconciliation service.

Delete `RoomPresenceProperties.enabled`, `NoopRoomPresenceDirectory`, all conditional annotations, and the fallback bean. A missing `StringRedisTemplate` now fails wiring; Redis health/readiness remains the deployment connectivity check.

- [ ] **Step 3: Add the retirement/configuration checks**

Update `RoomFanoutConfigurationTest` to assert one Redis-backed `RoomPresenceDirectory` and no no-op bean. Update `NacosImRealtimeBindingTest` to assert TTL/heartbeat/prefix and absence of `im.room-presence.enabled`.

Run:

```bash
(cd backend && mvn test -pl :im-realtime -Dtest=RedisRoomPresenceDirectoryTest,RoomFanoutConfigurationTest,NacosImRealtimeBindingTest,RoomFanoutRetirementTest)
```

Expected: PASS.

- [ ] **Step 4: Commit atomic Redis presence**

```bash
git add backend/community-im/im-realtime/src/main backend/community-im/im-realtime/src/test deploy/nacos/config/im-realtime.yaml
git commit -m "refactor(im): use atomic room presence leases"
```

### Task 5: Serialize Local Membership And Retry Presence Per Room

**Files:**
- Modify local presence, membership, consumer, handler, heartbeat, and related tests listed above.

**Interfaces:**
- Produces `RoomLocalPresenceService.joinLocalRoom(UUID roomId, WsConnection connection): void`.
- Produces `RoomLocalPresenceService.leaveLocalRoom(UUID roomId, WsConnection connection): void`.
- Produces `RoomLocalPresenceService.reconcileLocalMembership(UUID roomId, WsConnection connection, boolean expectedMember): void`.
- Produces `RoomLocalPresenceService.refreshPresence(): void`.
- Produces `RoomLocalIndex.hasConnections(UUID roomId): boolean`.

- [ ] **Step 1: Write failing local-state and retry tests**

Rewrite `RoomLocalPresenceServiceTest` around real `WsConnection` instances and a fail-once directory. Cover: join updates `joinedRooms` and index even when Redis activate fails; leave updates both even when deactivate fails; heartbeat retries pending activation and deactivation; one room's failure does not block another; heartbeat re-reads local occupancy so a final disconnect cannot be followed by ghost activation.

Add a two-thread barrier test proving concurrent final leave/new join for one room ends with connection/index agreement and the Redis operation matching final occupancy.

Run `(cd backend && mvn test -pl :im-realtime -Dtest=RoomLocalPresenceServiceTest,RoomLocalIndexMetricsTest)`; expect compilation failure until the new APIs exist.

- [ ] **Step 2: Make local index transitions atomic**

Implement `RoomLocalIndex.add/remove` with `ConcurrentHashMap.compute` and local `AtomicBoolean` result flags so add cannot target a set concurrently removed from the map. Preserve first/last transition return values and metric updates. Add `hasConnections(roomId)` for reconciliation.

- [ ] **Step 3: Move connection/index ownership into `RoomLocalPresenceService`**

Use a fixed array of lock objects (for example 256 stripes indexed by room UUID hash) to serialize room transitions without leaking one lock per historical room. Inside the lock, join calls `connection.joinRoom` then `roomLocalIndex.add`; leave calls `connection.leaveRoom` then `roomLocalIndex.remove`. Derive desired activity from `roomLocalIndex.hasConnections`, update the local active-room set, and then attempt Redis.

Catch Redis exceptions inside the service, add the room to `pendingRoomIds`, and retain the correct local state. On success remove the pending marker. `refreshPresence` iterates a snapshot of `activeRoomIds union pendingRoomIds`, locks and re-reads each room, and handles each failure independently.

- [ ] **Step 4: Write failing duplicate/stale consumer tests**

In `EventConsumersMembershipReconciliationTest`, mock `MembershipProjectionService.applyRoomMemberChanged` to return `false` while `isMember(room,user)` returns both `true` and `false` in separate tests. Assert every existing user connection is still passed to `reconcileLocalMembership(room, connection, currentState)`. Add a real-service case where the first Redis call fails, duplicate delivery repairs it, and local `joinedRooms` never regresses.

Run `(cd backend && mvn test -pl :im-realtime -Dtest=EventConsumersMembershipReconciliationTest)`; expect FAIL because `EventConsumers` currently returns on duplicate/stale delivery.

- [ ] **Step 5: Reconcile every membership delivery from current state**

Change `EventConsumers.onRoomMemberChanged` to validate IDs, call `applyRoomMemberChanged`, then always compute:

```java
boolean expectedMember = membershipProjectionService.isMember(event.roomId(), event.userId());
connectionRegistry.forEachConnectionByUserId(
        event.userId(),
        connection -> roomLocalPresenceService.reconcileLocalMembership(
                event.roomId(), connection, expectedMember));
```

Do not branch on the event's action after projection application. Update `MembershipProjectionService.bindExistingRooms` to call `joinLocalRoom`. Update WebSocket cleanup to call `leaveLocalRoom` for each joined room and isolate each room failure so connection completion still occurs. Make heartbeat unconditional and call `refreshPresence`.

- [ ] **Step 6: Verify and commit local reconciliation**

```bash
(cd backend && mvn test -pl :im-realtime -Dtest=RoomLocalPresenceServiceTest,RoomLocalIndexMetricsTest,EventConsumersMembershipReconciliationTest,ImRealtimeWebSocketIntegrationTest)
git add backend/community-im/im-realtime
git commit -m "fix(im): reconcile local room presence reliably"
```

Expected: PASS; fail-once Redis errors do not corrupt local membership and both activation/deactivation converge.

### Task 6: Provision Slots, DLQs, And Retire The HTTP Endpoint

**Files:**
- Modify security, compose, topic bootstrap, and deployment tests listed above.

**Interfaces:**
- Single compose exports `IM_ROOM_FANOUT_WORKER_INBOX_SLOT=0`.
- Cluster workers export `0`, `1`, `2` respectively.
- Topic bootstrap declares every source/DLQ pair with identical partition counts.

- [ ] **Step 1: Write failing topology and topic contract assertions**

Extend `topology_single_cluster.sh` to extract the rendered `im-realtime` environments, require slot `0` for single and slots `0/1/2` for cluster, and fail if the three cluster values are not unique.

Create `kafka_topics_contract.sh` to parse `bootstrap-kafka-topics.sh` and assert these pairs:

```text
im.event.private-persisted=12
im.event.room-persisted=12
im.event.private-committed=12
im.event.room-committed=12
im.event.private-rejected=12
im.event.room-rejected=12
im.event.room-member-changed=3
im.event.user-messaging-policy-changed=12
im.event.user-block-relation-changed=12
im.command.room-fanout-routed=64
```

For every line, `<topic>.dlq` must exist with the same count. Run both scripts; expect FAIL because slots and event DLQs are missing.

Use this complete script body:

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
BOOTSTRAP="${REPO_ROOT}/deploy/scripts/bootstrap-kafka-topics.sh"

partition_count() {
  local wanted="$1"
  awk -v wanted="${wanted}" '
    {
      topic = ""
      partitions = ""
      for (i = 1; i <= NF; i++) {
        if ($i == "--topic") topic = $(i + 1)
        if ($i == "--partitions") partitions = $(i + 1)
      }
      if (topic == wanted) {
        count++
        value = partitions
      }
    }
    END {
      if (count != 1 || value == "") exit 1
      print value
    }
  ' "${BOOTSTRAP}"
}

pairs=(
  im.event.private-persisted=12
  im.event.room-persisted=12
  im.event.private-committed=12
  im.event.room-committed=12
  im.event.private-rejected=12
  im.event.room-rejected=12
  im.event.room-member-changed=3
  im.event.user-messaging-policy-changed=12
  im.event.user-block-relation-changed=12
  im.command.room-fanout-routed=64
)

for pair in "${pairs[@]}"; do
  topic="${pair%%=*}"
  expected="${pair##*=}"
  test "$(partition_count "${topic}")" = "${expected}"
  test "$(partition_count "${topic}.dlq")" = "${expected}"
done
```

After creating the script, run `chmod +x deploy/tests/kafka_topics_contract.sh` before its first execution.

- [ ] **Step 2: Configure unique slots and all realtime DLQs**

Add the explicit slot environment variables to both compose topologies. Add all source event DLQs above to `bootstrap-kafka-topics.sh`; in particular `im.event.room-persisted.dlq` is `12`, while routed command/DLQ remain `64`.

- [ ] **Step 3: Remove the internal HTTP authorization surface**

Delete the `/internal/im/realtime/fanout/**` matcher from `ImRealtimeSecurityConfig`. Replace its integration tests with a retirement assertion that POST `/internal/im/realtime/fanout/room` returns `404` for anonymous and scoped callers, proving no controller mapping remains.

- [ ] **Step 4: Run deployment and security tests**

```bash
./deploy/tests/topology_single_cluster.sh
./deploy/tests/kafka_topics_contract.sh
(cd backend && mvn test -pl :im-realtime -Dtest=ImRealtimeSecurityIntegrationTest,NacosImRealtimeBindingTest,RoomFanoutRetirementTest)
```

Expected: all commands pass.

- [ ] **Step 5: Commit deployment contracts**

```bash
git add deploy backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/security
git commit -m "chore(im): provision routed fanout topology"
```

### Task 7: Verify The Routed Fanout Slice

**Files:**
- No production changes expected; fix only regressions caused by Tasks 1-6.

**Interfaces:**
- Confirms there is exactly one fanout topology and it converges under retry.

- [ ] **Step 1: Run the full IM realtime test suite**

```bash
(cd backend && mvn test -pl :im-realtime -am)
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run deployment contracts**

```bash
./deploy/tests/topology_single_cluster.sh
./deploy/tests/kafka_topics_contract.sh
```

Expected: both exit `0`.

- [ ] **Step 3: Search for retired fanout surfaces**

```bash
rg -n -U 'RoomPersistedLegacyConsumer|RoomFanoutOwnerCoalescer|HttpRoomFanoutDispatcher|RoomFanoutTargetController|NoopRoomPresenceDirectory|owner-flush-interval|target-path|target-timeout|room-fanout:\s*\n\s*mode|IM_ROOM_FANOUT_MODE|IM_ROOM_FANOUT_TRANSPORT|IM_ROOM_PRESENCE_ENABLED' backend --glob '**/src/main/**' --glob '!**/target/**'
rg -n -U 'RoomPersistedLegacyConsumer|RoomFanoutOwnerCoalescer|HttpRoomFanoutDispatcher|RoomFanoutTargetController|NoopRoomPresenceDirectory|owner-flush-interval|target-path|target-timeout|room-fanout:\s*\n\s*mode|IM_ROOM_FANOUT_MODE|IM_ROOM_FANOUT_TRANSPORT|IM_ROOM_PRESENCE_ENABLED' deploy --glob '!**/target/**'
rg -n 'worker-inbox-slot[^\n]*:0|worker-inbox-slot:0' backend/community-im --glob '**/src/main/**' --glob '!**/target/**'
rg -n 'worker-inbox-slot[^\n]*:0|worker-inbox-slot:0' deploy/nacos --glob '!**/target/**'
```

Expected: no production/config match; retirement tests may contain split class-name strings only.

- [ ] **Step 4: Commit verification-only fixes if needed**

```bash
git add backend/community-im deploy
git commit -m "test(im): lock routed fanout reliability"
```

Skip the commit when verification required no changes.

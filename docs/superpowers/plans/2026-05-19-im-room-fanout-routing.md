# IM Room Fanout Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop room persisted fanout cost from growing linearly with realtime worker count while preserving websocket-local delivery.

**Architecture:** Introduce a room fanout owner stage that consumes room persisted events once, resolves active target workers from distributed room presence, and routes one fanout command per active worker. Target workers keep the existing local `RoomFanoutCoalescer -> RoomUpdateCoalescer -> websocket` path.

**Tech Stack:** Spring Boot WebFlux, Spring Kafka, Redis/StringRedisTemplate for distributed presence, Nacos discovery metadata, Maven/JUnit/AssertJ.

---

## Phase 1: Routing Primitives And Local Presence Boundary

**Status:** Implemented in this change.

**Files:**
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutRoute.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutPlanner.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutRoutingService.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomPresenceDirectory.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/NoopRoomPresenceDirectory.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomLocalPresenceService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomLocalIndex.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/EventConsumers.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/MembershipProjectionService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutRoutingServiceTest.java`
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/presence/RoomLocalPresenceServiceTest.java`

**Verification:**

```bash
cd backend
mvn -pl community-im/im-realtime -am test -Dtest='RoomFanoutRoutingServiceTest,RoomLocalPresenceServiceTest'
```

Expected: 6 tests pass.

## Phase 2: Redis Room Presence Directory

**Status:** Implemented in this change.

**Goal:** Replace the no-op distributed presence implementation with a Redis-backed directory.

**Files:**
- Modify: `backend/community-im/im-realtime/pom.xml` to add `spring-boot-starter-data-redis`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RedisRoomPresenceDirectory.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomPresenceProperties.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomPresenceConfiguration.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/RoomPresenceHeartbeat.java`.
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`.
- Modify: `deploy/nacos/config/im-realtime.yaml`.
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/presence/RedisRoomPresenceDirectoryTest.java`.

**Acceptance:**
- `activate(roomId, workerId)` adds worker id to `im:room:{roomId}:workers` and sets `im:room:{roomId}:worker:{workerId}` with TTL.
- `activeWorkerIds(roomId)` returns only workers whose liveness key still exists.
- `deactivate(roomId, workerId)` removes the set member and liveness key.
- Redis failure is observable and routed mode can be kept disabled.

## Phase 3: Routed Fanout Target Transport

**Status:** Implemented in this change with HTTP internal transport. Fixed-shard Kafka worker inbox remains an optional transport replacement.

**Goal:** Allow room fanout owners to send target-worker fanout commands without every worker consuming the original room event.

**Files:**
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutCommand.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetController.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetService.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetResult.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutDispatcher.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/HttpRoomFanoutDispatcher.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RealtimeWorkerDirectory.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RealtimeWorkerEndpoint.java`.
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityConfig.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/ImRealtimeReactiveJwtConfiguration.java`.
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutTargetServiceTest.java`.
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RealtimeWorkerDirectoryTest.java`.
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityIntegrationTest.java`.

**Acceptance:**
- Target worker rejects commands for another `targetWorkerId`.
- Target worker calls only local `RoomFanoutCoalescer.markRoomUpdated`.
- Duplicate commands only update max seq and do not duplicate client-visible history.

## Phase 4: Room Owner Consumer Mode

**Status:** Implemented in this change. Runtime default remains `legacy`; `shadow` and `routed` are ready for deployment rollout.

**Goal:** Switch persisted room events from per-worker consumer groups to one owner group.

**Files:**
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutProperties.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutConfiguration.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutOwnerCoalescer.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomPersistedLegacyConsumer.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomPersistedOwnerConsumer.java`.
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutMetrics.java`.
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/EventConsumers.java` to move room persisted fanout into mode-specific consumers.
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutOwnerCoalescerTest.java`.
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/fanout/RoomFanoutConsumerAnnotationTest.java`.
- Test: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/config/NacosImRealtimeBindingTest.java`.

**Acceptance:**
- In `shadow`, legacy fanout still delivers while owner mode computes route sets only.
- In `routed`, each room persisted event is consumed once by the owner group and routed to active target workers.
- Rollback to `legacy` restores the current per-worker behavior.

## Phase 5: Load And Rebalance Validation

**Status:** Partially covered by focused unit/config tests in this change; full multi-process load validation remains a deployment test.

**Goal:** Prove bounded cost and failure behavior under worker count changes.

**Tests:**
- Start N realtime workers with only two workers holding room connections; route count stays 2 as N grows.
- Kill a worker; stale presence disappears after TTL and owner stops routing to it.
- Rebalance room owner consumers; duplicate route commands remain harmless.
- Add a high-rate room stream; room owner coalescing emits the latest seq per flush window.

**Acceptance:**
- Kafka room persisted consumption is bounded by owner partitions/concurrency.
- Routed commands scale with active target workers per room, not total realtime workers.
- No client protocol change is required.

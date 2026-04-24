# IM Realtime Hot Path Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild IM around a single realtime hot-path owner by moving authentication, session/ticket handling, membership/policy reads, and websocket command acceptance into `im-realtime`, while replacing synchronous downstream HTTP checks with snapshot + event driven projections.

**Architecture:** The implementation hard-cuts to a new IM contract surface in `im-common`, adds projection exports in `im-core` and `community-app`, boots `im-realtime` from room-membership and policy snapshots before it accepts traffic, and replaces websocket `auth` with `POST /api/im/sessions` + `connect(ticket)`. Gateway becomes a transparent worker-path proxy; old governance/bootstrap RPC clients and legacy websocket protocol code are deleted instead of feature-flagged.

**Tech Stack:** Spring Boot, Spring WebFlux/Web MVC, Spring Security OAuth2 Resource Server, Spring Kafka, JDBC/MyBatis, common-outbox, Jackson, JUnit 5, Mockito, Maven

---

## File Map

### New production files

- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/session/OpenImSessionRequest.java`
  Responsibility: request contract for `POST /api/im/sessions`.
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/session/OpenImSessionResponse.java`
  Responsibility: response contract carrying `sessionId`, `ticket`, `workerId`, `wsUrl`, and expiry.
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/ConnectFrame.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/SendPrivateTextFrame.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/SendRoomTextFrame.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/AckFrame.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/RejectFrame.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/CommittedFrame.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/PrivateMessageFrame.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/RoomMessageFrame.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/PingFrame.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/PongFrame.java`
  Responsibility: explicit websocket transport contracts replacing hand-built JSON strings.
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/RoomMembershipEntry.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/RoomMembershipSnapshot.java`
  Responsibility: paged room-membership snapshot contracts for realtime bootstrap.
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserMessagingPolicyEntry.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserMessagingPolicySnapshot.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserBlockRelationEntry.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserBlockRelationSnapshot.java`
  Responsibility: paged user-policy and block-relation snapshot contracts; the spec’s single policy projection is split into two page-friendly contracts because user rows and block pairs do not share a natural cursor.
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMemberChanged.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/UserMessagingPolicyChanged.java`
- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/UserBlockRelationChanged.java`
  Responsibility: projection change events consumed by `im-realtime`.
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/InternalRealtimeProjectionController.java`
  Responsibility: internal snapshot API for room-membership bootstrap.
- `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyChangePublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandler.java`
  Responsibility: export IM policy snapshots and durable change events from `community-app`.
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionApiController.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionService.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionProperties.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/SessionTicketCodec.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/RendezvousWorkerSelector.java`
  Responsibility: validate bearer tokens once, choose a worker, and mint short-lived session tickets.
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/MembershipSnapshotClient.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicySnapshotClient.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/MembershipProjectionService.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicyProjectionService.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicyDecision.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/ProjectionSyncCoordinator.java`
  Responsibility: bootstrap and own the local read model used by the hot path.
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImFrameCodec.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/service/MessageCommandIngressService.java`
  Responsibility: decode/encode websocket frames and centralize accepted/rejected command submission.
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/WorkerPathResolver.java`
  Responsibility: resolve `/ws/im/workers/{workerId}` without inspecting websocket frames.

### New test files

- `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/controller/InternalRealtimeProjectionControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicySnapshotControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandlerTest.java`
- `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/ImSessionApiIntegrationTest.java`
- `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/projection/ProjectionSyncCoordinatorTest.java`
  Responsibility: lock the new snapshot/session/projection surfaces before the websocket path is rewritten.

### Modified production files

- `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImTopics.java`
- `backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/JsonContractsTest.java`
  Responsibility: define the new contract namespace and serialization guarantees.
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/repository/RoomMemberRepository.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/service/RoomMembershipService.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/EventProducer.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaRoomMemberChangePublisher.java`
- `backend/community-im/im-core/src/main/resources/application.yml`
  Responsibility: export paged membership snapshots and publish the new membership-change event.
- `backend/community-app/pom.xml`
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserModerationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/block/DbBlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/block/RedisBlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/block/InMemoryBlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockService.java`
- `backend/community-app/src/main/resources/application.yml`
  Responsibility: add `im-common` + Kafka, scan block state, and publish projection changes after writes.
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/ImServiceClientProperties.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/LoadBalancedWebClientConfig.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityConfig.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/CommandProducer.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/EventConsumers.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/WsConnection.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/push/PrivatePushService.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/push/RoomFanoutCoalescer.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/push/SendResultPushService.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/WebSocketConfig.java`
- `backend/community-im/im-realtime/src/main/resources/application.yml`
  Responsibility: build readiness-aware projections, expose the session API, and rewrite websocket handling around `connect(ticket)` and local decisions.
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalImWebSocketHandler.java`
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/WsProxyProperties.java`
- `backend/community-gateway/src/main/resources/application.yml`
  Responsibility: convert gateway IM routing to a transparent worker-path proxy.

### Deleted production files

- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/AuthFrameParser.java`
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalWsSessionState.java`
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/ConsistentHashShardRouter.java`
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/ShardRouter.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/CommunityGovernanceClient.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/ImCoreClient.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/WsProtocol.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/InternalRealtimeBootstrapController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImGovernanceController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/governance/PrivateMessageGovernanceService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/governance/UserModerationGuard.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/governance/action/PrivateMessageGovernanceActionApi.java`
  Responsibility: remove the legacy synchronous hot-path surfaces after replacement code is live.

### Deleted test files

- `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/shard/ConsistentHashShardRouterTest.java`
- `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/GatewayPrivateFlowCompatibilityTest.java`
- `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/GatewayRoomFlowCompatibilityTest.java`
- `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/WsAuthStateMachineIntegrationTest.java`
- `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/client/CommunityGovernanceClientTraceHeadersTest.java`
- `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/client/ImCoreClientTraceHeadersTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/im/architecture/PrivateMessageOwnershipArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/im/controller/ImGovernanceControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/im/governance/PrivateMessageGovernanceServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/im/governance/UserModerationGuardTest.java`
  Responsibility: retire tests for code that must not exist after the hard cut.

---

### Task 1: Establish The New IM Contract Surface In `im-common`

**Files:**
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/session/OpenImSessionRequest.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/session/OpenImSessionResponse.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/ConnectFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/SendPrivateTextFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/SendRoomTextFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/AckFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/RejectFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/CommittedFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/PrivateMessageFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/RoomMessageFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/PingFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/PongFrame.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/RoomMembershipEntry.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/RoomMembershipSnapshot.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserMessagingPolicyEntry.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserMessagingPolicySnapshot.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserBlockRelationEntry.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/UserBlockRelationSnapshot.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMemberChanged.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/UserMessagingPolicyChanged.java`
- Create: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/UserBlockRelationChanged.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImTopics.java`
- Modify: `backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/JsonContractsTest.java`

- [ ] **Step 1: Write the failing serialization tests for the new contract surface**

```java
// backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/JsonContractsTest.java
@Test
void shouldRoundTripOpenImSessionResponse() throws Exception {
    OpenImSessionResponse response = new OpenImSessionResponse(
            "sess-1",
            "worker-a",
            "wss://community.example/ws/im/workers/worker-a",
            "ticket-1",
            1_712_345_678_901L
    );

    String json = objectMapper.writeValueAsString(response);
    OpenImSessionResponse back = objectMapper.readValue(json, OpenImSessionResponse.class);

    assertThat(back.workerId()).isEqualTo("worker-a");
    assertThat(back.wsUrl()).contains("/ws/im/workers/worker-a");
}

@Test
void shouldRoundTripRoomMembershipSnapshot() throws Exception {
    RoomMembershipSnapshot snapshot = new RoomMembershipSnapshot(
            List.of(new RoomMembershipEntry(
                    UUID.fromString("00000000-0000-7000-8000-000000000010"),
                    UUID.fromString("00000000-0000-7000-8000-000000000001")
            )),
            UUID.fromString("00000000-0000-7000-8000-000000000010"),
            UUID.fromString("00000000-0000-7000-8000-000000000001"),
            false
    );

    String json = objectMapper.writeValueAsString(snapshot);
    RoomMembershipSnapshot back = objectMapper.readValue(json, RoomMembershipSnapshot.class);

    assertThat(back.entries()).hasSize(1);
    assertThat(back.entries().get(0).roomId()).isEqualTo(snapshot.entries().get(0).roomId());
}

@Test
void shouldRoundTripUserBlockRelationChanged() throws Exception {
    UserBlockRelationChanged event = new UserBlockRelationChanged(
            "evt-block-1",
            UUID.fromString("00000000-0000-7000-8000-000000000011"),
            UUID.fromString("00000000-0000-7000-8000-000000000022"),
            true,
            1_712_345_678_901L
    );

    String json = objectMapper.writeValueAsString(event);
    UserBlockRelationChanged back = objectMapper.readValue(json, UserBlockRelationChanged.class);

    assertThat(back.active()).isTrue();
    assertThat(back.blockerUserId()).isEqualTo(event.blockerUserId());
}
```

- [ ] **Step 2: Run the `im-common` contract tests and verify they fail on missing types/constants**

Run:

```bash
cd backend && mvn -q -pl community-im/im-common -Dtest=JsonContractsTest test
```

Expected:

- compilation errors for missing `OpenImSessionResponse`, `RoomMembershipSnapshot`, and `UserBlockRelationChanged`

- [ ] **Step 3: Add the new contracts and topic constants**

```java
// backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/session/OpenImSessionResponse.java
package com.nowcoder.community.im.common.session;

public record OpenImSessionResponse(
        String sessionId,
        String workerId,
        String wsUrl,
        String ticket,
        long expiresAtEpochMillis
) {
}
```

```java
// backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ws/ConnectFrame.java
package com.nowcoder.community.im.common.ws;

public record ConnectFrame(String type, String ticket) {

    public ConnectFrame {
        if (!"connect".equals(type)) {
            throw new IllegalArgumentException("type must be connect");
        }
    }
}
```

```java
// backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/projection/RoomMembershipSnapshot.java
package com.nowcoder.community.im.common.projection;

import java.util.List;
import java.util.UUID;

public record RoomMembershipSnapshot(
        List<RoomMembershipEntry> entries,
        UUID nextRoomId,
        UUID nextUserId,
        boolean hasMore
) {
}
```

```java
// backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImTopics.java
public static final String EVENT_ROOM_MEMBER_CHANGED = "im.event.room-member-changed";
public static final String EVENT_USER_MESSAGING_POLICY_CHANGED = "im.event.user-messaging-policy-changed";
public static final String EVENT_USER_BLOCK_RELATION_CHANGED = "im.event.user-block-relation-changed";
```

- [ ] **Step 4: Re-run the `im-common` tests to lock the contract JSON**

Run:

```bash
cd backend && mvn -q -pl community-im/im-common -Dtest=JsonContractsTest test
```

Expected:

- `BUILD SUCCESS`
- `JsonContractsTest` passes with the new session/ws/projection contracts

- [ ] **Step 5: Commit the contract surface**

```bash
git add backend/community-im/im-common/src/main/java backend/community-im/im-common/src/test/java/com/nowcoder/community/im/common/JsonContractsTest.java
git commit -m "feat: add im session and projection contracts"
```

### Task 2: Export Room Membership Snapshots And Events From `im-core`

**Files:**
- Create: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/InternalRealtimeProjectionController.java`
- Create: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/controller/InternalRealtimeProjectionControllerTest.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/repository/RoomMemberRepository.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/service/RoomMembershipService.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/EventProducer.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaRoomMemberChangePublisher.java`
- Modify: `backend/community-im/im-core/src/main/resources/application.yml`
- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/kafka/ImCoreKafkaIntegrationTest.java`

- [ ] **Step 1: Write the failing snapshot and event tests**

```java
// backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/controller/InternalRealtimeProjectionControllerTest.java
@Test
void roomMembershipSnapshotShouldPageByRoomAndUser() throws Exception {
    UUID owner = uuid(1);
    UUID roomId = roomMembershipService.createRoom(owner, "room-a");
    roomMembershipService.joinRoom(uuid(2), roomId);

    mockMvc.perform(get("/internal/im/realtime/projections/room-memberships")
                    .header("Authorization", bearer(owner))
                    .queryParam("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].roomId").value(roomId.toString()))
            .andExpect(jsonPath("$.entries[0].userId").value(owner.toString()));
}
```

```java
// backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/kafka/ImCoreKafkaIntegrationTest.java
consumer.subscribe(List.of(ImTopics.EVENT_ROOM_MEMBER_CHANGED));
roomMembershipService.joinRoom(uuid(3), roomId);

ConsumerRecord<String, String> changedRecord =
        pollForSingleRecord(consumer, ImTopics.EVENT_ROOM_MEMBER_CHANGED, Duration.ofSeconds(10));
assertThat(changedRecord.value()).contains("\"action\":\"JOINED\"");
```

- [ ] **Step 2: Run the `im-core` tests and verify the new endpoint/event surface is missing**

Run:

```bash
cd backend && mvn -q -pl community-im/im-core -Dtest=InternalRealtimeProjectionControllerTest,ImCoreKafkaIntegrationTest test
```

Expected:

- 404 for `/internal/im/realtime/projections/room-memberships`
- missing `ImTopics.EVENT_ROOM_MEMBER_CHANGED`

- [ ] **Step 3: Implement paged snapshot scanning and publish the new membership event**

```java
// backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/repository/RoomMemberRepository.java
public List<RoomMembershipEntry> scanMemberships(UUID afterRoomId, UUID afterUserId, int limit) {
    UUID roomCursor = afterRoomId == null ? new UUID(0L, 0L) : afterRoomId;
    UUID userCursor = afterUserId == null ? new UUID(0L, 0L) : afterUserId;
    int l = Math.min(500, Math.max(1, limit));
    return jdbcTemplate.query(
            """
                    select room_id, user_id
                    from im_room_member
                    where (room_id > ?) or (room_id = ? and user_id > ?)
                    order by room_id asc, user_id asc
                    limit ?
                    """,
            (rs, rowNum) -> new RoomMembershipEntry(
                    BinaryUuidCodec.fromBytes(rs.getBytes("room_id")),
                    BinaryUuidCodec.fromBytes(rs.getBytes("user_id"))
            ),
            BinaryUuidCodec.toBytes(roomCursor),
            BinaryUuidCodec.toBytes(roomCursor),
            BinaryUuidCodec.toBytes(userCursor),
            l
    );
}
```

```java
// backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/InternalRealtimeProjectionController.java
@RestController
@RequestMapping("/internal/im/realtime/projections")
public class InternalRealtimeProjectionController {

    private final RoomMembershipService roomMembershipService;

    public InternalRealtimeProjectionController(RoomMembershipService roomMembershipService) {
        this.roomMembershipService = roomMembershipService;
    }

    @GetMapping("/room-memberships")
    public RoomMembershipSnapshot roomMemberships(
            @RequestParam(name = "afterRoomId", required = false) UUID afterRoomId,
            @RequestParam(name = "afterUserId", required = false) UUID afterUserId,
            @RequestParam(name = "limit", defaultValue = "500") int limit
    ) {
        return roomMembershipService.snapshot(afterRoomId, afterUserId, limit);
    }
}
```

```java
// backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/KafkaRoomMemberChangePublisher.java
eventProducer.publishRoomMemberChanged(new RoomMemberChanged(
        "evt_room_member_joined_" + roomId + "_" + userId + "_" + now.toEpochMilli(),
        roomId,
        userId,
        "JOINED",
        now.toEpochMilli()
));
```

- [ ] **Step 4: Re-run the `im-core` snapshot/event tests**

Run:

```bash
cd backend && mvn -q -pl community-im/im-core -Dtest=InternalRealtimeProjectionControllerTest,ImCoreKafkaIntegrationTest test
```

Expected:

- `BUILD SUCCESS`
- the new room-membership snapshot endpoint and event topic are covered

- [ ] **Step 5: Commit the `im-core` projection export**

```bash
git add backend/community-im/im-core/src/main/java backend/community-im/im-core/src/main/resources/application.yml backend/community-im/im-core/src/test/java
git commit -m "feat: export im core room membership projection"
```

### Task 3: Export Policy Snapshots And Durable Change Events From `community-app`

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyChangePublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandler.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicySnapshotControllerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandlerTest.java`
- Modify: `backend/community-app/pom.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserModerationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/block/DbBlockRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/block/RedisBlockRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/block/InMemoryBlockRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockService.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/service/BlockServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/service/UserModerationServiceTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImGovernanceController.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/im/governance/PrivateMessageGovernanceService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/im/governance/UserModerationGuard.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/im/governance/action/PrivateMessageGovernanceActionApi.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/im/architecture/PrivateMessageOwnershipArchTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/im/controller/ImGovernanceControllerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/im/governance/PrivateMessageGovernanceServiceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/im/governance/UserModerationGuardTest.java`

- [ ] **Step 1: Write the failing policy snapshot and publisher tests**

```java
// backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicySnapshotControllerTest.java
@Test
void userMessagingPolicySnapshotShouldExposeMuteBanAndExistence() throws Exception {
    UUID userId = uuid(7);
    userModerationService.applyModeration(userId, "mute", 300);

    mockMvc.perform(get("/internal/im/realtime/projections/user-policies")
                    .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].userId").value(userId.toString()))
            .andExpect(jsonPath("$.entries[0].canSendPrivate").value(false));
}

@Test
void userBlockRelationSnapshotShouldPageBlockPairs() throws Exception {
    blockService.block(uuid(1), uuid(2));

    mockMvc.perform(get("/internal/im/realtime/projections/block-relations")
                    .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries[0].blockerUserId").value(uuid(1).toString()))
            .andExpect(jsonPath("$.entries[0].blockedUserId").value(uuid(2).toString()));
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandlerTest.java
@Test
void moderationOutboxShouldPublishCurrentPolicyState() {
    KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    UserModerationService moderationService = mock(UserModerationService.class);
    BlockService blockService = mock(BlockService.class);
    UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
    when(moderationService.getModerationState(uuid(7)))
            .thenReturn(new UserModerationStateView(uuid(7), Instant.now().plusSeconds(60), null));
    when(userLookupQueryApi.getSummaryById(uuid(7))).thenReturn(new UserSummaryView(uuid(7), "u7", "/avatar.png", 0));

    ImPolicyKafkaOutboxHandler handler = new ImPolicyKafkaOutboxHandler(
            objectMapper,
            moderationService,
            blockService,
            userLookupQueryApi,
            kafkaTemplate
    );

    handler.handle(new OutboxEvent(
            UUID.randomUUID(),
            "evt-policy-1",
            ImPolicyKafkaOutboxHandler.TOPIC,
            uuid(7).toString(),
            "{\"kind\":\"MODERATION\",\"userId\":\"" + uuid(7) + "\"}",
            "PENDING",
            0,
            null,
            null
    ));

    verify(kafkaTemplate).send(eq(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED), eq(uuid(7).toString()), any(UserMessagingPolicyChanged.class));
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/social/service/BlockServiceTest.java
verify(imPolicyChangePublisher).publishBlockRelationChanged(USER_ID_1, USER_ID_2, true);
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/service/UserModerationServiceTest.java
verify(imPolicyChangePublisher).publishUserPolicyChanged(USER_ID_1);
```

- [ ] **Step 2: Run the targeted `community-app` tests and verify the new projection surfaces do not exist yet**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=BlockServiceTest,UserModerationServiceTest,ImPolicySnapshotControllerTest,ImPolicyKafkaOutboxHandlerTest test
```

Expected:

- missing `ImPolicySnapshotController`, `ImPolicyKafkaOutboxHandler`, and `ImPolicyChangePublisher`
- constructor mismatch in `BlockService` / `UserModerationService` after the new assertions are added

- [ ] **Step 3: Implement snapshot scans, outbox publishing, and remove the governance HTTP surface**

```java
// backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyChangePublisher.java
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImPolicyChangePublisher {

    private static final String TOPIC = "projection.im.policy";

    private final JdbcOutboxEventStore store;
    private final ObjectMapper objectMapper;

    public ImPolicyChangePublisher(JdbcOutboxEventStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public void publishUserPolicyChanged(UUID userId) {
        enqueue(new Payload("MODERATION", userId, null, null));
    }

    public void publishBlockRelationChanged(UUID blockerUserId, UUID blockedUserId, boolean active) {
        enqueue(new Payload("BLOCK", blockerUserId, blockedUserId, active));
    }

    private void enqueue(Payload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            store.enqueue("im-policy:" + payload.kind + ":" + payload.primaryUserId, TOPIC, String.valueOf(payload.primaryUserId), json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("im policy outbox payload 序列化失败", e);
        }
    }

    record Payload(String kind, UUID primaryUserId, UUID secondaryUserId, Boolean active) {}
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotController.java
@RestController
@RequestMapping("/internal/im/realtime/projections")
public class ImPolicySnapshotController {

    private final ImPolicySnapshotService snapshotService;

    public ImPolicySnapshotController(ImPolicySnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping("/user-policies")
    public UserMessagingPolicySnapshot userPolicies(
            @RequestParam(name = "afterUserId", required = false) UUID afterUserId,
            @RequestParam(name = "limit", defaultValue = "500") int limit
    ) {
        return snapshotService.userPolicies(afterUserId, limit);
    }

    @GetMapping("/block-relations")
    public UserBlockRelationSnapshot blockRelations(
            @RequestParam(name = "afterBlockerUserId", required = false) UUID afterBlockerUserId,
            @RequestParam(name = "afterBlockedUserId", required = false) UUID afterBlockedUserId,
            @RequestParam(name = "limit", defaultValue = "500") int limit
    ) {
        return snapshotService.blockRelations(afterBlockerUserId, afterBlockedUserId, limit);
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/service/UserModerationService.java
private final ImPolicyChangePublisher imPolicyChangePublisher;

public UserModerationService(UserMapper userMapper, ImPolicyChangePublisher imPolicyChangePublisher) {
    this.userMapper = userMapper;
    this.imPolicyChangePublisher = imPolicyChangePublisher;
}

@Transactional
public ModerationStatus applyModeration(UUID userId, String action, int durationSeconds) {
    int updated = userMapper.updateModerationUntil(
            userId,
            muteUntil == null ? null : Date.from(muteUntil),
            banUntil == null ? null : Date.from(banUntil)
    );
    if (updated <= 0) {
        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新处罚状态失败");
    }

    ModerationStatus status = new ModerationStatus();
    status.setUserId(userId);
    status.setMuteUntil(muteUntil);
    status.setBanUntil(banUntil);
    imPolicyChangePublisher.publishUserPolicyChanged(userId);
    return status;
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockService.java
private final ImPolicyChangePublisher imPolicyChangePublisher;

public BlockService(BlockRepository repository, SocialEventPublisher eventPublisher, ImPolicyChangePublisher imPolicyChangePublisher) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.imPolicyChangePublisher = imPolicyChangePublisher;
}

@Transactional
public void block(UUID userId, UUID targetUserId) {
    boolean changed = repository.block(userId, targetUserId);
    if (!changed) {
        return;
    }

    BlockPayload payload = new BlockPayload();
    payload.setBlockerUserId(userId);
    payload.setBlockedUserId(targetUserId);
    payload.setBlocked(Boolean.TRUE);
    eventPublisher.publishBlockRelationChanged(payload);
    imPolicyChangePublisher.publishBlockRelationChanged(userId, targetUserId, true);
}
```

- [ ] **Step 4: Re-run the `community-app` policy tests**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=BlockServiceTest,UserModerationServiceTest,ImPolicySnapshotControllerTest,ImPolicyKafkaOutboxHandlerTest test
```

Expected:

- `BUILD SUCCESS`
- block and moderation writes enqueue IM policy outbox work
- policy snapshots expose user and block state without the old governance controller

- [ ] **Step 5: Commit the `community-app` projection export and governance cleanup**

```bash
git add backend/community-app/pom.xml backend/community-app/src/main/java backend/community-app/src/main/resources/application.yml backend/community-app/src/test/java
git commit -m "feat: export im policy projection from community app"
```

### Task 4: Add Session Tickets And Projection Readiness To `im-realtime`

**Files:**
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionApiController.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionService.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionProperties.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/SessionTicketCodec.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/RendezvousWorkerSelector.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/MembershipSnapshotClient.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicySnapshotClient.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/MembershipProjectionService.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicyProjectionService.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicyDecision.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/ProjectionSyncCoordinator.java`
- Create: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/ImSessionApiIntegrationTest.java`
- Create: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/projection/ProjectionSyncCoordinatorTest.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/ImServiceClientProperties.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/LoadBalancedWebClientConfig.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityConfig.java`
- Modify: `backend/community-im/im-realtime/src/main/resources/application.yml`

- [ ] **Step 1: Write the failing session API and readiness tests**

```java
// backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/session/ImSessionApiIntegrationTest.java
@Test
void postImSessionsShouldReturnWorkerSpecificWsUrlAndTicket() {
    webTestClient.post()
            .uri("/api/im/sessions")
            .header(HttpHeaders.AUTHORIZATION, bearer(uuid(1)))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.code").isEqualTo(0)
            .jsonPath("$.data.workerId").isEqualTo("worker-a")
            .jsonPath("$.data.wsUrl").isEqualTo("wss://community.example/ws/im/workers/worker-a")
            .jsonPath("$.data.ticket").isNotEmpty();
}
```

```java
// backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/projection/ProjectionSyncCoordinatorTest.java
@Test
void readyShouldRemainFalseUntilRoomAndPolicySnapshotsAreLoaded() {
    MembershipProjectionService membership = mock(MembershipProjectionService.class);
    PolicyProjectionService policy = mock(PolicyProjectionService.class);
    when(membership.refresh()).thenReturn(Mono.empty());
    when(policy.refresh()).thenReturn(Mono.empty());

    ProjectionSyncCoordinator coordinator = new ProjectionSyncCoordinator(membership, policy);
    assertThat(coordinator.ready()).isFalse();

    coordinator.refreshNow().block();

    assertThat(coordinator.ready()).isTrue();
    verify(membership).refresh();
    verify(policy).refresh();
}
```

- [ ] **Step 2: Run the new `im-realtime` tests and verify the session/projection infrastructure is absent**

Run:

```bash
cd backend && mvn -q -pl community-im/im-realtime -Dtest=ImSessionApiIntegrationTest,ProjectionSyncCoordinatorTest test
```

Expected:

- 404 for `POST /api/im/sessions`
- missing `ProjectionSyncCoordinator`

- [ ] **Step 3: Implement ticket minting, worker selection, snapshot clients, and readiness coordination**

```java
// backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/session/ImSessionService.java
@Service
public class ImSessionService {

    private final JwtVerifier jwtVerifier;
    private final SessionTicketCodec sessionTicketCodec;
    private final RendezvousWorkerSelector workerSelector;
    private final ImSessionProperties properties;
    private final ProjectionSyncCoordinator projectionSyncCoordinator;

    public OpenImSessionResponse open(String bearerToken) {
        projectionSyncCoordinator.requireReady();
        JwtVerifier.VerifiedJwt verified = jwtVerifier.verify(extractBearerToken(bearerToken));
        String workerId = workerSelector.selectWorker(verified.userId());
        String sessionId = "sess_" + UUID.randomUUID();
        long expiresAt = Instant.now().plus(properties.getTicketTtl()).toEpochMilli();
        String ticket = sessionTicketCodec.encode(sessionId, verified.userId(), workerId, expiresAt);
        String wsUrl = properties.getPublicWsBaseUrl() + "/ws/im/workers/" + workerId;
        return new OpenImSessionResponse(sessionId, workerId, wsUrl, ticket, expiresAt);
    }
}
```

```java
// backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/ProjectionSyncCoordinator.java
@Component
public class ProjectionSyncCoordinator {

    private final MembershipProjectionService membershipProjectionService;
    private final PolicyProjectionService policyProjectionService;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public Mono<Void> refreshNow() {
        return Mono.when(
                membershipProjectionService.refresh(),
                policyProjectionService.refresh()
        ).doOnSuccess(ignored -> ready.set(true));
    }

    public boolean ready() {
        return ready.get();
    }

    public void requireReady() {
        if (!ready()) {
            throw new IllegalStateException("im projections not ready");
        }
    }
}
```

```java
// backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/projection/PolicyDecision.java
package com.nowcoder.community.im.realtime.projection;

public record PolicyDecision(boolean allowed, String message) {

    public static PolicyDecision allow() {
        return new PolicyDecision(true, "");
    }

    public static PolicyDecision deny(String message) {
        return new PolicyDecision(false, message == null ? "" : message);
    }
}
```

```java
// backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/security/ImRealtimeSecurityConfig.java
.authorizeExchange(ex -> ex
        .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
        .pathMatchers(HttpMethod.POST, "/api/im/sessions").permitAll()
        .pathMatchers(wsPathValue).permitAll()
        .anyExchange().denyAll()
)
```

- [ ] **Step 4: Re-run the session/readiness tests**

Run:

```bash
cd backend && mvn -q -pl community-im/im-realtime -Dtest=ImSessionApiIntegrationTest,ProjectionSyncCoordinatorTest test
```

Expected:

- `BUILD SUCCESS`
- session creation returns worker-specific websocket URLs
- readiness gate flips only after both projections load

- [ ] **Step 5: Commit the session/projection bootstrap slice**

```bash
git add backend/community-im/im-realtime/src/main/java backend/community-im/im-realtime/src/main/resources/application.yml backend/community-im/im-realtime/src/test/java
git commit -m "feat: add im session tickets and projection bootstrap"
```

### Task 5: Rewrite `im-realtime` WebSocket Handling Around `connect(ticket)` And Local Decisions

**Files:**
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImFrameCodec.java`
- Create: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/service/MessageCommandIngressService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/WebSocketConfig.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/presence/WsConnection.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/CommandProducer.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/EventConsumers.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/push/PrivatePushService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/push/SendResultPushService.java`
- Modify: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/push/RoomFanoutCoalescer.java`
- Modify: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java`
- Modify: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImTopics.java`
- Move: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/command/SendPrivateTextCommandV1.java` -> `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/command/SendPrivateTextCommand.java`
- Move: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/command/SendRoomTextCommandV1.java` -> `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/command/SendRoomTextCommand.java`
- Move: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/PrivateMessagePersistedEventV1.java` -> `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/PrivateMessagePersistedEvent.java`
- Move: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/PrivateMessageRejectedEventV1.java` -> `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/PrivateMessageRejectedEvent.java`
- Move: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMessagePersistedEventV1.java` -> `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMessagePersistedEvent.java`
- Move: `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMessageRejectedEventV1.java` -> `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/event/RoomMessageRejectedEvent.java`
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/CommunityGovernanceClient.java`
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/ImCoreClient.java`
- Delete: `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/WsProtocol.java`
- Delete: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/client/CommunityGovernanceClientTraceHeadersTest.java`
- Delete: `backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/client/ImCoreClientTraceHeadersTest.java`

- [ ] **Step 1: Rewrite the websocket integration test first**

```java
// backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java
String sessionRes = webTestClient.post()
        .uri("/api/im/sessions")
        .header(HttpHeaders.AUTHORIZATION, bearer(userId))
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();

JsonNode sessionJson = objectMapper.readTree(sessionRes);
String wsUrl = sessionJson.path("data").path("wsUrl").asText();
String ticket = sessionJson.path("data").path("ticket").asText();

outbound.tryEmitNext("{\"type\":\"connect\",\"ticket\":\"" + ticket + "\"}");
JsonNode connectedFrame = awaitType(received, "connected", Duration.ofSeconds(5));
assertThat(connectedFrame.path("sessionId").asText("")).isNotBlank();

outbound.tryEmitNext("{\"type\":\"sendPrivateText\",\"clientMsgId\":\"c1\",\"toUserId\":\"" + toUserId + "\",\"content\":\"hi\"}");
JsonNode privateAccepted = awaitType(received, "ack", Duration.ofSeconds(5));
assertThat(privateAccepted.path("cmd").asText("")).isEqualTo("sendPrivateText");
```

```java
// backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/ws/ImRealtimeWebSocketIntegrationTest.java
doReturn(PolicyDecision.deny("blocked"))
        .when(policyProjectionService)
        .canSendPrivate(userId, toUserId);

outbound.tryEmitNext("{\"type\":\"sendPrivateText\",\"clientMsgId\":\"c-blocked\",\"toUserId\":\"" + toUserId + "\",\"content\":\"hi\"}");

JsonNode rejected = awaitType(received, "reject", Duration.ofSeconds(5));
assertThat(rejected.path("reasonCode").asText("")).isEqualTo("policy_denied");
```

- [ ] **Step 2: Run the realtime websocket test and verify the old `auth` / synchronous client path is still wired**

Run:

```bash
cd backend && mvn -q -pl community-im/im-realtime -Dtest=ImRealtimeWebSocketIntegrationTest test
```

Expected:

- failure because the handler still expects `auth`
- missing `ack` / `reject` frame types
- tests still depend on `CommunityGovernanceClient` / `ImCoreClient`

- [ ] **Step 3: Implement the new handler flow, local command ingress, and rename the remaining message contracts**

```java
// backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java
private Mono<Void> handleConnect(WsConnection conn, JsonNode node) {
    String ticket = node.path("ticket").asText("");
    SessionTicketCodec.DecodedTicket decoded = sessionTicketCodec.decode(ticket);
    projectionSyncCoordinator.requireReady();

    conn.bindSession(decoded.sessionId(), decoded.userId(), decoded.workerId());
    membershipProjectionService.bindExistingRooms(conn);
    connectionRegistry.register(conn);
    conn.trySendText(frameCodec.write(new AckFrame("connected", "", decoded.sessionId())));
    return Mono.empty();
}

private Mono<Void> handleSendPrivate(WsConnection conn, JsonNode node) {
    SendPrivateTextFrame frame = frameCodec.read(node, SendPrivateTextFrame.class);
    PolicyDecision decision = policyProjectionService.canSendPrivate(conn.userId(), frame.toUserId());
    if (!decision.allowed()) {
        conn.trySendText(frameCodec.write(new RejectFrame("sendPrivateText", frame.clientMsgId(), "policy_denied", decision.message())));
        return Mono.empty();
    }
    return commandIngressService.sendPrivate(conn, frame);
}
```

```java
// backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/service/MessageCommandIngressService.java
public Mono<Void> sendPrivate(WsConnection conn, SendPrivateTextFrame frame) {
    String requestId = requestIdGenerator.next();
    SendPrivateTextCommand cmd = new SendPrivateTextCommand(
            requestId,
            frame.clientMsgId(),
            conn.userId(),
            frame.toUserId(),
            ConversationIdSupport.conversationId(conn.userId(), frame.toUserId()),
            frame.content(),
            System.currentTimeMillis()
    );
    CompletableFuture<?> future = commandProducer.sendPrivateText(cmd);
    conn.trySendText(frameCodec.write(new AckFrame("sendPrivateText", frame.clientMsgId(), requestId)));
    attachCompletionPush(conn, cmd, future);
    return Mono.empty();
}
```

```java
// backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImTopics.java
public static final String COMMAND_PRIVATE_TEXT = "im.command.private-text";
public static final String COMMAND_ROOM_TEXT = "im.command.room-text";
public static final String EVENT_PRIVATE_PERSISTED = "im.event.private-persisted";
public static final String EVENT_PRIVATE_REJECTED = "im.event.private-rejected";
public static final String EVENT_ROOM_PERSISTED = "im.event.room-persisted";
public static final String EVENT_ROOM_REJECTED = "im.event.room-rejected";
```

- [ ] **Step 4: Re-run the realtime websocket test against the new local hot path**

Run:

```bash
cd backend && mvn -q -pl community-im/im-realtime -Dtest=ImRealtimeWebSocketIntegrationTest test
```

Expected:

- `BUILD SUCCESS`
- `connect(ticket)` replaces `auth`
- private sends are accepted/rejected from local policy projection
- no runtime dependency remains on `CommunityGovernanceClient` or `ImCoreClient`

- [ ] **Step 5: Commit the realtime hot-path rewrite**

```bash
git add backend/community-im/im-common/src/main/java backend/community-im/im-realtime/src/main/java backend/community-im/im-realtime/src/test/java
git rm backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/CommunityGovernanceClient.java backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/ImCoreClient.java backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/WsProtocol.java backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/client/CommunityGovernanceClientTraceHeadersTest.java backend/community-im/im-realtime/src/test/java/com/nowcoder/community/im/realtime/client/ImCoreClientTraceHeadersTest.java
git commit -m "feat: move im websocket hot path fully local"
```

### Task 6: Make Gateway A Transparent Worker-Path Proxy And Delete The Legacy Surfaces

**Files:**
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/WorkerPathResolver.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalImWebSocketHandler.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/WsProxyProperties.java`
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/WsTransparentProxyIntegrationTest.java`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/security/GatewayDefaultSecurityIntegrationTest.java`
- Delete: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/AuthFrameParser.java`
- Delete: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalWsSessionState.java`
- Delete: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/ConsistentHashShardRouter.java`
- Delete: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/ShardRouter.java`
- Delete: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/shard/ConsistentHashShardRouterTest.java`
- Delete: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/GatewayPrivateFlowCompatibilityTest.java`
- Delete: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/GatewayRoomFlowCompatibilityTest.java`
- Delete: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/WsAuthStateMachineIntegrationTest.java`
- Delete: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/InternalRealtimeBootstrapController.java`

- [ ] **Step 1: Rewrite the gateway proxy test to prove routing happens from the worker path, not websocket frame auth**

```java
// backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/WsTransparentProxyIntegrationTest.java
@Test
void shouldProxyFramesToWorkerSelectedByPath() throws Exception {
    URI gatewayUri = URI.create("ws://localhost:" + port + "/ws/im/workers/worker-a");
    Disposable ws = client.execute(gatewayUri, session -> {
                connected.countDown();
                Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
                Mono<Void> recv = session.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .doOnNext(received::offer)
                        .take(1)
                        .then();
                return Mono.when(send, recv);
            })
            .subscribe();
    outbound.tryEmitNext("hello");
    assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("worker-a:hello");
    ws.dispose();
}
```

```java
// worker server setup inside the same test
.route(routes -> routes.ws("/internal/ws/im", (in, out) ->
        out.sendString(in.receive().asString().map(text -> "worker-a:" + text))
))
```

- [ ] **Step 2: Run the gateway websocket test and verify the current handler still depends on `AuthFrameParser` / shard routing**

Run:

```bash
cd backend && mvn -q -pl community-gateway -Dtest=WsTransparentProxyIntegrationTest,GatewayDefaultSecurityIntegrationTest test
```

Expected:

- failure because `/ws/im/workers/worker-a` is not recognized
- compile/runtime dependency on `AuthFrameParser` and `ShardRouter`

- [ ] **Step 3: Implement worker-path routing, remove websocket auth parsing, and delete the bootstrap/governance leftovers**

```java
// backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/WorkerPathResolver.java
@Component
public class WorkerPathResolver {

    public String resolve(URI uri) {
        String path = uri == null ? "" : String.valueOf(uri.getPath());
        String prefix = "/ws/im/workers/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            throw new IllegalArgumentException("workerId missing from path: " + path);
        }
        return path.substring(prefix.length());
    }
}
```

```java
// backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalImWebSocketHandler.java
@Override
public Mono<Void> handle(WebSocketSession session) {
    String workerId = workerPathResolver.resolve(session.getHandshakeInfo().getUri());
    WorkerDescriptor worker = workerRegistry.healthyWorkers().stream()
            .filter(candidate -> workerId.equals(candidate.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("worker not found: " + workerId));
    return bridgeFactory.create(worker.getUri())
            .bridge(session, session.receive().map(WebSocketMessage::getPayloadAsText));
}
```

```bash
# delete the retired surfaces after the new proxy path is in place
git rm backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/AuthFrameParser.java \
       backend/community-gateway/src/main/java/com/nowcoder/community/gateway/ws/ExternalWsSessionState.java \
       backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/ConsistentHashShardRouter.java \
       backend/community-gateway/src/main/java/com/nowcoder/community/gateway/shard/ShardRouter.java \
       backend/community-gateway/src/test/java/com/nowcoder/community/gateway/shard/ConsistentHashShardRouterTest.java \
       backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/GatewayPrivateFlowCompatibilityTest.java \
       backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/GatewayRoomFlowCompatibilityTest.java \
       backend/community-gateway/src/test/java/com/nowcoder/community/gateway/ws/WsAuthStateMachineIntegrationTest.java \
       backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/InternalRealtimeBootstrapController.java
```

- [ ] **Step 4: Run the final cross-module verification**

Run:

```bash
cd backend && mvn -q -pl community-im/im-common,community-im/im-core,community-im/im-realtime,community-gateway,community-app test
```

Expected:

- `BUILD SUCCESS`
- no module still references `auth` websocket frames, synchronous governance HTTP, or login bootstrap HTTP

- [ ] **Step 5: Commit the gateway hard cut and final cleanup**

```bash
git add backend/community-gateway/src/main/java backend/community-gateway/src/main/resources/application.yml backend/community-gateway/src/test/java backend/community-im/im-core/src/main/java backend/community-app/src/main/java backend/community-app/src/test/java
git commit -m "feat: hard cut im routing to worker-path proxy"
```

# IM Realtime (im-realtime / im-core) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a separated IM realtime system with `im-realtime` (WebSocket) + `im-core` (persistence + APIs), using Kafka as the backplane, supporting 1v1 content push and room state-only push (coalesced), with history + unread.

**Architecture:** Client connects to `im-realtime` via WebSocket, authenticates with JWT, and sends text messages via WS. `im-realtime` produces Kafka commands. `im-core` consumes commands, validates, writes MySQL, then emits persisted events. `im-realtime` consumes persisted events and pushes to connected users (private: content; room: state-only, coalesced). Correctness is ensured by HTTP pull from `im-core` using `(roomId/conversationId, seq)` watermarks.

**Tech Stack:** Java 17, Spring Boot 3.2.x, Spring WebFlux (WS), Spring Web (HTTP), Spring Kafka, MySQL (JDBC/MyBatis), Micrometer/Actuator, Docker Compose.

---

## Task 1: Add Maven modules for `im-contracts`, `im-core`, `im-realtime`

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/im-contracts/pom.xml`
- Create: `backend/im-core/pom.xml`
- Create: `backend/im-realtime/pom.xml`
- Create: `backend/im-core/src/main/java/.../ImCoreApplication.java`
- Create: `backend/im-realtime/src/main/java/.../ImRealtimeApplication.java`
- Create: `backend/im-core/src/main/resources/application.yml`
- Create: `backend/im-realtime/src/main/resources/application.yml`

**Step 1: Add modules to the reactor**

Modify `backend/pom.xml`:

- Add:
  - `<module>im-contracts</module>`
  - `<module>im-core</module>`
  - `<module>im-realtime</module>`

**Step 2: Create `im-contracts` module**

Create `backend/im-contracts/pom.xml`:
- packaging `jar`
- no Spring Boot plugin
- keep dependencies minimal (`jackson-annotations` if needed)

**Step 3: Create `im-core` module**

Create `backend/im-core/pom.xml`:
- `spring-boot-starter-web`
- `spring-boot-starter-actuator`
- `spring-boot-starter-validation`
- `spring-boot-starter-jdbc` (or MyBatis starter if preferred)
- `mysql-connector-j`
- `spring-kafka`
- depend on `im-contracts`

**Step 4: Create `im-realtime` module**

Create `backend/im-realtime/pom.xml`:
- `spring-boot-starter-webflux`
- `spring-boot-starter-actuator`
- `spring-boot-starter-validation`
- `spring-kafka`
- `spring-security-oauth2-jose` (for JWT verification) or `spring-boot-starter-oauth2-resource-server`
- depend on `im-contracts`

**Step 5: Add smoke tests**

Create:
- `backend/im-core/src/test/java/.../ImCoreApplicationTest.java`
- `backend/im-realtime/src/test/java/.../ImRealtimeApplicationTest.java`

Example (same pattern for both):

```java
@SpringBootTest
class ImCoreApplicationTest {
  @Test void contextLoads() {}
}
```

**Step 6: Verify builds**

Run:
- `mvn -f backend/pom.xml -pl im-contracts test`
- `mvn -f backend/pom.xml -pl im-core test`
- `mvn -f backend/pom.xml -pl im-realtime test`

Expected: PASS.

---

## Task 2: Define Kafka topics + command/event DTOs in `im-contracts`

**Files:**
- Create: `backend/im-contracts/src/main/java/.../ImTopics.java`
- Create: `backend/im-contracts/src/main/java/.../command/SendPrivateTextCommandV1.java`
- Create: `backend/im-contracts/src/main/java/.../command/SendRoomTextCommandV1.java`
- Create: `backend/im-contracts/src/main/java/.../event/PrivateMessagePersistedEventV1.java`
- Create: `backend/im-contracts/src/main/java/.../event/RoomMessagePersistedEventV1.java`
- Create: `backend/im-contracts/src/main/java/.../event/RoomMemberChangedEventV1.java`

**Step 1: Topics**

Create `ImTopics` constants:

```java
public final class ImTopics {
  public static final String COMMAND_PRIVATE_TEXT_V1 = "im.command.private_text.v1";
  public static final String COMMAND_ROOM_TEXT_V1 = "im.command.room_text.v1";
  public static final String EVENT_PRIVATE_PERSISTED_V1 = "im.event.private_persisted.v1";
  public static final String EVENT_ROOM_PERSISTED_V1 = "im.event.room_persisted.v1";
  public static final String EVENT_ROOM_MEMBER_CHANGED_V1 = "im.event.room_member_changed.v1";
}
```

**Step 2: Command schemas**

Commands must include:
- `requestId` (for tracing)
- `clientMsgId` (idempotency)
- `fromUserId` (derived from JWT in `im-realtime`)
- private: `toUserId`, `conversationId`
- room: `roomId`
- `content` (text)
- `createdAt` (client time optional; server time is authoritative)

**Step 3: Event schemas**

Events must include:
- `eventId` (unique id for dedup if needed)
- `messageId`
- `seq`
- private: `conversationId`, `fromUserId`, `toUserId`, `content`
- room: `roomId`, `fromUserId`, **no content**
- timestamps: `createdAt` (server time)

**Step 4: Serialize as JSON**

Keep DTOs as simple POJOs / records; ensure Jackson can serialize.

**Step 4.1: ConversationId derivation (consistency)**

To stay compatible with the existing message-domain convention, derive:

- `conversationId = min(fromUserId, toUserId) + "_" + max(fromUserId, toUserId)`

**Step 5: Unit test for JSON compatibility**

Add a minimal test:
- serialize + deserialize one command and one event.

Run:
- `mvn -f backend/pom.xml -pl im-contracts test`

---

## Task 3: MySQL schema for `im-core` (separate DB recommended)

**Files:**
- Modify: `deploy/mysql-init/001_create_databases.sh`
- Create: `deploy/mysql-init/031_schema_im_core.sql`

**Step 1: Add `im_core` database + user**

Extend `001_create_databases.sh` to create:
- database: `im_core`
- user: `im_core` (password via env, e.g. `IM_MYSQL_PASSWORD`)
- grant CRUD only on `im_core.*`

**Step 2: Create schema**

Add `031_schema_im_core.sql` with tables:
- `im_room`, `im_room_member`, `im_room_message`, `im_room_read_state`
- `im_conversation`, `im_private_message`, `im_conversation_read_state`

Indexes:
- `im_room_message(room_id, seq)` (unique)
- `im_private_message(conversation_id, seq)` (unique)
- idempotency unique keys as described in the design doc

**Step 3: Verify local initialization**

If using docker-compose for local dev:
- `docker compose -f deploy/docker-compose.yml up -d mysql`
- verify the DB exists and tables created.

---

## Task 4: `im-core` — DB access layer (read/write + seq allocation)

**Files:**
- Create: `backend/im-core/src/main/java/.../db/*` (DAO + mapper)
- Create: `backend/im-core/src/main/java/.../service/RoomMessageService.java`
- Create: `backend/im-core/src/main/java/.../service/PrivateMessageService.java`
- Test: `backend/im-core/src/test/java/.../service/*Test.java`

**Step 1: Implement seq allocation**

Create a small component `SeqAllocator` with:
- `nextRoomSeq(roomId)`
- `nextConversationSeq(conversationId)`

Implement using `LAST_INSERT_ID(last_seq + 1)` pattern.

**Step 2: Implement inserts with idempotency**

For each send:
- check unique key constraint on `(scope, from, clientMsgId)`
- on duplicate, load existing `(messageId, seq)` and return

**Step 3: Unit tests (no Kafka yet)**

Using H2 (or Testcontainers MySQL if available):
- `nextRoomSeq` increments
- insert with same `clientMsgId` is idempotent
- read-watermark updates do not decrease (`max(last_read_seq, incoming)`)

Run:
- `mvn -f backend/pom.xml -pl im-core test`

---

## Task 5: `im-core` — Kafka consumers (commands) + producers (persisted events)

**Files:**
- Create: `backend/im-core/src/main/java/.../kafka/KafkaConfig.java`
- Create: `backend/im-core/src/main/java/.../kafka/CommandConsumers.java`
- Create: `backend/im-core/src/main/java/.../kafka/EventProducer.java`
- Test: `backend/im-core/src/test/java/.../kafka/*Test.java`

**Step 1: Add `spring.kafka.*` config**

In `backend/im-core/src/main/resources/application.yml`:
- bootstrap servers
- consumer group ids
- JSON serializers/deserializers

**Step 2: Implement command listeners**

`@KafkaListener(topics = ImTopics.COMMAND_..., groupId = "...")`
- validate membership / permissions
- call service to persist
- publish persisted event

**Step 3: Ensure ordering**

Use keying:
- `roomId` for room commands/events
- `conversationId` for private commands/events

**Step 4: Integration smoke test**

If Testcontainers Kafka is too heavy, add a “wiring test” that boots context and asserts `KafkaTemplate` bean exists.

---

## Task 6: `im-core` — HTTP APIs (history + unread + read mark + internal bootstrap)

**Files:**
- Create: `backend/im-core/src/main/java/.../api/RoomController.java`
- Create: `backend/im-core/src/main/java/.../api/ConversationController.java`
- Create: `backend/im-core/src/main/java/.../api/UnreadController.java`
- Create: `backend/im-core/src/main/java/.../api/InternalRealtimeBootstrapController.java`
- Test: `backend/im-core/src/test/java/.../api/*Test.java`

**Step 1: History endpoints**

- `GET /api/im/rooms/{roomId}/messages?afterSeq&limit`
- `GET /api/im/conversations/{conversationId}/messages?afterSeq&limit`

Return items contain:
- ids, seq, from/to, createdAt
- **room returns content** (pull path)

**Step 2: Unread summary**

- `GET /api/im/unread/summary`
- compute:
  - room unread via `room.last_seq - room_read_state.last_read_seq`
  - private unread similarly

**Step 3: Mark read**

- `POST /api/im/rooms/{roomId}/read`
- `POST /api/im/conversations/{conversationId}/read`
- update watermark with `max(existing, incoming)`

**Step 4: Internal bootstrap**

- `GET /internal/im/realtime/users/{userId}/rooms?cursor&limit`
- returns paged room ids the user belongs to

---

## Task 7: `im-realtime` — JWT verification + WS endpoint skeleton

**Files:**
- Create: `backend/im-realtime/src/main/java/.../ws/ImWebSocketHandler.java`
- Create: `backend/im-realtime/src/main/java/.../ws/WsProtocol.java` (message types)
- Create: `backend/im-realtime/src/main/java/.../security/JwtVerifier.java`
- Test: `backend/im-realtime/src/test/java/.../security/JwtVerifierTest.java`

**Step 1: Implement `auth` first-message protocol**

If auth fails:
- send `auth_error` and close.

If auth success:
- store `userId` in session context and register connection.

**Step 2: Verify JWT with HS256 secret**

Use Nimbus APIs (via Spring Security jose) to validate and extract:
- JWT `sub` as `userId` (existing auth module encodes user id into `sub`)

---

## Task 8: `im-realtime` — connection registry + room membership bootstrap

**Files:**
- Create: `backend/im-realtime/src/main/java/.../presence/ConnectionRegistry.java`
- Create: `backend/im-realtime/src/main/java/.../presence/RoomLocalIndex.java`
- Create: `backend/im-realtime/src/main/java/.../client/ImCoreClient.java`
- Test: `backend/im-realtime/src/test/java/.../presence/*Test.java`

**Step 1: Register connection by userId**

- map `userId -> [connections...]`
- support multi-device

**Step 2: Bootstrap rooms**

After auth:
- call `im-core` internal bootstrap endpoint (paged)
- add connection into `roomId -> connections` local index

**Step 3: Handle disconnect**

On close:
- remove connection from all indices

---

## Task 9: `im-realtime` — Kafka event consumers + push logic

**Files:**
- Create: `backend/im-realtime/src/main/java/.../kafka/EventConsumers.java`
- Create: `backend/im-realtime/src/main/java/.../push/PrivatePushService.java`
- Create: `backend/im-realtime/src/main/java/.../push/RoomUpdateCoalescer.java`
- Create: `backend/im-realtime/src/main/java/.../push/WsSender.java`
- Test: `backend/im-realtime/src/test/java/.../push/RoomUpdateCoalescerTest.java`

**Step 1: Private persisted events**

On `PrivateMessagePersistedEventV1`:
- push to `toUserId` connections (content included)
- also push to `fromUserId` connections as “server echo”

**Step 2: Room persisted events (state only)**

On `RoomMessagePersistedEventV1`:
- for each local connection in that room:
  - coalesce update into `(conn, roomId) -> lastSeq`

**Step 3: Coalesced flush (50–200ms)**

Timer loop:
- for each connection with pending rooms:
  - send one `roomUpdatedBatch`

**Step 4: Backpressure**

If WS write fails or is slow:
- close connection
- rely on pull path

Unit test `RoomUpdateCoalescer`:
- multiple updates collapse to latest
- flush creates a batch payload with latest seq per room

---

## Task 10: `im-realtime` — inbound WS send handling (produce commands)

**Files:**
- Modify: `backend/im-realtime/src/main/java/.../ws/ImWebSocketHandler.java`
- Create: `backend/im-realtime/src/main/java/.../kafka/CommandProducer.java`
- Test: `backend/im-realtime/src/test/java/.../ws/SendValidationTest.java`

**Step 1: Parse and validate**

- text-only
- max bytes/length
- require `clientMsgId`

**Step 2: Produce Kafka command**

- private → `im.command.private_text.v1`
- room → `im.command.room_text.v1`

**Step 3: Sender ack**

Because delivery is event-driven, sender gets the persisted echo from events as the definitive ack.

---

## Task 11: Deployment (docker compose + kafka-init topics)

**Files:**
- Modify: `deploy/docker-compose.yml`

**Step 1: Add services**

- `im-core` service:
  - build context `../backend`, MODULE `im-core`
  - env: DB url/user/pass, kafka bootstrap, JWT secret
  - port expose for local dev (e.g. 18082)

- `im-realtime` service:
  - build context `../backend`, MODULE `im-realtime`
  - env: kafka bootstrap, JWT secret, `im-core` base url
  - port expose for WS (e.g. 18081)

**Step 2: Create Kafka topics**

Extend `kafka-init` to create the IM topics.

**Step 3: Local smoke**

- `docker compose -f deploy/docker-compose.yml up -d --build im-core im-realtime`
- Verify:
  - `GET /actuator/health` for both

---

## Task 12: Frontend integration (minimal)

**Files:**
- Create: `frontend/src/im/wsClient.ts`
- Modify: `frontend/src/stores/*` (auth token access + realtime lifecycle)
- Modify: `frontend/src/views/ConversationDetailView.vue`
- (Optional) Create: `frontend/src/views/RoomsView.vue`

**Step 1: Connect + auth**

- On login success, connect WS and send `{type:"auth", accessToken}`

**Step 2: Private push**

- Handle `privateMessage` events and update current conversation UI.

**Step 3: Room state push**

- Handle `roomUpdatedBatch` and update unread indicators for rooms.
- When opening a room, pull messages via `im-core` HTTP.

---

## Task 13: Verification checklist

Run locally:
- Maven unit tests:
  - `mvn -f backend/pom.xml test`
- Docker compose smoke:
  - `docker compose -f deploy/docker-compose.yml up -d --build`
  - connect with a browser and verify WS messages

Expected:
- 1v1 message content appears in realtime.
- Room receives state updates in realtime (no content).
- After disconnect/reconnect, unread/history is correct via pull.

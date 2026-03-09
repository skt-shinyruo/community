# IM Realtime (im-realtime / im-core) — Design

**Date:** 2026-03-09  
**Status:** Approved  

## Goal

Introduce a “pure IM” realtime capability that supports:

- **WebSocket long connections** (target: **100k concurrent online users**)
- **1v1 private chat**: message **content** is pushed in realtime (“秒到”)
- **Group chat / rooms**: each room supports up to **10,000 members**
  - Realtime push is **state-only** (no message content):
    - “room has new message(s) / lastSeq advanced”
  - Message content is retrieved via **HTTP pull** (history/backfill)
- Full persistence for private + room messages, supporting **history** + **unread**

Hard constraints (explicit):

- The realtime service must be named **`im-realtime`** (do **not** call it “gateway”).
- Do **not** use Redis Pub/Sub; the event backplane must be **Kafka**.
- IM must be **as separated from `community-app` as possible** so that traffic spikes do not mutually impact.

## Non-Goals

- At-least-once delivery to clients (WS push is **best-effort**; disconnect/reconnect + pull is the correctness path).
- Typing indicators / presence / message recall / reactions.
- Media messages (peak is text-only; attachments can be a future iteration).

## Architecture (Scheme 2 — Confirmed)

### Services

1) **`im-realtime`** (WS access layer)
- Responsibilities:
  - WebSocket connection lifecycle
  - JWT authentication
  - Send-side validation (size/format), coarse rate protection
  - Backpressure + coalescing for high fanout rooms
  - Consume persisted events and push to locally connected users
- Stateless for business data (no IM SSOT in-process; only ephemeral connection/mapping state)

2) **`im-core`** (IM SSOT)
- Responsibilities:
  - Validate permissions (room membership, private conversation)
  - Persist messages and assign sequencing (`seq`)
  - Maintain unread/read-watermarks
  - Provide HTTP APIs for history/backfill and read marking
  - Emit “persisted” events to Kafka

3) **Kafka**
- Command + Event topics
- Decouples “client send” from “DB persist” from “push delivery”

### Data Stores

- `im-core` owns its **dedicated MySQL schema** (recommended: dedicated database + dedicated user).
  - Local dev can use the same MySQL container but as a separate database (e.g. `im_core`) to keep ownership clear.
- `community-app` remains unchanged and does not participate in realtime delivery.

## Kafka Design

### Topic Types

**Commands** (`im-realtime` → `im-core`)
- `im.command.private_text.v1` (key: `conversationId`)
- `im.command.room_text.v1` (key: `roomId`)

**Events** (`im-core` → `im-realtime`)
- `im.event.private_persisted.v1` (key: `conversationId`)
- `im.event.room_persisted.v1` (key: `roomId`, no content)
- `im.event.room_member_changed.v1` (key: `roomId`)

### Consumer Strategy (Phase 1: broadcast events)

- `im-core` consumes command topics with a **single consumer group**.
- **Each `im-realtime` instance uses its own consumer group** for event topics.
  - Effect: each instance receives every persisted event, then pushes only to local connections.
  - Trade-off: Kafka read amplification, but simplest and robust for the first milestone.

> Future Phase 2 can introduce an `im-dispatch` service to avoid full broadcast once cluster size grows.

### Ordering & Partitioning

- Room ordering uses `(roomId, seq)`; keep room events ordered by setting **topic key = roomId**.
- Conversation ordering uses `(conversationId, seq)`; keep ordered by setting **topic key = conversationId**.

### Retention

- Commands: short retention (minutes to hours), enough for transient outages.
- Events: short retention (minutes), as clients do not rely on events for correctness (they pull from DB).

## Storage Model (im-core)

### ID / Sequencing

- Each private conversation and each room has a **monotonic `seq`**.
- Unread is computed from `last_seq - last_read_seq`, avoiding `(message × user)` state.

### Tables (proposed)

**Rooms**

- `im_room`
  - `room_id` (bigint PK)
  - `last_seq` (bigint, default 0)
  - metadata fields (name, created_at, ...)
- `im_room_member`
  - `(room_id, user_id)` unique
  - role/joined_at
- `im_room_message`
  - `(room_id, seq)` unique
  - `message_id` (globally unique bigint/ULID; primary key optional)
  - `from_user_id`
  - `content` (MEDIUMTEXT recommended)
  - `client_msg_id` (for idempotency; unique with `from_user_id + room_id`)
  - `created_at`
- `im_room_read_state`
  - `(room_id, user_id)` unique
  - `last_read_seq`
  - `updated_at`

Unread for rooms:
- `unread = im_room.last_seq - im_room_read_state.last_read_seq`

**Private (1v1)**

- `im_conversation`
  - `conversation_id` (string or bigint; stable derived id)
  - `user_a`, `user_b`
  - `last_seq`
- `im_private_message`
  - `(conversation_id, seq)` unique
  - `message_id` unique
  - `from_user_id`, `to_user_id`
  - `content` (MEDIUMTEXT recommended)
  - `client_msg_id` (unique with `from_user_id + conversation_id`)
  - `created_at`
- `im_conversation_read_state`
  - `(conversation_id, user_id)` unique
  - `last_read_seq`
  - `updated_at`

### Seq Allocation (room / conversation)

To avoid “select-for-update then increment” patterns that create extra round trips, use an atomic increment:

- `UPDATE im_room SET last_seq = LAST_INSERT_ID(last_seq + 1) WHERE room_id = ?;`
- `SELECT LAST_INSERT_ID();` → returns new `seq` for this connection

Same for `im_conversation`.

This still serializes per room/conversation (by design) but is predictable and safe.

### Idempotency (clientMsgId)

Each send command includes `clientMsgId` (client-generated unique id).

Enforce uniqueness:
- Room: unique `(room_id, from_user_id, client_msg_id)`
- Private: unique `(conversation_id, from_user_id, client_msg_id)`

If duplicated, return existing persisted `message_id/seq` (or treat as success without double insert).

## Realtime Protocol (im-realtime)

### Authentication

- WS connection is established without assuming `Authorization` header works in all browsers.
- First message must be:
  - `{"type":"auth","accessToken":"<jwt>"}`
- `im-realtime` validates JWT using the same HS256 secret (`JWT_HMAC_SECRET`).
- User identity source-of-truth:
  - use JWT `sub` (subject) as `userId` (the existing auth module encodes userId into `sub`).

### Client → Server (minimum set)

- `auth`
- `sendPrivateText`:
  - `{type, clientMsgId, toUserId, content}`
- `sendRoomText`:
  - `{type, clientMsgId, roomId, content}`
- `markRoomRead`:
  - `{type, roomId, lastReadSeq}`
- `markPrivateRead`:
  - `{type, conversationId, lastReadSeq}`
- `ping`

### Server → Client

**Private**
- `privateMessage` (push content):
  - `{type, conversationId, seq, messageId, fromUserId, toUserId, content|contentPreview, createdAt}`
- Sender also receives the persisted message as the definitive “server echo” (multi-device sync).

**Room (state only, no content)**
- `roomUpdated` (single room)
- `roomUpdatedBatch` (recommended; reduce WS frames)
  - Items contain:
    - `roomId, lastSeq, lastMessageId, fromUserId?, createdAt?`
  - No `content`

**Control**
- `resyncRequired` (slow consumer / server overload / protocol mismatch)
- `error` (validation failures, permission denied, server busy)

### Room Membership Mapping (for push fanout)

Because rooms push to all online members even when not “opened”, `im-realtime` must know:

- For a connected `userId`, which `roomId`s they belong to.

Bootstrap flow:
- After auth, `im-realtime` calls an internal `im-core` endpoint to fetch the user’s room membership list (paged).
- `im-realtime` registers this connection into an in-memory index:
  - `roomId -> {localConnectionIds...}`

Membership change flow:
- `im-core` emits `im.event.room_member_changed.v1`
- Each `im-realtime` instance updates in-memory indices for locally connected users.

## Backpressure & Coalescing (critical for “no msg/s limit”)

### Why needed

Even if the business does not set a message-per-second cap, the system must protect itself against:

- Slow clients (mobile background, poor network)
- Browser event loop stalls
- Peak room activity

Without backpressure, per-connection outbound queues grow unbounded and lead to OOM or tail-latency collapse.

### RoomUpdated coalescing (confirmed)

For room events:

- Maintain **only the latest `(roomId -> lastSeq)`** per connection between flush ticks.
- Flush interval: **50–200ms configurable**.
- Each flush emits at most 1 batch frame per connection (`roomUpdatedBatch`).

Effect:
- Even if a room produces extremely high message rates, each client receives bounded “state updates”, while correctness relies on pull.

### Slow consumer policy (hard rule)

When a connection is persistently backpressured:
- Close the connection.
- Client must reconnect and pull from `im-core` using `lastReadSeq` watermarks.

This is required to keep overall system healthy.

### Send-side overload protection

Even with “no business rate limit”, enforce:
- `maxMessageBytes` (text-only; reject oversized)
- `maxInflightSendsPerConnection` (avoid unbounded client burst)
- If Kafka is unavailable / `im-core` lag is critical: return `SERVER_BUSY` for sends

## Client Pull APIs (im-core)

Minimum endpoints:

- Private history:
  - `GET /api/im/conversations/{conversationId}/messages?afterSeq=...&limit=...`
- Room history:
  - `GET /api/im/rooms/{roomId}/messages?afterSeq=...&limit=...`
- Unread summary:
  - `GET /api/im/unread/summary` (rooms + private)
- Mark read:
  - `POST /api/im/rooms/{roomId}/read` (body: `lastReadSeq`)
  - `POST /api/im/conversations/{conversationId}/read` (body: `lastReadSeq`)

Internal endpoint for realtime bootstrap:
- `GET /internal/im/realtime/users/{userId}/rooms?cursor=...&limit=...`

## Observability (must-have)

`im-realtime`:
- Active connections, auth failures
- WS write latency, disconnect reasons (slow consumer)
- RoomUpdated flush counts, batch sizes
- Kafka consumer lag (events), processing latency

`im-core`:
- Kafka consumer lag (commands)
- DB write latency, transaction retries/conflicts
- Persisted event publish latency

Kafka:
- Topic throughput and lag

## Phase 2 (optional evolution)

If `im-realtime` cluster grows and broadcast becomes too costly:

- Add `im-dispatch`:
  - Consumes room persisted events once
  - Routes to the subset of `im-realtime` nodes that currently host online members of that room
  - Achieves “per-node fanout” without “per-user Kafka message explosion”

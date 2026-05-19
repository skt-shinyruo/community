# IM Room Fanout Routing Design

Date: 2026-05-19

## Status

Implemented through routed owner mode with a conservative rollout default.

The current code includes routing primitives, Redis-backed distributed room presence, an internal HTTP routed fanout target transport, `legacy|shadow|routed` owner mode wiring, configuration defaults, Nacos binding coverage, metrics, and focused tests. Runtime remains `legacy` by default so production can enable Redis presence and shadow validation before switching room persisted events to the shared owner group.

## Context

`im-realtime` currently consumes `RoomMessagePersistedEvent` from `im.event.room-persisted` on every realtime worker because the default consumer group includes the worker id:

```text
im-realtime-${IM_REALTIME_WORKER_ID}
```

Each worker receives every room persisted event, calls `RoomFanoutCoalescer.markRoomUpdated(roomId, seq)`, then `RoomFanoutCoalescer` checks `RoomLocalIndex` and only pushes to local websocket connections. This avoids cross-worker routing, but the Kafka consumption and per-room local lookup cost grows linearly with realtime worker count.

The current related components are:

- `EventConsumers`: Kafka listener for private, room, send-result, membership, and policy events.
- `RoomFanoutCoalescer`: room-level local coalescing. It keeps latest seq per room and fans out to connection ids in `RoomLocalIndex`.
- `RoomUpdateCoalescer`: connection-level batching. It emits one `roomUpdatedBatch` frame per connection per flush.
- `RoomLocalIndex`: in-process room id to connection id index.
- `ConnectionRegistry`: in-process connection id and user id registry.
- `community-im-gateway`: selects a websocket worker with `RendezvousWorkerSelector`, binds the chosen worker id into the session ticket, then routes the first websocket `connect` frame to that worker.

Changing only the room listener to a shared consumer group is unsafe. Kafka would deliver each room event to exactly one arbitrary worker, but that worker may not hold any local connections for the room. The event would be dropped by `RoomLocalIndex`, producing missed room notifications.

## Goals

- Make room persisted event consumption bounded by room partitions and active room workers, not total realtime workers.
- Preserve local websocket ownership: a room update is pushed only by the worker that owns the target connection.
- Keep client protocol unchanged: clients still receive `roomUpdatedBatch` and pull message history by seq.
- Keep existing gateway worker selection and ticket binding intact.
- Support high concurrency with room-level coalescing before worker routing and connection-level coalescing on the target worker.
- Provide migration steps that can run in shadow mode before switching production traffic.
- Add tests for no duplicate routing, no missing routed worker, and bounded fanout cost when worker count increases.

## Non-Goals

- Do not treat a single shared Kafka consumer group plus local filtering as the final design.
- Do not route all room traffic through `community-im-gateway`; gateway remains connection bootstrap and websocket bridge, not the room event fanout plane.
- Do not move IM room message storage out of `im-core`.
- Do not change websocket frame contracts in this design.
- Do not require existing clients to reconnect during the first migration step.

## Selected Architecture

Use a three-stage fanout plane:

```text
im-core outbox
  -> im.event.room-persisted keyed by roomId
  -> room fanout owner consumer group
  -> room presence directory: roomId -> active workerIds
  -> routed worker fanout command
  -> target realtime worker local RoomFanoutCoalescer
  -> target worker RoomUpdateCoalescer
  -> websocket connections
```

### Stage 1: Room Fanout Owner

Realtime workers join one shared room fanout owner consumer group for `im.event.room-persisted`. Kafka partitions are keyed by `roomId`, so one owner handles a room's persisted events at a time. The owner coalesces by room before routing and keeps only the latest seq per flush window.

This changes the expensive first hop from:

```text
roomEvents * realtimeWorkers
```

to:

```text
roomEvents * ownerConsumerConcurrency
```

The owner may be any realtime worker. It does not need to hold websocket connections for the room.

### Stage 2: Presence Routing

Each realtime worker publishes local room presence into a distributed directory when its first local connection joins a room, refreshes that membership while local connections remain, and removes it when the last local connection leaves.

The directory shape is:

```text
roomId -> set(workerId)
workerId + roomId -> liveness ttl
```

The directory must be distributed. Redis is the preferred first backend because the project already deploys Redis and it supports TTL cleanup for crashed workers. Stale set members are filtered by the per worker-room liveness key during route lookup.

`RoomLocalIndex` remains the local connection index. Distributed presence records only worker ownership for active local room connections, not user ids or connection ids.

### Stage 3: Worker Fanout Command

The room owner sends one routed fanout command per active target worker:

```text
targetWorkerId, roomId, lastSeq, sourceEventId, createdAtEpochMs
```

The target worker validates that `targetWorkerId` equals its local worker id, then calls local `RoomFanoutCoalescer.markRoomUpdated(roomId, lastSeq)`. The existing `RoomUpdateCoalescer` still batches by connection and sends `roomUpdatedBatch`.

The recommended durable long-term transport is a fixed-shard worker inbox topic:

```text
im.event.room-fanout-routed
```

Workers consume only their assigned inbox shard or static worker slot. The owner publishes to the target worker slot. This avoids every worker consuming every routed command.

The implemented first transport is an internal HTTP command endpoint:

```text
POST /internal/im/realtime/fanout/room
```

It is protected with `SCOPE_im.realtime.internal`, resolves target worker endpoints from discovery metadata, short-circuits local self-dispatch, validates `targetWorkerId` on the receiver, and calls only the receiver's local `RoomFanoutCoalescer`. A later step can replace this transport with the fixed-shard Kafka inbox without changing planner, presence, target validation, or local fanout semantics.

## Why Not The Alternatives

### Shared Consumer Group Only

This bounds Kafka consumption but loses messages. The consumer that receives the event may not own any local connections for that room.

### Keep Per-Worker Groups And Optimize Local Filtering

This preserves correctness but keeps the current linear cost. It does not meet the high-concurrency target.

### Route By Sender User's Gateway Worker

Gateway worker selection is user-affine. Room members are distributed across many workers, so sender affinity cannot deliver to all room members.

### One Topic Per Worker

This is easy to reason about but operationally poor at high worker counts. Topic churn and metadata pressure grow with workers. Use fixed shards or worker slots instead.

## Data Flow

### Connect

1. Client calls `POST /api/im/sessions`.
2. `community-im-gateway` selects a worker using `RendezvousWorkerSelector`.
3. Gateway returns a ticket bound to `workerId`.
4. Client connects to `/ws/im`.
5. Gateway reads the first `connect` frame, decodes the ticket, and bridges to the selected worker.
6. Worker validates the ticket, binds `WsConnection`, loads existing rooms from `MembershipProjectionService`, updates `RoomLocalIndex`, and publishes local room presence for each active room.

### Membership Change

1. Realtime workers still consume `RoomMemberChanged` as projection events.
2. If a locally connected user joins a room, the worker updates `RoomLocalIndex` and distributed room presence.
3. If a locally connected user leaves a room, the worker removes that local connection. When the local room becomes empty, it removes distributed room presence.

### Room Message Persisted

1. `im-core` writes the message and enqueues `RoomMessagePersistedEvent` keyed by `roomId`.
2. One room fanout owner consumes the event from the shared owner group.
3. Owner coalesces latest seq per room.
4. Owner reads active worker ids for the room from distributed presence.
5. Owner emits one routed command per active worker id.
6. Target worker calls local `RoomFanoutCoalescer`.
7. Target worker sends one batched notification per connection through `RoomUpdateCoalescer`.

## Failure Semantics

- Message storage remains authoritative in `im-core`; websocket fanout is a notification plane.
- Duplicate routed commands are allowed. Target coalescing and `WsConnection.markRoomSeq` keep max seq per room, so duplicates do not duplicate user-visible history.
- Missing a transient notification is recoverable if the client pulls by seq after reconnect, ping, or any later `roomUpdatedBatch`. The migration plan should add explicit client catch-up checks if not already present.
- Worker crash is handled by presence TTL expiry. Until TTL expires, owners may route to a dead worker. Durable worker inbox transport can retry; the implemented HTTP transport records route-failure metrics and continues dispatching to the other active target workers in the same flush.
- Redis outage in routed mode must fail closed for new routed owner decisions. The system should keep legacy mode available as rollback.
- Rebalance of the owner group may process the same room event twice. This is acceptable because routed commands are idempotent by room seq.

## Scaling Model

Let:

- `W` = realtime worker count
- `A(room)` = number of workers with at least one active local connection for the room
- `P` = room persisted topic partitions

Current cost per room event:

```text
O(W) Kafka deliveries + O(W) local room index checks
```

Target cost per room event:

```text
O(1) owner delivery + O(A(room)) routed worker commands
```

Adding idle workers does not increase fanout work for an existing room. Adding workers that actually hold connections for that room increases only the target worker command count, which is necessary work.

For very large public rooms, `A(room)` may approach `W`. That is unavoidable if every worker owns connections for the room, but the design still avoids duplicate room persisted consumption and keeps per-worker fanout local and coalesced.

## Migration Plan And Current State

1. Done: add routing primitives and tests:
   - route planner,
   - local presence update wrapper,
   - distributed presence interface,
   - route command model,
   - test fixtures for cost and duplicate behavior.
2. Done: add Redis-backed room presence directory:
   - first local connection activates `roomId -> workerId`,
   - last local connection removes it,
   - heartbeat refreshes worker-room liveness keys,
   - route lookup filters stale members.
3. Done: add routed fanout target endpoint and internal HTTP transport:
   - protected by `SCOPE_im.realtime.internal`,
   - validates `targetWorkerId`,
   - requires and de-duplicates `sourceEventId` per target worker with a bounded in-memory recent-event set,
   - invokes local `RoomFanoutCoalescer`.
4. Done: harden routed rollout reliability:
   - `routed` startup fails fast when the bound `RoomPresenceDirectory` is `NoopRoomPresenceDirectory`,
   - owner route planning failures keep the latest room update pending for the next flush,
   - owner target dispatch failures do not block other workers and keep the latest room update pending for retry,
   - target idempotency turns retried commands for already-successful workers into no-op `202 Accepted` responses.
5. Ready to run in deployment: enable shadow mode:
   - keep legacy per-worker room persisted consumers,
   - compute routed target sets and metrics,
   - compare routed target worker count with local fanout observations.
6. Ready after shadow validation: switch room persisted listener to owner mode:
   - set room fanout mode to `routed`,
   - set room persisted listener group to shared owner group,
   - keep rollback property to restore legacy per-worker group.
7. Optional optimization: replace HTTP transport with fixed-shard Kafka worker inbox if load tests show HTTP owner fanout is the bottleneck:
   - assign stable worker inbox slots through discovery metadata,
   - publish routed commands to target slot,
   - workers manually consume assigned slots, not all slots.

## Implemented Runtime Controls

- `im.room-presence.enabled=false` by default. Set it to `true` only after Redis is available to every realtime worker. `routed` mode requires a Redis-backed distributed `RoomPresenceDirectory`; startup fails fast if `routed` is combined with the noop presence directory.
- `im.room-fanout.mode=legacy` by default. This remains an explicit rollout gate. `legacy` keeps per-worker room persisted fanout. `shadow` keeps legacy delivery and computes owner routes without dispatching target commands. `routed` disables legacy room persisted consumption and dispatches through the shared owner group.
- `im.room-fanout.owner-group-id=im-realtime-room-fanout-owner` bounds the original room persisted event consumption by the owner group instead of realtime worker count.
- `im.room-fanout.target-path=/internal/im/realtime/fanout/room` controls the internal HTTP target endpoint.
- `im.room-fanout.worker-directory-cache-ttl` bounds discovery lookups while allowing worker topology changes to converge.

## Routed Reliability Semantics

- The routed owner keeps only the latest pending update per room. If route planning throws or any target dispatch fails, the owner records `routeFailed`, finishes attempting the remaining target workers for that room, and requeues the room's latest pending update for a later owner flush.
- Empty target sets are not retried. They mean distributed presence currently reports no active worker for the room.
- HTTP target dispatch is at-least-once while the owner process is alive. A process crash can lose the in-memory pending retry; this is the intentionally minimal reliability step before a durable outbox or worker inbox.
- Target workers require non-blank `sourceEventId` and keep a bounded in-memory recent-event set. A repeated `sourceEventId` returns a duplicate result to the service layer and the controller still responds `202 Accepted`, without re-triggering local fanout. Room update payloads remain state-only and clients still use room message `seq` / history backfill for end-to-end duplicate tolerance.

## Rollout Sequence

1. Deploy this code with defaults (`room-presence.enabled=false`, `room-fanout.mode=legacy`).
2. Enable `im.room-presence.enabled=true` and verify Redis room-worker keys, TTL refresh, and stale member cleanup. Do not set `room-fanout.mode=routed` until this is live on every realtime worker.
3. Set `im.room-fanout.mode=shadow`; verify owner metrics show bounded owner consumption and planned route counts match rooms with active local workers.
4. Set `im.room-fanout.mode=routed`; verify room persisted consumption is owned by `im-realtime-room-fanout-owner` and target command count follows `A(room)`, not total realtime workers.
5. Roll back by setting `im.room-fanout.mode=legacy`. Keep Redis presence enabled or disabled independently.

## Test Strategy

- Unit-test `RoomFanoutPlanner`:
  - duplicate worker ids produce one route,
  - blank/null worker ids are ignored,
  - every active worker is included,
  - adding unrelated workers without room presence does not change route count.
- Unit-test local presence:
  - first local connection activates room-worker presence once,
  - additional local connections do not duplicate activation,
  - removing one of several local connections does not deactivate,
  - removing the last connection deactivates.
- Unit-test Redis presence:
  - activation writes room worker set membership and a TTL liveness key,
  - lookup filters dead workers and removes stale set members,
  - deactivation removes both set member and liveness key.
- Unit-test owner routing:
   - latest seq is routed once per active worker,
   - no route is emitted when no worker has room presence,
   - shadow mode computes routes without target dispatch,
   - one target failure does not block other target workers,
   - dispatch and route planning failures keep the latest room update pending for retry.
- Unit-test target handling:
   - matching `targetWorkerId` calls local fanout,
   - wrong target is rejected,
   - invalid seq and blank `sourceEventId` are rejected,
   - duplicate `sourceEventId` is accepted as a no-op and does not trigger local fanout again.
- Unit-test worker discovery:
   - discovery metadata builds target endpoint URIs,
   - duplicate worker ids fail closed.
- Annotation/config tests:
   - legacy room persisted listener uses the worker-local default group only in `legacy|shadow`,
   - owner room persisted listener uses the shared owner group only in `shadow|routed`,
   - `routed` fails fast when the presence directory is noop and starts when Redis-backed presence is bound,
   - Nacos/default config binds the room presence and fanout controls.
- Security integration test:
  - `/internal/im/realtime/fanout/room` requires `SCOPE_im.realtime.internal`.
- Keep existing `RoomUpdateCoalescerTest` to verify connection-level batching.

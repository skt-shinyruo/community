# Community IM Gateway Edge Design

**Date:** 2026-05-03
**Status:** Draft for review
**Owner:** Codex

---

## 1. Goal

Introduce an independent IM edge deployable while keeping the external project entry unified.

The new deployable is `backend/community-im-gateway`. It owns IM access-layer concerns for session bootstrap and WebSocket bridging, but it remains behind the existing external entry. Browsers and clients still use the same public host and stable paths:

- `POST /api/im/sessions`
- `WS /ws/im`

This design removes the client-visible worker path `/ws/im/workers/{workerId}` from the primary contract and moves worker selection behind IM edge.

---

## 2. Confirmed Decisions

- Create a new `community-im-gateway` deployable.
- Keep `community-gateway` as the unified external entry.
- Route `POST /api/im/sessions` from `community-gateway` to `community-im-gateway`.
- Route external `WS /ws/im` from `community-gateway` to `community-im-gateway`.
- Return a stable `wsUrl` ending in `/ws/im` from session bootstrap.
- Keep `im-realtime` as an internal worker bound to `/internal/ws/im`.
- Continue using session tickets bound to `workerId`.
- Continue using rendezvous hashing for worker selection.
- Do not move IM persistence, history, unread, room membership authority, Kafka command handling, or online push business logic into `community-im-gateway`.
- Keep the first implementation pragmatic: duplicate or migrate only small edge mechanisms as needed, and defer a shared `im-edge-common` module until after the deployable split works.

---

## 3. Current Problems

The current IM edge path works, but the access boundary is still mixed:

- `community-gateway` owns the public WebSocket proxy path `/ws/im/workers/**`.
- `im-realtime` owns `POST /api/im/sessions`, session ticket creation, and worker selection.
- The client receives and connects to a worker-specific URL.
- The default gateway HTTP route for `/api/im/**` points at `im-core`, while the session bootstrap controller lives in `im-realtime`; this makes the intended bootstrap route easy to misconfigure.

The target is not a second public gateway. The target is an internal deployable split so IM long-connection concerns can be tuned, scaled, monitored, and rolled independently.

---

## 4. Scope And Non-Goals

### 4.1 In Scope

- New Spring Boot WebFlux deployable `community-im-gateway`.
- Session bootstrap endpoint:
  - `POST /api/im/sessions`
  - bearer token verification
  - worker discovery
  - rendezvous worker selection
  - session ticket signing
  - stable `wsUrl` assembly
- Public IM WebSocket edge:
  - external path `/ws/im`
  - first-frame `connect(ticket)` handling
  - ticket decode for routing
  - internal bridge to selected `im-realtime` worker
- Deployment updates for single and cluster compose topologies.
- Gateway route updates so the external entry still fronts all browser traffic.
- Frontend compatibility with stable `/ws/im`.
- Tests for routing, session bootstrap, worker selection, bridge behavior, and failure paths.

### 4.2 Non-Goals

- Do not introduce a separate public IM domain as the default client entry.
- Do not move IM message persistence or history APIs from `im-core`.
- Do not move Kafka command production or online fanout from `im-realtime`.
- Do not replace Kafka backplane behavior.
- Do not implement cross-region connection migration or live connection transfer.
- Do not make sticky session a correctness requirement.
- Do not create a broad shared module before the deployable boundary is proven.

---

## 5. Target Architecture

```text
Client
  -> unified external entry
    -> community-gateway
      -> /api/** except IM edge-specific routes
      -> /api/im/sessions -> community-im-gateway
      -> /ws/im           -> community-im-gateway
      -> /api/im/**       -> im-core

community-im-gateway
  -> discovers im-realtime-worker instances
  -> selects worker by userId
  -> signs session ticket
  -> bridges WS /ws/im to worker /internal/ws/im

im-realtime
  -> internal WS worker
  -> validates ticket workerId against local workerId
  -> owns online connection registry, protocol handling, Kafka command production, push

im-core
  -> owns messages, history, unread, sequence, idempotency, rooms, membership authority
```

`community-im-gateway` is an edge adapter, not a domain owner. It should keep packages named around edge responsibilities such as session, shard, ws, security, and config. It must not grow business service packages that compete with `im-core` or `im-realtime`.

---

## 6. External Contract

Session bootstrap remains:

```text
POST /api/im/sessions
Authorization: Bearer <access token>
```

The response remains `OpenImSessionResponse`:

```json
{
  "sessionId": "...",
  "wsUrl": "ws://<public-host>/ws/im",
  "ticket": "...",
  "expiresAtEpochMillis": 1770000000000
}
```

The client then opens:

```text
WS /ws/im
```

The first WebSocket frame is still:

```json
{"type":"connect","ticket":"..."}
```

The client no longer receives `/ws/im/workers/{workerId}` as the primary `wsUrl`, and the selected worker id is not part of the external session or WebSocket success payload. The `workerId` remains internal to the signed ticket, metrics, and logs.

---

## 7. Session Bootstrap Flow

```text
Client
  -> community-gateway
    -> community-im-gateway POST /api/im/sessions
      -> verify access token
      -> extract userId
      -> discover im-realtime-worker instances
      -> select worker by rendezvous hash(userId, workerId)
      -> create sessionId and expiry
      -> sign ticket(sessionId, userId, workerId, exp)
      -> return OpenImSessionResponse(wsUrl=/ws/im, ticket)
```

Worker discovery uses the same service and metadata shape as the current IM worker model:

- service id: `im-realtime-worker`
- metadata `workerId`
- metadata `wsPath`
- metadata `wsPort`

The edge should ignore incomplete worker metadata. If no valid worker is available, session bootstrap returns `503`.

---

## 8. WebSocket Bridge Flow

```text
Client
  -> community-gateway WS /ws/im
    -> community-im-gateway WS /ws/im
      -> wait for first text frame
      -> require type == connect
      -> decode ticket
      -> extract workerId
      -> resolve workerId to internal URI
      -> open internal WS to worker URI
      -> forward original connect(ticket) frame
      -> bidirectionally forward remaining frames
```

The first frame must be buffered only long enough to route and forward it. `community-im-gateway` should not parse or enforce private-message or room-message business payloads.

The existing `im-realtime` worker remains responsible for:

- ticket validity enforcement
- local `workerId` match enforcement
- connection registration
- membership and policy projection checks
- command acceptance and Kafka production
- online push and room update fanout

---

## 9. Error Handling

### 9.1 HTTP Session Bootstrap

- Missing bearer token: `401`
- Invalid bearer token: `401`
- No valid workers: `503`
- Duplicate worker ids in runtime discovery: fail closed with `503` and emit a metric/log event. Duplicate worker ids in static test or fixed configuration should fail application startup.
- Unexpected edge failure: `500` with normal trace headers and no token leakage.

### 9.2 WebSocket

- First frame missing, malformed, or not `connect`: send a reject frame and close.
- Invalid or expired ticket: send `reject/invalid_ticket` and close.
- Ticket worker unavailable: send `reject/worker_unavailable` and close.
- Internal bridge failure: close the external session and log the worker id, trace id, and reason.

The edge must never log bearer tokens or session tickets.

---

## 10. Observability

`community-im-gateway` has its own service identity:

```text
service.name=community-im-gateway
```

Required metrics:

- session bootstrap success count
- session bootstrap failure count by reason
- active external WebSocket connections
- bridge open success count
- bridge open failure count by reason
- worker unavailable count
- invalid first-frame count
- invalid or expired ticket count

Required log context:

- `traceId`
- `sessionId` after ticket decode
- `workerId` when selected or decoded
- failure reason

The deployable should expose the same health, info, and prometheus management posture as the existing gateway services.

---

## 11. Deployment Model

Single topology:

- Add one `community-im-gateway` service.
- Keep `community-gateway` and NGINX as the public entry.
- Route `/api/im/sessions` and `/ws/im` from `community-gateway` to `community-im-gateway`.
- Run `im-realtime` with:
  - `IM_EDGE_MODE=internal-worker`
  - `IM_WS_PATH=/internal/ws/im`
  - unique `IM_REALTIME_WORKER_ID`

Cluster topology:

- Add `community-im-gateway-1..N`.
- Register or address them as an internal upstream behind `community-gateway`.
- Keep `im-realtime-1..N` registered as `im-realtime-worker` with unique worker ids.
- Keep NGINX upstream pointed at `community-gateway-*`, not directly at IM workers.

The old `/ws/im/workers/**` route can remain temporarily for rollback, but it is no longer the primary client contract.

---

## 12. Migration Strategy

### Phase 1: Add IM Gateway Deployable

- Add module and bootable application.
- Add security, CORS, management, discovery, session, shard, and WebSocket bridge configuration.
- Port or duplicate only the edge mechanisms required for session and bridge.

### Phase 2: Move Session Bootstrap

- Implement `POST /api/im/sessions` in `community-im-gateway`.
- Return stable `wsUrl=/ws/im`.
- Keep `im-realtime` ticket decoder compatible with tickets signed by the edge.

### Phase 3: Move Public WebSocket Edge

- Implement external `WS /ws/im` in `community-im-gateway`.
- Bridge to `im-realtime` internal worker path.
- Ensure the original `connect(ticket)` frame reaches `im-realtime`.

### Phase 4: Route Through Unified External Entry

- Update `community-gateway` HTTP and WS routes:
  - `/api/im/sessions` -> `community-im-gateway`
  - `/ws/im` -> `community-im-gateway`
  - `/api/im/**` -> `im-core`
- Update frontend assumptions only where tests or URL handling expect worker-specific paths.

### Phase 5: Harden And Retire Legacy Path

- Add metrics and operational docs.
- Run single and cluster verification.
- Keep `/ws/im/workers/**` only as a documented rollback route or remove it after rollout confidence.

---

## 13. Testing Strategy

### 13.1 `community-im-gateway`

- `POST /api/im/sessions` returns `OpenImSessionResponse` with stable `/ws/im`.
- Missing and invalid bearer tokens return `401`.
- No valid workers returns `503`.
- Same user and same worker set choose the same worker.
- Worker metadata with missing `workerId`, `wsPath`, or `wsPort` is ignored.
- `WS /ws/im` rejects a non-connect first frame.
- `WS /ws/im` rejects invalid or expired ticket.
- `WS /ws/im` bridges to the worker encoded in the ticket.
- Worker responses flow back to the external client.

### 13.2 `im-realtime`

- Internal `/internal/ws/im` still accepts `connect(ticket)`.
- Ticket with another worker id is rejected.
- Private and room message flows still work behind the bridge.

### 13.3 `community-gateway`

- `/api/im/sessions` routes to `community-im-gateway`.
- `/ws/im` routes to `community-im-gateway`.
- `/api/im/conversations`, `/api/im/rooms`, and `/api/im/unread` still route to `im-core`.
- Optional rollback route `/ws/im/workers/**` behavior is covered only if it remains enabled.

### 13.4 Frontend

- Client uses the returned `wsUrl` without assuming worker path shape.
- Client still sends `connect(ticket)` immediately after WebSocket open.

---

## 14. Risks And Mitigations

### Risk: Edge Becomes A Business Service

If `community-im-gateway` starts owning IM business rules, it will compete with `im-core` and `im-realtime`.

Mitigation:

- Keep it limited to session, shard, bridge, security, CORS, and observability.
- Do not add Kafka command production or persistence dependencies.

### Risk: Ticket Codec Divergence

The edge signs tickets and workers decode them. Divergent issuer, secret, or claims break all connections.

Mitigation:

- Use the same `JwtProperties` contract and claim names.
- Add integration tests with an edge-signed ticket decoded by `im-realtime`.

### Risk: Hidden Route Regression

`/api/im/sessions` currently overlaps the `/api/im/**` route intended for `im-core`.

Mitigation:

- Add an explicit route for `/api/im/sessions` with higher priority than `/api/im/**`.
- Test session route and normal IM HTTP history routes together.

### Risk: Worker Availability Race

The selected worker may disappear between session bootstrap and WebSocket connect.

Mitigation:

- WebSocket edge resolves the worker again from current discovery data.
- If unavailable, it returns `worker_unavailable` and closes; client reconnects and obtains a fresh session.

### Risk: Gateway Layer Proliferation

Adding a new deployable could split external governance.

Mitigation:

- Keep one public entry through `community-gateway` and NGINX.
- `community-im-gateway` remains an internal upstream, not the default public domain.

---

## 15. Acceptance Criteria

The design is complete when:

1. Clients bootstrap through `POST /api/im/sessions` and receive stable `/ws/im`.
2. Clients connect through the unified external entry, not directly to `community-im-gateway`.
3. `community-im-gateway` selects workers and bridges to internal `im-realtime` worker URLs.
4. `im-realtime` no longer needs to expose public `/ws/im` in compose runtime.
5. `im-core` remains the owner for history, unread, room, and message persistence APIs.
6. Single and cluster compose topologies include the new deployable.
7. Tests cover bootstrap, route precedence, bridge success, bridge failure, and wrong-worker rejection.
8. Metrics and logs identify session, worker, bridge, and ticket failures without leaking credentials.

---

## 16. Recommended Conclusion

Build `community-im-gateway` as an independent internal IM edge deployable behind the existing unified external entry. This gives IM long-connection operations their own scaling and observability boundary without splitting the public client contract or moving IM business ownership into an edge service.

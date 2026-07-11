# BBS Reliability Platform P2 Core Slices Design

> **Superseded for the current implementation:** The Search/Growth secondary projection paths and publisher rollout switches in this historical design were retired by [Development Clean Break Design](2026-07-10-development-clean-break-design.md). Keep this file as decision history; do not use those sections as the current runtime contract.

## Context

This is a P2 child spec for the BBS reliability platform. It starts from the
current `origin/main` worktree state and narrows the remaining work to the three
core business slices named by the platform design:

1. Post/comment writes.
2. Like/follow writes.
3. Post detail and hot feed reads.

This spec does not add a new governance platform, new P1 operational surfaces,
or P3 performance optimizations. P0/P1 capabilities already on main, such as the
database outbox, replay APIs, projection lag metrics, operational controllers,
and architecture guardrails, are treated as existing platform primitives.

All implementation must keep the repository's DDD Tactical Layering rules:
inbound adapters call same-domain `*ApplicationService`; application services
orchestrate use cases, idempotency, repositories, domain events, and foreign
published APIs; infrastructure keeps Redis, MyBatis, Kafka, and outbox details.

## Current Main Inventory

### Slice 1: Post/Comment Writes

Existing runtime capabilities:

- `PostController` accepts `Idempotency-Key` for post and comment creation and
  calls content application services.
- `PostPublishingApplicationService` and `CommentApplicationService` use
  `IdempotencyGuard.executeRequired(...)` with request fingerprints before
  writing content facts.
- Content write facts are owned by the content domain and emit content domain
  events.
- `OutboxContentEventPublisher` is the default publisher under
  `content.events.publisher=outbox-kafka` and stores contract payloads in
  common outbox rows on topic `eventbus.content`.
- `ContentEventKafkaOutboxHandler` dispatches content outbox rows to Kafka.
- Search, notice, and growth have projection entry points that consume content
  contract events and dedupe by source event identity.
- Search also has a legacy local Spring-event enqueuer/handler path for
  `projection.search.post`; that path is a downstream projection outbox, not the
  canonical content eventbus boundary.

Existing verification:

- Unit tests cover idempotent post/comment application flows.
- Content outbox publisher and Kafka outbox handler tests cover payload
  creation and dispatch mechanics.
- Search, notice, and growth projection tests cover individual projection
  idempotency and stale/duplicate event handling.

Current gaps:

- Reliability loop verification gap: there is no single P2 acceptance story
  proving post/comment idempotency, content fact creation, eventbus publication,
  and downstream projection replay boundaries together.
- Reliability loop verification gap: the DEAD replay boundary is implicit. The
  common outbox can requeue `eventbus.content` rows and downstream projection
  outbox rows, but Kafka consumer failures that do not create outbox rows are
  not replayed by the common outbox.
- Behavior/documentation gap: `docs/handbook/reliability.md` still describes
  older content idempotency fingerprint details and must be aligned to current
  implementation semantics.

### Slice 2: Like/Follow Writes

Existing runtime capabilities:

- `LikeController` and `FollowController` call same-domain social application
  services.
- `LikeApplicationService.setLike(...)` writes desired state and suppresses
  duplicate same-state writes.
- `FollowApplicationService.follow(...)` is idempotent for already-existing
  follows.
- Social facts emit social domain events that `OutboxSocialDomainEventPublisher`
  persists to common outbox rows on topic `eventbus.social`.
- Social contract events carry stable source event identity and relation keys.
- Notice, growth, and hot-feed projection services have duplicate and
  out-of-order protection at their application boundaries.

Existing verification:

- Social application and outbox tests cover the state-covering write shape and
  eventbus social payloads.
- Notice and growth tests cover duplicate source event handling.
- Hot-feed projection tests cover projection ordering and rank metadata in
  focused cases.

Current gaps:

- Reliability loop verification gap: there is no explicit P2 acceptance story
  that ties like/follow facts to notification, growth, and hot-feed updates with
  duplicate and out-of-order protection.
- Reliability loop verification gap: social eventbus DEAD replay expectations
  are not documented beside the content eventbus boundary.
- No P2 behavior gap is currently required for the social write path unless the
  new tests expose one.

### Slice 3: Post Detail/Hot Feed Reads

Existing runtime capabilities:

- `FeedReadApplicationService` reads hot-feed cache, records
  `HotFeedReadMetrics`, falls back to source data, assembles summaries, and
  returns `rankVersion`.
- Hot-feed read results include summary data and counters.
- `PostReadApplicationService.getPostDetail(...)` uses a viewer-neutral detail
  shell cache and overlays counters and viewer-specific state at read time.
- Redis detail and summary cache adapters remove poison JSON payloads
  best-effort.
- Existing tests cover cache hit, fallback, rank version, board filtering,
  empty-page behavior, and poison JSON cleanup.

Existing verification:

- Feed reliability tests cover normal cache fallback and summary behavior.
- Post detail tests cover detail shell reuse and counter/viewer overlays.

Current gaps:

- Behavior gap: post detail cache read/write failures can still propagate from
  the application read path. Detail reads should fail open to repository/API
  source data when Redis is unavailable or a write-back fails.
- Behavior gap: hot-feed fallback currently warms feed cache and backfills
  summary cache inline. Those best-effort cache writes can propagate and fail a
  read response.
- Reliability loop verification gap: existing read-path tests are focused, but
  P2 needs explicit acceptance around cache miss/failure, counter overlay,
  rankVersion, fallback, and graceful degradation.

## P2 Scope

### Included

- Add or tighten tests proving P2 reliability loops for the three slices.
- Fix behavior gaps required for graceful read-path degradation in post detail
  and hot feed fallback.
- Align `docs/handbook/reliability.md` with current content idempotency and
  eventbus replay boundaries.

### Excluded

- New ops controllers, new governance dashboards, new replay APIs, or new
  outbox state machines.
- P3 performance optimizations such as single-flight loading, TTL jitter,
  advanced cache stampede controls, rank algorithm tuning, or bulk throughput
  work.
- Reworking existing legacy event paths unless required to prove the P2 slices.
- Architecture rule changes beyond staying inside existing DDD guardrails.

## Reliability Loop Verification vs Behavior Implementation

Reliability loop verification means tests and documentation that prove an
already-designed loop behaves end-to-end at the slice boundary:

- idempotent write attempt or state-covering write;
- durable owner-domain fact;
- stable contract event identity;
- common-outbox persistence and replay boundary;
- downstream projection duplicate/stale protection;
- read response remains correct when cache/projection data is missing or stale.

Behavior implementation gaps are runtime behaviors missing on main and required
by P2 acceptance:

- post detail cache read/write fail-open;
- hot-feed fallback cache warm/backfill fail-open;
- reliability handbook alignment with current fingerprints and replay limits.

If new tests reveal a missing behavior in the write slices, keep the fix local to
the owning application or infrastructure adapter and do not add new governance
surfaces.

## Slice Designs

### 1. Post/Comment Reliability Loop

Target flow:

```text
PostController
  -> PostPublishingApplicationService / CommentApplicationService
      -> IdempotencyGuard
      -> content domain fact
      -> content domain event
      -> OutboxContentEventPublisher
          -> common outbox topic eventbus.content
          -> ContentEventKafkaOutboxHandler
              -> Kafka content contract event
                  -> search / notice / growth projection application services
```

Rules:

- The HTTP adapter only binds request data and idempotency key, then calls the
  same content application service.
- The idempotency fingerprint must include stable business command fields that
  define the create intent. Duplicate keys with equivalent fingerprints return
  the recorded result; duplicate keys with different fingerprints fail.
- Content fact creation is the source of truth. Search, notice, and growth are
  projections and must tolerate duplicate/replayed source events.
- Common-outbox replay covers DEAD rows for `eventbus.content` and downstream
  projection outbox rows. It does not automatically replay arbitrary Kafka
  consumer failures unless those failures are materialized as outbox rows.

Acceptance:

- Reusing the same post/comment idempotency key and same command does not create
  a second content fact or second logical result.
- Reusing the same idempotency key with changed command content is rejected.
- Content eventbus payloads contain stable source event identity and enough
  owner fact data for search, notice, and growth projections.
- Replayed or duplicate content events do not double-apply search, notice, or
  growth projections.
- The reliability handbook states the eventbus and downstream outbox replay
  boundary in operational terms.

### 2. Like/Follow Reliability Loop

Target flow:

```text
LikeController / FollowController
  -> LikeApplicationService / FollowApplicationService
      -> social fact or no-op when already in desired state
      -> social domain event when state changes
      -> OutboxSocialDomainEventPublisher
          -> common outbox topic eventbus.social
          -> SocialEventKafkaOutboxHandler
              -> Kafka social contract event
                  -> notice / growth / hot-feed projection application services
```

Rules:

- The controller layer remains a thin inbound adapter and does not call foreign
  APIs, repositories, or infrastructure directly.
- Like writes are desired-state writes. Duplicate same-state requests are no-ops
  and must not emit extra logical events.
- Follow writes are idempotent when the relation already exists.
- Social contract events carry source event identity and relation keys so
  downstream projections can dedupe and order.
- Out-of-order projection events must not overwrite newer relation or counter
  state.

Acceptance:

- Duplicate like/follow requests do not create duplicate social facts or
  duplicate logical projection effects.
- Replayed social eventbus rows preserve the same source identity and are safe
  for notice, growth, and hot-feed consumers.
- Notice projection dedupes duplicate social source events.
- Growth projection dedupes duplicate social source events and handles rollback
  semantics when a like is removed.
- Hot-feed projection rejects stale or out-of-order social updates by source
  ordering/version metadata.

### 3. Post Detail and Hot Feed Reliability Loop

Target detail flow:

```text
PostController
  -> PostReadApplicationService
      -> detail shell cache best-effort read
      -> content source load on miss/failure
      -> counter overlay
      -> viewer overlay
      -> detail shell cache best-effort write-back
```

Target hot-feed flow:

```text
FeedController
  -> FeedReadApplicationService
      -> hot-feed cache read
      -> source fallback on miss/failure/empty page
      -> summary assembly
      -> counter data in summaries
      -> rankVersion in response
      -> best-effort feed and summary cache backfill
```

Rules:

- Cache reads and write-backs are read-path accelerators, not the source of
  truth.
- Detail shell cache must remain viewer-neutral. Counters and viewer state are
  always overlaid after cache read or source load.
- Hot-feed fallback must return source-backed results even if cache warm-up or
  summary backfill fails.
- `rankVersion` remains visible on cache hits and fallback responses so clients
  can reason about ranking freshness.
- Do not add P3 cache optimizations in this slice.

Acceptance:

- Detail cache read failure falls back to content source data and still returns
  counter and viewer overlays.
- Detail cache write failure does not fail the read response.
- Hot-feed cache miss or failure falls back to source data.
- Hot-feed fallback still succeeds when feed cache warm-up fails.
- Hot-feed fallback still succeeds when summary cache backfill fails.
- Responses include stable rank metadata and do not expose stale viewer-specific
  fields from shared caches.

## Documentation Updates

Update `docs/handbook/reliability.md` only where needed to reflect:

- current post/comment idempotency fingerprint semantics;
- `eventbus.content` and `eventbus.social` as the canonical owner-domain outbox
  topics for the two write slices;
- the difference between common-outbox DEAD replay and Kafka consumer retry/DLQ
  responsibilities;
- P2 acceptance expectations for read-path fail-open behavior.

No handbook update should introduce new platform capabilities that are not
implemented or planned in this P2 slice.

## Verification

Focused P2 tests should cover:

- content write idempotency and content eventbus payload/replay boundaries;
- search, notice, and growth projection duplicate/stale protection for content
  events;
- social write idempotency/state covering and social eventbus payload/replay
  boundaries;
- notice, growth, and hot-feed projection duplicate/stale protection for social
  events;
- post detail cache fail-open and overlay correctness;
- hot-feed fallback, rankVersion, and cache backfill fail-open.

Run focused tests first, then the backend architecture tests if application or
infrastructure boundaries were changed:

```bash
cd backend
mvn test -pl :community-app -Dtest='PostPublishingApplicationServiceTest,CommentApplicationServiceTest,OutboxContentEventPublisherTest,ContentEventKafkaOutboxHandlerTest,SearchPostProjectionApplicationServiceTest,NoticeProjectionApplicationServiceTest,TaskProgressApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventKafkaOutboxHandlerTest,PostHotFeedProjectionApplicationServiceTest,PostReadApplicationServiceTest,FeedReadApplicationServiceReliabilityTest'
mvn test -pl :community-app -Dtest='*ArchTest'
```

Also run:

```bash
git diff --check
```

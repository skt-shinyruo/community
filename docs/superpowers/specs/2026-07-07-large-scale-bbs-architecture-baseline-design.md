# Large-Scale BBS Architecture Baseline Design

## Context

This design records the agreed architecture baseline for evolving the community project into a large-scale BBS. It is intentionally higher level than the existing content-mainline implementation plan and should be used as the product and architecture input for later implementation plans.

The repository is already shaped as a modular community system:

- `community-gateway` is the public ingress.
- `community-app` owns the main site business domains and must keep strict DDD tactical layering.
- `content` owns posts, boards, two-level comments, reports, and content visibility state.
- `social` owns likes, follows, and blocks.
- `notice`, `search`, and `analytics` are derived read-model domains.
- `community-oss` and `community-im` remain separate deployables and are not redesigned by this baseline.

The chosen direction is not a CRUD forum and not an immediate microservice split. It is a strong modular monolith for owner writes, plus Redis, Kafka, Elasticsearch, and durable projections for high-concurrency reads.

## Approved Decisions

- Target scale baseline: `10 million DAU`, `100k+ read QPS`, and `10k+ write QPS`.
- Product shape: large public BBS with both board/category navigation and homepage feed navigation.
- Homepage model: two entries, `global hot feed` by default and `follow feed` as a logged-in user entry.
- Moderation stance: reserve graded moderation extension points, but do not implement review workflows now.
- Current publishing policy: normal posts and comments publish directly as public content.
- Hotness production: event-driven near-real-time precomputation, with rebuild and replay capabilities.
- Database strategy: start with single-primary MySQL for owner facts and absorb read pressure through derived read models.
- Comment structure: two-level comments, not infinite nesting.
- Consistency rule: owner writes are strongly consistent; feed, search, notice, counters, and analytics converge asynchronously.
- Service split strategy: do not split `community-app` business domains into microservices in this batch.

## Goals

- Keep post, comment, like, follow, and governance writes short, transactional, and owner-domain centered.
- Protect homepage, board feed, post detail, comment first page, and counters from hotspot read traffic.
- Make all derived models rebuildable and idempotent.
- Preserve the repository's DDD layering and cross-domain collaboration rules.
- Leave explicit extension points for moderation and later service extraction.
- Keep the architecture operable by a small backend team.

## Non-Goals

- Do not implement moderation review queues, reviewer tooling, or risk scoring in this baseline.
- Do not build a full recommendation platform.
- Do not shard MySQL before measured hotspots justify it.
- Do not use Elasticsearch as the homepage ranking source.
- Do not introduce multi-primary writes.
- Do not redesign IM, wallet, market, drive, or OSS beyond their existing collaboration boundaries.

## Alternatives Considered

### Alternative A: Database-Centric BBS

Most homepage, board, detail, and interaction reads query MySQL directly, with Redis used only as a thin cache.

This is simple, but it does not fit the approved read QPS and hotspot profile. Homepage and post-detail traffic would concentrate on a small set of rows and indexes.

### Alternative B: Modular Monolith With Event-Driven Read Models

`community-app` remains the owner write process. MySQL stores authoritative facts. Redis-backed feed, summary, detail, comment-page, and counter caches absorb read pressure. Kafka and outbox-backed consumers project search, notice, analytics, and hotness.

This is the recommended approach because it fits the current repository, keeps business ownership clear, and avoids premature distributed-service complexity.

### Alternative C: Platform-First Microservices

Split content, social, feed, search, notice, analytics, moderation, and recommendation into separate deployables immediately.

This has a higher long-term ceiling, but it is too expensive for the current stage. It would add distributed consistency, deployment, versioning, and incident-response complexity before the content mainline is stable.

## Chosen Architecture

Use Alternative B: owner facts stay in `community-app`; high-traffic reads move to specialized read models.

```text
Client
  -> community-gateway
  -> community-app

Owner writes
  -> same-domain controller/listener/job
  -> same-domain ApplicationService
  -> domain model / domain service / repository interface
  -> MySQL primary
  -> outbox

Asynchronous expansion
  -> outbox relay
  -> Kafka
  -> feed hotness projection
  -> search projection
  -> notice projection
  -> counter snapshot flush
  -> analytics ingest

Hot reads
  -> Redis feed ZSet
  -> Redis post summary cache
  -> Redis post detail cache
  -> Redis comment first-page cache
  -> Redis counter cache
  -> Elasticsearch search read model
  -> MySQL fallback / read replica
```

## Domain Boundaries

`content` owns BBS content facts:

- boards/categories
- posts
- two-level comments
- content visibility state
- reports
- governance state transitions

`social` owns social relationship facts:

- likes
- follows
- blocks

`notice` owns notification read models only. It must not decide whether a post, comment, like, or follow happened.

`search` owns indexing and query behavior only. It must rebuild from current owner truth and must not become the content source of truth.

`analytics` owns request and behavior statistics only. Analytics failure must not affect core BBS writes.

All inbound adapters must continue to call same-domain `*ApplicationService` only. Synchronous foreign collaboration uses owner `api.query` or `api.action`. Asynchronous collaboration uses owner `contracts.event`.

## Write Mainline

The write mainline optimizes for correctness, short transactions, and low synchronous side effects.

### Post Publishing

```text
PostController
  -> PostPublishingApplicationService
      -> validate actor, board, idempotency key, and content shape
      -> apply ContentModerationPolicy
      -> create Post domain model
      -> persist owner fact
      -> append content outbox event
  -> return post result
```

Synchronous success means the content owner fact committed. It does not mean Redis feed, Redis counters, Elasticsearch, notice, growth, or analytics have caught up.

### Comment Publishing

Comments use a two-level model:

- root comments belong directly to a post.
- replies belong to one root comment.
- replies may record `parentCommentId` and `replyToUserId` for display and notification context.

```text
CommentController
  -> CommentApplicationService
      -> validate actor, post visibility, and comment target
      -> apply ContentModerationPolicy
      -> create Comment domain model
      -> persist owner fact
      -> append content outbox event
  -> return comment result
```

New comments should be immediately visible from the post detail read path. Counter, feed, notice, search, and analytics changes may lag.

### Moderation Extension Point

Moderation is reserved but not implemented now.

The domain should expose content visibility states such as:

- `PUBLISHED`
- `AUTHOR_VISIBLE`
- `REVIEW_PENDING`
- `REJECTED`
- `REMOVED`

The current policy returns `PUBLISHED` for normal post and comment creation. Future risk scoring or manual review can replace that policy without rewriting the publishing mainline.

Feed, search, notice, and public detail projections must only expose content that is currently public.

## Homepage And Board Feed

The homepage has two first-class entries:

- default global hot feed
- logged-in user's follow feed

Both use cursor APIs. Public contracts must not expose raw offset semantics.

### Global Hot Feed

Redis stores precomputed ordered post IDs:

- `feed:hot:global`
- `feed:hot:board:{boardId}`

The read path is:

1. read post IDs from Redis sorted set using cursor/rank state
2. bulk read post summaries from Redis
3. batch backfill missing summaries from owner storage
4. return `items`, `nextCursor`, and `rankVersion`

The hotness score is produced by event consumers, not by live homepage queries. Score-affecting events include post publish/delete, comment create/delete, like/unlike, bookmark/unbookmark, view signals, and governance state changes.

### Follow Feed

The follow feed uses pull-merge first, not full fanout-on-write.

Read path:

1. load followed authors from `social` owner API/query contract
2. fetch recent post candidate sets for those authors
3. merge by time and hotness
4. mix in hot-feed fallback when the user has too few follow candidates

This avoids writing a feed item to every follower of a large author. Later, measured high-volume cases can add selective fanout or celebrity-author special handling.

### Cursor Semantics

Feed cursors are opaque to clients. They may contain rank position, score boundary, rank version, direction, and page-size hints.

The contract favors availability and stable user experience over strict snapshot paging. Minor duplicate items are acceptable; clients should deduplicate by `postId`.

## Detail, Comment, And Counter Reads

Post detail is split into independent hot paths.

### Detail Shell

`post:detail:{postId}` caches stable detail fields:

- title
- body or content blocks
- author display summary
- board/category
- tags
- publish time
- visibility state
- media references

Cache misses read through owner storage and refill Redis. Content or governance state changes invalidate or rebuild this cache.

### Comment Reads

Root comments use cursor pagination. Replies under a root comment use a separate cursor.

Hot posts may cache the first page of root comments with a short TTL. Deep pages should use MySQL cursor queries to avoid maintaining large cached blobs.

Deleting a comment should be soft by default. Root deletion should preserve enough structure for reply display and audit behavior.

### Counters

High-frequency counters live in Redis:

- views
- likes
- comments
- bookmarks

Redis counter state flushes periodically to durable snapshot tables. Detail pages prefer Redis real-time counters and fall back to snapshots when Redis is degraded.

View counting must use a dedupe window keyed by post and viewer/device/IP fingerprint, depending on the available identity quality.

## Event Backbone

Owner domains write outbox records in the same transaction as owner facts. Relays publish those records to Kafka.

Published contract events should carry:

- `eventId`
- `aggregateId`
- `aggregateType`
- `type`
- `occurredAt`
- `version`
- payload

Consumers must be idempotent by `eventId`. Projection code should rehydrate current owner truth when correctness matters, instead of trusting the event payload as final state.

Required projection capabilities:

- hot feed projection
- search projection
- notice projection
- counter snapshot flush
- analytics capture
- replay by post, board, user, or time window

## Caching And Degradation

The platform uses layered read models:

- feed cache: Redis sorted sets for global and board hot feeds
- follow candidate cache: recent posts by author or followed set
- summary cache: feed card data
- detail cache: post detail shell
- comment page cache: hot first-page comments
- counter cache: high-frequency counters
- snapshot tables: durable fallback for counters and projections
- Elasticsearch: search read model only

Degradation rules:

- stale feed rank versions are better than failing homepage reads.
- Redis feed unavailable: return a small recent-post fallback or previous cached snapshot.
- summary cache gaps: batch backfill with strict limits.
- Redis counter unavailable: show durable snapshots.
- search unavailable: do not block content publishing.
- notice unavailable: do not block comment, like, or follow writes.
- analytics unavailable: do not block user-facing traffic.
- Kafka lag: alert and keep owner writes available while projections catch up.

## Operations

The system must expose operational controls before traffic scale makes them urgent:

- projection lag metrics by topic and consumer group
- projection failure and dead-letter counts
- feed rank version age
- cache hit ratio by cache family
- hot key detection for posts and boards
- rebuild commands by `postId`, `boardId`, user, and time window
- toggles for search projection, notice projection, analytics capture, and hot-feed refresh

The first production posture should be single-primary write with dual-region disaster recovery capacity. Multi-primary writes are not part of this baseline.

## Testing Strategy

Backend tests should cover:

- DDD boundary ArchUnit rules after every package-boundary change
- controller-to-application boundaries
- publishing application services and moderation policy default behavior
- two-level comment creation and read cursors
- feed cursor contract and rank version behavior
- hotness projection idempotency
- outbox metadata serialization
- projection replay and stale-event handling
- Redis fallback paths
- counter flush and snapshot fallback

Frontend tests should cover:

- default global hot feed entry
- follow feed entry for logged-in users
- cursor loading and duplicate post de-duplication
- post detail read with lagging counters
- two-level comment posting and reply pagination
- graceful degraded states for feed and counters

## Implementation Slices

Recommended implementation order:

1. Feed API contract and cursor semantics.
2. Redis hot-feed, summary, and detail cache ports.
3. Event-driven hotness projection.
4. Counter cache and snapshot flush.
5. Two-level comment model and cursor reads.
6. Follow feed pull-merge read model.
7. Moderation policy extension point with default publish behavior.
8. Projection replay, degradation toggles, and operational metrics.

Each slice should be implemented through the existing strict DDD tactical layering. Do not introduce controller-to-service shortcuts or application-to-MyBatis shortcuts.

## Relationship To Existing Specs

This baseline complements the existing high-concurrency and content-mainline specs:

- `docs/superpowers/specs/2026-07-06-community-content-platform-high-concurrency-architecture-design.md`
- `docs/superpowers/specs/2026-07-06-large-scale-bbs-content-mainline-design.md`

When those specs conflict with this baseline, the explicit decisions in this baseline should be treated as the latest product direction from July 7, 2026.

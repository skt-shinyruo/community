# Community Content Platform High-Concurrency Architecture Design

## Context

This repository already contains the main deployable and domain structure for a community product:

- `community-gateway` is the public HTTP and WebSocket entrypoint.
- `community-app` is the primary owner-domain backend and already follows strict DDD tactical layering.
- `community-oss` owns object storage metadata and file delivery.
- `community-im`, `market`, and `wallet` already exist, but they are not part of this first architecture batch.

Inside `community-app`, the content-facing backbone is already partially aligned with a scalable community design:

- `content` owns posts, comments, categories, tags, reports, and moderation actions.
- `social` owns likes, follows, and blocks.
- `notice` owns in-app notification read models.
- `search` owns Elasticsearch projection and query behavior.
- `analytics` owns request, UV, and DAU capture.
- `auth` and `user` own identity, session, profile, and moderation-related user state.

The codebase already contains important architectural signals that should be extended rather than replaced:

- `PostPublishingApplicationService` owns synchronous content write orchestration.
- `PostContractEventApplicationService` publishes content integration events.
- `SearchPostProjectionApplicationService` rebuilds search state from current owner truth instead of trusting raw event payload alone.
- `NoticeProjectionApplicationService` already supports idempotent projection using source event identifiers.
- `PostHotFeedProjectionApplicationService`, `FeedReadApplicationService`, and `PostCounterApplicationService` already point toward feed precomputation, cache-backed reads, and asynchronous score/counter refresh.

This design therefore does not start from a blank-sheet microservice platform. It hardens the existing modular-monolith direction into a high-concurrency community content platform.

## Fixed Product And Scale Decisions

The following decisions are already approved and are treated as hard inputs:

- first architecture batch: `community content platform` only
- later batches: `IM` second, `market + wallet` third
- target scale: `10 million DAU`, `100k+ read QPS`, `10k+ write QPS`
- product shape: `mixed BBS`, where boards/categories and homepage feed are both first-class entrypoints
- homepage distribution: `follow feed + hot feed` in parallel, with categories still important
- consistency rule: core owner writes are strong-consistency, while feed/search/notice/counters/analytics may converge asynchronously
- deployment rule: `dual-region disaster recovery`, `single-primary write`
- organization rule: `small backend team`, so business service splitting should be minimized
- infrastructure stance: complete infrastructure is acceptable, but business deployables should stay consolidated
- database evolution rule: `single MySQL primary at first`, shard only after hotspot evidence
- moderation rule: `graded moderation`; low-risk content may publish immediately, high-risk content may wait for review

## Goals

- Define a content-platform architecture that fits the current repository and can evolve toward the target traffic without immediate business-service fragmentation.
- Keep the owner write path short, transactional, and explicit.
- Make homepage, board feed, and post detail survive heavy hotspot traffic through specialized read models.
- Separate owner truth from projection, ranking, notification, search, and analytics responsibilities.
- Preserve strict DDD tactical layering inside `community-app`.
- Support dual-region single-primary disaster recovery with explicit degradation paths.
- Keep the design operable by a small team.

## Non-Goals

- Do not design the internal architecture of `IM`, `market`, or `wallet` in this spec.
- Do not introduce multi-region multi-primary writes.
- Do not split `community-app` into many business services in this batch.
- Do not define a full personalization or recommendation platform beyond the approved `follow feed + hot feed` backbone.
- Do not lock the implementation to a specific cloud vendor, specific Redis product, or specific OLAP engine.
- Do not preserve historical paging, compatibility, or legacy data-model semantics when better long-term contracts exist.

## Current Architectural Findings

### The Main Opportunity Is Architectural Hardening, Not Ground-Up Rebuild

The repository is already beyond the stage of a CRUD forum prototype. The core domains, asynchronous contracts, and feed/search/notice skeletons exist. The architectural problem is therefore not "how to add content domains," but how to make the existing community backbone scale without turning `community-app` into a muddy pseudo-monolith.

### The Existing Owner-Write Boundary Is Directionally Correct

Controllers already tend to enter same-domain `*ApplicationService` classes. `PostPublishingApplicationService` is an example of the right write boundary:

- authentication and actor checks happen before persistence
- application service owns idempotency and orchestration
- domain and repositories own business facts
- side effects are triggered after owner persistence

This rule should be generalized across content, social, governance, and notice-producing actions.

### Derived Read Models Already Follow The Right Reliability Shape

Current search and notice projections already show the correct pattern for a large community:

- commit owner truth first
- emit or relay an integration event
- let a projection consumer rehydrate current state
- update or delete the derived model idempotently

That same rule should become the standard architecture for feed, search, notice, counters, and analytics.

## Alternatives Considered

### Alternative A: Database-Centric Modular Monolith

Keep one primary application and let MySQL serve most feed, detail, and interaction reads directly, with Redis only as a thin cache.

Tradeoff:

- simplest conceptual model
- unacceptable hotspot pressure on homepage, detail, counters, and follow feed at the approved scale

This is not recommended.

### Alternative B: Strong Modular Monolith With Event-Driven Read Models

Keep `community-app` as the single business write entrypoint, keep strict DDD domain boundaries inside it, and move scale pressure into specialized read models, caches, and projections backed by Redis, Kafka, Elasticsearch, and OLAP.

Tradeoff:

- more operational complexity than a database-centric monolith
- far less organizational complexity than business microservices
- best fit for the approved scale, repository state, and small-team constraint

This is the recommended approach.

### Alternative C: Platform-First Microservices

Split `auth`, `user`, `content`, `social`, `feed`, `search`, `notice`, and `analytics` into separate services immediately, while also extracting governance workflows.

Tradeoff:

- strong long-term ownership separation
- too much coordination, deployment, and failure-surface complexity for a small team

This is not recommended at this stage.

## Chosen Architecture

Use `Alternative B`: a strong modular monolith for owner writes, plus event-driven read models and independent infrastructure data planes.

```text
Client
  -> community-gateway
  -> community-app

community-app owner domains
  - auth
  - user
  - content
  - social
  - notice
  - search
  - analytics

Synchronous write path
  -> same-domain ApplicationService
  -> MySQL primary
  -> outbox

Asynchronous expansion
  -> outbox relay
  -> Kafka
  -> feed projection
  -> search projection
  -> notice projection
  -> counter refresh
  -> analytics ingest

Read path
  -> Redis feed and cache layers
  -> MySQL read replicas
  -> Elasticsearch
  -> OLAP
  -> OSS
```

This architecture accepts infrastructure complexity while delaying business-service complexity.

## Domain Boundaries

### Owner Domains

The community content platform should treat these as first-class owner domains inside `community-app`:

- `auth`: login, refresh, logout, session and security entry semantics
- `user`: profile, punishment state, user summary, avatar metadata
- `content`: posts, comments, categories, tags, reports, moderation workflow inputs
- `social`: likes, follows, blocks
- `notice`: in-app notification projection and unread state
- `search`: search projection and query behavior
- `analytics`: request and behavior ingestion

### Layering Rule

All inbound adapters must continue to follow repository guardrails:

```text
Controller / Listener / Handler / Bridge / Job
  -> same-domain ApplicationService
      -> domain model / domain service / repository interface / domain event
      -> foreign owner-domain api.query / api.action when needed
      -> contracts.event for published async collaboration
          -> infrastructure implementation
```

The architecture must not regress into:

- controller-to-repository shortcuts
- controller-to-foreign-api shortcuts
- application-to-MyBatis shortcuts
- domain-to-infrastructure dependencies

### Cross-Domain Collaboration Rule

The most important collaboration rule is:

- synchronous cross-domain work goes only through owner `api.query` or `api.action`
- asynchronous cross-domain work goes through published `contracts.event`

The content platform stays inside one deployable for now, but it still uses these boundaries so that future extractions remain possible.

## Target Topology

### Runtime Shape

The platform should run as one business write application plus several infrastructure runtimes:

- `community-gateway` for public ingress, traffic shaping, and edge policy
- `community-app` for business writes and owner-domain reads
- `MySQL primary` for authoritative business facts
- `MySQL replicas` for selected owner-backed read fallbacks and admin queries
- `Redis` for feed, cache, counters, unread counts, and hotspot protection
- `Kafka` for write-to-read-model decoupling and replay
- `Elasticsearch` for search and moderation recall
- `OLAP` for reporting and product analytics
- `OSS` for media and attachment objects

### Regional Shape

The approved regional topology is `dual-region single-primary`.

- `Region A` is primary for writes, Kafka production, MySQL primary, and main cache/index clusters.
- `Region B` holds read replicas, cache/index replicas, and recovery capacity.
- normal traffic may read from both regions where latency and consistency allow
- only one region accepts writes at a time

This avoids the operational and consistency cost of multi-primary content ownership.

## Write Mainline And Event Backbone

### Synchronous Write Boundary

Every core community write should stop at owner-fact commit:

- create post
- update post
- delete post
- create comment
- delete comment
- like
- unlike
- follow
- unfollow
- report creation
- moderation action

Synchronous success means only this:

- the owner-domain fact is durably committed in MySQL
- the outbox or event trigger is durably recorded in the same transaction

It must not mean:

- feed is already updated
- search is already synchronized
- notice is already projected
- counters are already converged
- analytics are already ingested

### Outbox And Kafka Rule

The content platform should standardize on:

```text
transactional owner write
  -> owner tables
  -> outbox_event
commit
  -> relay
  -> Kafka
  -> projection consumers
```

This is required to avoid the worst failure mode: committed owner facts without a durable downstream event.

### Event Semantics

Projection consumers should treat events as `triggers`, not as eternal truth payloads.

Required event fields:

- `eventId`
- `aggregateId`
- `aggregateType`
- `eventType`
- `actorId`
- `occurredAt`
- `version`
- minimal routing fields only

Consumers should:

1. read the event
2. rehydrate current owner truth or current projection input
3. upsert or delete their read model idempotently

This is already how search is moving. It should become the platform default.

### Moderation And Visibility

The approved moderation model is `graded moderation`.

That means content visibility has at least these states:

- `VISIBLE`
- `PENDING_REVIEW`
- `HIDDEN` or `REMOVED`

Rules:

- low-risk writes may commit directly as visible
- high-risk writes may commit as pending review and stay out of feed/search/notice
- moderation approval emits visibility-grant events
- moderation rejection or removal emits visibility-revoke events

Feed, search, notice, and ranking consumers must all obey visibility state.

## Target Read Architecture

The platform should use specialized read models per access pattern rather than one universal read path.

### Post Detail

Post detail should be split into:

1. detail shell
2. counters
3. viewer overlay
4. comment pagination

Rules:

- detail shell contains stable post content, category, tags, media, and lifecycle state
- detail shell may live in Redis with owner-backed rebuild
- counters come from Redis or snapshot overlay and may lag slightly
- per-viewer flags such as liked and bookmarked are overlaid late
- comments are not stored as one giant cache blob

This matches the current direction in `PostReadApplicationService` and `PostCounterApplicationService`.

### Global Hot Feed

Global hot feed should be a Redis-first ranking product.

Recommended key families:

- `feed:global:hot`
- `post:summary:{postId}`
- `post:counter:{postId}`

Rules:

- feed order lives in Redis sorted structures
- summary objects are cached separately from feed order
- DB fallback exists for cache misses and recovery
- feed reads should return cursor-based pages plus `rankVersion`

### Board Feed

Board feed should be a sibling of global hot feed, not a special SQL-only mode.

Recommended key family:

- `feed:board:{boardId}:hot`

Board feed may fall back to owner storage for latest or degraded reads, but the normal high-traffic path should still be cache-backed.

### Follow Feed

Follow feed should not begin with full fanout-on-write.

For a small team, the safer initial design is a `pull-merge model`:

- `follow` relations live in owner storage
- each author has a recent-post list
- request-time merge assembles the current user follow feed
- merged first-page results are short-cached in Redis
- only extreme hotspot creators justify targeted partial fanout later

This avoids massive write amplification and celebrity fanout storms too early.

### Search

Elasticsearch remains a derived search plane only.

Rules:

- search is not the homepage ranking source
- search documents are rebuilt from current owner truth
- delete or hidden states remove or suppress searchable visibility
- full rebuild, incremental replay, and repair scans must be supported

### Notice

Notice should remain a read model, not a synchronous requirement of content writes.

Rules:

- content and social events project into notice records asynchronously
- unread counts should be cached separately from notice bodies
- projection must be idempotent by source event identifier
- failed notice projection must not roll back owner writes

### Counters

Views, likes, bookmarks, comment counts, and score snapshots should be treated as high-churn read-side data.

Rules:

- live counters sit in Redis
- durable snapshots flush asynchronously
- view signals are deduplicated or rate-limited
- hotness uses decayed counters, not raw permanent totals

### Analytics

Analytics should be operationally separate from product-serving reads.

Rules:

- product request path emits events or commands, not heavy analytical queries
- analytics ingestion may lag without affecting core content correctness
- OLAP holds long-range reporting and operator analysis

## Storage And Data Layering

### MySQL

MySQL is the only authoritative source for owner business facts.

It owns:

- posts and comments
- visibility and moderation state
- follow and like relationships
- outbox records
- notice projection tables
- idempotency records
- operational audit trails

It should not be the primary engine for:

- homepage ranking
- follow feed assembly at scale
- large text search
- high-frequency counter increments
- analytical scans

### Redis

Redis is the primary hot read and hotspot protection layer.

It should hold:

- hot feed order
- board feed order
- follow feed short cache
- post summaries
- post detail shells
- unread counts
- counter state
- view-dedup windows
- request or actor rate-limit state

Redis data must always be rebuildable from owner truth and projections.

### Kafka

Kafka is the write-to-read-model event backbone.

It exists for:

- decoupling owner writes from derived models
- replay and repair
- smoothing traffic spikes
- keeping read-model runtimes independent from request latency

### Elasticsearch

Elasticsearch exists for:

- full-text search
- tag/category filters
- moderation and operator recall

It is not an owner source of truth.

### OLAP

OLAP exists for:

- exposure and click reports
- growth and conversion analysis
- moderation workload analysis
- operational dashboards beyond online serving

### OSS

OSS exists for:

- post images
- videos
- attachments
- immutable object payloads

Business ownership of those objects still lives in `community-app`.

## High-Concurrency And Disaster-Recovery Strategy

### Traffic Shaping

The system should apply control at multiple layers:

- gateway-level rate limits and blacklist controls
- application-level idempotency and actor throttles
- Redis-first cache hits for hot paths
- Kafka buffering for projections and analytics
- database reserved for true owner writes and selected rebuilds

### Cache Is A Primary Path, Not An Optional Optimization

At the approved traffic level, the normal read path should be:

- feed from Redis
- summaries and detail shells from Redis
- counters from Redis
- viewer overlays from lightweight owner lookups or dedicated caches

DB fallbacks are recovery paths, not the intended steady-state path.

Required protections:

- request coalescing or single-flight rebuild
- logical expiration or stale-while-revalidate
- hotspot allowlists and proactive refresh
- explicit deep-page rate limits

### Idempotent Consumers

Every projection consumer must be safe under:

- duplicate delivery
- retry
- out-of-order arrival
- replay from a historical offset

This requires:

- event identifiers
- aggregate versions or monotonic ordering keys
- upsert/delete semantics that tolerate repetition
- projection-side dedupe where needed

### Degradation Model

The platform must degrade intentionally instead of failing all-at-once.

Preferred degradation order:

- personalized follow feed may degrade to hot feed
- counters may stay stale temporarily
- search may lag or narrow its result window
- analytics may drop non-critical ingestion
- notice throughput may lag behind writes

Core writes must survive ahead of enhanced read experience.

### Dual-Region Failover

Failover should follow an explicit order:

1. stop old-primary write ingress
2. confirm replication and event-lag boundary
3. promote the new primary data plane
4. switch producer and consumer ownership
5. switch gateway write routing
6. run projection repair and cache warm-up

This procedure should be automated where possible and drilled operationally.

## Scaling And Evolution Strategy

### Stage 1: Strong Modular Monolith

The first scaling stage should remain:

- one main business deployable
- one MySQL primary plus replicas
- Redis-backed read models
- Kafka-backed projections
- Elasticsearch for search
- OLAP for analysis

This stage should be pushed far before splitting owner business services.

### Stage 2: Hotspot Data Optimization

When evidence appears, optimize hotspot data first:

- shard or partition the hottest tables
- isolate the most expensive projection consumers
- split cache clusters by responsibility if needed
- promote read-repair and replay tooling

Hotspot candidates include:

- comment-heavy tables
- feed order and counter snapshots
- follow-edge and recent-activity data

### Stage 3: Split Projection Runtimes Before Owner Writes

If runtime isolation becomes necessary, split in this order:

1. feed projection runtime
2. search runtime
3. analytics runtime
4. notice runtime if justified

Only after that, and only if team ownership and deployment pressure demand it, should owner write domains be extracted from `community-app`.

## Verification Requirements

Implementation work based on this design must verify both architecture rules and runtime behavior.

Required architecture verification:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Required behavior verification should cover:

- owner write success independent from feed/search/notice lag
- hot feed correctness under asynchronous projection delay
- follow feed correctness under pull-merge and cache refresh
- visibility changes under graded moderation
- search rebuild and delete correctness from owner truth
- notice idempotency under duplicate event delivery
- counter lag tolerance and snapshot recovery
- cache-loss recovery for feed and detail shells
- dual-region failover drill with replay and warm-up

## Implementation Direction

The implementation should follow this order:

1. harden owner write contracts and outbox semantics across content and social
2. standardize projection event envelopes and idempotency rules
3. formalize hot feed and board feed Redis projection
4. add pull-merge follow feed plus short-lived assembled cache
5. split detail shell, counters, and viewer overlays more cleanly
6. harden notice, search, and analytics projection replay paths
7. add degradation controls and regional failover tooling
8. revisit table sharding or runtime extraction only after measured hotspots justify it

The design principle stays constant through every phase:

`keep owner writes centralized, keep boundaries strict, and scale reads through specialized read models before splitting business services.`

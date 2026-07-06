# Large-Scale BBS Content Mainline Design

## Context

This repository already contains the major owner domains of a community system:

- `content` owns posts, comments, replies, bookmarks, categories, tags, reports, and moderation actions.
- `social` owns likes, follows, and blocks.
- `notice` owns notification read models.
- `search` owns Elasticsearch indexing and query behavior.
- `analytics` owns UV/DAU capture.
- `community-gateway` is the public HTTP/WebSocket entrypoint.

The current content architecture is already close to the desired use-case shape:

- `PostPublishingApplicationService` and `CommentApplicationService` own synchronous write orchestration.
- search projection already runs through durable outbox handling and rebuilds ES from current DB state rather than trusting raw event payload.
- post score refresh is already scheduled after commit through `PostWriteSideEffectScheduler`.

The next step is not "add more features first." The next step is to harden the content mainline for a large public BBS where homepage hot reads, post-detail hot reads, post/comment writes, and search concurrency all matter.

## Fixed Product Decisions

These decisions are already approved and are treated as hard inputs to the design:

- target scale: `large community`
- first sub-project: `content mainline`
- homepage model: `public global feed`
- homepage consistency: `detail immediately visible, feed catches up in 3-10 seconds`
- homepage ranking: `hot-first`
- post detail consistency: `new comments immediately visible, counters may lag by 1-5 seconds`
- comment structure: `two-level comments`
- moderation model: `no pre-publication review, but keep governance capabilities`
- hotness production: `near-real-time precomputation`
- information architecture: `fixed boards/categories`
- homepage entry: `global homepage first`
- compatibility stance: `breaking changes are allowed; do not preserve historical compatibility`

## Goals

- Make homepage and board feeds survive strong hotspot read traffic.
- Keep post and comment writes fast by limiting synchronous work to owner-domain facts.
- Let detail pages remain correct for newly written comments while allowing counters and ranking to converge asynchronously.
- Keep search as a derived read model that can lag without breaking core content flows.
- Define a storage and API shape that can evolve to large-scale traffic without immediate microservice splitting.
- Remove compatibility-driven constraints from the target design.

## Non-Goals

- Do not preserve old API pagination semantics if cursor-based contracts are better.
- Do not preserve generic historical comment modeling if a BBS-specific two-level model is better.
- Do not require dual-write, backward-compatibility adapters, or migration-phase contract bridging in this design.
- Do not split `community-app` into new deployables as part of this design batch.
- Do not redesign IM, wallet, market, or recommendation systems beyond their interaction with content.

## Current Architectural Findings

### Content Write Ownership Is Already In The Right Place

`PostPublishingApplicationService` and `CommentApplicationService` already provide the correct owner-domain write boundary:

- controller enters same-domain application service
- application service validates actor and request semantics
- domain and repository persist owner facts
- after-commit work is delegated to downstream side effects

This shape should be preserved.

### Search Projection Already Follows The Right Reliability Rule

`search.infrastructure.event.PostOutboxHandler` treats outbox payload as a trigger signal, then rebuilds the search document from current content DB state through `SearchPostProjectionApplicationService`.

This is the correct large-scale rule for derived read models:

- outbox event tells consumers "something changed"
- the consumer rehydrates current truth from the owner
- delete/upsert decisions come from current owner state

The same rule should be reused for homepage hot-feed projection and other derived models.

### Current "Post Score Refresh" Needs To Become A Clearer Hotness Pipeline

`PostWriteSideEffectScheduler` currently enqueues post score refresh after commit. That is directionally correct, but for large-community traffic it must evolve from a generic side effect into an explicit hotness and feed projection pipeline with:

- typed score-affecting events
- idempotent consumers
- Redis-backed score caches and sorted sets
- periodic recalibration

## Alternatives Considered

### Alternative A: Database-Centric Read Path

Use MySQL for most homepage, detail, and comment reads. Redis is only a thin cache. Elasticsearch handles only search.

Tradeoff:

- simple to reason about
- unacceptable hotspot pressure on homepage and detail read paths at large-community scale

This is not recommended.

### Alternative B: Cache And Precomputation Centered Mainline

Use MySQL only for owner facts. Use Redis for hot feeds, detail shells, counters, and hotspot protection. Use durable outbox and async consumers for search, hot-feed projection, notices, and growth side effects.

Tradeoff:

- moderate complexity
- aligns with the current codebase
- best fit for large public BBS traffic

This is the recommended approach.

### Alternative C: Full CQRS/Event-Driven Split From Day One

Split write model, homepage model, detail model, search model, and notification model into separate independently deployed services and streams immediately.

Tradeoff:

- strongest long-term separation
- too much operational and delivery complexity for the current stage

This is not recommended yet.

## Chosen Architecture

The chosen architecture is `Alternative B`: keep `community-app` as the owner-domain process for content writes, but shift high-traffic read paths onto Redis-backed precomputed feed and cache layers.

```text
Browser
  -> community-gateway
  -> community-app

Synchronous owner writes
  -> Post/Comment Controller
  -> content *ApplicationService
  -> MySQL owner facts

Asynchronous derived models
  -> domain event / outbox trigger
  -> feed projection
  -> search projection
  -> notice projection
  -> growth/reward projection

Read paths
  -> homepage/board feed from Redis
  -> post detail shell from Redis
  -> comment pages from MySQL
  -> counters from Redis
  -> search from Elasticsearch
```

## Target Read Architecture

### Homepage And Board Feed

Homepage and board feed must become precomputed feed reads rather than live DB or ES ranking queries.

Required Redis keys:

- `feed:global:hot`
- `feed:board:{boardId}:hot`
- `post:summary:{postId}`

Feed request path:

1. read ordered post IDs from `feed:*`
2. bulk read `post:summary:*`
3. if some summaries are missing, load only the missing posts from content owner storage
4. asynchronously backfill Redis summaries

This keeps homepage traffic on:

- Redis sorted-set/list reads
- Redis/MGET summary lookups
- limited DB summary backfill only on cache gaps

### Post Detail

Post detail must be split into three read parts:

1. `post detail shell`
2. `counters`
3. `comment pages`

Redis keys:

- `post:detail:{postId}`
- `post:counter:{postId}`

Rules:

- the detail shell contains stable post body data such as title, blocks, author, board, tags, and deletion state
- counters are read from Redis and may lag by 1-5 seconds
- comments are not treated as a single large cached blob
- first-screen comment data may use short TTL caching
- deep comment pages read from MySQL with cursor pagination

This preserves the approved consistency target:

- newly created comments are visible from primary owner storage immediately
- counters and hotness catch up asynchronously

### Search

Elasticsearch remains a derived read model only.

Rules:

- do not route homepage or board ranking through ES
- do not treat ES score as the homepage hotness source
- keep the current "rebuild from current DB state" rule

### Notice

Notice remains a derived read model, but for large-community operation the important content-interaction notifications should not stay on a long-term best-effort projection path.

Rules:

- key notice-producing content events should move onto durable outbox-backed projection
- notice projection failure must not roll back content owner writes
- replay and idempotent projection behavior must be supported

## Target Write Architecture

### Synchronous Write Boundary

The synchronous success of `create post`, `create comment`, `delete post`, and `delete comment` means only this:

- the owner-domain MySQL fact has committed

It must not mean:

- Redis feed already updated
- counters already converged
- ES already updated
- notice already projected
- reward/growth side effects already completed

The request thread should stay limited to:

- identity and idempotency validation
- user speaking eligibility
- board/category validation
- text sanitization and request normalization
- owner-fact persistence
- domain event publication or outbox trigger registration

### Score-Affecting Events

Hotness must move from a vague side effect to an explicit event model.

Required score-affecting triggers:

- `PostPublished`
- `PostDeleted`
- `CommentCreated`
- `CommentDeleted`
- `LikeAdded`
- `LikeRemoved`
- `BookmarkAdded`
- `BookmarkRemoved`
- `PostPinned`
- `PostWonderful`

Each event updates a Redis-backed per-post hotness state and may cause local reorder in:

- `feed:global:hot`
- `feed:board:{boardId}:hot`

### View Counting

Views are the largest-volume signal and should not behave like a normal owner write.

Rules:

- record raw view signals in Redis
- deduplicate or rate-limit by user/session/device/IP window
- fold them into hotness as a weak, decayed signal
- flush durable snapshots asynchronously if long-term storage is needed

## Hotness Model

Homepage ranking uses near-real-time precomputation plus time decay.

Illustrative formula:

```text
score =
  a * recent_views
+ b * recent_comments
+ c * recent_likes
+ d * recent_bookmarks
+ e * anti_spam_penalty
+ f * operator_boost
- g * age_decay
```

Required semantics:

- comments weigh more than likes
- bookmarks weigh more than plain views
- views are weak and must be de-duplicated and decayed
- age decay is mandatory so old posts do not remain permanently dominant
- operator boosts such as pinning or "wonderful" are layered separately from natural engagement

Ranking mechanics:

- interaction events cause local incremental adjustments
- every few seconds, small-window correction runs
- every few minutes, full recalibration runs from recent snapshots and durable owner facts

## Target API Shape

Breaking changes are explicitly allowed. The design therefore prefers the best long-term contract rather than compatibility with legacy paging or payload structure.

### Feed APIs

Feed APIs should move from `page + offset` toward cursor semantics.

Target shape:

- `GET /api/feed/global?cursor=...`
- `GET /api/boards/{boardId}/feed?cursor=...`

The response should include:

- ordered post summaries
- `nextCursor`
- `rankVersion`

The API meaning is explicit:

- feed is a precomputed, eventually consistent ranking product
- it is not a live SQL page over `discuss_post`

### Detail APIs

Post detail stays content-owner served, but the contract becomes explicit about partial consistency:

- detail shell is owner truth with cache acceleration
- counters are eventually consistent
- comment pages are cursor-based and owner-truth backed

### Comment Model

The comment model should stop optimizing for abstract generic entity trees and instead optimize for a real BBS two-level structure.

Target logical fields:

- `post_id`
- `root_comment_id`
- `parent_comment_id`
- `reply_to_user_id`
- `status`
- `create_time`

This removes expensive or awkward tree chasing from common read paths and makes:

- root comment pagination
- reply loading
- thread deletion
- hotspot indexing

significantly simpler.

## Target Data Model

### Posts

`discuss_post` remains the owner table for stable post facts:

- post identity
- author
- board/category
- lifecycle/status
- create/update time
- last active time
- operator flags

High-churn counters should not remain strong-consistency write targets on the same row for every interaction.

### Post Body

`post_content_block` remains the durable body model for blocks and media references. This is already aligned with a scalable content body architecture and should be retained.

### Comments

The `comment` table should be reshaped around the approved two-level model rather than a generic recursively interpreted entity graph.

This is a deliberate breaking change and does not require legacy compatibility behavior.

### Counters And Score Snapshots

Add or formalize snapshot tables for asynchronous persistence and Redis rebuild:

- `post_counter_snapshot`
- `post_score_snapshot`

Optional later addition if operationally necessary:

- `post_feed_snapshot`

Rules:

- Redis is the fast path
- snapshots are rebuild and recovery support, not the primary hot read path

## Index Strategy

### `discuss_post`

Recommended indexes:

- `(status, board_id, create_time desc, post_id desc)` for board latest fallback
- `(status, create_time desc, post_id desc)` for global latest fallback
- `(user_id, create_time desc, post_id desc)` for author profile pages
- `(board_id, last_active_time desc, post_id desc)` for activity-based recalibration support

### `comment`

Recommended indexes:

- `(post_id, root_comment_id, create_time desc, comment_id desc)` for root comment pagination
- `(root_comment_id, create_time asc, comment_id asc)` for reply threads
- `(user_id, create_time desc, comment_id desc)` for user history and governance queries

Deletion/status fields should participate in the actual chosen composite indexes to avoid hot-thread scans through logically deleted rows.

### `outbox_event`

Recommended indexes:

- `(topic, status, next_attempt_time, id)` for worker leasing and retry
- additional aggregate/event indexes as needed for replay and diagnosis

## Cache Layer And Hotspot Protection

The system needs explicit hotspot protection rather than naive TTL caching.

Required protections:

- request coalescing / singleflight on cache rebuild
- logical expiration with stale-while-revalidate behavior
- hotspot post allowlist with longer TTL and proactive refresh
- separate rate limits for deep comment pagination
- feed fallback to stale snapshots or latest-feed mode when hot ranking is unhealthy

Rules:

- keep post body cache stable longer than counter cache
- never synchronously write post counters to MySQL per request
- keep deep pages more expensive than first-screen pages by design

## Failure And Degradation Model

Large-community operation requires partial survival, not all-or-nothing correctness.

Required degradation behavior:

- if feed projection lags, homepage serves the previous hot snapshot
- if Redis hot feed is partially lost, downgrade to latest-feed fallback
- if ES is unhealthy, search degrades independently without blocking content writes
- if notice projection fails, do not roll back content writes
- if counter refresh lags, detail shell remains available and counters stay temporarily stale
- if a single post becomes extremely hot, preserve detail shell and first-screen comments first, and rate-limit deep pages

## Scaling Boundaries

### First Scaling Stage

Do not split services first.

The first scaling stage is:

- single owner write database
- read replicas where useful
- Redis for feed, summary, detail shell, counters, and hotness
- durable outbox consumers for search/feed/notice projection
- Elasticsearch for search only

This stage should be exhausted before introducing service splits.

### First Shard Candidate

The first table worth sharding is usually `comment`, not `discuss_post`.

Reason:

- comments carry more write amplification
- comment read load is concentrated on hot posts
- comment indexes become the earliest hotspot risk

Preferred sharding key:

- `post_id`

Reason:

- comment access is naturally grouped by post detail
- all comments of a post stay co-located
- detail-page fanout stays lower than user-based scatter/gather

### Service Split Boundary

Recommended service split order:

1. keep `content` owner writes inside `community-app`
2. if needed later, separate feed/ranking projection runtime
3. if needed later, evolve search independently
4. split owner write service only after traffic, team ownership, and deploy cadence justify it

Principle:

- split derived read-model runtimes before splitting the owner write service

## Verification Requirements

Before implementation is considered successful, verification must include:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

And implementation-specific verification must cover:

- homepage feed correctness under async lag
- post detail new-comment visibility
- counter lag tolerance within the defined 1-5 second window
- search lag tolerance under outbox delay
- hotspot post pressure on detail shell and comment pages
- replay safety for hotness and feed projection events

## Implementation Direction

The implementation should be phased around the content mainline:

1. define new feed and cursor contracts
2. introduce Redis-backed feed and summary projection
3. formalize hotness events and score consumers
4. split detail shell, counters, and comment pagination
5. reshape comment storage/indexing for the two-level model
6. add snapshot persistence and recovery support
7. add degradation and hotspot protection controls

Because compatibility is explicitly out of scope, the implementation may delete or replace old contracts once the new path is ready, rather than maintaining dual paths.

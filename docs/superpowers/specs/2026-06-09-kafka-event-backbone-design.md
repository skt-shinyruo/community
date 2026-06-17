# Kafka Event Backbone Design

## Context

The backend already has reusable Kafka and outbox infrastructure:

- `common-outbox` persists integration events and dispatches them with at-least-once semantics.
- `common-kafka` provides `TraceKafkaSender` and trace propagation for Kafka producers/consumers.
- `growth` already consumes Kafka events for task progress.
- `im` already uses outbox-to-Kafka for user messaging policy and block relation changes.

Several cross-domain projections still depend on Spring local events, direct outbox handlers, or synchronous side effects. The first migration batch establishes Kafka as the primary asynchronous event backbone for content, social, and user contract events while preserving strict DDD Tactical Layering.

## Current Worktree Baseline

As of 2026-06-16, `/home/feng/code/project/community/.worktrees/kafka-event-backbone` already contains a partial implementation:

- Added content, social, and user outbox publishers plus `*KafkaOutboxHandler` classes.
- Added notice, search, growth, and user reward Kafka listeners.
- Added notice reliable projection methods and `notice_projection_event_log` schema.
- Removed direct user reward calls from `PostPublishingApplicationService` and `LikeApplicationService`.
- Added focused unit tests for the new publishers, handlers, listeners, and affected application services.

The branch is not yet ready for cutover. Known gaps:

- `application.yml` still defaults `content.events.publisher` and `social.events.publisher` to `local`, and topic/group defaults are incomplete.
- `*KafkaOutboxHandler` classes currently dispatch directly with `KafkaTemplate` / `TraceKafkaSender`; this must be refactored because repository rules classify handlers as inbound adapters that may only call same-domain `*ApplicationService`.
- Notice commands currently carry foreign `contracts.event` envelopes. Prefer notice-owned command fields before finalizing the application boundary.
- `NoticeProjectionEventRepository` currently lives under `domain.repository`, but it records application idempotency events and should be reviewed as an application-owned technical port.
- Event id rules differ between this design's stable examples and current short/UUID-v7 prefixes such as `ce:*`, `se:*`, `ue:p:*`, and `gl:like:*`.

## Goals

- Move content, social, and user contract events from in-process local delivery to durable outbox-to-Kafka delivery.
- Let consumer domains subscribe to Kafka and call their own `*ApplicationService` entry points.
- Preserve DDD tactical layering: inbound Kafka listeners, handlers, bridges, enqueuers, and jobs must not call foreign application/domain/infrastructure code.
- Preserve source transaction consistency by writing outbox records before commit instead of sending Kafka directly from producer application services.
- Provide deterministic consumer idempotency keys so at-least-once Kafka delivery does not duplicate user-visible side effects.
- Keep the first batch focused on event backbone migration, not market-wallet Saga, analytics, mail, or media processing.

## Non-Goals

- Do not migrate market order wallet Saga in this batch.
- Do not migrate authentication mail sending in this batch.
- Do not replace Redis-based request/token/score state that is still better served by Redis.
- Do not introduce direct `ApplicationService -> KafkaTemplate` dependencies.
- Do not introduce controller/listener/handler calls to foreign domain APIs.
- Do not introduce new root legacy `service`, `entity`, `mapper`, or `app` code.
- Do not fold the existing IM-specific policy Kafka bridge into the shared backbone until the first batch is stable.

## Architecture

The DDD-compliant target flow is:

```text
producer ApplicationService
  -> same-domain event publisher interface
  -> infrastructure outbox publisher
  -> JdbcOutboxEventStore
  -> same-domain outbox dispatch ApplicationService
      -> application-owned Kafka dispatch port
          -> infrastructure Kafka sender adapter
              -> Kafka topic
  -> consumer-domain Kafka listener
  -> consumer-domain ApplicationService
```

The producer application service remains unaware of Kafka. Each producer domain owns the event publisher implementation in `infrastructure.event`, and Kafka dispatch orchestration must enter the same-domain application boundary before technical sending because outbox handlers are inbound adapters. The application boundary may use an application-owned technical port implemented by infrastructure.

Each consumer domain owns its Kafka listener in its own `infrastructure.event` package. The listener only performs transport adaptation: null checks, event type filtering, payload normalization, and command construction. Business rules, idempotency, domain repository access, and foreign synchronous `api.*` calls belong inside the same-domain `*ApplicationService`.

## DDD Boundary Rules

- Kafka listeners, local listeners, outbox handlers, event bridges, enqueuers, and scheduled jobs call only same-domain `*ApplicationService`.
- Inbound adapters do not call foreign `api.*`, foreign `application.*`, same-domain domain services/repositories/models, persistence mappers, dataobjects, or root legacy services.
- Application services own transactions, idempotency, use-case orchestration, domain calls, repository calls, domain event publication, and foreign owner-domain `api.query` / `api.action` collaboration.
- Application commands/results must express application semantics only. They must not expose HTTP transport types, Kafka record types, MyBatis mapper/dataobject types, or Spring Web upload types.
- Domain code must not depend on Spring, Kafka, outbox, infrastructure, MyBatis, HTTP DTOs, `api.*`, or `contracts.event`.
- `api.*` contracts must remain separate from `contracts.event`; synchronous `api.*` contracts must not import, return, or receive asynchronous event types.
- Consumers may parse foreign `contracts.event` in infrastructure adapters, but application boundaries should prefer same-domain command fields instead of passing the foreign envelope through unchanged.

## Topics And Keys

Use one topic per owner domain:

| Owner | Kafka topic | Outbox topic | Producer key rule |
| --- | --- | --- | --- |
| content | `content.events` | `eventbus.content` | `postId` for post events; `commentId` for comment events; target user id for moderation events |
| social | `social.events` | `eventbus.social` | `entityType:entityId` for like/follow; `blockerUserId` for block relation changes |
| user | `user.events` | `eventbus.user` | `userId` |

Kafka keys preserve ordering for the affected aggregate or projection key. If a future event affects multiple aggregates, choose the key that protects the consumer with the strictest ordering need and document the tradeoff in the contract table.

## Event Contracts

All Kafka messages use the published owner-domain `contracts.event` envelope:

```text
<Owner>ContractEvent(eventId, type, payload)
```

Payloads must be owner-domain published event models, not producer domain/internal application classes.

| Owner | Event type | Payload class | Required fields | Kafka key | Event id rule | Consumers |
| --- | --- | --- | --- | --- | --- | --- |
| content | `POST_PUBLISHED` | `PostPayload` | `postId`, `userId`, `createTime` when growth needs business date | `postId` | Stable source id such as `content:PostPublished:<postId>` or current `ce:post:published:*`; choose one before cutover | search, growth, user reward |
| content | `POST_UPDATED` | `PostPayload` | `postId` | `postId` | Unique message id if every update must be retained; include `postId` plus version/UUID-v7 | search |
| content | `POST_DELETED` | `PostPayload` | `postId` | `postId` | Stable source id such as `content:PostDeleted:<postId>` | search |
| content | `COMMENT_CREATED` | `CommentPayload` | `commentId`, `userId`, `targetUserId`, `createTime` | `commentId` | Stable source id such as `content:CommentCreated:<commentId>` | notice, growth, user reward |
| content | `COMMENT_DELETED` | `CommentPayload` | `commentId` | `commentId` | Stable source id such as `content:CommentDeleted:<commentId>` | future projections only unless explicitly wired |
| content | `MODERATION_ACTION_APPLIED` | `ModerationPayload` | `toUserId`, moderation action fields | `toUserId` | Unique message id including action/version | notice |
| social | `LIKE_CREATED` | `LikePayload` | `actorUserId`, `entityType`, `entityId`, `entityUserId`, `createTime` | `entityType:entityId` | Unique event id; reward/growth idempotency must use stable business source key if event id is UUID-v7 | notice, growth, user reward |
| social | `LIKE_REMOVED` | `LikePayload` | `actorUserId`, `entityType`, `entityId`, `entityUserId` | `entityType:entityId` | Unique event id; reward reversal idempotency must distinguish removal from creation | user reward |
| social | `FOLLOW_CREATED` | `FollowPayload` | `actorUserId`, `entityType`, `entityId`, `entityUserId` | `entityType:entityId` | Stable business id or unique message id plus consumer source key | notice |
| social | `BLOCK_RELATION_CHANGED` | `BlockPayload` | `blockerUserId`, `blockedUserId`, `blocked`, `version` | `blockerUserId` | Include `blockerUserId`, `blockedUserId`, and `version` or UUID-v7 with version in payload | IM policy / future projections |
| user | `USER_POLICY_CHANGED` | `UserPolicyChangedPayload` | `userId`, `userExists`, `canSendPrivate`, `version`, policy timestamps | `userId` | `ue:p:<dashlessUserId>:<version>` or `user-policy:<userId>:<version>`; must be monotonic by version | IM policy / future projections |

Contract evolution rules:

- Add optional fields only; do not remove or rename fields without a new event type or version.
- Consumers must tolerate unknown fields and unsupported event types.
- Required field additions need a staged rollout: producer writes both old and new-compatible forms before consumers require the new field.
- Event ids are immutable once published. If the final strategy changes from current `ce:/se:/ue:` prefixes, update producer tests, consumer idempotency tests, and this table in one change.

## Producer Design

Content, social, and user add outbox-backed publisher implementations. They replace local-only delivery as the default publisher behavior after consumers are idempotent and cutover configuration is ready. Application services keep depending on same-domain publisher interfaces.

Outbox payloads store the complete contract event envelope: `eventId`, `type`, and `payload`. Outbox topic names are separate from Kafka topic names: `eventbus.content`, `eventbus.social`, and `eventbus.user`.

Producer requirements:

- Publisher beans are enabled by `content.events.publisher=outbox-kafka`, `social.events.publisher=outbox-kafka`, and `user.events.publisher=outbox-kafka`.
- Local publisher beans are opt-in through `*.events.publisher=local`.
- If `events.outbox.enabled=false`, the outbox-backed publisher must not become the default bean unless another explicit event path is configured.
- Outbox records are written inside the source transaction. Kafka send happens after the outbox worker leases records.
- Serialization failure must fail the source transaction before an invalid outbox record is persisted.

## Consumer Design

Consumers subscribe to owner-domain topics and translate contract events into same-domain application commands.

| Consumer domain | Topic(s) | Event types | Application boundary | Idempotency / state rule |
| --- | --- | --- | --- | --- |
| notice | `content.events`, `social.events` | `COMMENT_CREATED`, `MODERATION_ACTION_APPLIED`, `LIKE_CREATED`, `FOLLOW_CREATED` | `NoticeProjectionApplicationService` reliable projection methods | Record `sourceEventId` once in `notice_projection_event_log` before creating a notice |
| search | `content.events` | `POST_PUBLISHED`, `POST_UPDATED`, `POST_DELETED` | `SearchPostProjectionApplicationService.projectPostFromOutbox` | Re-read current content projection with owner `api.query`; delete if missing/deleted to avoid stale resurrection |
| growth | `content.events`, `social.events` | `POST_PUBLISHED`, `COMMENT_CREATED`, `LIKE_CREATED` | `TaskProgressApplicationService` trigger methods | Existing task event log unique key by user/task/period/source event |
| user reward | `content.events`, `social.events` | `POST_PUBLISHED`, `COMMENT_CREATED`, `LIKE_CREATED`, `LIKE_REMOVED` | `UserRewardApplicationService.apply` | Wallet reward id uses deterministic `sourceEventId`; if event id is UUID-v7, derive stable business source ids for repeatable effects |
| IM policy | `social.events`, `user.events` | `BLOCK_RELATION_CHANGED`, `USER_POLICY_CHANGED` | Existing IM application boundary if/when folded into shared backbone | Existing IM policy idempotency remains authoritative until this migration explicitly covers it |

Listeners validate event type and payload before invoking application services. Invalid or unsupported event types are ignored only when they are outside the listener's responsibility. Malformed events for a supported type should either fail for retry/DLQ or be explicitly dropped with an error log and metric, depending on the project's Kafka error-handler policy.

## Reliability And Idempotency

- Source transactions write outbox records before commit, preserving source-of-truth consistency.
- Outbox dispatch must only mark success after Kafka send succeeds.
- Kafka send failures must bubble up to the outbox worker so existing outbox retry/backoff can retry.
- Consumers must be idempotent using either `eventId` or a stable business `sourceEventId` derived from payload fields.
- Missing `eventId` is invalid for side-effecting consumers and must no-op or fail before side effects.
- Search continues to project from current DB state through content `api.query` to avoid out-of-order delete/update resurrection.
- Reward processing must use stable source ids so repeated Kafka delivery does not duplicate wallet deltas. Like created and like removed must use distinct source ids.
- Consumer groups are per bounded context, not shared across unrelated projections.
- Consumer work that writes local state must run in the same-domain application transaction when the use case requires local idempotency plus side effect creation.

Retry and DLQ policy:

- Reuse the application's Kafka listener error handler if already centralized.
- Recoverable application failures should throw so Kafka retry/backoff applies.
- Poison messages after max retries should be routed to a domain-specific DLQ such as `content.events.notice.dlq` or `social.events.user-reward.dlq` if the runtime policy supports DLQs.
- Deserialization and payload shape failures must include `topic`, `partition`, `offset`, `eventId`, and `type` in logs.
- Unsupported event types should not enter retry loops.

## Configuration

Target runtime defaults:

```yaml
content:
  events:
    publisher: ${CONTENT_EVENTS_PUBLISHER:outbox-kafka}
    outbox-topic: ${CONTENT_EVENTS_OUTBOX_TOPIC:eventbus.content}
    kafka-topic: ${CONTENT_EVENTS_KAFKA_TOPIC:content.events}
social:
  events:
    publisher: ${SOCIAL_EVENTS_PUBLISHER:outbox-kafka}
    outbox-topic: ${SOCIAL_EVENTS_OUTBOX_TOPIC:eventbus.social}
    kafka-topic: ${SOCIAL_EVENTS_KAFKA_TOPIC:social.events}
user:
  events:
    publisher: ${USER_EVENTS_PUBLISHER:outbox-kafka}
    outbox-topic: ${USER_EVENTS_OUTBOX_TOPIC:eventbus.user}
    kafka-topic: ${USER_EVENTS_KAFKA_TOPIC:user.events}
  reward:
    kafka:
      consumer:
        group-id: ${USER_REWARD_KAFKA_CONSUMER_GROUP_ID:user-reward-projection}
        concurrency: ${USER_REWARD_KAFKA_CONSUMER_CONCURRENCY:3}
notice:
  kafka:
    consumer:
      group-id: ${NOTICE_KAFKA_CONSUMER_GROUP_ID:notice-projection}
      concurrency: ${NOTICE_KAFKA_CONSUMER_CONCURRENCY:3}
search:
  kafka:
    consumer:
      group-id: ${SEARCH_KAFKA_CONSUMER_GROUP_ID:search-post-projection}
      concurrency: ${SEARCH_KAFKA_CONSUMER_CONCURRENCY:3}
growth:
  task:
    kafka:
      consumer:
        group-id: ${GROWTH_TASK_KAFKA_CONSUMER_GROUP_ID:growth-task-progress}
        concurrency: ${GROWTH_TASK_KAFKA_CONSUMER_CONCURRENCY:3}
```

Configuration rules:

- Do not duplicate existing top-level YAML keys when adding nested event defaults.
- Update Nacos policy/config files only if they already carry application Kafka runtime defaults.
- Keep `CONTENT_EVENTS_PUBLISHER=local`, `SOCIAL_EVENTS_PUBLISHER=local`, and `USER_EVENTS_PUBLISHER=local` as rollback switches until production cutover is complete.
- If both local and Kafka paths can run at once, the consumer must have proven idempotency before dual-run is allowed.

## Rollout And Migration Strategy

| Phase | Action | Dual-run allowed? | Verification gate | Rollback |
| --- | --- | --- | --- | --- |
| 1 | Add outbox publishers and Kafka dispatch path behind config | No default cutover yet | Publisher/handler unit tests pass; source application tests still pass | Set `*.events.publisher=local` |
| 2 | Add consumer Kafka listeners behind existing topics/groups | Only for idempotent consumers | Listener tests cover typed/map payloads, unsupported events, duplicate events | Disable listener bean or pause consumer group |
| 3 | Add/verify idempotency stores and source ids | Required before dual-run | Duplicate delivery tests pass for notice/growth/reward/search | Keep old local path only |
| 4 | Remove direct synchronous reward side effects from content/social | No | Content/social tests prove only owner-domain events are emitted | Revert direct-call removal only if Kafka reward path is disabled |
| 5 | Switch default publisher to outbox-kafka | Yes, only where idempotent | Focused event backbone tests, affected application tests, and `*ArchTest` pass | Set env publisher switches to `local` |
| 6 | Retire old projection-specific listeners/outbox handlers | No until monitored stable | No duplicate notices/rewards/progress/search updates; lag and DLQ clean | Re-enable old beans and reset publisher switch |

Do not delete legacy classes in this batch unless a task proves they are unreachable under explicit rollback configuration.

## Testing

Required focused tests:

- Outbox publisher tests verify topic, event id, payload envelope, key derivation, serialization failure, and null/missing-id no-op behavior.
- Outbox dispatch tests verify Kafka topic/key/value, typed payload conversion, and failure propagation to outbox retry.
- Kafka listener tests verify event filtering, map-like payload conversion, command mapping, missing field handling, unsupported event no-op, and retry-worthy failures.
- Idempotency tests verify duplicate event delivery does not create duplicate notices, rewards, growth progress, or search side effects.
- Configuration tests verify local publisher fallback and outbox-kafka default once cutover is enabled.
- Contract compatibility tests verify producers and consumers agree on the published `contracts.event` classes.
- Architecture tests verify DDD boundaries after adding listeners, handlers, ports, repositories, and infrastructure classes.

Suggested commands from `backend`:

```bash
mvn -q -pl :community-app -am -Dtest='OutboxContentEventPublisherTest,ContentEventKafkaOutboxHandlerTest,LocalContentEventPublisherTest' test
mvn -q -pl :community-app -am -Dtest='OutboxSocialDomainEventPublisherTest,SocialEventKafkaOutboxHandlerTest,OutboxUserPolicyEventPublisherTest,UserEventKafkaOutboxHandlerTest,LocalSocialDomainEventPublisherTest,LocalUserPolicyEventPublisherTest' test
mvn -q -pl :community-app -am -Dtest='NoticeProjection*Test,NoticeProjectionKafkaListenerTest,SearchPostProjectionKafkaListenerTest,PostOutboxHandlerTest' test
mvn -q -pl :community-app -am -Dtest='TaskProgressEventBackboneKafkaListenerTest,UserRewardKafkaListenerTest,PostPublishingApplicationServiceTest,LikeApplicationServiceTest,CommentApplicationServiceTest' test
mvn test -pl :community-app -Dtest='*ArchTest'
```

Additional boundary scans:

```bash
rg -n 'contracts\.event' community-app/src/main/java/com/nowcoder/community/*/api -g '*.java'
rg -n 'KafkaTemplate|TraceKafkaSender|Mapper|DataObject|MultipartFile|ResponseEntity|ResponseCookie|Resource|MediaType|Servlet' community-app/src/main/java/com/nowcoder/community/*/application -g '*.java'
rg -n '^import .*\.(controller|application|infrastructure|mapper|api)\.|^import org\.springframework' community-app/src/main/java/com/nowcoder/community/*/domain -g '*.java'
```

## Observability

Emit structured logs and metrics for:

- Outbox backlog by topic and retry count.
- Kafka send success/failure by owner topic.
- Consumer lag by group id and topic.
- Listener processing success/failure by event type.
- Duplicate idempotency skips by consumer domain.
- DLQ count and oldest DLQ age.

Logs for failures must include `eventId`, `type`, owner topic, consumer domain, and source outbox id when available.

## Risks

- Duplicate side effects if old local listeners and new Kafka consumers run at the same time without idempotency.
- Event ordering issues for post update/delete and reward reversal if Kafka keys or source ids are unstable.
- Schema coupling if consumers depend on producer internal payload classes instead of published `contracts.event` models.
- Consumer lag can delay notifications, search index freshness, growth progress, rewards, and IM policy projection.
- Direct `*KafkaOutboxHandler -> KafkaTemplate` violates the target handler boundary and must be refactored behind a same-domain application boundary.
- Passing foreign `contracts.event` envelopes into application commands can blur ownership; normalize to same-domain command fields where feasible.
- Keeping `content.events.publisher=local` or `social.events.publisher=local` as default after adding Kafka consumers can hide missing cutover configuration during tests.

## Acceptance Criteria

- Content, social, and user contract events can be delivered through outbox-to-Kafka.
- Consumer domains receive Kafka events through their own inbound adapters and call only same-domain application services.
- Outbox dispatch follows `handler -> same-domain ApplicationService -> application port -> infrastructure Kafka sender`.
- Re-delivering the same Kafka event does not create duplicate notifications, rewards, search documents, growth progress, or IM policy state.
- Default runtime configuration can switch to outbox-kafka while `*.events.publisher=local` remains an explicit rollback path.
- Existing DDD ArchUnit guardrails pass.
- The application can disable old local projection paths after Kafka consumers are enabled and verified.

# Community Content Platform High-Concurrency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the first production-ready slice of the high-concurrency community content platform inside `community-app` without splitting business services.

**Architecture:** Keep owner writes centralized in the existing modular monolith, then scale read traffic through Redis-backed feed/detail models, Kafka-backed projections, and explicit degradation controls. The first deliverable hardens the event backbone, extends hot-feed and follow-feed reads, separates detail-shell and comment hot paths, and adds replay-safe search/notice/analytics runtime controls.

**Tech Stack:** Spring Boot, Maven, MyBatis, Redis, Kafka, Elasticsearch, JUnit 5, Mockito, ArchUnit

## Global Constraints

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
- All backend business code in `backend/community-app` MUST use strict DDD Tactical Layering.
- Inbound adapters include controllers, local event listeners, outbox handlers, event bridges, enqueuers, and scheduled jobs. They adapt input and call same-domain application services.
- `application` MUST NOT depend directly on MyBatis mapper or dataobject types.
- After changing backend architecture rules or package boundaries, run `cd backend && mvn test -pl :community-app -Dtest='*ArchTest'`.

---

## File Map

### Task 1: Event Backbone Metadata

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/ContentContractEvent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/SocialContractEvent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/PostPayload.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentPostPayloadAssembler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/BlockPayload.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/BlockRelationChangedDomainEvent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisherTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisherTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisherTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisherTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java`

### Task 2: Hot Feed Projection Hardening

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentFeedPolicyProperties.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/ProjectPostHotFeedCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostFeedCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCacheTest.java`

### Task 3: Follow Feed Pull-Merge Read Model

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedReadApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCache.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FollowFeedReadApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCacheTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/FeedController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java`
- Modify: `backend/community-app/src/main/resources/mapper/discusspost-mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/api/query/SocialFollowQueryApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/api/SocialFollowQueryApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/FollowRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisFollowRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/FollowMapper.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/api/SocialFollowQueryApiAdapterTest.java`

### Task 4: Detail Shell, Comment Hot Path, And Counter Hygiene

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentPageCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCache.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCacheTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostCounterApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostCounterSnapshotFlushJob.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentReadApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostCounterApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java`

### Task 5: Search And Notice Projection Guardrails

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPolicyProperties.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/application/command/ProjectPostOutboxCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/SearchPostProjectionKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticePolicyProperties.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/command/ProjectContentNoticeCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/command/ProjectSocialNoticeCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/SearchPostProjectionKafkaListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java`

### Task 6: Analytics Async Capture, Ops Toggles, And Verification

- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestEvent.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestKafkaListener.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestKafkaListenerTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsRequestCaptureFilter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsIngestProperties.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/business-logic/workflows/notice-search-analytics-ops.md`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsRequestCaptureFilterTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/analytics/controller/AnalyticsControllerUnitTest.java`

### Task 1: Event Backbone Metadata

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/ContentContractEvent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/SocialContractEvent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/PostPayload.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentPostPayloadAssembler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/BlockPayload.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/BlockRelationChangedDomainEvent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisherTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisherTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisherTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisherTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java`

**Interfaces:**
- Consumes: existing `PostPayload assemble(UUID postId)` from `ContentPostPayloadAssembler`
- Produces: `ContentContractEvent(String eventId, UUID aggregateId, String aggregateType, String type, Instant occurredAt, long version, Object payload)`
- Produces: `SocialContractEvent(String eventId, UUID aggregateId, String aggregateType, String type, Instant occurredAt, long version, Object payload)`
- Produces: `void dispatch(DispatchContentEventCommand command)` validating `eventId`, `aggregateId`, `type`, `occurredAt`, and `version`
- Produces: `void dispatch(DispatchSocialEventCommand command)` validating `eventId`, `aggregateId`, `type`, `occurredAt`, and `version`

- [x] **Step 1: Write the failing tests**

```java
@Test
void publishPostPublishedShouldSerializeBackboneMetadata() {
    UUID postId = UUID.randomUUID();
    PostPayload payload = new PostPayload();
    payload.setPostId(postId);
    payload.setCreateTime(Instant.parse("2026-07-06T08:00:00Z"));

    publisher.publishPostPublished(payload);

    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(store).enqueue(eq("content:PostPublished:" + postId), eq("eventbus.content"), eq(postId.toString()), payloadCaptor.capture());
    ContentContractEvent event = jsonCodec.fromJson(payloadCaptor.getValue(), ContentContractEvent.class);
    assertThat(event.aggregateId()).isEqualTo(postId);
    assertThat(event.aggregateType()).isEqualTo("post");
    assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-07-06T08:00:00Z"));
    assertThat(event.version()).isPositive();
}

@Test
void dispatchShouldRejectMissingAggregateMetadata() {
    String payloadJson = """
            {"eventId":"ce:1","type":"post.published","payload":{"postId":"%s"}}
            """.formatted(UUID.randomUUID());

    assertThatThrownBy(() -> applicationService.dispatch(new DispatchContentEventCommand("post:" + UUID.randomUUID(), payloadJson)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("aggregateId");
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl :community-app -Dtest=OutboxContentEventPublisherTest,ContentEventDispatchApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventDispatchApplicationServiceTest`

Expected: FAIL because `ContentContractEvent` and `SocialContractEvent` do not yet expose `aggregateId`, `aggregateType`, `occurredAt`, or `version`.

- [x] **Step 3: Write the minimal implementation**

```java
public record ContentContractEvent(
        String eventId,
        UUID aggregateId,
        String aggregateType,
        String type,
        Instant occurredAt,
        long version,
        Object payload
) {
}
```

```java
public record SocialContractEvent(
        String eventId,
        UUID aggregateId,
        String aggregateType,
        String type,
        Instant occurredAt,
        long version,
        Object payload
) {
}
```

```java
public class PostPayload implements Serializable {

    private Instant updateTime;

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }
}
```

```java
payload.setUpdateTime(post.getUpdateTime() == null ? null : post.getUpdateTime().toInstant());
```

```java
@Override
public void publishPostPublished(PostPayload payload) {
    UUID postId = payload == null ? null : payload.getPostId();
    if (postId == null) {
        return;
    }
    publish(
            "content:PostPublished:" + postId,
            ContentEventTypes.POST_PUBLISHED,
            postId.toString(),
            postId,
            "post",
            payload.getCreateTime(),
            payload.getCreateTime() == null ? System.currentTimeMillis() : payload.getCreateTime().toEpochMilli(),
            payload
    );
}
```

```java
public void dispatch(DispatchContentEventCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    ContentContractEvent contractEvent = parseContractEvent(command.payloadJson());
    if (contractEvent.aggregateId() == null) {
        throw new IllegalStateException("content event outbox payload missing aggregateId");
    }
    if (!StringUtils.hasText(contractEvent.aggregateType())) {
        throw new IllegalStateException("content event outbox payload missing aggregateType");
    }
    if (contractEvent.occurredAt() == null) {
        throw new IllegalStateException("content event outbox payload missing occurredAt");
    }
    if (contractEvent.version() <= 0L) {
        throw new IllegalStateException("content event outbox payload missing version");
    }
    dispatcher.dispatch(command.eventKey(), contractEvent);
}
```

```java
private void publish(String eventId, String type, String key, UUID aggregateId, String aggregateType, Instant occurredAt, long version, Object payload) {
    String payloadJson;
    try {
        payloadJson = jsonCodec.toJson(new ContentContractEvent(
                eventId,
                aggregateId,
                aggregateType,
                type,
                occurredAt,
                version,
                payload
        ));
    } catch (JsonCodecException e) {
        throw new IllegalStateException("content event outbox payload serialization failed: " + type, e);
    }
    store.enqueue(eventId, topic, key, payloadJson);
}
```

```java
private void publish(String eventId, String type, String key, UUID aggregateId, String aggregateType, Instant occurredAt, long version, Object payload) {
    String payloadJson;
    try {
        payloadJson = jsonCodec.toJson(new SocialContractEvent(
                eventId,
                aggregateId,
                aggregateType,
                type,
                occurredAt,
                version,
                payload
        ));
    } catch (JsonCodecException e) {
        throw new IllegalStateException("social event outbox payload serialization failed: " + type, e);
    }
    store.enqueue(eventId, topic, key, payloadJson);
}
```

Task 1 `version` requirement is a positive backbone value. Use the existing owner-authored version where the current codebase exposes one (block relation changes), and use the timestamp-derived positive fallback shown above for post/comment/like/follow events that do not yet expose owner revisions. Replay-safe consumers receive source metadata in Task 2 and Task 5 before they use it for projection guardrails.

- [x] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl :community-app -Dtest=OutboxContentEventPublisherTest,ContentEventDispatchApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventDispatchApplicationServiceTest`

Expected: PASS with all four test classes green.

- [x] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/ContentContractEvent.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/SocialContractEvent.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/PostPayload.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentPostPayloadAssembler.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisher.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisher.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/BlockPayload.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/BlockRelationChangedDomainEvent.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisherTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisherTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisherTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisherTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java
git commit -m "feat: add event backbone metadata"
```

### Task 2: Hot Feed Projection Hardening

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentFeedPolicyProperties.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/ProjectPostHotFeedCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostFeedCache.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCacheTest.java`

**Interfaces:**
- Consumes: `ContentContractEvent` and `SocialContractEvent` with backbone metadata from Task 1
- Produces: `record ProjectPostHotFeedCommand(UUID postId, UUID boardId, double signalWeight, String sourceEventId, long sourceVersion)`
- Produces: `String readRankVersion()` and `void writeRankVersion(String rankVersion)` on `PostFeedCache`
- Produces: `FeedPageResult listGlobalHotFeed(UUID currentUserId, String cursor, int size)` returning the cache-backed `rankVersion`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void projectShouldPersistRankVersionAndRemoveHiddenPosts() {
    UUID postId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    DiscussPost post = new DiscussPost();
    post.setId(postId);
    post.setCategoryId(boardId);
    post.setDeleted(false);
    post.setScore(42.0);

    when(postContentRepository.getByIdAllowDeleted(postId)).thenReturn(post);

    service.project(new ProjectPostHotFeedCommand(postId, boardId, 1.5d, "ce:1", 42L));

    verify(postFeedCache).writeRankVersion("hot-v2");
    verify(postFeedCache).upsertGlobalHot(postId, 42.0, "hot-v2");
    verify(postFeedCache).upsertBoardHot(boardId, postId, 42.0, "hot-v2");
}

@Test
void listGlobalHotFeedShouldReturnCacheRankVersion() {
    when(postFeedCache.readRankVersion()).thenReturn("hot-v2");
    when(postFeedCache.readGlobalHotIds("", 20)).thenReturn(List.of());
    when(postContentRepository.listPosts(0, 20, PostContentRepository.ORDER_HOT)).thenReturn(List.of());

    FeedPageResult page = service.listGlobalHotFeed(null, "", 20);

    assertThat(page.rankVersion()).isEqualTo("hot-v2");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl :community-app -Dtest=PostHotFeedProjectionApplicationServiceTest,FeedReadApplicationServiceTest,PostHotFeedProjectionKafkaListenerTest,RedisPostFeedCacheTest`

Expected: FAIL because the command, cache, and read service do not yet expose source metadata or cache-managed rank version.

- [ ] **Step 3: Write the minimal implementation**

```java
@ConfigurationProperties(prefix = "content.feed")
public class ContentFeedPolicyProperties {

    private String hotRankVersion = "hot-v2";
    private boolean latestFallbackEnabled = true;

    public String getHotRankVersion() {
        return hotRankVersion;
    }

    public void setHotRankVersion(String hotRankVersion) {
        this.hotRankVersion = hotRankVersion;
    }

    public boolean isLatestFallbackEnabled() {
        return latestFallbackEnabled;
    }

    public void setLatestFallbackEnabled(boolean latestFallbackEnabled) {
        this.latestFallbackEnabled = latestFallbackEnabled;
    }
}
```

```java
public record ProjectPostHotFeedCommand(
        UUID postId,
        UUID boardId,
        double signalWeight,
        String sourceEventId,
        long sourceVersion
) {
}
```

```java
public interface PostFeedCache {

    List<UUID> readGlobalHotIds(String cursor, int size);

    List<UUID> readBoardHotIds(UUID boardId, String cursor, int size);

    void upsertGlobalHot(UUID postId, double score, String rankVersion);

    void upsertBoardHot(UUID boardId, UUID postId, double score, String rankVersion);

    void writeRankVersion(String rankVersion);

    String readRankVersion();

    void remove(UUID postId, UUID boardId);
}
```

```java
@Override
public void writeRankVersion(String rankVersion) {
    if (!StringUtils.hasText(rankVersion)) {
        return;
    }
    redisTemplate.opsForValue().set(GLOBAL_HOT_KEY + ":rank-version", rankVersion);
}

@Override
public String readRankVersion() {
    String value = redisTemplate.opsForValue().get(GLOBAL_HOT_KEY + ":rank-version");
    return StringUtils.hasText(value) ? value : "hot-v2";
}
```

```java
@Transactional
public void project(ProjectPostHotFeedCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    DiscussPost post = postContentRepository.getByIdAllowDeleted(command.postId());
    String rankVersion = policyProperties.getHotRankVersion();
    postFeedCache.writeRankVersion(rankVersion);
    if (post == null || post.isDeleted() || post.getStatus() != 0) {
        evictReadModels(command.postId());
        return;
    }
    long likeCount = likeQueryPort.countPostLikes(command.postId());
    double score = postHotnessDomainService.recomputeScore(post, likeCount, command.signalWeight());
    postContentRepository.updateScore(command.postId(), score);
    postCounterCache.updateScore(command.postId(), score);
    postFeedCache.upsertGlobalHot(command.postId(), score, rankVersion);
    if (post.getCategoryId() != null) {
        postFeedCache.upsertBoardHot(post.getCategoryId(), command.postId(), score, rankVersion);
    }
}
```

```java
FeedPageResult listHotFeed(String cursor, int size, UUID boardId) {
    FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
    int requestedLimit = state.size() > 0 ? normalizeRequestedSize(state.size()) : normalizeRequestedSize(size);
    LoadedFeedPage page = loadHotPage(cursor, state.page(), requestedLimit, boardId);
    String nextCursor = page.hasNext() ? feedCursorCodec.encodePage(state.page() + 1, requestedLimit) : "";
    return new FeedPageResult(page.items(), nextCursor, postFeedCache.readRankVersion());
}
```

```yaml
content:
  feed:
    hot-rank-version: ${CONTENT_FEED_HOT_RANK_VERSION:hot-v2}
    latest-fallback-enabled: ${CONTENT_FEED_LATEST_FALLBACK_ENABLED:true}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl :community-app -Dtest=PostHotFeedProjectionApplicationServiceTest,FeedReadApplicationServiceTest,PostHotFeedProjectionKafkaListenerTest,RedisPostFeedCacheTest`

Expected: PASS with Redis feed cache, listener, and read service tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentFeedPolicyProperties.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/command/ProjectPostHotFeedCommand.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/FeedReadApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/PostFeedCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListener.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCache.java \
        backend/community-app/src/main/resources/application.yml \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/PostHotFeedProjectionApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/FeedReadApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisPostFeedCacheTest.java
git commit -m "feat: harden hot feed projection"
```

### Task 3: Follow Feed Pull-Merge Read Model

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedReadApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCache.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/FollowFeedReadApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCacheTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/FeedController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java`
- Modify: `backend/community-app/src/main/resources/mapper/discusspost-mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/api/query/SocialFollowQueryApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/api/SocialFollowQueryApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/FollowRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisFollowRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/FollowMapper.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/api/SocialFollowQueryApiAdapterTest.java`

**Interfaces:**
- Consumes: `List<UUID> listFolloweeIds(UUID userId, int limit)` from `SocialFollowQueryApi`
- Consumes: `List<DiscussPost> listRecentVisiblePostsByAuthorIds(List<UUID> authorIds, int limit)` from `PostContentRepository`
- Produces: `FeedPageResult listFollowFeed(UUID currentUserId, String cursor, int size)` from `FollowFeedReadApplicationService`
- Produces: `List<UUID> getOrLoadPage(UUID userId, int page, int size, Supplier<List<UUID>> loader)` from `FollowFeedCache`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void listFollowFeedShouldMergeFolloweeRecentPostsAndCachePage() {
    UUID viewerId = UUID.randomUUID();
    UUID authorA = UUID.randomUUID();
    UUID authorB = UUID.randomUUID();
    DiscussPost post1 = post(authorA, Instant.parse("2026-07-06T10:00:00Z"));
    DiscussPost post2 = post(authorB, Instant.parse("2026-07-06T09:00:00Z"));

    when(followQueryApi.listFolloweeIds(viewerId, 200)).thenReturn(List.of(authorA, authorB));
    when(postContentRepository.listRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 200)).thenReturn(List.of(post1, post2));
    when(followFeedCache.getOrLoadPage(eq(viewerId), eq(0), eq(20), any())).thenAnswer(invocation -> invocation.<Supplier<List<UUID>>>getArgument(3).get());

    FeedPageResult page = service.listFollowFeed(viewerId, "", 20);

    assertThat(page.items()).extracting(PostSummaryResult::id).containsExactly(post1.getId(), post2.getId());
    verify(followFeedCache).getOrLoadPage(eq(viewerId), eq(0), eq(20), any());
}

@Test
void globalControllerShouldExposeFollowFeedEndpoint() throws Exception {
    UUID viewerId = UUID.randomUUID();
    when(followFeedReadApplicationService.listFollowFeed(eq(viewerId), eq(""), eq(20)))
            .thenReturn(new FeedPageResult(List.of(), "", "follow-v1"));

    mockMvc.perform(get("/api/feed/follow").with(authentication(authentication(viewerId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rankVersion").value("follow-v1"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest`

Expected: FAIL because there is no `FollowFeedReadApplicationService`, no `/api/feed/follow` endpoint, and no social query to list followee ids.

- [ ] **Step 3: Write the minimal implementation**

```java
public interface SocialFollowQueryApi {

    boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId);

    long followeeCount(UUID userId, int entityType);

    long followerCount(int entityType, UUID entityId);

    List<UUID> listFolloweeIds(UUID userId, int limit);
}
```

```java
public interface FollowRepository {

    boolean follow(UUID actorUserId, int entityType, UUID entityId, long now);

    void unfollow(UUID actorUserId, int entityType, UUID entityId);

    boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId);

    long countFolloweesExcludingBlocked(UUID userId, int entityType, BlockRepository blockRepository);

    long countFollowersExcludingBlocked(int entityType, UUID entityId, BlockRepository blockRepository);

    List<FollowRelation> listFolloweesExcludingBlocked(UUID userId, int entityType, BlockRepository blockRepository, int offset, int size);

    List<UUID> listFolloweeIds(UUID userId, int entityType, int limit);
}
```

```java
public interface PostContentRepository {

    List<DiscussPost> listRecentVisiblePostsByAuthorIds(List<UUID> authorIds, int limit);
}
```

```xml
<select id="listRecentVisiblePostsByAuthorIds" resultType="com.nowcoder.community.content.domain.model.DiscussPost">
  select *
  from discuss_post
  where status = 0
    and status != 2
    and user_id in
    <foreach collection="authorIds" item="authorId" open="(" separator="," close=")">
      #{authorId}
    </foreach>
  order by create_time desc, id desc
  limit #{limit}
</select>
```

```java
@Service
public class FollowFeedReadApplicationService {

    private final SocialFollowQueryApi followQueryApi;
    private final PostContentRepository postContentRepository;
    private final FollowFeedCache followFeedCache;
    private final PostSummaryAssembler postSummaryAssembler;
    private final FeedCursorCodec feedCursorCodec;

    public FeedPageResult listFollowFeed(UUID currentUserId, String cursor, int size) {
        if (currentUserId == null) {
            throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
        }
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        int page = Math.max(0, state.page());
        int requestedSize = Math.min(50, Math.max(1, state.size() > 0 ? state.size() : size));
        List<UUID> pageIds = followFeedCache.getOrLoadPage(currentUserId, page, requestedSize, () -> loadFollowFeedIds(currentUserId, requestedSize));
        List<PostSummaryResult> items = pageIds.isEmpty() ? List.of() : postContentRepository.listPostsByIds(pageIds).stream()
                .map(post -> postSummaryAssembler.assemble(post, null, List.of(), ""))
                .toList();
        String nextCursor = items.size() < requestedSize ? "" : feedCursorCodec.encodePage(page + 1, requestedSize);
        return new FeedPageResult(items, nextCursor, "follow-v1");
    }

    private List<UUID> loadFollowFeedIds(UUID currentUserId, int requestedSize) {
        List<UUID> authorIds = followQueryApi.listFolloweeIds(currentUserId, 200);
        if (authorIds.isEmpty()) {
            return List.of();
        }
        return postContentRepository.listRecentVisiblePostsByAuthorIds(authorIds, requestedSize * 4).stream()
                .sorted(Comparator.comparing(DiscussPost::getCreateTime).reversed())
                .limit(requestedSize)
                .map(DiscussPost::getId)
                .toList();
    }
}
```

```java
@GetMapping("/feed/follow")
public Result<FeedPageResponse> follow(
        Authentication authentication,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer size
) {
    UUID currentUserId = CurrentUser.tryUserUuid(authentication);
    FeedPageResult page = followFeedReadApplicationService.listFollowFeed(currentUserId, cursor, size == null ? 20 : size);
    return Result.ok(FeedPageResponse.from(page));
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl :community-app -Dtest=FollowFeedReadApplicationServiceTest,RedisFollowFeedCacheTest,FeedControllerTest,FollowApplicationServiceTest,SocialFollowQueryApiAdapterTest`

Expected: PASS with the new follow-feed read model and controller path covered.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/FollowFeedReadApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/controller/FeedController.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java \
        backend/community-app/src/main/resources/mapper/discusspost-mapper.xml \
        backend/community-app/src/main/java/com/nowcoder/community/social/api/query/SocialFollowQueryApi.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/api/SocialFollowQueryApiAdapter.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/FollowRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisFollowRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/FollowMapper.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/FollowFeedReadApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisFollowFeedCacheTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/controller/FeedControllerTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/api/SocialFollowQueryApiAdapterTest.java
git commit -m "feat: add pull-merge follow feed"
```

### Task 4: Detail Shell, Comment Hot Path, And Counter Hygiene

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentPageCache.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCache.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCacheTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostCounterApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostCounterSnapshotFlushJob.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentReadApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostCounterApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java`

**Interfaces:**
- Produces: `CommentPageCache.getRootPage(UUID postId, String cursor, int size): CommentPageResult`
- Produces: `CommentPageCache.putRootPage(UUID postId, String cursor, int size, CommentPageResult result): void`
- Produces: `CommentPageCache.evictPost(UUID postId): void`
- Produces: `int flushSnapshots(int batchSize)` continuing to persist only dirty post counters

- [ ] **Step 1: Write the failing tests**

```java
@Test
void listRootCommentsShouldServeFirstPageFromCache() {
    UUID postId = UUID.randomUUID();
    CommentPageResult cached = new CommentPageResult(List.of(commentResult(postId)), "next");

    when(commentPageCache.getRootPage(postId, "", 10)).thenReturn(cached);

    CommentPageResult result = service.listRootComments(postId, "", 10);

    assertThat(result).isSameAs(cached);
    verifyNoInteractions(commentContentRepository);
}

@Test
void createShouldEvictCommentPageCacheAfterCommit() {
    UUID postId = UUID.randomUUID();
    CommentCreateResult result = new CommentCreateResult(UUID.randomUUID(), postId);

    when(domainService.createComment(any())).thenReturn(result);

    applicationService.create(new CreateCommentCommand(postId, UUID.randomUUID(), "body", null, null));

    verify(commentPageCache).evictPost(postId);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl :community-app -Dtest=CommentReadApplicationServiceTest,CommentApplicationServiceTest,PostCounterApplicationServiceTest,PostReadApplicationServiceTest`

Expected: FAIL because comment first-page caching and write-side eviction do not exist yet.

- [ ] **Step 3: Write the minimal implementation**

```java
public interface CommentPageCache {

    CommentPageResult getRootPage(UUID postId, String cursor, int size);

    void putRootPage(UUID postId, String cursor, int size, CommentPageResult result);

    void evictPost(UUID postId);
}
```

```java
public CommentPageResult listRootComments(UUID postId, String cursor, Integer size) {
    int requestedSize = requestedSize(cursor, size);
    if ("".equals(cursor == null ? "" : cursor.trim())) {
        CommentPageResult cached = commentPageCache.getRootPage(postId, "", requestedSize);
        if (cached != null) {
            return cached;
        }
    }
    FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
    List<Comment> rows = commentContentPort.listRootComments(postId, state.page(), requestedSize);
    List<CommentResult> items = rows == null ? List.of() : rows.stream().map(this::toResult).toList();
    CommentPageResult result = new CommentPageResult(items, nextCursor(cursor, state.page(), requestedSize, items.size()));
    if (state.page() == 0) {
        commentPageCache.putRootPage(postId, "", requestedSize, result);
    }
    return result;
}
```

```java
@Transactional
public CommentCreateResult create(CreateCommentCommand command) {
    CommentCreateResult result = doCreate(command);
    AfterCommitExecutor.runAfterCommit(() -> commentPageCache.evictPost(result.postId()));
    return result;
}
```

```java
public int flushSnapshots(int batchSize) {
    int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
    List<UUID> requested = postCounterCache.dirtyPostIds(safeBatchSize);
    if (requested.isEmpty()) {
        return 0;
    }
    List<UUID> flushed = new ArrayList<>();
    for (UUID postId : requested) {
        PostCounterSnapshot snapshot = read(postId);
        postCounterSnapshotRepository.upsert(postId, snapshot.viewCount(), snapshot.likeCount(), snapshot.commentCount(), snapshot.bookmarkCount(), snapshot.score());
        flushed.add(postId);
    }
    postCounterCache.clearDirtyPostIds(flushed);
    return flushed.size();
}
```

```yaml
content:
  comments:
    first-page-cache-ttl-seconds: ${CONTENT_COMMENT_FIRST_PAGE_CACHE_TTL_SECONDS:15}
  counters:
    flush-batch-size: ${CONTENT_COUNTER_FLUSH_BATCH_SIZE:200}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl :community-app -Dtest=CommentReadApplicationServiceTest,CommentApplicationServiceTest,PostCounterApplicationServiceTest,PostReadApplicationServiceTest,RedisCommentPageCacheTest`

Expected: PASS with comment read, write-side eviction, and counter flush coverage green.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentPageCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCache.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentReadApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/application/PostCounterApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/job/PostCounterSnapshotFlushJob.java \
        backend/community-app/src/main/resources/application.yml \
        backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/RedisCommentPageCacheTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentReadApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/PostCounterApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/application/PostReadApplicationServiceTest.java
git commit -m "feat: cache first-page comments and tighten counter flush"
```

### Task 5: Search And Notice Projection Guardrails

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPolicyProperties.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/application/command/ProjectPostOutboxCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/SearchPostProjectionKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticePolicyProperties.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/command/ProjectContentNoticeCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/command/ProjectSocialNoticeCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/SearchPostProjectionKafkaListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java`

**Interfaces:**
- Produces: `ProjectPostOutboxCommand(UUID postId, String sourceEventId, long sourceVersion)`
- Produces: `ProjectContentNoticeCommand(String sourceEventId, long sourceVersion, String eventType, Object payload)`
- Produces: `ProjectSocialNoticeCommand(String sourceEventId, long sourceVersion, String eventType, Object payload)`
- Produces: `SearchPolicyProperties.projectionEnabled` and `NoticePolicyProperties.projectionEnabled`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void projectPostFromOutboxShouldRejectBlankSourceEventId() {
    assertThatThrownBy(() -> service.projectPostFromOutbox(new ProjectPostOutboxCommand(UUID.randomUUID(), "", 0L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("source event id");
}

@Test
void reliableContentProjectionShouldSkipWhenProjectionDisabled() {
    NoticePolicyProperties properties = new NoticePolicyProperties();
    properties.getChannels().setInAppEnabled(true);
    properties.setProjectionEnabled(false);
    NoticeProjectionApplicationService service = new NoticeProjectionApplicationService(jsonCodec, noticeApplicationService, new NoticeProjectionDomainService(), properties, recorder);

    service.projectContentEventReliably(new ProjectContentNoticeCommand("ce:1", 42L, ContentEventTypes.COMMENT_CREATED, commentPayload()));

    verifyNoInteractions(noticeApplicationService);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl :community-app -Dtest=SearchPostProjectionApplicationServiceTest,SearchPostProjectionKafkaListenerTest,NoticeProjectionApplicationServiceTest,NoticeProjectionKafkaListenerTest`

Expected: FAIL because commands do not yet carry source version, and search/notice policy objects do not expose projection toggles.

- [ ] **Step 3: Write the minimal implementation**

```java
public record ProjectPostOutboxCommand(UUID postId, String sourceEventId, long sourceVersion) {
}
```

```java
public class SearchPolicyProperties {

    private boolean projectionEnabled = true;

    public boolean isProjectionEnabled() {
        return projectionEnabled;
    }

    public void setProjectionEnabled(boolean projectionEnabled) {
        this.projectionEnabled = projectionEnabled;
    }
}
```

```java
public void projectPostFromOutbox(ProjectPostOutboxCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (!StringUtils.hasText(command.sourceEventId())) {
        throw new IllegalStateException("search projection source event id is blank");
    }
    if (!policyProperties.isProjectionEnabled() || command.postId() == null) {
        return;
    }
    PostScanView.PostProjectionView projection = postScanQueryApi.getPostProjectionAllowDeleted(command.postId());
    if (projection == null || projection.postId() == null) {
        searchApplicationService.deletePost(new DeleteIndexedPostCommand(command.postId()));
        return;
    }
    searchApplicationService.syncPostProjection(PostSearchPayloadAssembler.toSyncCommand(projection));
}
```

```java
public class NoticePolicyProperties {

    private boolean projectionEnabled = true;
    private final Channels channels = new Channels();

    public boolean isProjectionEnabled() {
        return projectionEnabled;
    }

    public void setProjectionEnabled(boolean projectionEnabled) {
        this.projectionEnabled = projectionEnabled;
    }
}
```

```java
private boolean shouldProject(NoticeProjection projection) {
    if (!noticePolicyProperties.isProjectionEnabled()) {
        return false;
    }
    if (!noticePolicyProperties.getChannels().isInAppEnabled()) {
        return false;
    }
    return noticeProjectionDomainService.shouldProject(projection);
}
```

```yaml
search:
  projection-enabled: ${SEARCH_PROJECTION_ENABLED:true}
notice:
  projection-enabled: ${NOTICE_PROJECTION_ENABLED:true}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl :community-app -Dtest=SearchPostProjectionApplicationServiceTest,SearchPostProjectionKafkaListenerTest,NoticeProjectionApplicationServiceTest,NoticeProjectionKafkaListenerTest`

Expected: PASS with search and notice projection tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPolicyProperties.java \
        backend/community-app/src/main/java/com/nowcoder/community/search/application/command/ProjectPostOutboxCommand.java \
        backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/SearchPostProjectionKafkaListener.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticePolicyProperties.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/application/command/ProjectContentNoticeCommand.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/application/command/ProjectSocialNoticeCommand.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java \
        backend/community-app/src/main/resources/application.yml \
        backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/SearchPostProjectionKafkaListenerTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java
git commit -m "feat: add projection guardrails for search and notice"
```

### Task 6: Analytics Async Capture, Ops Toggles, And Verification

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestEvent.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestKafkaListener.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestKafkaListenerTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsRequestCaptureFilter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsIngestProperties.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `docs/handbook/operations.md`
- Modify: `docs/handbook/business-logic/workflows/notice-search-analytics-ops.md`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsRequestCaptureFilterTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/analytics/controller/AnalyticsControllerUnitTest.java`

**Interfaces:**
- Produces: `record AnalyticsRequestEvent(String ip, UUID userId, boolean recordUv, boolean recordDau)`
- Produces: `void publish(AnalyticsRequestEvent event)` from `AnalyticsRequestEventPublisher`
- Produces: Kafka listener method that converts `AnalyticsRequestEvent` into `RecordRequestCommand`

- [ ] **Step 1: Write the failing tests**

```java
@Test
void filterShouldPublishAnalyticsEventInsteadOfCallingServiceDirectlyWhenAsyncEnabled() throws Exception {
    when(properties.isAsyncEnabled()).thenReturn(true);

    filter.doFilterInternal(request, response, chain);

    verify(analyticsRequestEventPublisher).publish(any(AnalyticsRequestEvent.class));
    verifyNoInteractions(analyticsIngestApplicationService);
}

@Test
void kafkaListenerShouldTranslateEventIntoRecordRequestCommand() {
    AnalyticsRequestEvent event = new AnalyticsRequestEvent("127.0.0.1", UUID.randomUUID(), true, true);

    listener.onMessage(event);

    verify(analyticsIngestApplicationService).recordRequest(new RecordRequestCommand(event.ip(), event.userId(), event.recordUv(), event.recordDau()));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -pl :community-app -Dtest=AnalyticsRequestCaptureFilterTest,AnalyticsRequestKafkaListenerTest,AnalyticsIngestApplicationServiceTest,AnalyticsControllerUnitTest && mvn test -pl :community-app -Dtest='*ArchTest'`

Expected: FAIL because there is no async publisher/listener path and no analytics async toggle.

- [ ] **Step 3: Write the minimal implementation**

```java
public record AnalyticsRequestEvent(String ip, UUID userId, boolean recordUv, boolean recordDau) {
}
```

```java
@Component
@ConditionalOnProperty(prefix = "analytics.ingest", name = "async-enabled", havingValue = "true")
public class AnalyticsRequestEventPublisher {

    private final KafkaTemplate<String, AnalyticsRequestEvent> kafkaTemplate;
    private final String topic;

    public AnalyticsRequestEventPublisher(
            KafkaTemplate<String, AnalyticsRequestEvent> kafkaTemplate,
            @Value("${analytics.ingest.kafka-topic:analytics.request}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(AnalyticsRequestEvent event) {
        kafkaTemplate.send(topic, event.userId() == null ? "anonymous" : event.userId().toString(), event);
    }
}
```

```java
@Component
@ConditionalOnProperty(prefix = "analytics.ingest", name = "async-enabled", havingValue = "true")
public class AnalyticsRequestKafkaListener {

    private final AnalyticsIngestApplicationService analyticsIngestApplicationService;

    public AnalyticsRequestKafkaListener(AnalyticsIngestApplicationService analyticsIngestApplicationService) {
        this.analyticsIngestApplicationService = analyticsIngestApplicationService;
    }

    @KafkaListener(
            topics = "${analytics.ingest.kafka-topic:analytics.request}",
            groupId = "${analytics.ingest.kafka-group-id:analytics-request}",
            concurrency = "${analytics.ingest.kafka-concurrency:2}"
    )
    public void onMessage(AnalyticsRequestEvent event) {
        if (event == null) {
            return;
        }
        analyticsIngestApplicationService.recordRequest(new RecordRequestCommand(event.ip(), event.userId(), event.recordUv(), event.recordDau()));
    }
}
```

```java
if (properties.isAsyncEnabled()) {
    analyticsRequestEventPublisher.publish(new AnalyticsRequestEvent(ip, userId, recordUv, recordDau));
    filterChain.doFilter(request, response);
    return;
}
analyticsIngestApplicationService.recordRequest(new RecordRequestCommand(ip, userId, recordUv, recordDau));
filterChain.doFilter(request, response);
```

```yaml
analytics:
  ingest:
    async-enabled: ${ANALYTICS_INGEST_ASYNC_ENABLED:true}
    kafka-topic: ${ANALYTICS_INGEST_KAFKA_TOPIC:analytics.request}
    kafka-group-id: ${ANALYTICS_INGEST_KAFKA_GROUP_ID:analytics-request}
    kafka-concurrency: ${ANALYTICS_INGEST_KAFKA_CONCURRENCY:2}
```

```md
## Content Platform Degradation

- `CONTENT_FEED_LATEST_FALLBACK_ENABLED=true` keeps global and board feed available when hot ranking lags.
- `SEARCH_PROJECTION_ENABLED=false` stops search projection writes without blocking owner writes.
- `NOTICE_PROJECTION_ENABLED=false` pauses in-app projection while content and social writes continue.
- `ANALYTICS_INGEST_ASYNC_ENABLED=true` keeps request latency off the analytics path and allows independent throttling.
- Dual-region failover order is: freeze old primary writes, confirm replay boundary, promote new primary, switch Kafka producers and consumers, warm caches, then reopen writes.
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn test -pl :community-app -Dtest=AnalyticsRequestCaptureFilterTest,AnalyticsRequestKafkaListenerTest,AnalyticsIngestApplicationServiceTest,AnalyticsControllerUnitTest`

Expected: PASS with analytics async path tests green.

Run: `cd backend && mvn test -pl :community-app -Dtest='*ArchTest'`

Expected: PASS with all architecture guardrails green after the new files and listeners are added.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestEvent.java \
        backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestEventPublisher.java \
        backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestKafkaListener.java \
        backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsRequestCaptureFilter.java \
        backend/community-app/src/main/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsIngestProperties.java \
        backend/community-app/src/main/resources/application.yml \
        docs/handbook/operations.md \
        docs/handbook/business-logic/workflows/notice-search-analytics-ops.md \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/event/AnalyticsRequestKafkaListenerTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsRequestCaptureFilterTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/analytics/controller/AnalyticsControllerUnitTest.java
git commit -m "feat: add async analytics capture and ops toggles"
```

## Self-Review

### Spec Coverage

- owner write hardening and event backbone: covered by Task 1
- hot feed and board feed projection: covered by Task 2
- follow feed pull-merge: covered by Task 3
- detail shell, counters, and comment hot path: covered by Task 4
- search and notice projection guardrails: covered by Task 5
- analytics decoupling, degradation toggles, and failover/ops runbook updates: covered by Task 6
- dual-region runtime behavior is addressed in Task 6 through runbook and configuration, not infra provisioning; deploy automation remains an infrastructure follow-up outside this repository plan

### Placeholder Scan

- No `TODO`, `TBD`, or “implement later” markers remain.
- Every task lists exact file paths, targeted interfaces, test classes, commands, and commit messages.
- No task depends on unnamed helper types.

### Type Consistency

- `ContentContractEvent` and `SocialContractEvent` metadata fields are introduced in Task 1 and consumed consistently in Tasks 2 and 5.
- `ProjectPostHotFeedCommand` gains `sourceEventId` and `sourceVersion` in Task 2 before listener and projection usage.
- `ProjectPostOutboxCommand`, `ProjectContentNoticeCommand`, and `ProjectSocialNoticeCommand` gain source metadata in Task 5 before replay-safe consumption logic uses them.
- `FollowFeedReadApplicationService.listFollowFeed(UUID currentUserId, String cursor, int size)` is introduced in Task 3 and used by the controller in the same task.

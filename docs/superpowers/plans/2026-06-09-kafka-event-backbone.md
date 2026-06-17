# Kafka Event Backbone Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the Kafka Event Backbone branch so content, social, and user contract events flow through durable outbox-to-Kafka delivery and are consumed through same-domain Kafka adapters.

**Architecture:** Producer application services keep using same-domain publisher interfaces. Outbox publishers persist published `contracts.event` envelopes, outbox dispatch enters a same-domain application boundary before technical Kafka sending, and consumer-domain Kafka listeners convert transport events into same-domain application commands. All listener/handler/bridge/enqueuer/job code must respect the repository DDD Tactical Layering rules.

**Tech Stack:** Spring Boot, Spring Kafka, existing `JdbcOutboxEventStore`, existing `TraceKafkaSender`, `JsonCodec`, MyBatis, JUnit 5, Mockito, AssertJ, ArchUnit.

---

## Current Branch Baseline

This plan continues from `/home/feng/code/project/community/.worktrees/kafka-event-backbone` as it exists on 2026-06-16. It is not a from-scratch plan.

Already present in the worktree:

- `OutboxContentEventPublisher`, `ContentEventKafkaOutboxHandler`, and tests.
- `OutboxSocialDomainEventPublisher`, `SocialEventKafkaOutboxHandler`, and tests.
- `OutboxUserPolicyEventPublisher`, `UserEventKafkaOutboxHandler`, and tests.
- `NoticeProjectionKafkaListener`, `SearchPostProjectionKafkaListener`, `TaskProgressEventBackboneKafkaListener`, `UserRewardKafkaListener`, and tests.
- Notice reliable projection methods, `NoticeProjectionEventRepository`, MyBatis implementation, mapper XML, and `notice_projection_event_log` schema.
- Direct user reward calls removed from `PostPublishingApplicationService` and `LikeApplicationService`.

Open branch gaps to close before implementation is reliable input:

- `application.yml` and `deploy/nacos/config/community-kafka-policy.yaml` do not yet define the shared backbone topic/group defaults, and `content`/`social` still default to `local`.
- `*KafkaOutboxHandler` directly uses `KafkaTemplate` / `TraceKafkaSender`, which violates the target AGENTS.md boundary because handlers are inbound adapters. The target is `Handler -> same-domain ApplicationService -> application port -> infrastructure Kafka sender`.
- Notice application commands currently expose foreign `contracts.event` envelopes.
- Notice consumption idempotency is modeled as a domain repository even though it is application-level event processing state.
- Event id strategy is inconsistent across spec examples, current tests, and current code. Like/reward idempotency needs explicit stable source keys.
- The current task checkboxes below are intentionally unchecked because they describe remaining validation and convergence work.

## File Structure

Only touch the files named by the active task. Do not modify main-worktree files.

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java` will own content outbox dispatch orchestration.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventKafkaDispatchPort.java` will be the content application-owned technical port.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaSenderAdapter.java` will implement the port with `TraceKafkaSender`.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaOutboxHandler.java` will call only `ContentEventDispatchApplicationService`.
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java` will cover parsing, payload typing, and port failure propagation.
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaOutboxHandlerTest.java` will verify handler-to-application delegation only.
- Equivalent social and user files will be created/modified for `SocialEventDispatchApplicationService`, `SocialEventKafkaDispatchPort`, `SocialEventKafkaSenderAdapter`, `UserEventDispatchApplicationService`, `UserEventKafkaDispatchPort`, and `UserEventKafkaSenderAdapter`.
- `backend/community-app/src/main/java/com/nowcoder/community/notice/application/command/ProjectContentNoticeCommand.java` and `ProjectSocialNoticeCommand.java` will be converted to notice-owned fields instead of foreign envelopes if the team accepts the boundary cleanup.
- `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionEventRecorder.java` will replace domain-owned notice idempotency if the port is moved.
- `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/MyBatisNoticeProjectionEventRecorder.java` and mapper XML/schema will implement that application port.
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListener.java` and tests will make like reward source ids deterministic if current UUID-v7 event ids remain.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListener.java` and tests will document or adjust growth like source ids.
- `backend/community-app/src/main/resources/application.yml`, `backend/community-app/src/test/resources/application.yml`, and `deploy/nacos/config/community-kafka-policy.yaml` will receive final backbone defaults.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/*ArchTest.java` will be updated only if architecture rules need explicit coverage for the new handler/application-port boundary.

## Scope Guard

This plan does not migrate market-wallet Saga, analytics ingest, auth mail, media processing, Redis token/state paths, or the existing IM policy bridge. Do not delete legacy local listeners or old projection-specific outbox handlers until the rollback switches and idempotency tests are in place.

## Architecture Guardrails

- Listener/handler/bridge/enqueuer/job code calls only same-domain `*ApplicationService`.
- Application services may call same-domain domain services/repositories and foreign owner-domain `api.query` / `api.action`.
- Application services must not depend on MyBatis mapper/dataobject types, HTTP transport types, `KafkaTemplate`, or `TraceKafkaSender`.
- Domain code must not depend on Spring, Kafka, outbox, infrastructure, `api.*`, or `contracts.event`.
- Kafka listeners may parse foreign `contracts.event` in infrastructure, but application commands should use same-domain fields where feasible.
- `api.*` must not import, return, or receive `contracts.event`.

### Task 0: Baseline And Decision Lock

**Files:**
- Read: `docs/superpowers/specs/2026-06-09-kafka-event-backbone-design.md`
- Read: `backend/community-app/src/main/resources/application.yml`
- Read: `deploy/nacos/config/community-kafka-policy.yaml`
- Read: `backend/community-app/src/main/java/com/nowcoder/community/{content,social,user,notice,search,growth}/**/*.java`
- Modify: `docs/superpowers/plans/2026-06-09-kafka-event-backbone.md` only if branch reality changes before execution

- [ ] **Step 1: Confirm branch status**

Run from repository root:

```bash
git status --short --branch
git diff --name-status
```

Expected: branch is `kafka-event-backbone`; existing business-code changes remain present and are not reverted.

- [ ] **Step 2: Lock the outbox handler boundary decision**

Decision to implement unless AGENTS.md is changed first:

```text
OutboxHandler is an inbound adapter.
It must call same-domain *ApplicationService only.
KafkaTemplate/TraceKafkaSender move behind an application-owned dispatch port implemented by infrastructure.
```

Completion standard: content/social/user handler tests will assert delegation to same-domain dispatch application services, not direct Kafka sends.

- [ ] **Step 3: Lock event id and source id policy**

Document this branch policy in code comments/tests where needed:

```text
eventId identifies a single emitted backbone message.
sourceEventId identifies downstream business idempotency.
If eventId uses UUID-v7 for repeatable business actions, consumer sourceEventId must be derived from stable payload fields.
```

Completion standard: reward and growth tests explicitly distinguish message `eventId` from business `sourceEventId` for like events.

- [ ] **Step 4: Risk and rollback note**

Risk: executing old RED steps from the earlier plan would fail incorrectly because classes already exist. Rollback for this task is no code rollback; update this plan only if the baseline changes.

### Task 1: Refactor Content Outbox Dispatch To Same-Domain Application Boundary

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventKafkaDispatchPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaSenderAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaOutboxHandler.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaOutboxHandlerTest.java`

- [ ] **Step 1: Write failing application dispatch tests**

Create `ContentEventDispatchApplicationServiceTest` with tests for typed payload conversion and send failure propagation:

```java
@Test
void dispatchShouldConvertPostPayloadAndCallPort() {
    ContentEventKafkaDispatchPort port = mock(ContentEventKafkaDispatchPort.class);
    ContentEventDispatchApplicationService service = new ContentEventDispatchApplicationService(
            new JacksonJsonCodec(JsonMappers.standard()), port, "content.events");
    PostPayload payload = new PostPayload();
    payload.setPostId(uuid(100));
    String json = jsonCodec().toJson(new ContentContractEvent(
            "content:PostPublished:" + uuid(100),
            ContentEventTypes.POST_PUBLISHED,
            payload
    ));

    service.dispatch("post-key", json);

    verify(port).send(eq("content.events"), eq("post-key"), argThat(event ->
            ContentEventTypes.POST_PUBLISHED.equals(event.type())
                    && event.payload() instanceof PostPayload));
}

@Test
void dispatchShouldPropagatePortFailureForOutboxRetry() {
    ContentEventKafkaDispatchPort port = mock(ContentEventKafkaDispatchPort.class);
    doThrow(new IllegalStateException("send failed")).when(port).send(anyString(), anyString(), any());
    ContentEventDispatchApplicationService service = new ContentEventDispatchApplicationService(
            new JacksonJsonCodec(JsonMappers.standard()), port, "content.events");

    assertThatThrownBy(() -> service.dispatch("post-key", validPostEventJson()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("send failed");
}
```

- [ ] **Step 2: Run RED**

Run from `backend`:

```bash
mvn -q -pl :community-app -am -Dtest='ContentEventDispatchApplicationServiceTest,ContentEventKafkaOutboxHandlerTest' test
```

Expected: FAIL because `ContentEventDispatchApplicationService` and `ContentEventKafkaDispatchPort` do not exist, and the handler still depends on `KafkaTemplate`.

- [ ] **Step 3: Implement application service and port**

Implement `ContentEventKafkaDispatchPort` in `content.application`:

```java
public interface ContentEventKafkaDispatchPort {
    void send(String topic, String key, ContentContractEvent event);
}
```

Implement `ContentEventDispatchApplicationService` in `content.application`. It owns parsing and typed payload conversion currently inside `ContentEventKafkaOutboxHandler`, then calls the port:

```java
@Service
public class ContentEventDispatchApplicationService {
    private final JsonCodec jsonCodec;
    private final ContentEventKafkaDispatchPort dispatchPort;
    private final String kafkaTopic;

    public ContentEventDispatchApplicationService(
            JsonCodec jsonCodec,
            ContentEventKafkaDispatchPort dispatchPort,
            @Value("${content.events.kafka-topic:content.events}") String kafkaTopic
    ) {
        this.jsonCodec = jsonCodec;
        this.dispatchPort = dispatchPort;
        this.kafkaTopic = kafkaTopic;
    }

    public void dispatch(String key, String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return;
        }
        ContentContractEvent event = parse(payloadJson);
        if (event == null || !StringUtils.hasText(event.eventId())) {
            return;
        }
        dispatchPort.send(kafkaTopic, key, event);
    }

    private ContentContractEvent parse(String payloadJson) {
        JsonNode root = jsonCodec.readTree(payloadJson);
        String type = text(root, "type");
        return new ContentContractEvent(text(root, "eventId"), type, typedPayload(type, root.get("payload")));
    }
}
```

Use the existing `typedPayload` mapping for post, comment, and moderation payloads.

- [ ] **Step 4: Implement infrastructure sender adapter**

Create `ContentEventKafkaSenderAdapter`:

```java
@Component
@ConditionalOnClass(KafkaTemplate.class)
public class ContentEventKafkaSenderAdapter implements ContentEventKafkaDispatchPort {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ContentEventKafkaSenderAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, String key, ContentContractEvent event) {
        try {
            TraceKafkaSender.send(kafkaTemplate, topic, key, event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("content event kafka publish failed: " + topic, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException("content event kafka publish failed: " + topic, e);
        }
    }
}
```

- [ ] **Step 5: Refactor handler to adapter-only delegation**

Modify `ContentEventKafkaOutboxHandler` so its only collaborator is `ContentEventDispatchApplicationService` plus `outboxTopic`:

```java
@Override
public void handle(OutboxEvent event) {
    if (event == null) {
        return;
    }
    applicationService.dispatch(event.eventKey(), event.payload());
}
```

Update `ContentEventKafkaOutboxHandlerTest` to assert `applicationService.dispatch(key, payload)` and remove direct `KafkaTemplate` assertions from handler tests. Direct Kafka send assertions move to sender adapter tests if needed.

- [ ] **Step 6: Run GREEN**

Run from `backend`:

```bash
mvn -q -pl :community-app -am -Dtest='ContentEventDispatchApplicationServiceTest,ContentEventKafkaOutboxHandlerTest,OutboxContentEventPublisherTest' test
```

Expected: PASS.

Completion standard: content outbox handler no longer imports `KafkaTemplate`, `TraceKafkaSender`, `JsonNode`, or contract payload classes.

Risk: moving parsing to application means application imports `contracts.event`, which is acceptable for owner-domain published async contracts but still must not import mapper/dataobject/HTTP/Kafka transport types.

Rollback: revert this task's content dispatch files and handler/test changes; set `CONTENT_EVENTS_PUBLISHER=local` if branch must run before dispatch refactor is complete.

### Task 2: Refactor Social And User Outbox Dispatch To Same-Domain Application Boundary

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventKafkaDispatchPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/SocialEventKafkaSenderAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/SocialEventKafkaOutboxHandler.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/SocialEventKafkaOutboxHandlerTest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserEventDispatchApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserEventKafkaDispatchPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserEventKafkaSenderAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserEventKafkaOutboxHandler.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserEventDispatchApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserEventKafkaOutboxHandlerTest.java`

- [ ] **Step 1: Write social dispatch RED tests**

Use the same structure as Task 1, but assert typed conversion for:

```text
LIKE_CREATED / LIKE_REMOVED -> LikePayload
FOLLOW_CREATED -> FollowPayload
BLOCK_RELATION_CHANGED -> BlockPayload
```

Run:

```bash
mvn -q -pl :community-app -am -Dtest='SocialEventDispatchApplicationServiceTest,SocialEventKafkaOutboxHandlerTest' test
```

Expected: FAIL until social dispatch service/port exist and handler delegates.

- [ ] **Step 2: Implement social dispatch boundary**

Create `SocialEventKafkaDispatchPort`:

```java
public interface SocialEventKafkaDispatchPort {
    void send(String topic, String key, SocialContractEvent event);
}
```

Move typed payload parsing from `SocialEventKafkaOutboxHandler` to `SocialEventDispatchApplicationService`. Create `SocialEventKafkaSenderAdapter` with `TraceKafkaSender`. Refactor `SocialEventKafkaOutboxHandler` to call only `SocialEventDispatchApplicationService`.

- [ ] **Step 3: Write user dispatch RED tests**

Assert `USER_POLICY_CHANGED -> UserPolicyChangedPayload`, blank payload no-op, and port failure propagation:

```bash
mvn -q -pl :community-app -am -Dtest='UserEventDispatchApplicationServiceTest,UserEventKafkaOutboxHandlerTest' test
```

Expected: FAIL until user dispatch service/port exist and handler delegates.

- [ ] **Step 4: Implement user dispatch boundary**

Create `UserEventKafkaDispatchPort`:

```java
public interface UserEventKafkaDispatchPort {
    void send(String topic, String key, UserContractEvent event);
}
```

Move typed payload parsing from `UserEventKafkaOutboxHandler` to `UserEventDispatchApplicationService`. Create `UserEventKafkaSenderAdapter` with `TraceKafkaSender`. Refactor `UserEventKafkaOutboxHandler` to call only `UserEventDispatchApplicationService`.

- [ ] **Step 5: Run GREEN**

Run:

```bash
mvn -q -pl :community-app -am -Dtest='SocialEventDispatchApplicationServiceTest,SocialEventKafkaOutboxHandlerTest,OutboxSocialDomainEventPublisherTest,UserEventDispatchApplicationServiceTest,UserEventKafkaOutboxHandlerTest,OutboxUserPolicyEventPublisherTest' test
```

Expected: PASS.

Completion standard: social/user outbox handlers no longer import `KafkaTemplate`, `TraceKafkaSender`, `JsonNode`, or payload classes.

Risk: duplicated dispatch parsing across content/social/user can drift. Keep only small owner-domain services for now; do not introduce a cross-domain generic event dispatcher unless duplication becomes material after tests pass.

Rollback: revert this task's social/user dispatch files and handler/test changes; use `SOCIAL_EVENTS_PUBLISHER=local` or `USER_EVENTS_PUBLISHER=local` for runtime fallback.

### Task 3: Clarify Consumer Idempotency And Application Commands

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/command/ProjectContentNoticeCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/command/ProjectSocialNoticeCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListenerTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListenerTest.java`

- [ ] **Step 1: Write notice command boundary RED tests**

Update listener tests so they assert notice-owned command fields, not foreign event envelopes:

```java
verify(service).projectContentEventReliably(argThat(command ->
        "evt-comment-1".equals(command.sourceEventId())
                && ContentEventTypes.COMMENT_CREATED.equals(command.eventType())
                && command.payload() instanceof CommentPayload));
```

Run:

```bash
mvn -q -pl :community-app -am -Dtest='NoticeProjectionApplicationServiceTest,NoticeProjectionKafkaListenerTest' test
```

Expected: FAIL until `ProjectContentNoticeCommand` and `ProjectSocialNoticeCommand` expose notice-owned fields.

- [ ] **Step 2: Refactor notice commands**

Change commands to:

```java
public record ProjectContentNoticeCommand(String sourceEventId, String eventType, Object payload) {
}

public record ProjectSocialNoticeCommand(String sourceEventId, String eventType, Object payload) {
}
```

Update `NoticeProjectionApplicationService` so it constructs projection state from command fields. Keep foreign `contracts.event` imports out of `notice.application.command`.

- [ ] **Step 3: Write user reward deterministic source RED tests**

If social producer keeps UUID-v7 event ids, update tests to require stable reward source ids:

```java
listener.onSocialEvent(new SocialContractEvent("se:like:created:uuid-v7-a", SocialEventTypes.LIKE_CREATED, payload));
listener.onSocialEvent(new SocialContractEvent("se:like:created:uuid-v7-b", SocialEventTypes.LIKE_CREATED, payload));

verify(walletRewardActionApi, times(2)).applyDelta(
        eq("wallet-reward:like-created:" + uuid(1) + ":" + POST + ":" + uuid(100)),
        eq(uuid(2)),
        eq(1),
        eq("LikeCreated")
);
```

This RED may reveal current behavior uses `event.eventId()` as the reward source.

- [ ] **Step 4: Implement reward source id policy**

In `UserRewardKafkaListener`, derive like source ids from payload fields:

```java
private String likeSourceEventId(String prefix, LikePayload payload) {
    return prefix + ":" + payload.getActorUserId() + ":" + payload.getEntityType() + ":" + payload.getEntityId();
}
```

Use `like-created` and `like-removed` prefixes so creation and reversal are distinct.

- [ ] **Step 5: Verify or document growth source id policy**

Current growth listener derives `gl:like:<nameUUID(actor:type:entity)>`. Either keep this and update tests/spec comments to state it is the stable growth source id, or change it to the readable `like-created:<actor>:<type>:<entity>` form. The test must assert the chosen exact value.

- [ ] **Step 6: Run GREEN**

Run:

```bash
mvn -q -pl :community-app -am -Dtest='NoticeProjectionApplicationServiceTest,NoticeProjectionKafkaListenerTest,UserRewardKafkaListenerTest,TaskProgressEventBackboneKafkaListenerTest' test
```

Expected: PASS.

Completion standard: notice command classes no longer import `contracts.event`; reward and growth tests prove repeated Kafka redelivery uses deterministic business source ids.

Risk: changing reward source ids after any production delivery would change wallet idempotency keys. This branch is pre-cutover; do not change after release without a migration note.

Rollback: if command refactor is too broad for the same implementation slice, keep envelope commands temporarily and leave Task 3 incomplete. Do not merge until the boundary is either refactored or validated against an explicit repository architecture rule update requested separately.

### Task 4: Move Notice Projection Idempotency To An Application-Owned Port

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionEventRecorder.java`
- Delete or deprecate: `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/repository/NoticeProjectionEventRepository.java`
- Rename/modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/MyBatisNoticeProjectionEventRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/mapper/NoticeProjectionEventMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/notice_projection_event_mapper.xml`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/persistence/MyBatisNoticeProjectionEventRecorderTest.java`

- [ ] **Step 1: Write repository/recorder RED tests**

Add tests for duplicate insert, blank id, and trim behavior:

```java
@Test
void blankSourceEventIdShouldNotInsert() {
    NoticeProjectionEventMapper mapper = mock(NoticeProjectionEventMapper.class);
    MyBatisNoticeProjectionEventRecorder recorder = new MyBatisNoticeProjectionEventRecorder(mapper);

    assertThat(recorder.tryRecord("  ")).isFalse();

    verifyNoInteractions(mapper);
}

@Test
void duplicateInsertShouldReturnFalse() {
    NoticeProjectionEventMapper mapper = mock(NoticeProjectionEventMapper.class);
    doThrow(new DataIntegrityViolationException("duplicate")).when(mapper).insert("evt-1");
    MyBatisNoticeProjectionEventRecorder recorder = new MyBatisNoticeProjectionEventRecorder(mapper);

    assertThat(recorder.tryRecord("evt-1")).isFalse();
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
mvn -q -pl :community-app -am -Dtest='NoticeProjectionApplicationServiceTest,MyBatisNoticeProjectionEventRecorderTest' test
```

Expected: FAIL until the application port and recorder implementation exist.

- [ ] **Step 3: Implement application-owned port**

Create:

```java
package com.nowcoder.community.notice.application;

public interface NoticeProjectionEventRecorder {
    boolean tryRecord(String sourceEventId);
}
```

Rename the infrastructure implementation to `MyBatisNoticeProjectionEventRecorder` and implement the application port. Add blank guard and trim before mapper insert.

- [ ] **Step 4: Update service dependency**

Change `NoticeProjectionApplicationService` to depend on `ObjectProvider<NoticeProjectionEventRecorder>` or a nullable constructor parameter of that application port. Remove imports from `notice.domain.repository`.

- [ ] **Step 5: Run GREEN**

Run:

```bash
mvn -q -pl :community-app -am -Dtest='NoticeProjectionApplicationServiceTest,NoticeProjectionKafkaListenerTest,MyBatisNoticeProjectionEventRecorderTest' test
```

Expected: PASS.

Completion standard: `notice.domain.repository` no longer contains projection event processing state unless a domain concept justifies it.

Risk: renaming files may require mapper scan/package updates. Keep XML namespace pointed at the mapper interface, not the recorder class.

Rollback: keep the old interface name for one commit only if package movement breaks Spring wiring; do not leave it as domain repository without documenting the decision.

### Task 5: Runtime Configuration And Legacy Path Cutover

**Files:**
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-app/src/test/resources/application.yml`
- Modify: `deploy/nacos/config/community-kafka-policy.yaml`
- Modify or create focused config tests under `backend/community-app/src/test/java/com/nowcoder/community/event` or `backend/community-app/src/test/java/com/nowcoder/community/config`

- [ ] **Step 1: Write configuration RED test**

Add a focused test that loads `application.yml` and asserts these properties exist:

```text
content.events.publisher = outbox-kafka
content.events.outbox-topic = eventbus.content
content.events.kafka-topic = content.events
social.events.publisher = outbox-kafka
social.events.outbox-topic = eventbus.social
social.events.kafka-topic = social.events
user.events.publisher = outbox-kafka
user.events.outbox-topic = eventbus.user
user.events.kafka-topic = user.events
notice.kafka.consumer.group-id = notice-projection
search.kafka.consumer.group-id = search-post-projection
user.reward.kafka.consumer.group-id = user-reward-projection
```

Run:

```bash
mvn -q -pl :community-app -am -Dtest='*KafkaBackboneConfig*Test,EventDeliverySurfaceRetirementTest,EventDeliverySemanticsStructureTest,SearchEventSurfaceRetirementTest' test
```

Expected: FAIL because `content.events.publisher` and `social.events.publisher` currently default to `local` and topic defaults are missing.

- [ ] **Step 2: Update `application.yml`**

Merge these defaults without duplicating top-level keys:

```yaml
content:
  storage: redis
  events:
    publisher: ${CONTENT_EVENTS_PUBLISHER:outbox-kafka}
    outbox-topic: ${CONTENT_EVENTS_OUTBOX_TOPIC:eventbus.content}
    kafka-topic: ${CONTENT_EVENTS_KAFKA_TOPIC:content.events}

social:
  storage: db
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
```

Keep existing `growth.task.kafka.consumer` defaults and point backbone listeners at the existing group id unless a separate group is introduced deliberately.

- [ ] **Step 3: Update test resources and Nacos policy**

Update `backend/community-app/src/test/resources/application.yml` consistently so tests do not silently exercise the old local default. Add matching topic/group defaults to `deploy/nacos/config/community-kafka-policy.yaml` because that file already carries app Kafka policy defaults.

- [ ] **Step 4: Preserve rollback switches**

Document in config comments or deployment notes:

```text
CONTENT_EVENTS_PUBLISHER=local
SOCIAL_EVENTS_PUBLISHER=local
USER_EVENTS_PUBLISHER=local
```

These switches keep old local publishers available while legacy listeners remain in the codebase.

- [ ] **Step 5: Run GREEN**

Run:

```bash
mvn -q -pl :community-app -am -Dtest='*KafkaBackboneConfig*Test,EventDeliverySurfaceRetirementTest,EventDeliverySemanticsStructureTest,SearchEventSurfaceRetirementTest' test
```

Expected: PASS.

Completion standard: default config uses outbox-kafka for content/social/user, all shared topics/groups are configurable, and rollback to local is explicit via env.

Risk: enabling outbox-kafka by default without `events.outbox.enabled=true` would disable delivery. Keep or test the combined condition on outbox publisher beans.

Rollback: set publisher env vars to `local`; do not revert config by editing tracked files during incident rollback.

### Task 6: Contract, Idempotency, And Producer Consistency Tests

**Files:**
- Modify or create tests under `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event`
- Modify or create tests under `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event`
- Modify or create tests under `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event`
- Modify or create consumer tests under `notice`, `search`, `growth`, and `user` infrastructure/application packages

- [ ] **Step 1: Add producer-to-dispatch contract tests**

For each owner domain, test that the publisher's serialized outbox payload can be passed into the dispatch application service and produces the expected typed contract event for the Kafka port.

Example shape:

```java
publisher.publishPostPublished(payload);
String outboxPayload = capturedStorePayload();

dispatchService.dispatch(uuid(100).toString(), outboxPayload);

verify(port).send(eq("content.events"), eq(uuid(100).toString()), argThat(event ->
        event.payload() instanceof PostPayload
                && ContentEventTypes.POST_PUBLISHED.equals(event.type())));
```

- [ ] **Step 2: Add duplicate delivery tests**

Cover:

```text
notice duplicate eventId -> one notice
growth duplicate sourceEventId -> one progress event
user reward duplicate sourceEventId -> one wallet delta through wallet idempotency key
search repeated post update/delete -> final index state follows content source projection
```

- [ ] **Step 3: Add malformed event behavior tests**

For each listener, assert unsupported event types no-op and missing required fields no-op. For supported event types with invalid payload shape, choose either throw-for-retry or logged no-op, then assert the chosen behavior.

- [ ] **Step 4: Run focused contract/idempotency tests**

Run:

```bash
mvn -q -pl :community-app -am -Dtest='*EventKafkaOutboxHandlerTest,*EventDispatchApplicationServiceTest,Outbox*EventPublisherTest,*KafkaListenerTest,NoticeProjection*Test,SearchPostProjectionKafkaListenerTest,UserRewardKafkaListenerTest,TaskProgressEventBackboneKafkaListenerTest' test
```

Expected: PASS.

Completion standard: tests prove publisher payloads match dispatch parsing, listeners match contracts, and duplicate delivery cannot duplicate side effects.

Risk: full integration tests may be expensive if embedded Kafka is introduced. Prefer port-level unit tests unless the project already has embedded Kafka support.

Rollback: remove only tests added in this task if they duplicate stronger coverage from earlier tasks.

### Task 7: Architecture Guardrails And Final Verification

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- No production files unless ArchUnit exposes a real violation in files touched by Tasks 1-6.

- [ ] **Step 1: Add or confirm handler boundary ArchUnit coverage**

Add coverage that classes named `*Listener`, `*Handler`, `*Bridge`, `*Enqueuer`, and `*Job` in business domains do not directly depend on foreign `api.*`, foreign `application.*`, domain repositories/services/models, mappers, dataobjects, `KafkaTemplate`, or `TraceKafkaSender`. If `*KafkaOutboxHandler` remains direct-to-Kafka, this step must fail.

- [ ] **Step 2: Add boundary scans to final checklist**

Run from `backend`:

```bash
rg -n 'contracts\.event' community-app/src/main/java/com/nowcoder/community/*/api -g '*.java'
rg -n 'KafkaTemplate|TraceKafkaSender|Mapper|DataObject|MultipartFile|ResponseEntity|ResponseCookie|Resource|MediaType|Servlet' community-app/src/main/java/com/nowcoder/community/*/application -g '*.java'
rg -n '^import .*\.(controller|application|infrastructure|mapper|api)\.|^import org\.springframework' community-app/src/main/java/com/nowcoder/community/*/domain -g '*.java'
```

Expected: no prohibited matches, except reviewed owner-domain `contracts.event` imports in application services if retained deliberately and not in `api.*`.

- [ ] **Step 3: Run architecture tests**

Run:

```bash
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: PASS. If an ArchUnit failure reports a listener or handler calling a foreign API or repository, move that call behind the same-domain application service instead of suppressing the rule.

- [ ] **Step 4: Run affected application tests**

Run:

```bash
mvn -q -pl :community-app -am -Dtest='PostPublishingApplicationServiceTest,LikeApplicationServiceTest,CommentApplicationServiceTest,NoticeProjectionListenerTest,PostOutboxHandlerTest,CommentRewardOutboxHandlerTest,TaskProgressKafkaListenerTest' test
```

Expected: PASS. Legacy listener tests may remain because old paths are still available for explicit local rollback.

- [ ] **Step 5: Run full module test if time allows**

Run:

```bash
mvn -q -pl :community-app -am test
```

Expected: PASS. If this is too slow for an agent turn, record the focused commands that passed and leave full-suite execution as an explicit follow-up.

- [ ] **Step 6: Inspect final diff**

Run from repository root:

```bash
git status --short --branch
git diff -- docs/superpowers backend/community-app/src/main/java backend/community-app/src/test/java backend/community-app/src/main/resources deploy/nacos deploy/mysql
```

Expected: only files listed in this plan changed, plus tightly related test expectation updates caused by the Kafka backbone default.

Completion standard: focused tests, ArchUnit, and final diff inspection are complete; no main-worktree tracked files changed; rollback switches are documented.

## Self-Review Notes

- Spec coverage: this plan maps producer dispatch, consumer listeners, idempotency, configuration, rollout, and architecture guardrails back to `docs/superpowers/specs/2026-06-09-kafka-event-backbone-design.md`.
- Placeholder scan: no intentional placeholder language remains. Any remaining broad step must name files, commands, expected result, risk, and rollback.
- Current-state correction: previous from-scratch RED steps are replaced with branch-aware RED/GREEN steps because the worktree already contains many implementation files.
- DDD correction: outbox dispatch is planned through same-domain application services and application-owned ports rather than direct business handler collaboration.

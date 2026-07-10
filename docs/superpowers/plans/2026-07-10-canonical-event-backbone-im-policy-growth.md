# Canonical Event Backbone, IM Policy, And Growth Retirement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Content, Social, and User publish through one owner outbox-to-Kafka backbone, give every downstream projection one canonical Kafka ingress, repair the IM policy projection boundary, and retire Growth's synchronous and secondary event surfaces.

**Architecture:** Owner ApplicationServices keep their existing publisher interfaces, but each interface has one unconditional outbox adapter and one Kafka dispatch tail. Consumer Kafka listeners validate recognized contract events and call only their same-domain `*ApplicationService`; IM policy uses a new application service and application-owned outbox port so Kafka replay is absorbed by a deterministic outbox event ID. A shared `community-app` listener factory applies the configured retry/DLQ policy to all canonical consumers.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Kafka, Jackson, JDBC outbox, MySQL/H2, JUnit 5, Mockito, AssertJ, ArchUnit, Bash.

## Global Constraints

- Execute this plan after `2026-07-10-im-contract-projection-clean-break.md` and before the final Market/Trace/docs plan.
- Content, Social, and User each have exactly one producer path: owner transaction -> `eventbus.<owner>` -> owner outbox handler -> `<owner>.events` Kafka topic.
- `events.outbox.enabled=false` is an invalid `community-app` configuration and must fail application startup in every profile.
- Remove `content.events.publisher`, `social.events.publisher`, and `user.events.publisher`; no local publisher or selector remains.
- Search, Growth, User reward, Hot feed, and Notice consume owner Kafka contract events directly; no secondary Search/Growth/User-reward projection outbox remains.
- A non-target event type is ignored. A recognized event with missing/invalid event ID, owner version, timestamp, identity, or required payload data throws and enters retry/DLQ.
- Kafka retry count comes from `KafkaPolicyDecisions.retryMaxAttempts()` and includes the initial delivery; retry backoff uses its base and maximum durations.
- `content.events`, `social.events`, and `user.events` and their `.dlq` topics each use 12 partitions.
- IM policy preserves source event ID, source time, and positive owner version; duplicate source delivery inserts at most one `projection.im.policy` outbox row.
- IM policy projection outbox event IDs are deterministic ASCII strings no longer than 64 characters.
- Kafka listeners obey `Listener -> same-domain ApplicationService -> application-owned port -> infrastructure adapter`.
- Keep the canonical `projection.im.policy` outbox-to-IM-Kafka tail; it is a reliability boundary, not a migration duplicate.
- Growth's canonical entry is `TaskProgressEventBackboneKafkaListener -> TaskProgressApplicationService`; keep like-removal rollback and source-event idempotency.
- Do not modify Market request replay or MDC behavior in this plan.

---

## File Structure

### Canonical Owner Publishers And Startup Guard

- Create: `backend/community-app/src/main/java/com/nowcoder/community/app/config/CanonicalEventBackboneGuard.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/app/config/CanonicalEventBackboneGuardTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisher.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/LocalUserPolicyEventPublisher.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/LocalUserEventPublisher.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/UserEventPublisher.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisherTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisherTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/LocalUserPolicyEventPublisherTest.java`
- Note: the unused `LocalUserEventPublisher` has no focused test.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaSenderAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/SocialEventKafkaOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/SocialEventKafkaSenderAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/OutboxUserPolicyEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserEventKafkaOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserEventKafkaSenderAdapter.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisherTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaOutboxHandlerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaSenderAdapterTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisherTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/SocialEventKafkaOutboxHandlerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/SocialEventKafkaSenderAdapterTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/OutboxUserPolicyEventPublisherTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserEventDispatchApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserEventKafkaOutboxHandlerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserEventKafkaSenderAdapterTest.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-app/src/test/resources/application.yml`
- Modify: `deploy/nacos/config/community-kafka-policy.yaml`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/config/KafkaBackboneConfigTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/config/NacosPolicyBindingTest.java`

### Canonical Downstream Projections

- Delete: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandler.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/PostOutboxEnqueuerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandlerTest.java`
- Rename: `backend/community-app/src/main/java/com/nowcoder/community/search/application/command/ProjectPostOutboxCommand.java` -> `backend/community-app/src/main/java/com/nowcoder/community/search/application/command/ProjectPostCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/infrastructure/event/SearchPostProjectionKafkaListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/SearchPostProjectionKafkaListenerTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandler.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandlerTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionLocalListener.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionLocalListenerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/event/SpringEventAdapterConstructorSelectionTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListenerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/event/EventDeliverySemanticsStructureTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/event/EventDeliverySurfaceRetirementTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/search/event/SearchEventSurfaceRetirementTest.java`

### IM Policy Application Boundary

- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/application/command/ProjectUserPolicyCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/application/command/ProjectBlockRelationCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyProjectionEvent.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyProjectionOutboxPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyProjectionApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/JdbcImPolicyProjectionOutboxAdapter.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyChangePublisher.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/ImPolicyOutboxEnqueuerTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyBackboneKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyKafkaOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyEventKafkaSenderAdapter.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/im/application/ImPolicyProjectionApplicationServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/JdbcImPolicyProjectionOutboxAdapterTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/ImPolicyBackboneKafkaListenerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/ImPolicyKafkaOutboxHandlerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/ImPolicyEventKafkaSenderAdapterTest.java`

### Growth Retirement

- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/api/action/GrowthTaskProgressActionApi.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/api/model/GrowthCommentTaskProgressRequest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/api/model/GrowthLikeTaskProgressRequest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/api/GrowthTaskProgressActionApiAdapter.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/api/GrowthTaskProgressActionApiAdapterTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/PostTaskProgressKafkaOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/PostTaskProgressKafkaOutboxHandler.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressKafkaOutboxHandler.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxHandler.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressOutboxDispatchApplicationService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressIntegrationEventDispatcher.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/DispatchTaskProgressEventCommand.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/TaskProgressDispatchKind.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaSenderAdapter.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListener.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressOutboxDispatchApplicationServiceTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/PostTaskProgressKafkaOutboxEnqueuerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/PostTaskProgressKafkaOutboxHandlerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressOutboxEnqueuerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressKafkaOutboxHandlerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxEnqueuerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxHandlerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaSenderAdapterTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListenerTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListenerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`

### Kafka Reliability And Architecture Guardrails

- Create: `backend/community-app/src/main/java/com/nowcoder/community/app/config/CommunityKafkaListenerConfiguration.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/app/config/CommunityKafkaListenerConfigurationTest.java`
- Modify: `deploy/scripts/bootstrap-kafka-topics.sh`
- Create: `deploy/tests/community_backbone_topics_contract.sh`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java`

---

### Task 1: Make Owner Outbox Publishers Unconditional

**Files:**
- Create/delete/modify the canonical owner publisher and startup-guard files listed above.

**Interfaces:**
- Produces `CanonicalEventBackboneGuard(OutboxProperties properties)`; construction throws unless `properties.isEnabled()` is true.
- Preserves `ContentEventPublisher`, `SocialDomainEventPublisher`, and `UserPolicyEventPublisher` as owner application/domain ports.
- Makes `OutboxContentEventPublisher`, `OutboxSocialDomainEventPublisher`, and `OutboxUserPolicyEventPublisher` their only implementations.

- [ ] **Step 1: Write failing startup and retirement tests**

Create `CanonicalEventBackboneGuardTest`:

```java
@Test
void disabledOutboxShouldFailFast() {
    OutboxProperties properties = new OutboxProperties();
    properties.setEnabled(false);

    assertThatThrownBy(() -> new CanonicalEventBackboneGuard(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("events.outbox.enabled must be true");
}

@Test
void enabledOutboxShouldBeAccepted() {
    OutboxProperties properties = new OutboxProperties();
    properties.setEnabled(true);
    assertThatCode(() -> new CanonicalEventBackboneGuard(properties)).doesNotThrowAnyException();
}
```

Extend `EventDeliverySurfaceRetirementTest` with classpath assertions for all four local publishers and `PostHotFeedProjectionLocalListener`. Update `KafkaBackboneConfigTest` and `NacosPolicyBindingTest` to assert the three `*.events.publisher` properties are absent while the outbox/Kafka topic properties remain.

Run:

```bash
(cd backend && mvn test -pl :community-app -Dtest=CanonicalEventBackboneGuardTest,EventDeliverySurfaceRetirementTest,KafkaBackboneConfigTest,NacosPolicyBindingTest)
```

Expected: FAIL because the guard does not exist and selector properties/classes remain.

- [ ] **Step 2: Add the all-profile startup guard**

Implement the component without a profile condition:

```java
@Component
public final class CanonicalEventBackboneGuard {
    public CanonicalEventBackboneGuard(OutboxProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            throw new IllegalStateException(
                    "events.outbox.enabled must be true for the canonical event backbone");
        }
    }
}
```

Do not add a local/dev exception. `OutboxProperties` remains supplied by common-outbox auto-configuration.

- [ ] **Step 3: Delete local publishers and remove conditional selection**

Delete the local classes/tests and the unused `UserEventPublisher`/`LocalUserEventPublisher`. From every retained owner publisher/dispatch/handler/sender class remove only the `@ConditionalOnExpression` import and annotation. The retained shape is simply:

```java
@Component // or @Service on the existing application service
public class OutboxContentEventPublisher implements ContentEventPublisher {
    // Existing enqueue behavior is unchanged.
}
```

Apply the same rule to Social and User. Update their focused tests to assert the port implementation and dispatch behavior, not Spring conditional annotation strings.

- [ ] **Step 4: Remove selector configuration**

Delete `publisher:` under `content.events`, `social.events`, and `user.events` from both application YAML files and Nacos. Delete `CONTENT_EVENTS_PUBLISHER`, `SOCIAL_EVENTS_PUBLISHER`, and `USER_EVENTS_PUBLISHER` rollback comments. Keep:

```yaml
content:
  events:
    outbox-topic: ${CONTENT_EVENTS_OUTBOX_TOPIC:eventbus.content}
    kafka-topic: ${CONTENT_EVENTS_KAFKA_TOPIC:content.events}
```

with equivalent Social/User sections. Keep `events.outbox.enabled: true` explicit.

- [ ] **Step 5: Verify and commit the canonical producer side**

```bash
(cd backend && mvn test -pl :community-app -Dtest=CanonicalEventBackboneGuardTest,KafkaBackboneConfigTest,NacosPolicyBindingTest,OutboxContentEventPublisherTest,ContentEventKafkaOutboxHandlerTest,ContentEventDispatchApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventKafkaOutboxHandlerTest,SocialEventDispatchApplicationServiceTest,OutboxUserPolicyEventPublisherTest,UserEventKafkaOutboxHandlerTest,UserEventDispatchApplicationServiceTest,EventDeliverySurfaceRetirementTest)
rg -n 'events\.publisher|EVENTS_PUBLISHER|Local(Content|Social|User).*Publisher|LocalUserEventPublisher' backend/community-app/src/main deploy/nacos --glob '!**/target/**'
git add backend/community-app deploy/nacos/config/community-kafka-policy.yaml
git commit -m "refactor(events): make owner outbox backbone canonical"
```

Expected: tests pass and the search has no production/config match; retirement tests may contain split class-name literals.

### Task 2: Remove Secondary Search, Reward, And Hot-Feed Paths

**Files:**
- Delete/rename/modify the canonical downstream projection files listed above.

**Interfaces:**
- Renames `ProjectPostOutboxCommand` to `ProjectPostCommand(UUID postId, String sourceEventId, long sourceVersion)`.
- Renames `SearchPostProjectionApplicationService.projectPostFromOutbox(...)` to `projectPost(ProjectPostCommand)`.
- Preserves the existing Kafka listener -> same-domain ApplicationService signatures for User reward, Hot feed, and Notice.

- [ ] **Step 1: Write strict canonical-listener and retirement tests**

First rename Search test expectations to:

```java
verify(applicationService).projectPost(new ProjectPostCommand(postId, "evt-1", 41L));
```

For Search, User reward, Hot feed, and Notice add two cases per recognized event family:

```java
assertThatThrownBy(() -> listener.onContentEvent(recognizedEventWithMissingRequiredField()))
        .isInstanceOf(IllegalArgumentException.class);
listener.onContentEvent(unrelatedEvent());
verifyNoInteractions(applicationService);
```

Extend retirement tests for `PostOutboxEnqueuer`, `PostOutboxHandler`, `CommentRewardOutboxEnqueuer`, and `CommentRewardOutboxHandler`. Rewrite `EventDeliverySemanticsStructureTest` so it requires only the five canonical Kafka ingress classes and asserts no cross-domain `@TransactionalEventListener(AFTER_COMMIT)` remains.

Run:

```bash
(cd backend && mvn test -pl :community-app -Dtest=SearchPostProjectionKafkaListenerTest,UserRewardKafkaListenerTest,PostHotFeedProjectionKafkaListenerTest,NoticeProjectionKafkaListenerTest,EventDeliverySemanticsStructureTest,EventDeliverySurfaceRetirementTest,SearchEventSurfaceRetirementTest)
```

Expected: FAIL because recognized malformed events are silently returned and secondary classes remain.

- [ ] **Step 2: Rename the Search application vocabulary**

Rename the command file/class and change the application method to:

```java
public void projectPost(ProjectPostCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (!StringUtils.hasText(command.sourceEventId()) || command.sourceVersion() <= 0L) {
        throw new IllegalArgumentException("search projection source metadata is invalid");
    }
    // Preserve the existing projection-enabled guard and owner query/upsert/delete behavior.
}
```

The Kafka listener requires a nonblank event ID, non-null `occurredAt`, positive owner version, and non-null post ID before calling `projectPost`. Delete the Search enqueuer/handler and their tests.

- [ ] **Step 3: Delete local/secondary projection adapters**

Delete the User comment reward enqueuer/handler, Hot-feed local listener, their tests, and the now-empty constructor-selection test. Do not change the canonical `UserRewardKafkaListener` or `PostHotFeedProjectionKafkaListener` topology.

For every recognized event in User reward, Hot feed, and Notice, replace a malformed-payload `return` with an `IllegalArgumentException` carrying the event type and source event ID. Retain business no-ops such as self-like and unrelated types.

- [ ] **Step 4: Remove retired projection configuration and update structure tests**

Remove `search.outbox.post-topic` and `user.reward.outbox.comment-topic` from Nacos and application YAML. Update `NacosPolicyBindingTest` to require the canonical Search/User Kafka consumer settings and to assert both retired properties are absent. `EventDeliverySemanticsStructureTest` should require:

```java
assertThat(List.of(
        SearchPostProjectionKafkaListener.class,
        TaskProgressEventBackboneKafkaListener.class,
        UserRewardKafkaListener.class,
        PostHotFeedProjectionKafkaListener.class,
        NoticeProjectionKafkaListener.class
)).allMatch(Objects::nonNull);
```

Delete `SpringEventAdapterConstructorSelectionTest`; it tested only the retired local listener.

- [ ] **Step 5: Verify and commit downstream convergence**

```bash
(cd backend && mvn test -pl :community-app -Dtest=SearchPostProjectionApplicationServiceTest,SearchPostProjectionKafkaListenerTest,UserRewardKafkaListenerTest,PostHotFeedProjectionKafkaListenerTest,NoticeProjectionKafkaListenerTest,EventDeliverySemanticsStructureTest,EventDeliverySurfaceRetirementTest,SearchEventSurfaceRetirementTest,NacosPolicyBindingTest)
rg -n 'PostOutbox(Enqueuer|Handler)|ProjectPostOutboxCommand|projectPostFromOutbox|CommentRewardOutbox|PostHotFeedProjectionLocalListener|projection\.search\.post|projection\.user\.reward\.comment' backend/community-app/src/main deploy/nacos --glob '!**/target/**'
git add backend/community-app deploy/nacos/config/community-kafka-policy.yaml
git commit -m "refactor(events): remove secondary projection paths"
```

Expected: tests pass and the search has no production/config match.

### Task 3: Route User And Social Policy Events Through An IM Application Boundary

**Files:**
- Create/delete/modify the IM policy files listed above.

**Interfaces:**
- Produces `ImPolicyProjectionApplicationService.projectUserPolicy(ProjectUserPolicyCommand): void`.
- Produces `ImPolicyProjectionApplicationService.projectBlockRelation(ProjectBlockRelationCommand): void`.
- Produces `ImPolicyProjectionOutboxPort.enqueue(ImPolicyProjectionEvent): void`.
- Produces deterministic IDs `ip:u:<base64url-sha256>` and `ip:s:<base64url-sha256>` (48 ASCII characters).

- [ ] **Step 1: Write failing listener/application/adapter tests**

Replace `ImPolicyBackboneKafkaListenerTest` mocks with `ImPolicyProjectionApplicationService`. Assert User ingress:

```java
listener.onUserEvent(new UserContractEvent("user-event-1", USER_POLICY_CHANGED, payload));
verify(applicationService).projectUserPolicy(new ProjectUserPolicyCommand(
        "user", "user-event-1", userId, true, false, true,
        muteUntil, banUntil, false, 1712345678901L, 777L));
```

Assert Social ingress uses the top-level source metadata:

```java
listener.onSocialEvent(new SocialContractEvent(
        "social-event-1", blockerId, "user", BLOCK_RELATION_CHANGED,
        Instant.parse("2026-07-10T01:02:03Z"), 888L, payload));
verify(applicationService).projectBlockRelation(new ProjectBlockRelationCommand(
        "social", "social-event-1", blockerId, blockedId, true,
        Instant.parse("2026-07-10T01:02:03Z").toEpochMilli(), 888L));
```

Add malformed recognized-event assertions for blank ID, missing identities/time, and non-positive version; unrelated event types remain ignored. Add `ImPolicyProjectionApplicationServiceTest` that captures the port event. Add `JdbcImPolicyProjectionOutboxAdapterTest` with H2 schema: enqueue the same source twice, assert one `projection.im.policy` row, equal payload, ID length `48`, and prefix `ip:u:`/`ip:s:`.

Run:

```bash
(cd backend && mvn test -pl :community-app -Dtest=ImPolicyBackboneKafkaListenerTest,ImPolicyProjectionApplicationServiceTest,JdbcImPolicyProjectionOutboxAdapterTest)
```

Expected: compilation fails until the new application boundary exists.

- [ ] **Step 2: Define commands, event, port, and application service**

Create these exact command fields:

```java
public record ProjectUserPolicyCommand(
        String sourceDomain, String sourceEventId, UUID userId,
        boolean userExists, boolean suspended, boolean muted,
        Long muteUntil, Long banUntil, boolean canSendPrivate,
        long occurredAtEpochMillis, long version) {}

public record ProjectBlockRelationCommand(
        String sourceDomain, String sourceEventId,
        UUID blockerUserId, UUID blockedUserId, boolean active,
        long occurredAtEpochMillis, long version) {}
```

Define the application-owned outbox event exactly as:

```java
public record ImPolicyProjectionEvent(
        String sourceDomain,
        String sourceEventId,
        String kind,
        UUID primaryUserId,
        UUID secondaryUserId,
        Boolean active,
        boolean userExists,
        boolean suspended,
        boolean muted,
        Long muteUntil,
        Long banUntil,
        boolean canSendPrivate,
        long occurredAtEpochMillis,
        long version
) {}
```

Define the application-owned port as:

```java
public interface ImPolicyProjectionOutboxPort {
    void enqueue(ImPolicyProjectionEvent event);
}
```

`ImPolicyProjectionApplicationService` is `@Service` and both methods are `@Transactional`. Each method requires the expected source domain (`user` or `social`), nonblank source ID, required UUIDs, positive occurred-at millis, and positive version, constructs kind `USER_POLICY` or `BLOCK`, then calls the port exactly once.

- [ ] **Step 3: Implement deterministic JDBC outbox adaptation**

`JdbcImPolicyProjectionOutboxAdapter` implements the port and serializes the application event. Build IDs as:

```java
byte[] digest = MessageDigest.getInstance("SHA-256").digest(
        (event.sourceDomain() + "\u0000" + event.sourceEventId() + "\u0000" + event.kind())
                .getBytes(StandardCharsets.UTF_8));
String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
String eventId = ("USER_POLICY".equals(event.kind()) ? "ip:u:" : "ip:s:") + suffix;
store.enqueue(eventId, topic, event.primaryUserId().toString(), jsonCodec.toJson(event));
```

Wrap `NoSuchAlgorithmException` as an impossible `IllegalStateException`; wrap JSON failures with the existing IM policy serialization message. Ignore the boolean return from `enqueue`: `false` is the intended duplicate-source replay result.

- [ ] **Step 4: Rewrite the backbone listener and retain the dispatch tail**

The listener constructor takes only `ImPolicyProjectionApplicationService` and `JsonCodec`. Add a User `@KafkaListener` using `${user.events.kafka-topic:user.events}` and the existing IM policy group/concurrency. Social uses the same group ID.

Normalize map payloads through `JsonCodec`; for recognized types throw on invalid fields. User time/version come from `UserPolicyChangedPayload`; Social time/version come from `SocialContractEvent.occurredAt/version`. Delete `ImPolicyOutboxEnqueuer`, `ImPolicyChangePublisher`, and their tests.

Remove `events.outbox` conditional annotations from the retained IM policy outbox handler, dispatch ApplicationService, and Kafka sender. In `ImPolicyEventDispatchApplicationService`, unknown kind or malformed recognized internal payload throws rather than returning success; keep the two existing IM event dispatch calls.

- [ ] **Step 5: Verify idempotency, layering, and commit**

```bash
(cd backend && mvn test -pl :community-app -Dtest=ImPolicyBackboneKafkaListenerTest,ImPolicyProjectionApplicationServiceTest,JdbcImPolicyProjectionOutboxAdapterTest,ImPolicyKafkaOutboxHandlerTest,ImPolicyEventDispatchApplicationServiceTest,ImPolicyEventKafkaSenderAdapterTest)
rg -n 'ImPolicyOutboxEnqueuer|ImPolicyChangePublisher|System\.currentTimeMillis\(\)' backend/community-app/src/main/java/com/nowcoder/community/im --glob '*.java'
git add backend/community-app
git commit -m "fix(im): converge policy events through application boundary"
```

Expected: tests pass; duplicate source events create one outbox row; the search has no match.

### Task 4: Retire Growth Synchronous And Secondary Pipelines

**Files:**
- Delete/modify all Growth files listed above.

**Interfaces:**
- Retains only `TaskProgressEventBackboneKafkaListener -> TaskProgressApplicationService` for post/comment/like task progress.
- Preserves `triggerPostPublished`, `triggerCommentCreated`, `triggerLikeCreated`, and `triggerLikeRemoved` application methods and commands already used by that listener.

- [ ] **Step 1: Expand Growth retirement and malformed-event tests**

Add class-retirement assertions for the action API, adapter, two request models, six projection outbox adapters, dispatch service/command/kind, secondary sender, and secondary listener. In `TaskProgressEventBackboneKafkaListenerTest`, replace `missingRequiredGrowthPayloadFieldsShouldBeIgnored` with:

```java
assertThatThrownBy(() -> listener.onContentEvent(recognizedPostWithMissingUser()))
        .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> listener.onSocialEvent(recognizedLikeWithMissingTime()))
        .isInstanceOf(IllegalArgumentException.class);
```

Retain tests for unsupported event ignore, self-like ignore, stable relation key, and `LIKE_REMOVED` rollback.

Run:

```bash
(cd backend && mvn test -pl :community-app -Dtest=TaskProgressEventBackboneKafkaListenerTest,LegacyGrowthSurfaceRetirementTest)
```

Expected: FAIL because the old classes remain and malformed events are swallowed.

- [ ] **Step 2: Delete the unused synchronous owner API**

Delete `GrowthTaskProgressActionApi`, its adapter, its two request models, and the adapter test. Do not delete `UserLevelQueryApi`, wallet reward APIs, or the existing `TaskProgressApplicationService` commands.

- [ ] **Step 3: Delete the secondary Growth outbox/Kafka chain**

Delete the exact post/comment/like projection enqueuers, handlers, dispatch classes, secondary sender/listener, and test files enumerated under **Growth Retirement** in File Structure.

Remove `growth.task.outbox.*` and `growth.task.kafka.topics.*` from application/Nacos configuration. Update `NacosPolicyBindingTest` to assert those keys are absent and the canonical consumer keys remain. Keep only:

```yaml
growth:
  task:
    kafka:
      consumer:
        group-id: ${GROWTH_TASK_KAFKA_CONSUMER_GROUP_ID:growth-task-progress}
        concurrency: ${GROWTH_TASK_KAFKA_CONSUMER_CONCURRENCY:3}
```

- [ ] **Step 4: Make the canonical Growth listener fail closed**

For each recognized Content/Social event, require nonblank source event ID, positive source version, required IDs, and required source time before constructing the existing application command. Throw an `IllegalArgumentException` containing the event type and source ID on malformed data. Keep this business no-op:

```java
if (payload.getActorUserId().equals(payload.getEntityUserId())) {
    return; // A self-like is valid but contributes no task progress.
}
```

- [ ] **Step 5: Verify and commit Growth retirement**

```bash
(cd backend && mvn test -pl :community-app -Dtest=TaskProgressEventBackboneKafkaListenerTest,TaskProgressApplicationServiceTest,LegacyGrowthSurfaceRetirementTest,CommentApplicationServiceTest,NacosPolicyBindingTest)
rg -n 'GrowthTaskProgressActionApi|Growth(Comment|Like)TaskProgressRequest|TaskProgressOutbox|TaskProgressKafka(SenderAdapter|Listener)|TaskProgressKafkaOutbox|projection\.growth\.task|growth\.task\.(post-published|comment-created|like-created|like-removed)' backend/community-app/src/main deploy/nacos --glob '!**/target/**'
git add backend/community-app deploy/nacos/config/community-kafka-policy.yaml
git commit -m "refactor(growth): retire duplicate task progress surfaces"
```

Expected: tests pass and the search has no production/config match.

### Task 5: Apply Retry/DLQ Policy To The Community App Kafka Factory

**Files:**
- Create the Kafka configuration/test and deployment topic contract files listed above.

**Interfaces:**
- Produces bean `kafkaListenerContainerFactory` with a shared `DefaultErrorHandler` and composed trace/runtime interceptors.
- Produces bean `communityKafkaDefaultErrorHandler` using `<source-topic>.dlq` in the original partition.
- Produces package-private `retryBackOff(KafkaPolicyDecisions): ExponentialBackOff` and `deadLetterPublishingRecoverer(KafkaTemplate<Object, Object>): DeadLetterPublishingRecoverer` helpers for focused policy tests.
- Requires `community.kafka-policy.dlq.enabled=true`.

- [ ] **Step 1: Write failing factory/recoverer tests**

Create `CommunityKafkaListenerConfigurationTest`. With `KafkaPolicyProperties` set to three total attempts, `100ms` base, `1s` max, assert the configuration creates exactly two backoff intervals:

```java
BackOffExecution execution = configuration.retryBackOff(decisions).start();
assertThat(execution.nextBackOff()).isEqualTo(100L);
assertThat(execution.nextBackOff()).isEqualTo(200L);
assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
```

Capture a DLQ `ProducerRecord` from a recovered `ConsumerRecord("content.events", 4, 19L, ...)`. Decode Kafka native header bytes explicitly:

```java
private static String stringHeader(ProducerRecord<?, ?> record, String key) {
    Header header = requireNonNull(record.headers().lastHeader(key));
    return new String(header.value(), StandardCharsets.UTF_8);
}

private static int intHeader(ProducerRecord<?, ?> record, String key) {
    Header header = requireNonNull(record.headers().lastHeader(key));
    return ByteBuffer.wrap(header.value()).getInt();
}

private static long longHeader(ProducerRecord<?, ?> record, String key) {
    Header header = requireNonNull(record.headers().lastHeader(key));
    return ByteBuffer.wrap(header.value()).getLong();
}
```

Then assert:

```java
assertThat(dlq.topic()).isEqualTo("content.events.dlq");
assertThat(dlq.partition()).isEqualTo(4);
assertThat(stringHeader(dlq, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo("content.events");
assertThat(intHeader(dlq, KafkaHeaders.DLT_ORIGINAL_PARTITION)).isEqualTo(4);
assertThat(longHeader(dlq, KafkaHeaders.DLT_ORIGINAL_OFFSET)).isEqualTo(19L);
assertThat(stringHeader(dlq, KafkaHeaders.DLT_EXCEPTION_FQCN))
        .isEqualTo(IllegalArgumentException.class.getName());
```

Make the mocked Kafka send fail and assert recovery propagates rather than acknowledging success. Assert disabled DLQ throws during bean creation.

Run `(cd backend && mvn test -pl :community-app -Dtest=CommunityKafkaListenerConfigurationTest)`; expect compilation failure.

- [ ] **Step 2: Implement the listener factory and policy-driven handler**

Follow the IM modules' factory composition: configure with `ConcurrentKafkaListenerContainerFactoryConfigurer`, set the common error handler, prepend `new TraceRecordInterceptor()`, and append any other `RecordInterceptor` beans in order.

Build backoff explicitly:

```java
ExponentialBackOff backOff = new ExponentialBackOff(
        decisions.retryBaseBackoff().toMillis(), 2.0);
backOff.setMaxInterval(decisions.retryMaxBackoff().toMillis());
backOff.setMaxAttempts(Math.max(0, decisions.retryMaxAttempts() - 1));
```

`ExponentialBackOff.maxAttempts` counts retry/backoff intervals, while `KafkaPolicyDecisions.retryMaxAttempts()` counts the initial delivery too; the subtraction is therefore required, including the zero-retry case when the configured total is `1`.

If `dlqEnabled()` is false, throw `IllegalStateException`. Configure the recoverer destination as `new TopicPartition(record.topic() + ".dlq", record.partition())`, then call:

```java
recoverer.setAppendOriginalHeaders(true);
recoverer.setRetainExceptionHeader(true);
recoverer.setFailIfSendResultIsError(true);
recoverer.setStripPreviousExceptionHeaders(false);
```

Do not classify `IllegalArgumentException` as non-retryable; recognized malformed events must use the finite retry budget before DLQ. Deserialization failures may remain non-retryable because retry cannot change the bytes.

- [ ] **Step 3: Add owner topics and same-partition DLQs**

Add these exact topic declarations to `bootstrap-kafka-topics.sh`:

```text
content.events=12
content.events.dlq=12
social.events=12
social.events.dlq=12
user.events=12
user.events.dlq=12
```

Create `community_backbone_topics_contract.sh` with this complete body; `partition_count` also enforces that every declaration appears exactly once:

```bash
#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
BOOTSTRAP="${REPO_ROOT}/deploy/scripts/bootstrap-kafka-topics.sh"

partition_count() {
  local wanted="$1"
  awk -v wanted="${wanted}" '
    {
      topic = ""
      partitions = ""
      for (i = 1; i <= NF; i++) {
        if ($i == "--topic") topic = $(i + 1)
        if ($i == "--partitions") partitions = $(i + 1)
      }
      if (topic == wanted) {
        count++
        value = partitions
      }
    }
    END {
      if (count != 1 || value == "") exit 1
      print value
    }
  ' "${BOOTSTRAP}"
}

for topic in content.events social.events user.events; do
  test "$(partition_count "${topic}")" = "12"
  test "$(partition_count "${topic}.dlq")" = "12"
done
```

Run `chmod +x deploy/tests/community_backbone_topics_contract.sh` before its first execution.

- [ ] **Step 4: Verify and commit Kafka reliability**

```bash
(cd backend && mvn test -pl :community-app -Dtest=CommunityKafkaListenerConfigurationTest,KafkaBackboneConfigTest)
./deploy/tests/community_backbone_topics_contract.sh
git add backend/community-app deploy/scripts/bootstrap-kafka-topics.sh deploy/tests/community_backbone_topics_contract.sh
git commit -m "feat(events): enforce consumer retry and dlq policy"
```

Expected: tests and topic contract pass; DLQ send failure remains visible to the listener container.

### Task 6: Lock The Application Boundary And Verify The Event Slice

**Files:**
- Modify the ArchUnit helper/test and retirement tests listed above.

**Interfaces:**
- Adds an ArchUnit condition forbidding inbound adapters from depending on another same-domain infrastructure class before an ApplicationService.

- [ ] **Step 1: Add the failing same-domain-infrastructure rule**

In `ArchitectureRulesSupport`, add:

```java
static ArchCondition<JavaClass> notDependOnSameDomainInfrastructureBeforeApplicationService(
        Set<String> legacyOriginWhitelist) {
    return new ArchCondition<>("not depend on same-domain infrastructure before ApplicationService boundary") {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            if (isWhitelisted(item, legacyOriginWhitelist)) return;
            String originDomain = domainOf(item);
            for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (originDomain.equals(domainOf(target))
                        && residesInLayer(target, Set.of("infrastructure"))
                        && !sharesTopLevelOwner(item, target)) {
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        }
    };
}
```

Apply it in `ListenerBoundaryArchTest` to the existing Listener/Handler/Bridge/Enqueuer/Job selection. Run the ArchTest first; expect it to expose any remaining listener-to-publisher/helper dependency.

- [ ] **Step 2: Complete retirement inventory**

Make `EventDeliverySurfaceRetirementTest`, `SearchEventSurfaceRetirementTest`, and `LegacyGrowthSurfaceRetirementTest` enumerate every class deleted by Tasks 1-4. Split class-name literals when the final repository search intentionally requires zero exact-name matches, for example:

```java
assertClassRetired("com.nowcoder.community.im.infrastructure.event.ImPolicy" + "ChangePublisher");
```

- [ ] **Step 3: Run focused event and architecture suites**

```bash
(cd backend && mvn test -pl :community-app -Dtest='*ArchTest,EventDelivery*Test,*KafkaListenerTest,*Outbox*Test,LegacyGrowthSurfaceRetirementTest,SearchEventSurfaceRetirementTest')
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Search for retired runtime surfaces**

```bash
rg -n 'events\.publisher|EVENTS_PUBLISHER|Local(Content|Social|User).*Publisher|LocalUserEventPublisher|PostHotFeedProjectionLocalListener|PostOutbox(Enqueuer|Handler)|ProjectPostOutboxCommand|CommentRewardOutbox|ImPolicyOutboxEnqueuer|ImPolicyChangePublisher|GrowthTaskProgressActionApi|TaskProgressOutbox|TaskProgressKafka(SenderAdapter|Listener)|projection\.(search\.post|growth\.task|user\.reward\.comment)' backend/community-app/src/main deploy --glob '!**/target/**'
```

Expected: no match. `projection.im.policy` remains and must not be included in the retired search.

- [ ] **Step 5: Commit verification-only fixes if needed**

```bash
git add backend/community-app deploy
git commit -m "test(events): lock canonical backbone boundaries"
```

Skip the commit when verification required no changes.

# Community Social Chain Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the social interaction chain correct under the default outbox-kafka deployment mode, reject self-like at the owner boundary, make unlike and cleanup produce reversible downstream semantics, revoke like notices, and roll back only unclaimed like-driven growth progress.

**Architecture:** Keep `social` as the owner domain for likes, follows, and blocks, but make `social.events` the canonical async source for cross-domain consumers. Harden owner-side like lifecycle semantics with a stable relation identity and replace silent bulk cleanup with paged standard unlike emission. Downstream modules consume standard `LikeCreated` / `LikeRemoved` behavior and implement idempotent revoke/reverse logic.

**Tech Stack:** Spring Boot, MyBatis, Kafka, JDBC outbox, ArchUnit, JUnit 5, Mockito

---

## File Structure

### Social owner files

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/LikeDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/LikeChangedDomainEvent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/LikePayload.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/LikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/model/LikeRelation.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisLikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/LikeMapper.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/dataobject/LikeScanDataObject.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java`

### Startup / configuration guardrails

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/startup/StartupValidationTest.java`
- Modify: `docs/handbook/architecture.md`
- Modify: `docs/handbook/system-design.md`

### IM backbone consumer files

- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyBackboneKafkaListener.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/ImPolicyBackboneKafkaListenerTest.java`

### Content post-score backbone consumer files

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SocialInteractionBackboneKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/SocialInteractionProjectionApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/event/SocialInteractionBackboneKafkaListenerTest.java`

### Notice revoke files

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/model/NoticeRecord.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/repository/NoticeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/dataobject/NoticeRecordDataObject.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/mapper/NoticeMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/notice_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/MyBatisNoticeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java`

### Growth reverse files

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/TriggerLikeCreatedCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/TriggerLikeRemovedCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/domain/repository/UserTaskEventLogRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/mapper/UserTaskEventLogMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/user_task_event_log_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/MyBatisUserTaskEventLogRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/domain/repository/UserTaskProgressRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/mapper/UserTaskProgressMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/user_task_progress_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/MyBatisUserTaskProgressRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListener.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListenerTest.java`

### Reward reverse alignment files

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRewardApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListener.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListenerTest.java`

### Social storage redis guard / tests

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/persistence/RedisBlockRepositoryTest.java`

### Optional cleanup of local-only correctness paths

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyOutboxEnqueuer.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SocialInteractionProjectionListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxEnqueuer.java`

---

### Task 1: Backbone Correctness for IM Block Projection

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyBackboneKafkaListener.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/ImPolicyBackboneKafkaListenerTest.java`

- [ ] **Step 1: Write the failing test for `BlockRelationChanged` backbone consumption**

```java
@Test
void shouldForwardBlockRelationChangedFromSocialBackbone() {
    ImPolicyChangePublisher publisher = mock(ImPolicyChangePublisher.class);
    ImPolicyBackboneKafkaListener listener = new ImPolicyBackboneKafkaListener(publisher);

    BlockPayload payload = new BlockPayload();
    payload.setBlockerUserId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
    payload.setBlockedUserId(UUID.fromString("00000000-0000-0000-0000-000000000022"));
    payload.setBlocked(Boolean.TRUE);
    payload.setVersion(123L);

    listener.onSocialEvent(new SocialContractEvent(
            "evt-block-1",
            SocialEventTypes.BLOCK_RELATION_CHANGED,
            payload
    ));

    verify(publisher).publishBlockRelationChanged(
            payload.getBlockerUserId(),
            payload.getBlockedUserId(),
            true,
            123L
    );
}
```

- [ ] **Step 2: Run the test to confirm the listener does not exist yet**

Run: `cd backend && mvn test -pl :community-app -Dtest=ImPolicyBackboneKafkaListenerTest`
Expected: FAIL with compilation error for missing `ImPolicyBackboneKafkaListener`

- [ ] **Step 3: Implement the minimal backbone Kafka listener**

```java
@Component
public class ImPolicyBackboneKafkaListener {

    private final ImPolicyChangePublisher imPolicyChangePublisher;

    public ImPolicyBackboneKafkaListener(ImPolicyChangePublisher imPolicyChangePublisher) {
        this.imPolicyChangePublisher = imPolicyChangePublisher;
    }

    @KafkaListener(
            topics = "${social.events.kafka-topic:social.events}",
            groupId = "${im.policy.kafka.consumer.group-id:im-policy-projection}",
            concurrency = "${im.policy.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null
                || !SocialEventTypes.BLOCK_RELATION_CHANGED.equals(event.type())
                || !(event.payload() instanceof BlockPayload payload)
                || payload.getBlockerUserId() == null
                || payload.getBlockedUserId() == null
                || payload.getBlocked() == null) {
            return;
        }
        imPolicyChangePublisher.publishBlockRelationChanged(
                payload.getBlockerUserId(),
                payload.getBlockedUserId(),
                payload.getBlocked(),
                payload.getVersion() == null ? 0L : payload.getVersion()
        );
    }
}
```

- [ ] **Step 4: Run the targeted test**

Run: `cd backend && mvn test -pl :community-app -Dtest=ImPolicyBackboneKafkaListenerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyBackboneKafkaListener.java \
        backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/ImPolicyBackboneKafkaListenerTest.java
git commit -m "feat: consume social block events for im policy"
```

### Task 2: Backbone Correctness for Post Score Projection

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SocialInteractionBackboneKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/SocialInteractionProjectionApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/event/SocialInteractionBackboneKafkaListenerTest.java`

- [ ] **Step 1: Write the failing backbone listener test for post like/unlike**

```java
@Test
void shouldProjectPostLikeEventsFromSocialBackbone() {
    SocialInteractionProjectionApplicationService applicationService = mock(SocialInteractionProjectionApplicationService.class);
    SocialInteractionBackboneKafkaListener listener = new SocialInteractionBackboneKafkaListener(applicationService);

    LikePayload payload = new LikePayload();
    payload.setActorUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    payload.setEntityType(EntityTypes.POST);
    payload.setEntityId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
    payload.setPostId(payload.getEntityId());

    listener.onSocialEvent(new SocialContractEvent("evt-like-1", SocialEventTypes.LIKE_CREATED, payload));

    verify(applicationService).projectSocialEvent(any(SocialContractEvent.class));
}
```

- [ ] **Step 2: Run the test to confirm the listener is missing**

Run: `cd backend && mvn test -pl :community-app -Dtest=SocialInteractionBackboneKafkaListenerTest`
Expected: FAIL with compilation error for missing `SocialInteractionBackboneKafkaListener`

- [ ] **Step 3: Implement the backbone listener**

```java
@Component
public class SocialInteractionBackboneKafkaListener {

    private final SocialInteractionProjectionApplicationService applicationService;

    public SocialInteractionBackboneKafkaListener(
            SocialInteractionProjectionApplicationService applicationService
    ) {
        this.applicationService = applicationService;
    }

    @KafkaListener(
            topics = "${social.events.kafka-topic:social.events}",
            groupId = "${content.score.kafka.consumer.group-id:content-post-score}",
            concurrency = "${content.score.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        applicationService.projectSocialEvent(event);
    }
}
```

- [ ] **Step 4: Run the targeted test**

Run: `cd backend && mvn test -pl :community-app -Dtest=SocialInteractionBackboneKafkaListenerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SocialInteractionBackboneKafkaListener.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/event/SocialInteractionBackboneKafkaListenerTest.java
git commit -m "feat: consume social like events for post score projection"
```

### Task 3: Reject Self-Like and Enrich Like Event Contract

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/LikeDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/LikeChangedDomainEvent.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/LikePayload.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`

- [ ] **Step 1: Add a failing test for self-like rejection**

```java
@Test
void shouldRejectSelfLikeForUserEntity() {
    LikeApplicationService service = serviceWithDefaults();

    SetLikeCommand command = new SetLikeCommand(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            EntityTypes.USER,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            true
    );

    assertThatThrownBy(() -> service.setLike(command))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不能给自己点赞");
}
```

- [ ] **Step 2: Add a failing test for stable relation identity in emitted like events**

```java
@Test
void shouldEmitStableRelationKeyForLikeCreatedAndRemoved() {
    SocialDomainEventPublisher publisher = mock(SocialDomainEventPublisher.class);
    LikeApplicationService service = serviceWithPublisher(publisher);

    UUID actor = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID post = UUID.fromString("00000000-0000-0000-0000-000000000099");

    service.setLike(new SetLikeCommand(actor, EntityTypes.POST, post, true));
    service.setLike(new SetLikeCommand(actor, EntityTypes.POST, post, false));

    ArgumentCaptor<LikeChangedDomainEvent> captor = ArgumentCaptor.forClass(LikeChangedDomainEvent.class);
    verify(publisher, times(2)).publishLikeChanged(captor.capture());

    List<LikeChangedDomainEvent> events = captor.getAllValues();
    assertThat(events.get(0).relationKey()).isEqualTo(events.get(1).relationKey());
}
```

- [ ] **Step 3: Run the focused social application tests**

Run: `cd backend && mvn test -pl :community-app -Dtest=LikeApplicationServiceTest`
Expected: FAIL on missing self-like rule and missing `relationKey`

- [ ] **Step 4: Implement the minimal owner-side changes**

```java
public record LikeChangedDomainEvent(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId,
        UUID postId,
        String relationKey,
        boolean liked,
        Instant occurredAt
) {
}
```

```java
public class LikeDomainService {

    public void validateLike(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        if (!isSupportedLikeEntityType(entityType) || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }
        if (entityType == USER && actorUserId.equals(entityId)) {
            throw new BusinessException(INVALID_ARGUMENT, "不能给自己点赞");
        }
    }

    public String relationKey(UUID actorUserId, int entityType, UUID entityId) {
        return "like:" + actorUserId + ":" + entityType + ":" + entityId;
    }
}
```

- [ ] **Step 5: Update payload publication to include `relationKey` and `occurredAt`**

```java
payload.setRelationKey(event.relationKey());
payload.setOccurredAt(event.occurredAt());
```

- [ ] **Step 6: Run the focused social tests**

Run: `cd backend && mvn test -pl :community-app -Dtest=LikeApplicationServiceTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/LikeDomainService.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/LikeChangedDomainEvent.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/LikePayload.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java \
        backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java
git commit -m "feat: reject self like and stabilize like event identity"
```

### Task 4: Enforce DB-Backed Social Storage at Startup

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/startup/StartupValidationTest.java`
- Modify: `docs/handbook/architecture.md`
- Modify: `docs/handbook/system-design.md`

- [ ] **Step 1: Write the failing startup validation test**

```java
@Test
void shouldRejectNonDbSocialStorage() {
    MockEnvironment environment = prodEnvironment()
            .withProperty("social.storage", "redis");

    assertThatThrownBy(() -> new StartupValidation().validateOrThrow(environment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("social.storage=db");
}
```

- [ ] **Step 2: Run the startup validation test**

Run: `cd backend && mvn test -pl :community-app -Dtest=StartupValidationTest`
Expected: FAIL because the startup guard does not exist

- [ ] **Step 3: Add the startup guard**

```java
private void validateSocialStorage(Environment environment, List<String> errors) {
    String socialStorage = getTrimmed(environment, "social.storage");
    if (!StringUtils.hasText(socialStorage)) {
        socialStorage = "db";
    }
    if (!"db".equalsIgnoreCase(socialStorage)) {
        errors.add("配置不安全：social.storage=" + socialStorage + "（strict social chain requires social.storage=db）");
    }
}
```

- [ ] **Step 4: Update handbook docs to reflect the explicit DB-only constraint**

```markdown
- `social` strict interaction chain requires `social.storage=db`.
- Redis-backed social storage is not a supported runtime for like/block/im-policy correctness.
```

- [ ] **Step 5: Run the test**

Run: `cd backend && mvn test -pl :community-app -Dtest=StartupValidationTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/infra/startup/StartupValidation.java \
        backend/community-app/src/test/java/com/nowcoder/community/infra/startup/StartupValidationTest.java \
        docs/handbook/architecture.md \
        docs/handbook/system-design.md
git commit -m "feat: enforce db-backed social storage"
```

### Task 5: Replace Silent Bulk Like Cleanup with Paged Standard Unlike Emission

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/LikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/model/LikeRelation.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/dataobject/LikeScanDataObject.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/LikeMapper.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisLikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`

- [ ] **Step 1: Add a failing cleanup test that expects per-like removed events**

```java
@Test
void cleanupShouldEmitLikeRemovedForEachExistingLike() {
    SocialDomainEventPublisher publisher = mock(SocialDomainEventPublisher.class);
    LikeRepository repository = repositoryWithTwoLikesOnSameEntity();
    LikeApplicationService service = service(repository, publisher);

    long removed = service.cleanupEntityLikes(EntityTypes.POST, UUID.fromString("00000000-0000-0000-0000-000000000099"));

    assertThat(removed).isEqualTo(2L);
    verify(publisher, times(2)).publishLikeChanged(argThat(event -> !event.liked()));
}
```

- [ ] **Step 2: Run the targeted social test**

Run: `cd backend && mvn test -pl :community-app -Dtest=LikeApplicationServiceTest`
Expected: FAIL because cleanup is currently silent

- [ ] **Step 3: Extend the repository contract for scan-based cleanup**

```java
default List<LikeRelation> scanLikesByEntity(int entityType, UUID entityId, UUID afterActorUserId, int limit) {
    return List.of();
}
```

```java
public record LikeRelation(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId
) {
}
```

- [ ] **Step 4: Implement paged DB scanning and per-like cleanup**

```java
@Transactional
public long cleanupEntityLikes(int entityType, UUID entityId) {
    validateLikeEntity(entityType, entityId);
    long removed = 0L;
    UUID afterActorUserId = new UUID(0L, 0L);
    while (true) {
        List<LikeRelation> page = likeRepository.scanLikesByEntity(entityType, entityId, afterActorUserId, 200);
        if (page.isEmpty()) {
            return removed;
        }
        for (LikeRelation relation : page) {
            boolean changed = likeRepository.setLike(
                    relation.actorUserId(),
                    entityType,
                    entityId,
                    relation.entityUserId(),
                    false
            );
            if (!changed) {
                continue;
            }
            eventPublisher.publishLikeChanged(likeDomainService.likeChangedEvent(
                    relation.actorUserId(),
                    entityType,
                    entityId,
                    new ResolvedSocialEntity(relation.entityUserId(), entityType == POST ? entityId : null),
                    false,
                    Instant.now()
            ));
            removed++;
            afterActorUserId = relation.actorUserId();
        }
    }
}
```

- [ ] **Step 5: Remove absolute reset logic from the normal DB cleanup path**

```java
@Override
public long deleteLikesByEntity(int entityType, UUID entityId) {
    throw new UnsupportedOperationException("bulk delete path is not part of the standard social cleanup flow");
}
```

- [ ] **Step 6: Run the focused cleanup tests**

Run: `cd backend && mvn test -pl :community-app -Dtest=LikeApplicationServiceTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/LikeRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/domain/model/LikeRelation.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/dataobject/LikeScanDataObject.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/LikeMapper.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisLikeRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java
git commit -m "feat: emit standard unlike events during like cleanup"
```

### Task 6: Add Like Notice Revocation

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/model/NoticeRecord.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/domain/repository/NoticeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/dataobject/NoticeRecordDataObject.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/mapper/NoticeMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/notice_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/MyBatisNoticeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java`

- [ ] **Step 1: Add a failing projection test for notice revoke on `LikeRemoved`**

```java
@Test
void shouldRevokeLikeNoticeOnLikeRemoved() {
    NoticeApplicationService noticeApplicationService = mock(NoticeApplicationService.class);
    NoticeProjectionApplicationService service = projectionService(noticeApplicationService);

    LikePayload payload = new LikePayload();
    payload.setEntityUserId(UUID.fromString("00000000-0000-0000-0000-000000000055"));
    payload.setRelationKey("like:actor:3:entity");

    service.projectSocialEventReliably(new ProjectSocialNoticeCommand(
            "evt-like-removed-1",
            SocialEventTypes.LIKE_REMOVED,
            payload
    ));

    verify(noticeApplicationService).revokeLikeNotice(
            payload.getEntityUserId(),
            payload.getRelationKey()
    );
}
```

- [ ] **Step 2: Run the projection test**

Run: `cd backend && mvn test -pl :community-app -Dtest=NoticeProjectionApplicationServiceTest`
Expected: FAIL because no revoke flow exists

- [ ] **Step 3: Add repository and application revoke capability**

```java
public interface NoticeRepository {
    int revokeLikeNotice(UUID recipientUserId, String relationKey, int revokedStatus);
}
```

```java
public void revokeLikeNotice(UUID recipientUserId, String relationKey) {
    if (recipientUserId == null || relationKey == null || relationKey.isBlank()) {
        return;
    }
    noticeRepository.revokeLikeNotice(recipientUserId, relationKey.trim(), STATUS_REVOKED);
}
```

- [ ] **Step 4: Update social notice projection to handle `LikeRemoved`**

```java
if (SocialEventTypes.LIKE_REMOVED.equals(command.eventType()) && command.payload() instanceof LikePayload payload) {
    noticeApplicationService.revokeLikeNotice(payload.getEntityUserId(), payload.getRelationKey());
    return null;
}
```

- [ ] **Step 5: Extend Kafka listener support for `LikeRemoved`**

```java
private boolean isSupportedSocialNoticeEvent(String type) {
    return SocialEventTypes.LIKE_CREATED.equals(type)
            || SocialEventTypes.LIKE_REMOVED.equals(type)
            || SocialEventTypes.FOLLOW_CREATED.equals(type);
}
```

- [ ] **Step 6: Run the notice tests**

Run: `cd backend && mvn test -pl :community-app -Dtest=NoticeProjectionApplicationServiceTest,NoticeApplicationServiceTest,NoticeProjectionKafkaListenerTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/notice/domain/model/NoticeRecord.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/domain/repository/NoticeRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/dataobject/NoticeRecordDataObject.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/mapper/NoticeMapper.java \
        backend/community-app/src/main/resources/mapper/notice_mapper.xml \
        backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/persistence/MyBatisNoticeRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListener.java \
        backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java
git commit -m "feat: revoke like notices on unlike"
```

### Task 7: Roll Back Only Unclaimed Growth Progress on Like Removed

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/TriggerLikeRemovedCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/domain/repository/UserTaskEventLogRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/mapper/UserTaskEventLogMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/user_task_event_log_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/MyBatisUserTaskEventLogRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/domain/repository/UserTaskProgressRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/mapper/UserTaskProgressMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/user_task_progress_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/MyBatisUserTaskProgressRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListener.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListenerTest.java`

- [ ] **Step 1: Add a failing application test for unclaimed rollback**

```java
@Test
void likeRemovedShouldRollbackUnclaimedLikeTaskProgress() {
    TaskProgressApplicationService service = serviceWithLikeProgressFixtures(
            progress("daily-like", "2026-06-26", 1, 3, "CLAIMABLE", null)
    );

    service.triggerLikeRemoved(new TriggerLikeRemovedCommand(
            "like:0001:3:0099",
            UUID.fromString("00000000-0000-0000-0000-000000000055")
    ));

    verify(userTaskProgressRepository).updateProgress(
            any(),
            eq(0),
            eq("IN_PROGRESS"),
            isNull(),
            isNull(),
            isNull(),
            eq("like:0001:3:0099")
    );
}
```

- [ ] **Step 2: Add a failing application test for claimed no-op**

```java
@Test
void likeRemovedShouldNotRollbackClaimedLikeTaskProgress() {
    TaskProgressApplicationService service = serviceWithLikeProgressFixtures(
            progress("daily-like", "2026-06-26", 1, 1, "CLAIMED", "grant-1")
    );

    service.triggerLikeRemoved(new TriggerLikeRemovedCommand(
            "like:0001:3:0099",
            UUID.fromString("00000000-0000-0000-0000-000000000055")
    ));

    verify(userTaskProgressRepository, never()).updateProgress(any(), anyInt(), anyString(), any(), any(), any(), anyString());
}
```

- [ ] **Step 3: Run the growth application tests**

Run: `cd backend && mvn test -pl :community-app -Dtest=TaskProgressApplicationServiceTest`
Expected: FAIL because `TriggerLikeRemovedCommand` and reverse logic do not exist

- [ ] **Step 4: Implement the reverse command and reverse path**

```java
public record TriggerLikeRemovedCommand(String relationKey, UUID entityUserId) {
}
```

```java
@Transactional
public void triggerLikeRemoved(TriggerLikeRemovedCommand command) {
    if (command == null || !StringUtils.hasText(command.relationKey()) || command.entityUserId() == null) {
        return;
    }
    rollbackLikeCreatedProgress(command.entityUserId(), command.relationKey().trim());
}
```

- [ ] **Step 5: Implement repository support to locate and remove matching event-log rows**

```java
record UserTaskContributionLog(UUID userId, String taskCode, String periodKey, String sourceEventId) {
}

List<UserTaskContributionLog> findLikeContributionLogs(UUID userId, String relationKey);

int deleteByUserTaskPeriodAndSourceEventId(UUID userId, String taskCode, String periodKey, String sourceEventId);
```

- [ ] **Step 6: Add reverse-aware listener coverage for both backbone and compatibility topic**

```java
if (event == null || (!SocialEventTypes.LIKE_CREATED.equals(event.type()) && !SocialEventTypes.LIKE_REMOVED.equals(event.type()))) {
    return;
}
```

```java
if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
    applicationService.triggerLikeRemoved(new TriggerLikeRemovedCommand(
            payload.getRelationKey(),
            payload.getEntityUserId()
    ));
}
```

- [ ] **Step 7: Run the growth tests**

Run: `cd backend && mvn test -pl :community-app -Dtest=TaskProgressApplicationServiceTest,TaskProgressEventBackboneKafkaListenerTest,TaskProgressKafkaListenerTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/growth/application/command/TriggerLikeRemovedCommand.java \
        backend/community-app/src/main/java/com/nowcoder/community/growth/domain/repository/UserTaskEventLogRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/mapper/UserTaskEventLogMapper.java \
        backend/community-app/src/main/resources/mapper/user_task_event_log_mapper.xml \
        backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/MyBatisUserTaskEventLogRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/growth/domain/repository/UserTaskProgressRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/mapper/UserTaskProgressMapper.java \
        backend/community-app/src/main/resources/mapper/user_task_progress_mapper.xml \
        backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/persistence/MyBatisUserTaskProgressRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListener.java \
        backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListener.java \
        backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListenerTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressKafkaListenerTest.java
git commit -m "feat: rollback unclaimed like task progress on unlike"
```

### Task 8: Align User Reward Reverse Identity with Stable Like Relation Key

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRewardApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListener.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListenerTest.java`

- [ ] **Step 1: Write a failing reward listener test that expects relation-key based reverse identity**

```java
@Test
void likeRemovedShouldUseStableRelationKeyAsRewardSource() {
    WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
    UserRewardKafkaListener listener = listener(walletRewardActionApi);

    LikePayload payload = new LikePayload();
    payload.setActorUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    payload.setEntityType(EntityTypes.POST);
    payload.setEntityId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
    payload.setEntityUserId(UUID.fromString("00000000-0000-0000-0000-000000000055"));
    payload.setRelationKey("like:00000000-0000-0000-0000-000000000001:3:00000000-0000-0000-0000-000000000099");

    listener.onSocialEvent(new SocialContractEvent("evt-like-removed-1", SocialEventTypes.LIKE_REMOVED, payload));

    verify(walletRewardActionApi).applyDelta(
            "wallet-reward:" + payload.getRelationKey() + ":removed",
            payload.getEntityUserId(),
            -1,
            "LikeRemoved"
    );
}
```

- [ ] **Step 2: Run the reward listener test**

Run: `cd backend && mvn test -pl :community-app -Dtest=UserRewardKafkaListenerTest`
Expected: FAIL because listener still builds source ids from ad-hoc concatenation

- [ ] **Step 3: Implement stable source-id handling**

```java
private String likeSourceId(String action, LikePayload payload) {
    if (payload.getRelationKey() != null && !payload.getRelationKey().isBlank()) {
        return payload.getRelationKey().trim() + ":" + action;
    }
    return action + ":" + dashless(payload.getActorUserId()) + ":" + payload.getEntityType() + ":" + dashless(payload.getEntityId());
}
```

- [ ] **Step 4: Run the reward tests**

Run: `cd backend && mvn test -pl :community-app -Dtest=UserRewardKafkaListenerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRewardApplicationService.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListener.java \
        backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListenerTest.java
git commit -m "feat: use stable like relation identity for reward reversal"
```

### Task 9: Remove Local-Only Correctness Dependency

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyOutboxEnqueuer.java`
- Keep: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SocialInteractionProjectionListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxEnqueuer.java`
- Test: existing listener and structure tests

- [ ] **Step 1: Write or update tests that assert backbone listeners exist for correctness**

```java
@Test
void socialBackboneShouldOwnImAndPostScoreCorrectness() {
    assertThat(ImPolicyBackboneKafkaListener.class).isNotNull();
    assertThat(SocialInteractionBackboneKafkaListener.class).isNotNull();
}
```

- [ ] **Step 2: Run the targeted structure tests**

Run: `cd backend && mvn test -pl :community-app -Dtest=EventDeliverySemanticsStructureTest,ImPolicyBackboneKafkaListenerTest,SocialInteractionBackboneKafkaListenerTest`
Expected: FAIL if any test still assumes local-only correctness paths

- [ ] **Step 3: Narrow local listeners to explicit compatibility or best-effort roles**

```java
// Keep SocialInteractionProjectionListener as best-effort only.
// Keep ImPolicyOutboxEnqueuer only for UserPolicyChanged plus optional local block compatibility.
// Keep LikeTaskProgressKafkaOutboxEnqueuer only as compatibility fan-out, no longer the sole correctness path.
```

- [ ] **Step 4: Run the structure and listener tests**

Run: `cd backend && mvn test -pl :community-app -Dtest=EventDeliverySemanticsStructureTest,ImPolicyBackboneKafkaListenerTest,SocialInteractionBackboneKafkaListenerTest,ImPolicyOutboxEnqueuerTest,SocialInteractionProjectionListenerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyOutboxEnqueuer.java \
        backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SocialInteractionProjectionListener.java \
        backend/community-app/src/main/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxEnqueuer.java \
        backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/ImPolicyOutboxEnqueuerTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/content/event/SocialInteractionProjectionListenerTest.java
git commit -m "refactor: remove local-only social correctness dependencies"
```

### Task 10: Full Verification Sweep

**Files:**
- Test only

- [ ] **Step 1: Run the focused social / notice / growth / im / content / user test sweep**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='LikeApplicationServiceTest,FollowApplicationServiceTest,BlockApplicationServiceTest,NoticeApplicationServiceTest,NoticeProjectionApplicationServiceTest,NoticeProjectionKafkaListenerTest,TaskProgressApplicationServiceTest,TaskProgressEventBackboneKafkaListenerTest,TaskProgressKafkaListenerTest,UserRewardKafkaListenerTest,ImPolicyBackboneKafkaListenerTest,SocialInteractionBackboneKafkaListenerTest,SocialEventDispatchApplicationServiceTest'
```

Expected: PASS

- [ ] **Step 2: Run architecture guardrails if package boundaries or listeners changed materially**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: PASS

- [ ] **Step 3: Run a final git diff review**

Run:

```bash
git status --short
git diff --stat
```

Expected: only the planned files are modified and staged as expected

- [ ] **Step 4: Commit final verification-only fixes if needed**

```bash
git add -A
git commit -m "test: verify social chain hardening end to end"
```

---

## Self-Review

### Spec Coverage

- Backbone correctness for IM and content score is covered by Tasks 1 and 2.
- Self-like rejection and stable like identity are covered by Task 3.
- DB-only social storage enforcement is covered by Task 4.
- Cleanup hardening and atomic-delta-only cleanup flow are covered by Task 5.
- Notice revoke semantics are covered by Task 6.
- Growth unclaimed rollback is covered by Task 7.
- Reward reverse identity alignment is covered by Task 8.
- Removal of local-only correctness dependency is covered by Task 9.
- Verification and architecture guards are covered by Task 10.

### Placeholder Scan

- No `TODO`, `TBD`, or “implement later” placeholders remain.
- Every task includes exact file paths, exact tests, and exact commands.

### Type Consistency

- `relationKey` is the stable reverse identity across social, notice, growth, and reward tasks.
- `LikeRemoved` remains the reverse event across all tasks.
- `social.events` is the only required correctness backbone across all downstream tasks.

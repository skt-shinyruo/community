# Social Domain DDD Tactical Layering Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the `social` domain from mixed `like`/`follow`/`block` packages plus raw services into strict DDD Tactical Layering.

**Architecture:** HTTP controllers live in `social.controller` and call `social.application.*ApplicationService` only. Application services own transactions, idempotency, compensation registration, content/user/growth foreign API calls, command/result assembly, and domain-event publication. Domain services and repository interfaces hold social rules; MyBatis, Redis, in-memory repositories, and Spring event publication move to `social.infrastructure`.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, Redis, ArchUnit, JUnit 5, Mockito, Maven.

---

## File Structure Map

### Controller And HTTP DTOs

- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/LikeController.java`
  Owner HTTP adapter for `/api/likes`; maps `LikeRequest` into `SetLikeCommand` and `LikeResult` into `LikeResponse`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/FollowController.java`
  Owner HTTP adapter for `/api/follows`; maps HTTP input into follow application commands and maps relation results into `FollowItem`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/BlockController.java`
  Owner HTTP adapter for `/api/blocks`; maps HTTP input into block application commands.
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/dto/LikeRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/dto/LikeResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/dto/FollowRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/dto/FollowItem.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/controller/dto/BlockRequest.java`

### Application

- `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
  Like write/read use-case entry. Replaces raw `social.like.LikeService` and current thin `social.service.LikeApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java`
  Follow write/read use-case entry. Replaces raw `social.follow.FollowService` and current thin `social.service.FollowApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java`
  Block write/read use-case entry. Replaces raw `social.block.BlockService` and current thin `social.service.BlockApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/ContentEntityResolver.java`
  Application-owned foreign content query helper using `content.api.query.ContentEntityQueryApi`.

### Application Commands And Results

- `backend/community-app/src/main/java/com/nowcoder/community/social/application/command/SetLikeCommand.java`
  `record SetLikeCommand(UUID actorUserId, int entityType, UUID entityId, Boolean liked)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/command/FollowCommand.java`
  `record FollowCommand(UUID actorUserId, int entityType, UUID entityId)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/command/UnfollowCommand.java`
  `record UnfollowCommand(UUID actorUserId, int entityType, UUID entityId)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/command/BlockCommand.java`
  `record BlockCommand(UUID actorUserId, UUID targetUserId)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/command/UnblockCommand.java`
  `record UnblockCommand(UUID actorUserId, UUID targetUserId)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/result/LikeResult.java`
  `record LikeResult(boolean liked, long likeCount)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/result/FollowRelationResult.java`
  `record FollowRelationResult(UUID targetId, Instant followTime)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/result/BlockRelationResult.java`
  `record BlockRelationResult(UUID blockerUserId, UUID blockedUserId)`.

### Domain

- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/model/ResolvedSocialEntity.java`
  Entity owner/post metadata resolved by application before like events are emitted.
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/model/FollowRelation.java`
  Domain model for one follow list row.
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/model/BlockRelation.java`
  Domain model for one block relation scan row.
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/LikeDomainService.java`
  Validates like actor/entity rules, computes toggle/set target state, and builds `LikeChangedDomainEvent`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/FollowDomainService.java`
  Validates follow rules, including `USER`-only and cannot-follow-self.
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/BlockDomainService.java`
  Validates block rules and exposes anti-harassment relation checks.
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/LikeRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/FollowRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/BlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/LikeChangedDomainEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/FollowCreatedDomainEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/BlockRelationChangedDomainEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/SocialDomainEventPublisher.java`

### Infrastructure

- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisLikeRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisFollowRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisBlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/RedisLikeRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/RedisFollowRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/RedisBlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/InMemoryLikeRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/InMemoryFollowRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/InMemoryBlockRepository.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/LikeMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/FollowMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/mapper/BlockMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/dataobject/EntityLikeCountDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/dataobject/LikeScanDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/dataobject/FollowRelationDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/dataobject/BlockRelationDataObject.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/InMemorySocialDomainEventPublisher.java`

### Foreign API Adapters

- `backend/community-app/src/main/java/com/nowcoder/community/social/service/SocialLikeQueryApiAdapter.java`
  Implements `SocialLikeQueryApi` for foreign domains and delegates to `social.application.LikeApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/service/SocialFollowQueryApiAdapter.java`
  Implements `SocialFollowQueryApi` for foreign domains and delegates to `social.application.FollowApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/service/SocialBlockQueryApiAdapter.java`
  Implements `SocialBlockQueryApi` for foreign domains and delegates to `social.application.BlockApplicationService`.

### Guardrails And Docs

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
  Add social-specific retirement rules for `social.like`, `social.follow`, `social.block`, `social.event`, and non-adapter `social.service` classes.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
  Remove direct references to legacy raw `LikeService` and `FollowService`.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`
  Remove `com.nowcoder.community.social.service.LikeApplicationService` from legacy DTO exceptions after application results replace HTTP response DTOs.
- `docs/business-logic/social-like-follow-outbox-flow.md`
  Update the social flow from raw service names to application/domain/infrastructure names.
- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`

---

## Task 1: Add Social RED Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`

- [x] **Step 1: Add failing social DDD retirement rules**

Add these rules to `DddLayeringArchTest`:

```java
@ArchTest
static final ArchRule social_service_package_must_only_publish_foreign_api_adapters =
        classes()
                .that().resideInAnyPackage("..social.service..")
                .should().haveSimpleNameEndingWith("ApiAdapter")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule legacy_social_feature_packages_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage(
                        "..social.like..",
                        "..social.follow..",
                        "..social.block..",
                        "..social.event.."
                )
                .allowEmptyShould(true);

@ArchTest
static final ArchRule social_controllers_must_not_depend_on_legacy_social_surfaces =
        noClasses()
                .that().resideInAnyPackage("..social.controller..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..social.service..",
                        "..social.infrastructure..",
                        "..social.domain..",
                        "..social.like..",
                        "..social.follow..",
                        "..social.block..",
                        "..social.event.."
                )
                .allowEmptyShould(true);
```

- [x] **Step 2: Remove legacy social reflection checks**

Delete `DomainBoundaryArchTest.socialWriteServicesShouldNotReadStoragePropertyDirectly()` and the two imports:

```java
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.like.LikeService;
```

The storage-property rule becomes obsolete because `social.storage` is now read only by infrastructure repository selection through Spring conditions.

- [x] **Step 3: Remove the social DTO exception**

Remove this entry from `DtoBoundaryArchTest.LEGACY_SERVICE_RESPONSE_DTO_CALLERS`:

```java
"com.nowcoder.community.social.service.LikeApplicationService",
```

- [x] **Step 4: Run the RED architecture test**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DddLayeringArchTest,DomainBoundaryArchTest,DtoBoundaryArchTest test
```

Expected: `DddLayeringArchTest` fails because current production code still has `social.like`, `social.follow`, `social.block`, `social.event`, and non-adapter classes in `social.service`.

---

## Task 2: Establish Social Domain And Infrastructure Foundation

**Files:**
- Create domain files listed in the Domain section of the File Structure Map.
- Move: `backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeRepository.java` -> `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/LikeRepository.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/social/follow/FollowRepository.java` -> `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/FollowRepository.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockRepository.java` -> `backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/BlockRepository.java`
- Move MyBatis/Redis/in-memory repository implementations to `social.infrastructure.persistence`.
- Move mappers to `social.infrastructure.persistence.mapper`.
- Move row classes to `social.infrastructure.persistence.dataobject`.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/domain/service/LikeDomainServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/domain/service/FollowDomainServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/domain/service/BlockDomainServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/persistence/RedisBlockRepositoryTest.java`

- [x] **Step 1: Write domain service tests**

Create `LikeDomainServiceTest` with these cases:

```java
@Test
void resolveTargetStateShouldToggleWhenLikedIsNull() {
    LikeDomainService service = new LikeDomainService();

    assertThat(service.resolveTargetState(false, null)).isTrue();
    assertThat(service.resolveTargetState(true, null)).isFalse();
}

@Test
void validateLikeShouldRejectInvalidActorAndEntity() {
    LikeDomainService service = new LikeDomainService();

    assertThatThrownBy(() -> service.validateLike(null, EntityTypes.POST, uuid(1)))
            .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.validateLike(uuid(1), 0, uuid(1)))
            .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.validateLike(uuid(1), EntityTypes.POST, null))
            .isInstanceOf(BusinessException.class);
}
```

Create `FollowDomainServiceTest` with self-follow and `USER`-only rejection. Create `BlockDomainServiceTest` with self-block rejection and same-user `isEitherBlocked` false semantics.

- [x] **Step 2: Run domain tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.domain.service.LikeDomainServiceTest,com.nowcoder.community.social.domain.service.FollowDomainServiceTest,com.nowcoder.community.social.domain.service.BlockDomainServiceTest test
```

Expected: compile failure until the new domain package exists.

- [x] **Step 3: Create domain models, events, and repository interfaces**

Use these record signatures:

```java
public record ResolvedSocialEntity(UUID entityUserId, UUID postId) {}
public record FollowRelation(UUID targetId, Instant followTime) {}
public record BlockRelation(UUID blockerUserId, UUID blockedUserId) {}

public record LikeChangedDomainEvent(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId,
        UUID postId,
        boolean liked,
        Instant createTime
) {}

public record FollowCreatedDomainEvent(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId,
        Instant createTime
) {}

public record BlockRelationChangedDomainEvent(UUID blockerUserId, UUID blockedUserId, boolean blocked) {}
```

Move repository interfaces into `social.domain.repository`. Change `FollowRepository` list methods to return `List<FollowRelation>`. Change `BlockRepository.scanBlocksAfter(...)` to return `List<BlockRelation>`.

- [x] **Step 4: Move persistence implementations**

Move these classes with `git mv`:

```bash
git mv backend/community-app/src/main/java/com/nowcoder/community/social/like/DbLikeRepository.java backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisLikeRepository.java
git mv backend/community-app/src/main/java/com/nowcoder/community/social/follow/DbFollowRepository.java backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisFollowRepository.java
git mv backend/community-app/src/main/java/com/nowcoder/community/social/block/DbBlockRepository.java backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/MyBatisBlockRepository.java
git mv backend/community-app/src/main/java/com/nowcoder/community/social/like/RedisLikeRepository.java backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/RedisLikeRepository.java
git mv backend/community-app/src/main/java/com/nowcoder/community/social/follow/RedisFollowRepository.java backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/RedisFollowRepository.java
git mv backend/community-app/src/main/java/com/nowcoder/community/social/block/RedisBlockRepository.java backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/RedisBlockRepository.java
git mv backend/community-app/src/main/java/com/nowcoder/community/social/like/InMemoryLikeRepository.java backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/InMemoryLikeRepository.java
git mv backend/community-app/src/main/java/com/nowcoder/community/social/follow/InMemoryFollowRepository.java backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/InMemoryFollowRepository.java
git mv backend/community-app/src/main/java/com/nowcoder/community/social/block/InMemoryBlockRepository.java backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/persistence/InMemoryBlockRepository.java
```

Move mapper and row classes with `git mv`. Rename classes in-place to the names listed in the Infrastructure section.

- [x] **Step 5: Implement domain services**

`LikeDomainService` exposes:

```java
void validateLike(UUID actorUserId, int entityType, UUID entityId);
boolean resolveTargetState(boolean existed, Boolean requestedLiked);
LikeChangedDomainEvent likeChangedEvent(UUID actorUserId, int entityType, UUID entityId, ResolvedSocialEntity resolved, boolean liked, Instant createTime);
```

`FollowDomainService` exposes:

```java
void validateFollow(UUID actorUserId, int entityType, UUID entityId);
void validateUnfollow(UUID actorUserId, int entityType, UUID entityId);
FollowCreatedDomainEvent followCreatedEvent(UUID actorUserId, int entityType, UUID entityId, Instant createTime);
```

`BlockDomainService` exposes:

```java
void validateBlock(UUID actorUserId, UUID targetUserId);
void validateUnblock(UUID actorUserId, UUID targetUserId);
boolean isEitherBlocked(UUID userIdA, UUID userIdB, BlockRepository repository);
BlockRelationChangedDomainEvent blockChangedEvent(UUID actorUserId, UUID targetUserId, boolean blocked);
```

- [x] **Step 6: Run foundation tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.domain.service.LikeDomainServiceTest,com.nowcoder.community.social.domain.service.FollowDomainServiceTest,com.nowcoder.community.social.domain.service.BlockDomainServiceTest,com.nowcoder.community.social.infrastructure.persistence.RedisBlockRepositoryTest test
```

Expected: PASS after package moves and type mappings are complete.

---

## Task 3: Move Social Event Publication Behind Domain Event Port

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/SocialDomainEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/InMemorySocialDomainEventPublisher.java`
- Delete legacy `backend/community-app/src/main/java/com/nowcoder/community/social/event/*` after Task 4 removes raw service callers.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisherTest.java`

- [x] **Step 1: Write publisher mapping test**

Create `LocalSocialDomainEventPublisherTest` that publishes a `LikeChangedDomainEvent` with `liked=true` and captures the Spring event:

```java
@Test
void publishLikeChangedShouldMapCreatedEventToContractPayload() {
    ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
    LocalSocialDomainEventPublisher publisher = new LocalSocialDomainEventPublisher(springPublisher);

    publisher.publishLikeChanged(new LikeChangedDomainEvent(
            uuid(1), EntityTypes.POST, uuid(10), uuid(2), uuid(10), true, Instant.parse("2026-04-28T00:00:00Z")
    ));

    ArgumentCaptor<SocialContractEvent> captor = ArgumentCaptor.forClass(SocialContractEvent.class);
    verify(springPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().type()).isEqualTo(SocialEventTypes.LIKE_CREATED);
    assertThat(captor.getValue().payload()).isInstanceOf(LikePayload.class);
}
```

- [x] **Step 2: Run publisher test RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.infrastructure.event.LocalSocialDomainEventPublisherTest test
```

Expected: compile failure until publisher classes are moved and renamed.

- [x] **Step 3: Implement event adapter mapping**

`SocialDomainEventPublisher` exposes:

```java
void publishLikeChanged(LikeChangedDomainEvent event);
void publishFollowCreated(FollowCreatedDomainEvent event);
void publishBlockRelationChanged(BlockRelationChangedDomainEvent event);
```

`LocalSocialDomainEventPublisher` maps domain events to existing `contracts.event` payloads and keeps current event type semantics:

```text
LikeChangedDomainEvent(liked=true)  -> SocialEventTypes.LIKE_CREATED
LikeChangedDomainEvent(liked=false) -> SocialEventTypes.LIKE_REMOVED
FollowCreatedDomainEvent           -> SocialEventTypes.FOLLOW_CREATED
BlockRelationChangedDomainEvent    -> SocialEventTypes.BLOCK_RELATION_CHANGED
```

- [x] **Step 4: Run publisher tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.infrastructure.event.LocalSocialDomainEventPublisherTest test
```

Expected: PASS.

---

## Task 4: Implement Application Services

**Files:**
- Create application files and command/result files listed in the Application sections.
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/social/follow/FollowService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/social/service/LikeApplicationService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/social/service/FollowApplicationService.java`
- Delete after replacement: `backend/community-app/src/main/java/com/nowcoder/community/social/service/BlockApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/social/service/ContentEntityResolver.java` -> `backend/community-app/src/main/java/com/nowcoder/community/social/application/ContentEntityResolver.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/BlockApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/ContentEntityResolverTest.java`

- [x] **Step 1: Move and rewrite service tests as application tests**

Move existing tests:

```bash
git mv backend/community-app/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/social/service/FollowServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/social/service/BlockServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/social/application/BlockApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/social/service/ContentEntityResolverTest.java backend/community-app/src/test/java/com/nowcoder/community/social/application/ContentEntityResolverTest.java
```

Update tests to construct `LikeApplicationService`, `FollowApplicationService`, and `BlockApplicationService` directly. Replace HTTP DTO setup with command records, for example:

```java
LikeResult result = service.setLike(new SetLikeCommand(uuid(1), EntityTypes.POST, uuid(100), true));

assertThat(result.liked()).isTrue();
assertThat(result.likeCount()).isEqualTo(1);
```

- [x] **Step 2: Run application tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.application.LikeApplicationServiceTest,com.nowcoder.community.social.application.FollowApplicationServiceTest,com.nowcoder.community.social.application.BlockApplicationServiceTest,com.nowcoder.community.social.application.ContentEntityResolverTest test
```

Expected: compile failure until application services and commands/results exist.

- [x] **Step 3: Implement `LikeApplicationService`**

Constructor dependencies:

```java
LikeRepository likeRepository;
BlockRepository blockRepository;
LikeDomainService likeDomainService;
BlockDomainService blockDomainService;
ContentEntityResolver contentEntityResolver;
SocialDomainEventPublisher eventPublisher;
UserPointsAwardActionApi pointsAwardActionApi;
GrowthTaskProgressActionApi taskProgressActionApi;
```

Public methods:

```java
@Transactional
LikeResult setLike(SetLikeCommand command);
boolean isLiked(UUID actorUserId, int entityType, UUID entityId);
long count(int entityType, UUID entityId);
Map<UUID, Long> counts(int entityType, List<UUID> entityIds);
Map<UUID, Boolean> statuses(UUID actorUserId, int entityType, List<UUID> entityIds);
long userLikeCount(UUID userId);
```

Preserve current behavior: fail invalid input, ignore client-supplied owner fields, resolve `POST`/`COMMENT` owner from content API, infer `USER` owner from `entityId`, block creation when either side blocked, idempotent set/toggle, compensate Redis/in-memory state on publish or side-effect failure, call points/task APIs before publishing the social event, and share one generated `like-created:` event id across points and task progress.

- [x] **Step 4: Implement `FollowApplicationService`**

Constructor dependencies:

```java
FollowRepository followRepository;
BlockRepository blockRepository;
FollowDomainService followDomainService;
BlockDomainService blockDomainService;
SocialDomainEventPublisher eventPublisher;
```

Public methods:

```java
@Transactional
void follow(FollowCommand command);
@Transactional
void unfollow(UnfollowCommand command);
boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId);
long followeeCount(UUID userId, int entityType);
long followerCount(int entityType, UUID entityId);
List<FollowRelationResult> listFollowees(UUID userId, int entityType, int page, int size);
List<FollowRelationResult> listFollowers(int entityType, UUID entityId, int page, int size);
```

Preserve current behavior: support only `EntityTypes.USER`, reject self-follow, block creation when either side blocked, publish `FollowCreated` only for newly created relations, do not publish an event on unfollow, cap page size at 50, and use `Pagination.safeOffset`.

- [x] **Step 5: Implement `BlockApplicationService`**

Constructor dependencies:

```java
BlockRepository blockRepository;
BlockDomainService blockDomainService;
SocialDomainEventPublisher eventPublisher;
```

Public methods:

```java
@Transactional
void block(BlockCommand command);
@Transactional
void unblock(UnblockCommand command);
boolean hasBlocked(UUID userId, UUID targetUserId);
boolean isEitherBlocked(UUID userIdA, UUID userIdB);
List<UUID> listBlockedUserIds(UUID userId);
List<BlockRelationResult> scanBlockRelationsAfter(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit);
```

Preserve current behavior: reject self-block, ignore same-user `isEitherBlocked`, publish `BlockRelationChanged` only when the relation changes, and keep compensation for repositories that require explicit compensation.

- [x] **Step 6: Run application tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.application.LikeApplicationServiceTest,com.nowcoder.community.social.application.FollowApplicationServiceTest,com.nowcoder.community.social.application.BlockApplicationServiceTest,com.nowcoder.community.social.application.ContentEntityResolverTest test
```

Expected: PASS.

---

## Task 5: Move Controllers And DTO Conversion

**Files:**
- Move: `backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeController.java` -> `backend/community-app/src/main/java/com/nowcoder/community/social/controller/LikeController.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/social/follow/FollowController.java` -> `backend/community-app/src/main/java/com/nowcoder/community/social/controller/FollowController.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockController.java` -> `backend/community-app/src/main/java/com/nowcoder/community/social/controller/BlockController.java`
- Move DTOs to `social.controller.dto` as listed in the File Structure Map.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/controller/LikeControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/controller/FollowControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/controller/BlockControllerTest.java`

- [x] **Step 1: Move controller tests**

Move tests:

```bash
git mv backend/community-app/src/test/java/com/nowcoder/community/social/like/LikeControllerTest.java backend/community-app/src/test/java/com/nowcoder/community/social/controller/LikeControllerTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/social/follow/FollowControllerTest.java backend/community-app/src/test/java/com/nowcoder/community/social/controller/FollowControllerTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/social/block/BlockControllerTest.java backend/community-app/src/test/java/com/nowcoder/community/social/controller/BlockControllerTest.java
```

Update controller tests to mock `social.application.*ApplicationService` and verify command objects:

```java
verify(likeApplicationService).setLike(new SetLikeCommand(userId, EntityTypes.POST, uuid(11), Boolean.TRUE));
verify(followApplicationService).follow(new FollowCommand(userId, EntityTypes.USER, uuid(8)));
verify(blockApplicationService).block(new BlockCommand(actorId, uuid(8)));
```

- [x] **Step 2: Run controller tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.controller.LikeControllerTest,com.nowcoder.community.social.controller.FollowControllerTest,com.nowcoder.community.social.controller.BlockControllerTest test
```

Expected: compile failure until controllers and DTOs move.

- [x] **Step 3: Move controllers and DTOs**

Controllers must import `social.application.*ApplicationService`, `social.application.command.*`, and `social.application.result.*`. Controllers must not import `social.domain.*`, `social.infrastructure.*`, `social.service.*`, or same-domain `social.api.*`.

Map results at the controller boundary:

```java
private LikeResponse toResponse(LikeResult result) {
    LikeResponse response = new LikeResponse();
    response.setLiked(result.liked());
    response.setLikeCount(result.likeCount());
    return response;
}

private FollowItem toItem(FollowRelationResult result) {
    FollowItem item = new FollowItem();
    item.setTargetId(result.targetId());
    item.setFollowTime(result.followTime());
    return item;
}
```

- [x] **Step 4: Run controller tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.controller.LikeControllerTest,com.nowcoder.community.social.controller.FollowControllerTest,com.nowcoder.community.social.controller.BlockControllerTest,ControllerBoundaryArchTest,DddLayeringArchTest test
```

Expected: PASS for controller tests; `DddLayeringArchTest` may still fail until API adapters and legacy package deletion finish.

---

## Task 6: Add Foreign API Adapters And Retire Raw Services

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/service/SocialLikeQueryApiAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/service/SocialFollowQueryApiAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/service/SocialBlockQueryApiAdapter.java`
- Delete legacy raw services and legacy packages after replacements compile.
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/service/SocialLikeQueryApiAdapterTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/service/SocialFollowQueryApiAdapterTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/service/SocialBlockQueryApiAdapterTest.java`

- [x] **Step 1: Write API adapter tests**

Each adapter test mocks the matching application service and asserts delegation:

```java
when(likeApplicationService.count(EntityTypes.POST, uuid(10))).thenReturn(7L);

SocialLikeQueryApiAdapter adapter = new SocialLikeQueryApiAdapter(likeApplicationService);

assertThat(adapter.count(EntityTypes.POST, uuid(10))).isEqualTo(7L);
verify(likeApplicationService).count(EntityTypes.POST, uuid(10));
```

For `SocialBlockQueryApiAdapter.scanBlockRelationsAfter(...)`, map `BlockRelationResult` to `SocialBlockRelationView`.

- [x] **Step 2: Run API adapter tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.service.SocialLikeQueryApiAdapterTest,com.nowcoder.community.social.service.SocialFollowQueryApiAdapterTest,com.nowcoder.community.social.service.SocialBlockQueryApiAdapterTest test
```

Expected: compile failure until adapters exist.

- [x] **Step 3: Implement API adapters**

Implement these contracts:

```java
final class SocialLikeQueryApiAdapter implements SocialLikeQueryApi
final class SocialFollowQueryApiAdapter implements SocialFollowQueryApi
final class SocialBlockQueryApiAdapter implements SocialBlockQueryApi
```

Adapters delegate to same-domain application services and map application results to `api.model` records. No controller or same-domain application code may inject these adapters.

- [x] **Step 4: Delete legacy social raw service surfaces**

After production and tests compile, delete empty legacy package files:

```text
backend/community-app/src/main/java/com/nowcoder/community/social/like/*
backend/community-app/src/main/java/com/nowcoder/community/social/follow/*
backend/community-app/src/main/java/com/nowcoder/community/social/block/*
backend/community-app/src/main/java/com/nowcoder/community/social/event/*
```

The remaining `social.service` production classes must be exactly the three `*ApiAdapter` classes.

- [x] **Step 5: Run adapter and architecture tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.service.SocialLikeQueryApiAdapterTest,com.nowcoder.community.social.service.SocialFollowQueryApiAdapterTest,com.nowcoder.community.social.service.SocialBlockQueryApiAdapterTest,DddLayeringArchTest,DtoBoundaryArchTest,DomainBoundaryArchTest test
```

Expected: PASS.

---

## Task 7: Update Cross-Domain Callers, Docs, And Verification

**Files:**
- Modify references returned by this scan:

```bash
rg -n "social\\.(like|follow|block|event|service)\\.(LikeService|FollowService|BlockService|LikeRepository|FollowRepository|BlockRepository|InMemory|Redis|Db|SocialEventPublisher|LikeApplicationService|FollowApplicationService|BlockApplicationService|ContentEntityResolver)" backend/community-app/src/main/java backend/community-app/src/test/java
```

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicySnapshotControllerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java` if package moves affect controller references.
- Modify docs listed in Guardrails And Docs.

- [x] **Step 1: Update remaining imports and package names**

Known callers to update:

```text
backend/community-app/src/main/java/com/nowcoder/community/user/application/UserProfileApplicationService.java
backend/community-app/src/main/java/com/nowcoder/community/content/like/SocialLikeQueryService.java
backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentService.java
backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotService.java
backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java
backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicySnapshotControllerTest.java
```

Foreign domains keep depending on `social.api.query.*`. Tests that instantiate social owner internals must use `social.application` or `social.infrastructure.persistence` types after the move.

- [x] **Step 2: Update social outbox flow docs**

In `docs/business-logic/social-like-follow-outbox-flow.md`, replace raw flow names:

```text
LikeService / FollowService / BlockService
```

with:

```text
LikeApplicationService / FollowApplicationService / BlockApplicationService
SocialDomainEventPublisher
LocalSocialDomainEventPublisher
MyBatisLikeRepository / MyBatisFollowRepository / MyBatisBlockRepository
```

Document that contract payload publication is now an infrastructure adapter mapping from social domain events.

- [x] **Step 3: Run focused social suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.social.application.LikeApplicationServiceTest,com.nowcoder.community.social.application.FollowApplicationServiceTest,com.nowcoder.community.social.application.BlockApplicationServiceTest,com.nowcoder.community.social.application.ContentEntityResolverTest,com.nowcoder.community.social.controller.LikeControllerTest,com.nowcoder.community.social.controller.FollowControllerTest,com.nowcoder.community.social.controller.BlockControllerTest,com.nowcoder.community.social.service.SocialLikeQueryApiAdapterTest,com.nowcoder.community.social.service.SocialFollowQueryApiAdapterTest,com.nowcoder.community.social.service.SocialBlockQueryApiAdapterTest,com.nowcoder.community.social.infrastructure.event.LocalSocialDomainEventPublisherTest,com.nowcoder.community.social.infrastructure.persistence.RedisBlockRepositoryTest test
```

Expected: PASS.

- [x] **Step 4: Run architecture suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest,ListenerBoundaryArchTest,DddLayeringArchTest,DtoBoundaryArchTest test
```

Expected: PASS.

- [x] **Step 5: Run full backend verification**

Run:

```bash
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
```

Expected: PASS.

---

## Self-Review

### Spec Coverage

- Strict package shape: covered by moving controllers to `social.controller`, use cases to `social.application`, rules/repositories/events to `social.domain`, and storage/event adapters to `social.infrastructure`.
- Application as only same-domain entry: covered by Tasks 4 and 5; controllers call only `social.application.*ApplicationService`.
- Domain purity: covered by Task 2 and `DddLayeringArchTest`; domain does not depend on HTTP DTOs, Spring infrastructure, MyBatis mappers, or same-domain API adapters.
- Infrastructure ownership: covered by Tasks 2 and 3; MyBatis, Redis, in-memory repositories, and Spring event publication sit behind domain interfaces.
- Foreign synchronous collaboration: covered by Task 6; `social.api.query.*` remains for foreign callers and is implemented by `*ApiAdapter` classes only.
- Async contracts: covered by Task 3; social domain events are mapped to existing `contracts.event` payloads by infrastructure.

### Placeholder Scan

The plan contains concrete file paths, class names, method signatures, commands, and expected verification outcomes. It avoids placeholder markers and does not ask a worker to infer unnamed tests or files.

### Type Consistency

Command/result records are used only by controllers and application services. Domain services use domain models and domain events. API adapters map application results to `social.api.model` records for foreign domains.

# Community Social Controller Boundary Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move social follow/like business validation out of controllers and make unsupported entity-type and batch ID behavior explicit at the application/domain boundary.

**Architecture:** Controllers remain HTTP adapters that extract authentication, bind request parameters, and convert DTOs. `FollowApplicationService` and `LikeApplicationService` own use-case validation before repository or foreign owner API collaboration, with `FollowDomainService` and `LikeDomainService` retaining domain write-rule checks.

**Tech Stack:** Java 17, Spring Boot MVC, Jakarta Validation, JUnit 5, AssertJ, Mockito, MockMvc, Maven, ArchUnit.

---

## File Structure

- Modify `backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java`
  - Add read-side validation helpers.
  - Replace non-`USER` empty/default query responses with `BusinessException(INVALID_ARGUMENT)`.
- Modify `backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java`
  - Add focused tests for non-`USER` follow read/write rejection.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/LikeDomainService.java`
  - Extend write validation to reject unsupported like entity types.
- Modify `backend/community-app/src/test/java/com/nowcoder/community/social/domain/service/LikeDomainServiceTest.java`
  - Add tests for accepted and rejected like entity types.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
  - Add query/action validation for all like entry points.
  - Normalize batch entity IDs with strict null, duplicate, and 200-item handling.
- Modify `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`
  - Add tests for unsupported entity types, early rejection, batch deduplication, empty batches, and over-limit batches.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/social/controller/FollowController.java`
  - Remove controller-owned business rule checks.
- Modify `backend/community-app/src/test/java/com/nowcoder/community/social/controller/FollowControllerTest.java`
  - Add delegation tests that would fail if the controller still rejects business inputs.
- Modify `backend/community-app/src/main/java/com/nowcoder/community/social/controller/LikeController.java`
  - Remove controller-owned `EntityTypes.isValid(...)` checks.
  - Replace private string UUID parser with Spring MVC `List<UUID>` request binding.
- Modify `backend/community-app/src/test/java/com/nowcoder/community/social/controller/LikeControllerTest.java`
  - Add delegation and MockMvc binding tests for comma-separated UUID lists and invalid UUIDs.
- Modify `docs/handbook/business-logic/social.md`
  - Document follow/like supported entity types and strict batch like ID behavior.

---

### Task 1: Follow Application Boundary Contract

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java`

- [ ] **Step 1: Write the failing follow application test**

In `FollowApplicationServiceTest.java`, change the static entity type import:

```java
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
```

Add this test method after `followShouldRejectSelfWhenUuidValuesMatchButInstancesDiffer()`:

```java
    @Test
    void followUseCasesShouldRejectNonUserEntityTypeAtApplicationBoundary() {
        FollowApplicationService service = newService(
                new StatefulFollowRepository(),
                new StatefulBlockRepository(),
                new RecordingSocialDomainEventPublisher()
        );
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);

        assertThatThrownBy(() -> service.follow(new FollowCommand(actorUserId, POST, targetUserId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.unfollow(new UnfollowCommand(actorUserId, POST, targetUserId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.hasFollowed(actorUserId, POST, targetUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.followeeCount(actorUserId, POST))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.followerCount(POST, targetUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.listFollowees(actorUserId, POST, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.listFollowers(POST, targetUserId, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
    }
```

- [ ] **Step 2: Run the follow application test and verify it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=FollowApplicationServiceTest
```

Expected: FAIL. The new test should fail on current behavior because `hasFollowed`, `followeeCount`, `followerCount`, `listFollowees`, and `listFollowers` return default values instead of throwing for `POST`.

- [ ] **Step 3: Implement follow application validation**

In `FollowApplicationService.java`, replace the bodies of these methods and add the private helpers before `toResult(...)`:

```java
    public boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId) {
        validateFollowRelationQuery(actorUserId, entityType, entityId);
        return followRepository.hasFollowed(actorUserId, entityType, entityId);
    }

    public long followeeCount(UUID userId, int entityType) {
        validateFollowUserQuery(userId, entityType);
        return followRepository.countFolloweesExcludingBlocked(userId, entityType, blockRepository);
    }

    public long followerCount(int entityType, UUID entityId) {
        validateFollowTargetQuery(entityType, entityId);
        return followRepository.countFollowersExcludingBlocked(entityType, entityId, blockRepository);
    }

    public List<FollowRelationResult> listFollowees(UUID userId, int entityType, int page, int size) {
        validateFollowUserQuery(userId, entityType);
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return followRepository.listFolloweesExcludingBlocked(userId, entityType, blockRepository, Pagination.safeOffset(p, s), s)
                .stream()
                .map(this::toResult)
                .toList();
    }

    public List<FollowRelationResult> listFollowers(int entityType, UUID entityId, int page, int size) {
        validateFollowTargetQuery(entityType, entityId);
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return followRepository.listFollowersExcludingBlocked(entityType, entityId, blockRepository, Pagination.safeOffset(p, s), s)
                .stream()
                .map(this::toResult)
                .toList();
    }

    private void validateFollowRelationQuery(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null || entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        validateUserOnlyEntityType(entityType);
    }

    private void validateFollowUserQuery(UUID userId, int entityType) {
        if (userId == null || entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        validateUserOnlyEntityType(entityType);
    }

    private void validateFollowTargetQuery(int entityType, UUID entityId) {
        if (entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        validateUserOnlyEntityType(entityType);
    }

    private void validateUserOnlyEntityType(int entityType) {
        if (entityType != USER) {
            throw new BusinessException(INVALID_ARGUMENT, "follow 仅支持 USER");
        }
    }
```

- [ ] **Step 4: Run the follow application test and verify it passes**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=FollowApplicationServiceTest
```

Expected: PASS. Existing idempotency, block filtering, and compensation tests should continue to pass.

- [ ] **Step 5: Commit the follow application contract**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java
git commit -m "fix: enforce follow entity type in application"
```

---

### Task 2: Like Domain And Application Boundary Contract

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/domain/service/LikeDomainServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/LikeDomainService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`

- [ ] **Step 1: Write failing like domain tests**

In `LikeDomainServiceTest.java`, add these imports:

```java
import com.nowcoder.community.common.exception.CommonErrorCode;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static org.assertj.core.api.Assertions.assertThatCode;
```

Keep the existing `EntityTypes` import until all existing references are either left as-is or changed.

Add this test after `validateLikeShouldRejectInvalidActorAndEntity()`:

```java
    @Test
    void validateLikeShouldAcceptOnlySupportedEntityTypes() {
        LikeDomainService service = new LikeDomainService();

        assertThatCode(() -> service.validateLike(uuid(1), USER, uuid(2)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateLike(uuid(1), POST, uuid(2)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateLike(uuid(1), COMMENT, uuid(2)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.validateLike(uuid(1), 999, uuid(2)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
    }
```

- [ ] **Step 2: Run the like domain test and verify it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=LikeDomainServiceTest
```

Expected: FAIL. The unsupported positive entity type currently passes `LikeDomainService.validateLike(...)`.

- [ ] **Step 3: Implement like domain supported-type validation**

In `LikeDomainService.java`, add these static imports:

```java
import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
```

Replace `validateLike(...)` and add `isSupportedLikeEntityType(...)` below it:

```java
    public void validateLike(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        if (!isSupportedLikeEntityType(entityType) || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }
    }

    private boolean isSupportedLikeEntityType(int entityType) {
        return entityType == USER || entityType == POST || entityType == COMMENT;
    }
```

- [ ] **Step 4: Run the like domain test and verify it passes**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=LikeDomainServiceTest
```

Expected: PASS.

- [ ] **Step 5: Write failing like application tests**

In `LikeApplicationServiceTest.java`, add these imports:

```java
import java.util.stream.IntStream;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
```

Add these tests before the private `newService(...)` helper:

```java
    @Test
    void setLikeShouldRejectUnsupportedEntityTypeBeforeCollaborators() {
        LikeRepository repo = mock(LikeRepository.class);
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class),
                resolver,
                null,
                null
        );

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(uuid(1), 999, uuid(100), true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        verifyNoInteractions(repo, resolver);
    }

    @Test
    void likeQueriesShouldRejectUnsupportedEntityTypeBeforeRepository() {
        LikeRepository repo = mock(LikeRepository.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class),
                mock(ContentEntityResolver.class),
                null,
                null
        );

        assertThatThrownBy(() -> service.isLiked(uuid(1), 999, uuid(100)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.count(999, uuid(100)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.cleanupEntityLikes(999, uuid(100)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.counts(999, List.of(uuid(100))))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.statuses(uuid(1), 999, List.of(uuid(100))))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        verifyNoInteractions(repo);
    }

    @Test
    void likeBatchQueriesShouldNormalizeIdsBeforeRepository() {
        LikeRepository repo = mock(LikeRepository.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class),
                mock(ContentEntityResolver.class),
                null,
                null
        );
        UUID actorUserId = uuid(1);
        UUID firstEntityId = uuid(100);
        UUID secondEntityId = uuid(101);

        when(repo.countEntityLikesBatch(POST, List.of(firstEntityId, secondEntityId)))
                .thenReturn(Map.of(firstEntityId, 2L, secondEntityId, 3L));
        when(repo.likedStatusesBatch(actorUserId, POST, List.of(firstEntityId, secondEntityId)))
                .thenReturn(Map.of(firstEntityId, true, secondEntityId, false));

        assertThat(service.counts(POST, List.of(firstEntityId, secondEntityId, firstEntityId)))
                .containsEntry(firstEntityId, 2L)
                .containsEntry(secondEntityId, 3L);
        assertThat(service.statuses(actorUserId, POST, List.of(firstEntityId, secondEntityId, firstEntityId)))
                .containsEntry(firstEntityId, true)
                .containsEntry(secondEntityId, false);

        verify(repo).countEntityLikesBatch(POST, List.of(firstEntityId, secondEntityId));
        verify(repo).likedStatusesBatch(actorUserId, POST, List.of(firstEntityId, secondEntityId));
    }

    @Test
    void likeBatchQueriesShouldReturnEmptyMapsForEmptyIdsWithoutRepository() {
        LikeRepository repo = mock(LikeRepository.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class),
                mock(ContentEntityResolver.class),
                null,
                null
        );

        assertThat(service.counts(POST, null)).isEmpty();
        assertThat(service.counts(POST, List.of())).isEmpty();
        assertThat(service.statuses(uuid(1), POST, null)).isEmpty();
        assertThat(service.statuses(uuid(1), POST, List.of())).isEmpty();

        verifyNoInteractions(repo);
    }

    @Test
    void likeBatchQueriesShouldRejectNullAndOverLimitIdsBeforeRepository() {
        LikeRepository repo = mock(LikeRepository.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class),
                mock(ContentEntityResolver.class),
                null,
                null
        );
        UUID actorUserId = uuid(1);
        List<UUID> overLimitIds = IntStream.rangeClosed(1, 201)
                .mapToObj(com.nowcoder.community.support.TestUuids::uuid)
                .toList();

        assertThatThrownBy(() -> service.counts(POST, java.util.Arrays.asList(uuid(100), null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.statuses(actorUserId, POST, java.util.Arrays.asList(uuid(100), null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.counts(POST, overLimitIds))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.statuses(actorUserId, POST, overLimitIds))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        verifyNoInteractions(repo);
    }
```

- [ ] **Step 6: Run the like application test and verify it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=LikeApplicationServiceTest
```

Expected: FAIL. Unsupported query entity types currently pass positive `entityType` validation, and batch methods currently delegate null, duplicate, and over-limit lists to the repository.

- [ ] **Step 7: Implement like application validation and batch normalization**

In `LikeApplicationService.java`, add imports:

```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
```

Add this constant after the logger:

```java
    private static final int MAX_BATCH_ENTITY_IDS = 200;
```

Replace these methods:

```java
    public boolean isLiked(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        validateLikeEntity(entityType, entityId);
        return likeRepository.isLiked(actorUserId, entityType, entityId);
    }

    public long count(int entityType, UUID entityId) {
        validateLikeEntity(entityType, entityId);
        return likeRepository.countEntityLikes(entityType, entityId);
    }

    @Transactional
    public long cleanupEntityLikes(int entityType, UUID entityId) {
        validateLikeEntity(entityType, entityId);
        return likeRepository.deleteLikesByEntity(entityType, entityId);
    }

    public Map<UUID, Long> counts(int entityType, List<UUID> entityIds) {
        validateLikeEntityType(entityType);
        List<UUID> ids = normalizeBatchEntityIds(entityIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return likeRepository.countEntityLikesBatch(entityType, ids);
    }

    public Map<UUID, Boolean> statuses(UUID actorUserId, int entityType, List<UUID> entityIds) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        validateLikeEntityType(entityType);
        List<UUID> ids = normalizeBatchEntityIds(entityIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return likeRepository.likedStatusesBatch(actorUserId, entityType, ids);
    }
```

Add these helpers before `resolveEntity(...)`:

```java
    private void validateLikeEntity(int entityType, UUID entityId) {
        validateLikeEntityType(entityType);
        if (entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityId 非法");
        }
    }

    private void validateLikeEntityType(int entityType) {
        if (entityType != USER && entityType != POST && entityType != COMMENT) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
    }

    private List<UUID> normalizeBatchEntityIds(List<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        if (entityIds.size() > MAX_BATCH_ENTITY_IDS) {
            throw new BusinessException(INVALID_ARGUMENT, "entityIds 不能超过200");
        }
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>();
        for (UUID entityId : entityIds) {
            if (entityId == null) {
                throw new BusinessException(INVALID_ARGUMENT, "entityIds 非法");
            }
            uniqueIds.add(entityId);
        }
        return new ArrayList<>(uniqueIds);
    }
```

- [ ] **Step 8: Run like domain and application tests and verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=LikeDomainServiceTest,LikeApplicationServiceTest
```

Expected: PASS.

- [ ] **Step 9: Commit the like boundary contract**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/social/domain/service/LikeDomainService.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/domain/service/LikeDomainServiceTest.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java
git commit -m "fix: enforce like entity type in application"
```

---

### Task 3: Social Controller Boundary Cleanup

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/controller/FollowControllerTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/controller/FollowController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/controller/LikeControllerTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/controller/LikeController.java`

- [ ] **Step 1: Write failing follow controller delegation tests**

In `FollowControllerTest.java`, add these imports:

```java
import com.nowcoder.community.social.application.command.UnfollowCommand;
import com.nowcoder.community.social.application.result.FollowRelationResult;

import java.util.List;
```

Add these tests after `followShouldDelegateToFollowApplicationService()`:

```java
    @Test
    void followShouldDelegateNonUserEntityTypeToApplicationService() {
        FollowApplicationService followApplicationService = mock(FollowApplicationService.class);
        FollowController controller = new FollowController(followApplicationService);
        UUID userId = uuid(7);
        UUID targetId = uuid(8);

        FollowRequest request = new FollowRequest();
        request.setEntityType(EntityTypes.POST);
        request.setEntityId(targetId);

        Result<Void> result = controller.follow(authentication(userId), request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(followApplicationService).follow(new FollowCommand(userId, EntityTypes.POST, targetId));
    }

    @Test
    void unfollowAndStatusShouldDelegateNonUserEntityTypeToApplicationService() {
        FollowApplicationService followApplicationService = mock(FollowApplicationService.class);
        FollowController controller = new FollowController(followApplicationService);
        UUID userId = uuid(7);
        UUID targetId = uuid(8);
        when(followApplicationService.hasFollowed(userId, EntityTypes.POST, targetId)).thenReturn(false);

        Result<Void> unfollowResult = controller.unfollow(authentication(userId), EntityTypes.POST, targetId);
        Result<Boolean> statusResult = controller.status(authentication(userId), EntityTypes.POST, targetId);

        assertThat(unfollowResult.getCode()).isEqualTo(0);
        assertThat(statusResult.getData()).isFalse();
        verify(followApplicationService).unfollow(new UnfollowCommand(userId, EntityTypes.POST, targetId));
        verify(followApplicationService).hasFollowed(userId, EntityTypes.POST, targetId);
    }

    @Test
    void followListAndCountEndpointsShouldDelegateSuppliedEntityType() {
        FollowApplicationService followApplicationService = mock(FollowApplicationService.class);
        FollowController controller = new FollowController(followApplicationService);
        UUID userId = uuid(7);
        UUID targetId = uuid(8);
        when(followApplicationService.listFollowees(userId, EntityTypes.POST, 1, 20))
                .thenReturn(List.of(new FollowRelationResult(targetId, Instant.EPOCH)));
        when(followApplicationService.listFollowers(EntityTypes.POST, userId, 1, 20))
                .thenReturn(List.of(new FollowRelationResult(targetId, Instant.EPOCH)));
        when(followApplicationService.followeeCount(userId, EntityTypes.POST)).thenReturn(3L);
        when(followApplicationService.followerCount(EntityTypes.POST, userId)).thenReturn(4L);

        assertThat(controller.followees(userId, EntityTypes.POST, 1, 20).getData())
                .extracting("targetId")
                .containsExactly(targetId);
        assertThat(controller.followers(userId, EntityTypes.POST, 1, 20).getData())
                .extracting("targetId")
                .containsExactly(targetId);
        assertThat(controller.followeeCount(userId, EntityTypes.POST).getData()).isEqualTo(3L);
        assertThat(controller.followerCount(userId, EntityTypes.POST).getData()).isEqualTo(4L);
        verify(followApplicationService).listFollowees(userId, EntityTypes.POST, 1, 20);
        verify(followApplicationService).listFollowers(EntityTypes.POST, userId, 1, 20);
        verify(followApplicationService).followeeCount(userId, EntityTypes.POST);
        verify(followApplicationService).followerCount(EntityTypes.POST, userId);
    }
```

- [ ] **Step 2: Run follow controller tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=FollowControllerTest
```

Expected: FAIL. Current `FollowController` throws before delegation for non-`USER` entity types.

- [ ] **Step 3: Remove follow controller business checks**

In `FollowController.java`:

Remove these imports:

```java
import com.nowcoder.community.common.exception.BusinessException;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
```

Remove the `if` blocks from `follow`, `unfollow`, `status`, `followees`, `followers`, `followeeCount`, and `followerCount`. The resulting methods should keep this shape:

```java
    @PostMapping
    public Result<Void> follow(Authentication authentication, @Valid @RequestBody FollowRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        followApplicationService.follow(new FollowCommand(userId, request.getEntityType(), request.getEntityId()));
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> unfollow(Authentication authentication, @RequestParam int entityType, @RequestParam UUID entityId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        followApplicationService.unfollow(new UnfollowCommand(userId, entityType, entityId));
        return Result.ok();
    }

    @GetMapping("/status")
    public Result<Boolean> status(Authentication authentication, @RequestParam int entityType, @RequestParam UUID entityId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(followApplicationService.hasFollowed(userId, entityType, entityId));
    }
```

For list/count methods, keep defaulting `entityType == null ? ENTITY_TYPE_USER : entityType`, keep page/size defaulting, and pass `t` to `FollowApplicationService` without calling `EntityTypes.isValid(t)`.

- [ ] **Step 4: Run follow controller tests and verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=FollowControllerTest
```

Expected: PASS.

- [ ] **Step 5: Write failing like controller tests**

In `LikeControllerTest.java`, add these imports:

```java
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
```

Add these tests after `setLikeShouldDelegateToLikeApplicationService()`:

```java
    @Test
    void setLikeShouldDelegateUnsupportedEntityTypeToApplicationService() {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        LikeController controller = new LikeController(likeApplicationService);
        UUID userId = uuid(7);
        UUID entityId = uuid(11);

        LikeRequest request = new LikeRequest();
        request.setEntityType(999);
        request.setEntityId(entityId);
        request.setLiked(Boolean.TRUE);

        when(likeApplicationService.setLike(new SetLikeCommand(userId, 999, entityId, Boolean.TRUE)))
                .thenReturn(new LikeResult(false, 0L));

        Result<LikeResponse> result = controller.setLike(authentication(userId), request);

        assertThat(result.getCode()).isEqualTo(0);
        verify(likeApplicationService).setLike(new SetLikeCommand(userId, 999, entityId, Boolean.TRUE));
    }

    @Test
    void likeReadEndpointsShouldDelegateUnsupportedEntityTypeToApplicationService() {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        LikeController controller = new LikeController(likeApplicationService);
        UUID userId = uuid(7);
        UUID entityId = uuid(11);
        when(likeApplicationService.isLiked(userId, 999, entityId)).thenReturn(false);
        when(likeApplicationService.count(999, entityId)).thenReturn(5L);
        when(likeApplicationService.counts(999, List.of(entityId))).thenReturn(Map.of(entityId, 5L));
        when(likeApplicationService.statuses(userId, 999, List.of(entityId))).thenReturn(Map.of(entityId, false));

        assertThat(controller.status(authentication(userId), 999, entityId).getData()).isFalse();
        assertThat(controller.count(999, entityId).getData()).isEqualTo(5L);
        assertThat(controller.counts(999, List.of(entityId)).getData()).containsEntry(entityId, 5L);
        assertThat(controller.statuses(authentication(userId), 999, List.of(entityId)).getData()).containsEntry(entityId, false);

        verify(likeApplicationService).isLiked(userId, 999, entityId);
        verify(likeApplicationService).count(999, entityId);
        verify(likeApplicationService).counts(999, List.of(entityId));
        verify(likeApplicationService).statuses(userId, 999, List.of(entityId));
    }

    @Test
    void countsShouldBindCommaSeparatedUuidList() throws Exception {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LikeController(likeApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        UUID firstEntityId = uuid(11);
        UUID secondEntityId = uuid(12);
        when(likeApplicationService.counts(EntityTypes.POST, List.of(firstEntityId, secondEntityId)))
                .thenReturn(Map.of(firstEntityId, 2L, secondEntityId, 3L));

        mockMvc.perform(get("/api/likes/counts")
                        .param("entityType", String.valueOf(EntityTypes.POST))
                        .param("entityIds", firstEntityId + "," + secondEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['" + firstEntityId + "']").value(2))
                .andExpect(jsonPath("$.data['" + secondEntityId + "']").value(3));

        verify(likeApplicationService).counts(EntityTypes.POST, List.of(firstEntityId, secondEntityId));
    }

    @Test
    void countsShouldRejectInvalidUuidBeforeApplicationService() throws Exception {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LikeController(likeApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/likes/counts")
                        .param("entityType", String.valueOf(EntityTypes.POST))
                        .param("entityIds", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_ARGUMENT.getCode()));

        verifyNoInteractions(likeApplicationService);
    }
```

- [ ] **Step 6: Run like controller tests and verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=LikeControllerTest
```

Expected: FAIL. Current `LikeController` rejects unsupported entity types and still has `counts(..., String entityIds)` and `statuses(..., String entityIds)` signatures.

- [ ] **Step 7: Remove like controller business checks and use strict UUID binding**

In `LikeController.java`, remove these imports:

```java
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
```

Replace `setLike`, `status`, `count`, `counts`, and `statuses` with:

```java
    @PostMapping
    public Result<LikeResponse> setLike(Authentication authentication, @Valid @RequestBody LikeRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        LikeResult result = likeApplicationService.setLike(new SetLikeCommand(
                userId,
                request.getEntityType(),
                request.getEntityId(),
                request.getLiked()
        ));
        return Result.ok(toResponse(result));
    }

    @GetMapping("/status")
    public Result<Boolean> status(
            Authentication authentication,
            @RequestParam int entityType,
            @RequestParam UUID entityId
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(likeApplicationService.isLiked(userId, entityType, entityId));
    }

    @GetMapping("/count")
    public Result<Long> count(@RequestParam int entityType, @RequestParam UUID entityId) {
        return Result.ok(likeApplicationService.count(entityType, entityId));
    }

    @GetMapping("/counts")
    public Result<Map<UUID, Long>> counts(
            @RequestParam int entityType,
            @RequestParam(required = false) List<UUID> entityIds
    ) {
        return Result.ok(likeApplicationService.counts(entityType, entityIds));
    }

    @GetMapping("/statuses")
    public Result<Map<UUID, Boolean>> statuses(
            Authentication authentication,
            @RequestParam int entityType,
            @RequestParam(required = false) List<UUID> entityIds
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(likeApplicationService.statuses(userId, entityType, entityIds));
    }
```

Delete the whole `parseEntityIds(String raw, int limit)` method.

- [ ] **Step 8: Run social controller tests and verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=FollowControllerTest,LikeControllerTest
```

Expected: PASS.

- [ ] **Step 9: Commit controller boundary cleanup**

Run:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/social/controller/FollowController.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/controller/FollowControllerTest.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/controller/LikeController.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/controller/LikeControllerTest.java
git commit -m "fix: remove social validation from controllers"
```

---

### Task 4: Social Business Documentation

**Files:**
- Modify: `docs/handbook/business-logic/social.md`

- [ ] **Step 1: Update follow and like contract documentation**

In `docs/handbook/business-logic/social.md`, in the "关键语义" list under "点赞", add:

```markdown
- 点赞支持 `USER`、`POST` 和 `COMMENT` 实体类型；其他 `entityType` 由 `LikeApplicationService` / `LikeDomainService` 拒绝。
- 批量点赞查询严格处理 `entityIds`：非法 UUID、空 token、超过 200 个 ID 都返回参数错误；重复 ID 在应用层按首次出现顺序去重。
```

Under the "关注" section, after the `unfollow(...)` paragraph and before "查询能力", add:

```markdown
关键语义：

- 关注只支持 `USER` 关系；写入、状态、列表和计数查询对非 `USER` `entityType` 都返回参数错误。
- Controller 只做 HTTP 绑定、认证提取和 DTO 转换，是否支持某个 `entityType` 由 `FollowApplicationService` / `FollowDomainService` 决定。
```

- [ ] **Step 2: Verify the documentation diff**

Run:

```bash
git diff -- docs/handbook/business-logic/social.md
```

Expected: The diff documents the same behavior implemented in Tasks 1-3 and does not mention controller-owned validation.

- [ ] **Step 3: Commit documentation**

Run:

```bash
git add docs/handbook/business-logic/social.md
git commit -m "docs: clarify social entity type validation"
```

---

### Task 5: Final Verification

**Files:**
- Verify all files changed in Tasks 1-4.

- [ ] **Step 1: Run focused social tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='FollowControllerTest,LikeControllerTest,FollowApplicationServiceTest,LikeApplicationServiceTest,FollowDomainServiceTest,LikeDomainServiceTest'
```

Expected: PASS.

- [ ] **Step 2: Run architecture tests**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: PASS.

- [ ] **Step 3: Check formatting and worktree state**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` exits 0. `git status --short` contains no unstaged changes from this social boundary validation work.

- [ ] **Step 4: Record final result**

In the final handoff, report:

- The focused social test command and result.
- The architecture test command and result.
- Any unrelated pre-existing worktree files that remain, including `docs/superpowers/specs/2026-05-20-community-wallet-market-rich-domain-model-design.md` if it is still untracked.

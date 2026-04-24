# Community App Event Delivery Semantics Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `events.outbox.enabled` as a business-semantics switch inside `community-app` by keeping search outbox-only, notice local-only, and replacing points/task-progress event adapters with explicit local orchestration.

**Architecture:** The implementation keeps `ContentContractEvent` and `SocialContractEvent` for search and notice, but removes points/task-progress from broad event fan-out. Post, comment, and like write paths will call narrow local collaborators directly inside the owning transaction, while notice stays a single `AFTER_COMMIT` listener and search stays a single outbox-backed projection. There is no current production check-in publisher in `community-app`, so this slice retires the dead growth-event adapter surface instead of inventing an unused check-in caller.

**Tech Stack:** Spring Boot, Spring transactions, Spring application events, Jackson, JUnit 5, Mockito, Maven

---

## File Map

### New production files

- `backend/community-app/src/main/java/com/nowcoder/community/user/service/PointsAwardService.java`
  Responsibility: direct local reward orchestration for post publish, comment create, and like create/remove by reusing `PointsProjectionService`.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressTriggerService.java`
  Responsibility: direct local task-progress orchestration for post publish, comment create, and like create by reusing `TaskProgressProjectionService`.

### New test files

- `backend/community-app/src/test/java/com/nowcoder/community/user/service/PointsAwardServiceTest.java`
  Responsibility: lock direct points-award semantics after the event adapters are removed.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressTriggerServiceTest.java`
  Responsibility: lock direct task-progress trigger semantics after the event adapters are removed.
- `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerStructureTest.java`
  Responsibility: assert notice no longer depends on `@ConditionalOnProperty(events.outbox.enabled=false)`.
- `backend/community-app/src/test/java/com/nowcoder/community/search/event/SearchEventSurfaceRetirementTest.java`
  Responsibility: assert `PostProjectionListener` is gone.
- `backend/community-app/src/test/java/com/nowcoder/community/event/EventDeliverySurfaceRetirementTest.java`
  Responsibility: assert removed points/task-progress/notice/growth adapter classes stay retired.

### Modified production files

- `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/CreatePostUseCase.java`
  Responsibility: invoke direct points/task-progress collaborators in the post-create write path.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentService.java`
  Responsibility: invoke direct points/task-progress collaborators in the comment-create write path.
- `backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeService.java`
  Responsibility: invoke direct points/task-progress collaborators in the like write path and keep explicit rollback compensation.
- `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java`
  Responsibility: become the only notice-delivery adapter regardless of outbox property.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java`
  Responsibility: drop dead growth-event mapping once the growth event adapter surface is removed.
- `backend/community-app/src/test/resources/application.yml`
  Responsibility: default test profile to `events.outbox.enabled=true` so search semantics match production.
- `backend/community-app/src/test/java/com/nowcoder/community/content/app/post/CreatePostUseCaseTest.java`
  Responsibility: assert direct points/task-progress orchestration in post creation.
- `backend/community-app/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java`
  Responsibility: assert direct points/task-progress orchestration in comment creation.
- `backend/community-app/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java`
  Responsibility: assert direct points/task-progress orchestration and rollback on local side-effect failure.
- `backend/community-app/src/test/java/com/nowcoder/community/event/SpringEventAdapterConstructorSelectionTest.java`
  Responsibility: keep only the remaining multi-constructor adapter expectation.

### Deleted production files

- `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/LocalGrowthEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthLocalEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/payload/CheckInPayload.java`

### Deleted test files

- `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsProjectionListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsOutboxEnqueuerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsOutboxHandlerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressProjectionListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandlerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeOutboxHandlerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/search/event/PostProjectionListenerTest.java`

---

### Task 1: Replace Points And Task-Progress Event Adapters With Direct Local Orchestration

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/PointsAwardService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressTriggerService.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/user/service/PointsAwardServiceTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressTriggerServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/CreatePostUseCase.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/app/post/CreatePostUseCaseTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsProjectionListenerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsOutboxEnqueuerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsOutboxHandlerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressProjectionListenerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandlerTest.java`

- [ ] **Step 1: Write the failing tests for the new local collaborators and direct caller wiring**

```java
// backend/community-app/src/test/java/com/nowcoder/community/user/service/PointsAwardServiceTest.java
class PointsAwardServiceTest {

    @Test
    void awardPostPublishedShouldCreditAuthorWithoutSpringEventListener() {
        WalletRewardService walletRewardService = mock(WalletRewardService.class);
        PointsAwardService service = new PointsAwardService(new PointsProjectionService(walletRewardService));
        UUID postId = uuid(101);
        UUID userId = uuid(7);

        service.awardPostPublished(postId, userId);

        verify(walletRewardService).applyDelta(
                "wallet-reward:post-published:" + postId,
                userId,
                10,
                ContentEventTypes.POST_PUBLISHED
        );
    }
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressTriggerServiceTest.java
class TaskProgressTriggerServiceTest {

    @Test
    void advanceForCommentCreatedShouldUseCommentCreateTimeAsBizDate() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = new GrowthBusinessTimeService(
                Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                ZoneId.of("Asia/Shanghai")
        );
        TaskProgressTriggerService service = new TaskProgressTriggerService(
                new TaskProgressProjectionService(taskProgressService, businessTimeService)
        );

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(123));
        payload.setUserId(uuid(7));
        payload.setCreateTime(Instant.parse("2026-03-21T16:30:00Z"));

        service.advanceForCommentCreated(payload);

        verify(taskProgressService).processEvent(
                uuid(7),
                ContentEventTypes.COMMENT_CREATED,
                "comment-created:" + uuid(123),
                LocalDate.of(2026, 3, 22)
        );
    }
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/app/post/CreatePostUseCaseTest.java
verify(pointsAwardService).awardPostPublished(postId, userId);
verify(taskProgressTriggerService).advanceForPostPublished(
        eq(postId),
        eq(userId),
        argThat(instant -> instant != null)
);
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java
verify(pointsAwardService).awardCommentCreated(argThat(payload ->
        actorUserId.equals(payload.getUserId()) && postId.equals(payload.getPostId())
));
verify(taskProgressTriggerService).advanceForCommentCreated(argThat(payload ->
        actorUserId.equals(payload.getUserId()) && postId.equals(payload.getPostId())
));
verify(eventPublisher).publishCommentCreated(any());
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java
@Test
void likeShouldRollbackStateWhenPointsAwardFailsForCompensatingRepository() {
    InMemoryLikeRepository repo = new InMemoryLikeRepository();
    ContentEntityResolver resolver = Mockito.mock(ContentEntityResolver.class);
    Mockito.when(resolver.resolve(1, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

    PointsAwardService pointsAwardService = mock(PointsAwardService.class);
    doThrow(new IllegalStateException("award failed")).when(pointsAwardService).awardLikeCreated(anyString(), any(LikePayload.class));

    LikeService service = new LikeService(
            repo,
            new InMemorySocialEventPublisher(),
            resolver,
            new BlockService(new InMemoryBlockRepository(), new InMemorySocialEventPublisher()),
            pointsAwardService,
            mock(TaskProgressTriggerService.class)
    );

    LikeRequest req = new LikeRequest();
    req.setEntityType(1);
    req.setEntityId(uuid(100));
    req.setLiked(true);

    assertThatThrownBy(() -> service.setLike(uuid(1), req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("award failed");

    assertThat(repo.isLiked(uuid(1), 1, uuid(100))).isFalse();
}
```

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=CreatePostUseCaseTest,CommentServiceTest,LikeServiceTest,PointsAwardServiceTest,TaskProgressTriggerServiceTest test
```

Expected:

- compilation failure for missing `PointsAwardService` and `TaskProgressTriggerService`
- constructor mismatch errors in `CreatePostUseCase`, `CommentService`, and `LikeService`

- [ ] **Step 3: Implement the direct local collaborator classes**

```java
// backend/community-app/src/main/java/com/nowcoder/community/user/service/PointsAwardService.java
@Service
public class PointsAwardService {

    private final PointsProjectionService pointsProjectionService;

    public PointsAwardService(PointsProjectionService pointsProjectionService) {
        this.pointsProjectionService = pointsProjectionService;
    }

    public void awardPostPublished(UUID postId, UUID userId) {
        if (postId == null || userId == null) {
            return;
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        pointsProjectionService.project(pointsProjectionService.commandForContentEvent(
                new ContentContractEvent("post-published:" + postId, ContentEventTypes.POST_PUBLISHED, payload)
        ));
    }

    public void awardCommentCreated(CommentPayload payload) {
        if (payload == null || payload.getCommentId() == null) {
            return;
        }
        pointsProjectionService.project(pointsProjectionService.commandForContentEvent(
                new ContentContractEvent("comment-created:" + payload.getCommentId(), ContentEventTypes.COMMENT_CREATED, payload)
        ));
    }

    public void awardLikeCreated(String sourceEventId, LikePayload payload) {
        pointsProjectionService.project(pointsProjectionService.commandForSocialEvent(
                new SocialContractEvent(sourceEventId, SocialEventTypes.LIKE_CREATED, payload)
        ));
    }

    public void awardLikeRemoved(String sourceEventId, LikePayload payload) {
        pointsProjectionService.project(pointsProjectionService.commandForSocialEvent(
                new SocialContractEvent(sourceEventId, SocialEventTypes.LIKE_REMOVED, payload)
        ));
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressTriggerService.java
@Service
public class TaskProgressTriggerService {

    private final TaskProgressProjectionService taskProgressProjectionService;

    public TaskProgressTriggerService(TaskProgressProjectionService taskProgressProjectionService) {
        this.taskProgressProjectionService = taskProgressProjectionService;
    }

    public void advanceForPostPublished(UUID postId, UUID userId, Instant createTime) {
        if (postId == null || userId == null || createTime == null) {
            return;
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        payload.setCreateTime(createTime);
        taskProgressProjectionService.project(taskProgressProjectionService.commandForContentEvent(
                new ContentContractEvent("post-published:" + postId, ContentEventTypes.POST_PUBLISHED, payload)
        ));
    }

    public void advanceForCommentCreated(CommentPayload payload) {
        if (payload == null || payload.getCommentId() == null) {
            return;
        }
        taskProgressProjectionService.project(taskProgressProjectionService.commandForContentEvent(
                new ContentContractEvent("comment-created:" + payload.getCommentId(), ContentEventTypes.COMMENT_CREATED, payload)
        ));
    }

    public void advanceForLikeCreated(String sourceEventId, LikePayload payload) {
        taskProgressProjectionService.project(taskProgressProjectionService.commandForSocialEvent(
                new SocialContractEvent(sourceEventId, SocialEventTypes.LIKE_CREATED, payload)
        ));
    }
}
```

- [ ] **Step 4: Wire post, comment, and like write paths to the new collaborators**

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/app/post/CreatePostUseCase.java
private final PointsAwardService pointsAwardService;
private final TaskProgressTriggerService taskProgressTriggerService;

public CreatePostUseCase(
        PostService postService,
        CategoryService categoryService,
        TagService tagService,
        UserModerationGuard moderationGuard,
        PostDomainEventPublisher domainEventPublisher,
        PostWriteSideEffectScheduler postWriteSideEffectScheduler,
        PointsAwardService pointsAwardService,
        TaskProgressTriggerService taskProgressTriggerService
) {
    this.postService = postService;
    this.categoryService = categoryService;
    this.tagService = tagService;
    this.moderationGuard = moderationGuard;
    this.domainEventPublisher = domainEventPublisher;
    this.postWriteSideEffectScheduler = postWriteSideEffectScheduler;
    this.pointsAwardService = pointsAwardService;
    this.taskProgressTriggerService = taskProgressTriggerService;
}

UUID postId = postService.create(post);
tagService.bindTagsToPost(postId, tags);
pointsAwardService.awardPostPublished(postId, userId);
taskProgressTriggerService.advanceForPostPublished(postId, userId, post.getCreateTime().toInstant());
domainEventPublisher.postPublished(postId);
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentService.java
private final PointsAwardService pointsAwardService;
private final TaskProgressTriggerService taskProgressTriggerService;

public CommentService(
        CommentMapper commentMapper,
        PostService postService,
        SensitiveFilter sensitiveFilter,
        PostScoreQueue postScoreQueue,
        ContentEventPublisher eventPublisher,
        SocialBlockQueryApi blockQueryApi,
        UserModerationGuard moderationGuard,
        ContentTextCodec textCodec,
        PointsAwardService pointsAwardService,
        TaskProgressTriggerService taskProgressTriggerService
) {
    this.commentMapper = commentMapper;
    this.postService = postService;
    this.sensitiveFilter = sensitiveFilter;
    this.postScoreQueue = postScoreQueue;
    this.eventPublisher = eventPublisher;
    this.blockQueryApi = blockQueryApi;
    this.moderationGuard = moderationGuard;
    this.textCodec = textCodec;
    this.idGenerator = new UuidV7Generator();
    this.pointsAwardService = pointsAwardService;
    this.taskProgressTriggerService = taskProgressTriggerService;
}

pointsAwardService.awardCommentCreated(payload);
taskProgressTriggerService.advanceForCommentCreated(payload);
eventPublisher.publishCommentCreated(payload);
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeService.java
private final PointsAwardService pointsAwardService;
private final TaskProgressTriggerService taskProgressTriggerService;
private final UuidV7Generator idGenerator;

public LikeService(
        LikeRepository likeRepository,
        SocialEventPublisher eventPublisher,
        ContentEntityResolver contentEntityResolver,
        BlockService blockService,
        PointsAwardService pointsAwardService,
        TaskProgressTriggerService taskProgressTriggerService
) {
    this(likeRepository, eventPublisher, contentEntityResolver, blockService, pointsAwardService, taskProgressTriggerService, new UuidV7Generator());
}

LikeService(
        LikeRepository likeRepository,
        SocialEventPublisher eventPublisher,
        ContentEntityResolver contentEntityResolver,
        BlockService blockService,
        PointsAwardService pointsAwardService,
        TaskProgressTriggerService taskProgressTriggerService,
        UuidV7Generator idGenerator
) {
    this.likeRepository = likeRepository;
    this.eventPublisher = eventPublisher;
    this.contentEntityResolver = contentEntityResolver;
    this.blockService = blockService;
    this.pointsAwardService = pointsAwardService;
    this.taskProgressTriggerService = taskProgressTriggerService;
    this.idGenerator = idGenerator;
}

String sideEffectEventId = (liked ? "like-created:" : "like-removed:") + idGenerator.next();
try {
    if (liked) {
        pointsAwardService.awardLikeCreated(sideEffectEventId, payload);
        taskProgressTriggerService.advanceForLikeCreated(sideEffectEventId, payload);
        eventPublisher.publishLikeCreated(payload);
    } else {
        pointsAwardService.awardLikeRemoved(sideEffectEventId, payload);
        eventPublisher.publishLikeRemoved(payload);
    }
} catch (RuntimeException ex) {
    if (needsExplicitCompensation) {
        try {
            rollback.run();
        } catch (RuntimeException rollbackEx) {
            log.warn("[like] rollback failed after publish error (entityType={}, entityId={}, actorUserId={}, liked={}): {}",
                    entityType, entityId, actorUserId, liked, rollbackEx.toString());
        }
    }
    throw ex;
}
```

- [ ] **Step 5: Delete the retired points/task-progress event adapters and their old tests**

Run:

```bash
cd backend/community-app && git rm \
  src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java \
  src/main/java/com/nowcoder/community/user/event/PointsOutboxEnqueuer.java \
  src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java \
  src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java \
  src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuer.java \
  src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java \
  src/test/java/com/nowcoder/community/user/event/PointsProjectionListenerTest.java \
  src/test/java/com/nowcoder/community/user/event/PointsOutboxEnqueuerTest.java \
  src/test/java/com/nowcoder/community/user/event/PointsOutboxHandlerTest.java \
  src/test/java/com/nowcoder/community/growth/event/TaskProgressProjectionListenerTest.java \
  src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuerTest.java \
  src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandlerTest.java
```

Expected:

- the worktree shows those adapter classes removed instead of gated by `events.outbox.enabled`

- [ ] **Step 6: Run the focused suite and verify it passes**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=CreatePostUseCaseTest,CommentServiceTest,LikeServiceTest,PointsAwardServiceTest,TaskProgressTriggerServiceTest,PointsProjectionServiceIntegrationTest,TaskProgressServiceTest test
```

Expected:

- PASS
- no remaining references to `PointsProjectionListener`, `PointsOutbox*`, or `TaskProgress*` event adapters

- [ ] **Step 7: Commit the local-orchestration slice**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/user/service/PointsAwardService.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressTriggerService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/app/post/CreatePostUseCase.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentService.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeService.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/service/PointsAwardServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressTriggerServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/app/post/CreatePostUseCaseTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java
git add -u \
  backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxEnqueuer.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuer.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsProjectionListenerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsOutboxEnqueuerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsOutboxHandlerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressProjectionListenerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandlerTest.java
git commit -m "refactor: inline points and task progress side effects"
```

### Task 2: Keep Notice Projection Local-Only

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerStructureTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/event/SpringEventAdapterConstructorSelectionTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeOutboxHandlerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerTest.java`

- [ ] **Step 1: Write the failing structure test that locks notice to one local adapter**

```java
// backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerStructureTest.java
class NoticeProjectionListenerStructureTest {

    @Test
    void noticeProjectionListenerShouldNotDependOnEventsOutboxProperty() {
        assertThat(NoticeProjectionListener.class.isAnnotationPresent(ConditionalOnProperty.class)).isFalse();
    }
}
```

- [ ] **Step 2: Run the notice-focused tests to verify the new structure test fails**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=NoticeProjectionListenerStructureTest,NoticeProjectionListenerTest,SpringEventAdapterConstructorSelectionTest test
```

Expected:

- FAIL because `NoticeProjectionListener` still carries `@ConditionalOnProperty`
- `SpringEventAdapterConstructorSelectionTest` still expects notice outbox types that will be removed in this task

- [ ] **Step 3: Remove the outbox condition from the local notice listener and prune the constructor-selection expectations**

```java
// backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java
@Component
public class NoticeProjectionListener {
    private static final Logger log = LoggerFactory.getLogger(NoticeProjectionListener.class);

    private final NoticeProjectionService noticeProjectionService;

    @Autowired
    public NoticeProjectionListener(NoticeProjectionService noticeProjectionService) {
        this.noticeProjectionService = noticeProjectionService;
    }
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/event/SpringEventAdapterConstructorSelectionTest.java
assertThat(List.of(
        expectation(NoticeProjectionListener.class, NoticeProjectionService.class)
)).allSatisfy(expectation -> {
    Constructor<?>[] constructors = expectation.type.getDeclaredConstructors();
    assertThat(constructors)
            .withFailMessage("%s should keep its test-only convenience constructor", expectation.type.getName())
            .hasSizeGreaterThan(1);

    assertThat(constructors)
            .filteredOn(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .singleElement()
            .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                    .containsExactly(expectation.injectionParameterTypes));
});
```

- [ ] **Step 4: Delete the retired notice outbox classes and tests**

Run:

```bash
cd backend/community-app && git rm \
  src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java \
  src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java \
  src/test/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuerTest.java \
  src/test/java/com/nowcoder/community/notice/event/NoticeOutboxHandlerTest.java
```

- [ ] **Step 5: Run the notice-focused suite and verify it passes**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=NoticeProjectionListenerStructureTest,NoticeProjectionListenerTest,SpringEventAdapterConstructorSelectionTest test
```

Expected:

- PASS
- `NoticeProjectionListener` is now the only notice delivery adapter in production code

- [ ] **Step 6: Commit the notice-local-only slice**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerStructureTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/event/SpringEventAdapterConstructorSelectionTest.java
git add -u \
  backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java \
  backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeOutboxHandlerTest.java
git commit -m "refactor: keep notice projection local only"
```

### Task 3: Keep Search Projection Outbox-Only And Align Test Defaults

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/search/event/SearchEventSurfaceRetirementTest.java`
- Modify: `backend/community-app/src/test/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/event/PostOutboxEnqueuerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/event/PostOutboxHandlerTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostProjectionListener.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/search/event/PostProjectionListenerTest.java`

- [ ] **Step 1: Write the failing retirement test for the synchronous search listener**

```java
// backend/community-app/src/test/java/com/nowcoder/community/search/event/SearchEventSurfaceRetirementTest.java
class SearchEventSurfaceRetirementTest {

    @Test
    void syncPostProjectionListenerShouldBeRetired() {
        assertThatThrownBy(() -> Class.forName("com.nowcoder.community.search.event.PostProjectionListener"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run the search-focused tests to verify the retirement test fails**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=SearchEventSurfaceRetirementTest,PostOutboxEnqueuerTest,PostOutboxHandlerTest,PostProjectionListenerTest test
```

Expected:

- FAIL because `PostProjectionListener` is still on the classpath

- [ ] **Step 3: Delete the synchronous listener and its test, then align the shared test profile with production semantics**

Run:

```bash
cd backend/community-app && git rm \
  src/main/java/com/nowcoder/community/search/event/PostProjectionListener.java \
  src/test/java/com/nowcoder/community/search/event/PostProjectionListenerTest.java
```

```yaml
# backend/community-app/src/test/resources/application.yml
events:
  outbox:
    enabled: true
```

- [ ] **Step 4: Run the search-focused suite and verify it passes on the outbox-only path**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=SearchEventSurfaceRetirementTest,PostOutboxEnqueuerTest,PostOutboxHandlerTest,SearchReindexExecutionServiceTest test
```

Expected:

- PASS
- no remaining test asserts direct `PostProjectionListener` behavior
- the default `test` profile no longer diverges from production on `events.outbox.enabled`

- [ ] **Step 5: Commit the search outbox-only slice**

```bash
git add \
  backend/community-app/src/test/resources/application.yml \
  backend/community-app/src/test/java/com/nowcoder/community/search/event/SearchEventSurfaceRetirementTest.java
git add -u \
  backend/community-app/src/main/java/com/nowcoder/community/search/event/PostProjectionListener.java \
  backend/community-app/src/test/java/com/nowcoder/community/search/event/PostProjectionListenerTest.java
git commit -m "refactor: keep search projection outbox only"
```

### Task 4: Retire The Dead Growth Event Surface And Lock The New Architecture

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/event/EventDeliverySurfaceRetirementTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventPublisher.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/LocalGrowthEventPublisher.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthLocalEvent.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/payload/CheckInPayload.java`

- [ ] **Step 1: Write the failing retirement test for the removed adapter surface**

```java
// backend/community-app/src/test/java/com/nowcoder/community/event/EventDeliverySurfaceRetirementTest.java
class EventDeliverySurfaceRetirementTest {

    @Test
    void retiredEventAdaptersShouldNotRemainOnClasspath() {
        assertClassRetired("com.nowcoder.community.user.event.PointsProjectionListener");
        assertClassRetired("com.nowcoder.community.user.event.PointsOutboxEnqueuer");
        assertClassRetired("com.nowcoder.community.user.event.PointsOutboxHandler");
        assertClassRetired("com.nowcoder.community.growth.event.TaskProgressProjectionListener");
        assertClassRetired("com.nowcoder.community.growth.event.TaskProgressOutboxEnqueuer");
        assertClassRetired("com.nowcoder.community.growth.event.TaskProgressOutboxHandler");
        assertClassRetired("com.nowcoder.community.notice.event.NoticeOutboxEnqueuer");
        assertClassRetired("com.nowcoder.community.notice.event.NoticeOutboxHandler");
        assertClassRetired("com.nowcoder.community.search.event.PostProjectionListener");
        assertClassRetired("com.nowcoder.community.growth.event.GrowthEventPublisher");
        assertClassRetired("com.nowcoder.community.growth.event.LocalGrowthEventPublisher");
        assertClassRetired("com.nowcoder.community.growth.event.GrowthLocalEvent");
        assertClassRetired("com.nowcoder.community.growth.event.payload.CheckInPayload");
    }

    private void assertClassRetired(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run the architecture-focused tests to verify the dead growth-event surface still fails**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=EventDeliverySurfaceRetirementTest,TaskProgressServiceTest,TaskProgressTriggerServiceTest test
```

Expected:

- FAIL because `GrowthEventPublisher`, `LocalGrowthEventPublisher`, `GrowthLocalEvent`, and `CheckInPayload` still exist

- [ ] **Step 3: Delete the dead growth-event adapter files and trim the unused growth-event mapping from `TaskProgressProjectionService`**

Run:

```bash
cd backend/community-app && git rm \
  src/main/java/com/nowcoder/community/growth/event/GrowthEventPublisher.java \
  src/main/java/com/nowcoder/community/growth/event/LocalGrowthEventPublisher.java \
  src/main/java/com/nowcoder/community/growth/event/GrowthLocalEvent.java \
  src/main/java/com/nowcoder/community/growth/event/payload/CheckInPayload.java
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java
public class TaskProgressProjectionService {
    private final TaskProgressService taskProgressService;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public TaskProgressProjectionService(TaskProgressService taskProgressService, GrowthBusinessTimeService growthBusinessTimeService) {
        this.taskProgressService = taskProgressService;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    public TaskProgressProjectionCommand commandForContentEvent(ContentContractEvent event) {
        if (event == null) {
            return null;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) && event.payload() instanceof PostPayload payload) {
            return new TaskProgressProjectionCommand(payload.getUserId(), event.type(), event.eventId(), toDate(payload.getCreateTime()));
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            return new TaskProgressProjectionCommand(payload.getUserId(), event.type(), event.eventId(), toDate(payload.getCreateTime()));
        }
        return null;
    }

    public TaskProgressProjectionCommand commandForSocialEvent(SocialContractEvent event) {
        if (event == null || !(event.payload() instanceof LikePayload payload)) {
            return null;
        }
        UUID toUserId = payload.getEntityUserId();
        if (!SocialEventTypes.LIKE_CREATED.equals(event.type()) || toUserId == null || toUserId.equals(payload.getActorUserId())) {
            return null;
        }
        return new TaskProgressProjectionCommand(toUserId, event.type(), event.eventId(), toDate(payload.getCreateTime()));
    }

    public void project(TaskProgressProjectionCommand command) {
        if (command == null || command.userId() == null || command.bizDate() == null) {
            return;
        }
        taskProgressService.processEvent(command.userId(), command.triggerEventType(), command.sourceEventId(), command.bizDate());
    }

    private LocalDate toDate(Instant instant) {
        return growthBusinessTimeService.dateOf(instant);
    }

    public record TaskProgressProjectionCommand(
            UUID userId,
            String triggerEventType,
            String sourceEventId,
            LocalDate bizDate
    ) {
    }
}
```

- [ ] **Step 4: Run the final focused verification suite**

Run:

```bash
cd backend && mvn -q -pl community-app -Dtest=CreatePostUseCaseTest,CommentServiceTest,LikeServiceTest,PointsAwardServiceTest,TaskProgressTriggerServiceTest,NoticeProjectionListenerStructureTest,NoticeProjectionListenerTest,SearchEventSurfaceRetirementTest,PostOutboxEnqueuerTest,PostOutboxHandlerTest,SpringEventAdapterConstructorSelectionTest,EventDeliverySurfaceRetirementTest,PointsProjectionServiceIntegrationTest,TaskProgressServiceTest test
```

Expected:

- PASS
- no removed adapter class remains on the classpath
- targeted post/comment/like/notice/search/task-progress semantics are all verified under the new single-path model

- [ ] **Step 5: Commit the architecture lock-in**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java \
  backend/community-app/src/test/java/com/nowcoder/community/event/EventDeliverySurfaceRetirementTest.java
git add -u \
  backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventPublisher.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/event/LocalGrowthEventPublisher.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthLocalEvent.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/event/payload/CheckInPayload.java
git commit -m "test: lock event delivery surface"
```

---

## Self-Review Notes

- Spec coverage:
  - points/task-progress are moved to explicit local orchestration in Task 1
  - notice becomes local-only in Task 2
  - search becomes outbox-only and test semantics align in Task 3
  - dead growth adapter surface and architecture guardrails are handled in Task 4
- Placeholder scan:
  - no `TODO`, `TBD`, or “similar to Task N” markers remain
  - every task includes exact files, code snippets, commands, and expected outcomes
- Type consistency:
  - collaborator names are consistently `PointsAwardService` and `TaskProgressTriggerService`
  - post/comment/like method names stay consistent between production code and tests

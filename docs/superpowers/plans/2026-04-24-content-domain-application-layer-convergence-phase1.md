# Content Domain Application Layer Convergence Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Converge the highest-risk `content`-domain entry paths onto explicit owner `ApplicationService` boundaries, stop leaking HTTP DTO work into collaboration-facing application services, and freeze the rule in ArchUnit without widening scope to unrelated endpoints.

**Architecture:** Keep foreign-domain `content.api.query` / `content.api.action` stable. For same-domain HTTP paths, make `PostController`, `ReportController`, and `ModerationController` depend on owner `*ApplicationService` entry points only; keep `CreatePostUseCase`, `TakeModerationActionUseCase`, and similar types as internal transaction helpers. Move post/comment HTTP DTO conversion back to controller-adjacent mapping so `PostReadApplicationService` and `CommentReadApplicationService` can remain collaboration-safe.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, ArchUnit, JUnit 5, Mockito, Maven

---

## Planned File Structure

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
  Adds a reusable ArchUnit helper that blocks same-domain controllers from reaching non-`ApplicationService` services or `..app..` use cases.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
  Freezes the content-domain migration rule and temporary whitelist for still-unmigrated lightweight controllers.
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
  Becomes a pure HTTP adapter that consumes owner application services returning owner views/results, not HTTP DTOs.
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostControllerResponseMapper.java`
  New controller-adjacent mapper for `PostSummaryView`, `PostDetailView`, `CommentView`, and `PostCreateResult` to HTTP DTOs.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadApplicationService.java`
  Keeps orchestration and owner/collaboration models only; drops `PostSummaryResponse` / `PostDetailResponse` helpers.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadApplicationService.java`
  Keeps `CommentView` reads only; drops `CommentResponse` helpers.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java`
  Removes controller-only convenience methods that bypass the owner result type.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/ReportApplicationService.java`
  New owner application service that absorbs target-type parsing and report submission orchestration.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/ModerationApplicationService.java`
  New owner application service that absorbs pagination defaults and orchestration between `ModerationService` and `TakeModerationActionUseCase`.
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/ReportController.java`
  Stops parsing target types and delegates to `ReportApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/ModerationController.java`
  Stops depending on both a raw service and a use case; depends on `ModerationApplicationService` only.
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`
  Rewritten to prove controller-side DTO mapping from owner views/results.
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/ModerationControllerTest.java`
  Rewritten to prove the controller only depends on `ModerationApplicationService`.
- `backend/community-app/src/test/java/com/nowcoder/community/content/controller/ReportControllerTest.java`
  New controller test for the new `ReportApplicationService` entry point.
- `docs/ARCHITECTURE.md`
  Documents the phase-1 rule for `content`.
- `docs/SYSTEM_DESIGN.md`
  Documents the temporary whitelist and rollout sequence for remaining simple content controllers.

## Scope And Deliberate Deferrals

- In scope for this plan:
  - `PostController`
  - `ReportController`
  - `ModerationController`
  - `PostReadApplicationService`
  - `CommentReadApplicationService`
  - `PostPublishingApplicationService`
  - content-domain ArchUnit and docs
- Explicitly deferred to a later slice:
  - `CategoryController`
  - `TagController`
  - `SubscriptionController`
  - `BookmarkController`
- Reason for deferral:
  - `BookmarkController` already enters through `BookmarkApplicationService`
  - `CategoryController`, `TagController`, and `SubscriptionController` are single-use-case HTTP shims; forcing wrappers in this slice would create low-value forwarding layers while the real drift remains in post/report/moderation

### Task 1: Freeze The Content-Domain Controller Boundary

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`

- [ ] **Step 1: Write the failing ArchUnit rule for content controllers**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
private static final Set<String> LEGACY_CONTENT_CONTROLLER_APPLICATION_BOUNDARY = Set.of(
        "com.nowcoder.community.content.controller.CategoryController",
        "com.nowcoder.community.content.controller.SubscriptionController",
        "com.nowcoder.community.content.controller.TagController",
        "com.nowcoder.community.content.controller.ReportController",
        "com.nowcoder.community.content.controller.ModerationController"
);

@ArchTest
static final ArchRule content_controllers_must_not_depend_on_same_domain_non_application_entry_points =
        classes()
                .that().resideInAnyPackage("..content.controller..")
                .should(ArchitectureRulesSupport.notDependOnSameDomainServicesExceptApplicationServices(
                        LEGACY_CONTENT_CONTROLLER_APPLICATION_BOUNDARY
                ));
```

- [ ] **Step 2: Run the focused ArchUnit test and verify it fails**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=ControllerBoundaryArchTest test
```

Expected:

- FAIL because the new helper does not exist yet
- once the helper is added, the failing origins should be the temporary whitelist controllers listed above

- [ ] **Step 3: Add the helper that blocks same-domain controller access to raw services and use cases**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java
static ArchCondition<JavaClass> notDependOnSameDomainServicesExceptApplicationServices(Set<String> legacyOriginWhitelist) {
    return new ArchCondition<>("not depend on same-domain non-ApplicationService services or app packages") {
        @Override
        public void check(JavaClass item, ConditionEvents events) {
            if (isWhitelisted(item, legacyOriginWhitelist)) {
                return;
            }
            String originDomain = domainOf(item);
            if (originDomain.isEmpty()) {
                return;
            }
            for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (!originDomain.equals(domainOf(target))) {
                    continue;
                }
                boolean sameDomainRawService =
                        residesInLayer(target, Set.of("service"))
                                && !target.getSimpleName().endsWith("ApplicationService");
                boolean sameDomainUseCaseOrAppPackage = residesInLayer(target, Set.of("app"));
                if (sameDomainRawService || sameDomainUseCaseOrAppPackage) {
                    events.add(SimpleConditionEvent.violated(item, dependency.getDescription()));
                }
            }
        }
    };
}
```

- [ ] **Step 4: Document the rule and the temporary content whitelist**

```markdown
<!-- docs/ARCHITECTURE.md -->
- `content` phase 1 rule:
  - same-domain HTTP entrypoints use owner `*ApplicationService`
  - `content.controller..` must not directly depend on `..content.app..`
  - `content.controller..` must not directly depend on same-domain `..content.service..` classes unless the target ends with `ApplicationService`
  - foreign-domain callers keep using `content.api.query` / `content.api.action`

<!-- docs/SYSTEM_DESIGN.md -->
Temporary content-controller whitelist for phase 1:
- `CategoryController`
- `SubscriptionController`
- `TagController`
- `ReportController`
- `ModerationController`

Exit rule:
- remove a controller from the whitelist in the same PR that rewires it to an owner `ApplicationService`
```

- [ ] **Step 5: Re-run the ArchUnit test and verify it passes with the temporary whitelist**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=ControllerBoundaryArchTest test
```

Expected:

- PASS
- the new rule is live
- only the temporary whitelist still masks unresolved content controllers

- [ ] **Step 6: Commit the boundary freeze**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java \
  docs/ARCHITECTURE.md \
  docs/SYSTEM_DESIGN.md
git commit -m "test: freeze content controller application boundary"
```

### Task 2: Remove HTTP DTO Leakage From The Post/Comment Application Layer

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostControllerResponseMapper.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`

- [ ] **Step 1: Write the failing controller test against owner views/results instead of DTO-returning services**

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java
@Mock
private PostControllerResponseMapper responseMapper;

@BeforeEach
void setUp() {
    controller = new PostController(
            postReadApplicationService,
            commentReadApplicationService,
            postPublishingApplicationService,
            postModerationApplicationService,
            commentApplicationService,
            responseMapper
    );
}

@Test
void listAndDetailShouldMapOwnerViewsInsideTheControllerBoundary() {
    UUID userId = uuid(7);
    UUID postId = uuid(11);
    UUID categoryId = uuid(3);
    PostSummaryView summaryView = new PostSummaryView(
            postId, userId, "first", 0, 0, new Date(), 1, 10.0, categoryId,
            List.of("java"), null, null, new Date(), null
    );
    PostSummaryResponse summaryResponse = postSummary(postId, userId, categoryId, new Date(), "first");
    PostDetailView detailView = new PostDetailView(
            postId, userId, "detail", "body", 0, 0, new Date(), null, 0, 1, 10.0,
            categoryId, List.of("java"), 3L, false, false
    );
    PostDetailResponse detailResponse = new PostDetailResponse();
    detailResponse.setId(postId);
    detailResponse.setTitle("detail");

    when(postReadApplicationService.listPosts(userId, "latest", categoryId, "java", false, 0, 10))
            .thenReturn(List.of(summaryView));
    when(responseMapper.toPostSummaryResponses(List.of(summaryView)))
            .thenReturn(List.of(summaryResponse));
    when(postReadApplicationService.getPostDetail(userId, postId))
            .thenReturn(detailView);
    when(responseMapper.toPostDetailResponse(detailView))
            .thenReturn(detailResponse);

    Result<List<PostSummaryResponse>> listResult = controller.list(authentication(userId), "latest", categoryId, "java", false, 0, 10);
    Result<PostDetailResponse> detailResult = controller.detail(authentication(userId), postId);

    assertThat(listResult.getData()).containsExactly(summaryResponse);
    assertThat(detailResult.getData()).isSameAs(detailResponse);
}
```

- [ ] **Step 2: Run the focused post/controller tests and verify they fail**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=PostControllerUnitTest,PostPublishingApplicationServiceTest test
```

Expected:

- FAIL because `PostController` does not accept `PostControllerResponseMapper`
- FAIL because controller still calls `listPostSummaryResponses`, `getPostDetailResponse`, `commentResponses`, or `createPost`

- [ ] **Step 3: Add a controller-adjacent mapper and rewire `PostController` onto owner views/results**

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostControllerResponseMapper.java
package com.nowcoder.community.content.controller;

import com.nowcoder.community.content.api.model.CommentView;
import com.nowcoder.community.content.api.model.PostDetailView;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.api.model.PostCreateResult;
import com.nowcoder.community.content.dto.CommentResponse;
import com.nowcoder.community.content.dto.CreatePostResponse;
import com.nowcoder.community.content.dto.PostDetailResponse;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PostControllerResponseMapper {

    List<PostSummaryResponse> toPostSummaryResponses(List<PostSummaryView> views) {
        return views == null ? List.of() : views.stream().map(this::toPostSummaryResponse).toList();
    }

    PostSummaryResponse toPostSummaryResponse(PostSummaryView view) {
        PostSummaryResponse response = new PostSummaryResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setTitle(view.title());
        response.setType(view.type());
        response.setStatus(view.status());
        response.setCreateTime(view.createTime());
        response.setCommentCount(view.commentCount());
        response.setScore(view.score());
        response.setCategoryId(view.categoryId());
        response.setTags(view.tags());
        response.setLastReplyUserId(view.lastReplyUserId());
        response.setLastReplyTime(view.lastReplyTime());
        response.setLastActivityTime(view.lastActivityTime());
        response.setLastReplyPreview(view.lastReplyPreview());
        return response;
    }

    PostDetailResponse toPostDetailResponse(PostDetailView view) {
        PostDetailResponse response = new PostDetailResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setTitle(view.title());
        response.setContent(view.content());
        response.setType(view.type());
        response.setStatus(view.status());
        response.setCreateTime(view.createTime());
        response.setUpdateTime(view.updateTime());
        response.setEditCount(view.editCount());
        response.setCommentCount(view.commentCount());
        response.setScore(view.score());
        response.setCategoryId(view.categoryId());
        response.setTags(view.tags());
        response.setLikeCount(view.likeCount());
        response.setLiked(view.liked());
        response.setBookmarked(view.bookmarked());
        return response;
    }

    List<CommentResponse> toCommentResponses(List<CommentView> views) {
        return views == null ? List.of() : views.stream().map(this::toCommentResponse).toList();
    }

    CommentResponse toCommentResponse(CommentView view) {
        CommentResponse response = new CommentResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setEntityType(view.entityType());
        response.setEntityId(view.entityId());
        response.setTargetId(view.targetId());
        response.setContent(view.content());
        response.setCreateTime(view.createTime());
        response.setUpdateTime(view.updateTime());
        response.setEditCount(view.editCount());
        return response;
    }

    CreatePostResponse toCreatePostResponse(PostCreateResult result) {
        CreatePostResponse response = new CreatePostResponse();
        response.setPostId(result == null ? null : result.postId());
        return response;
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java
public class PostController {

    private final PostReadApplicationService postReadApplicationService;
    private final CommentReadApplicationService commentReadApplicationService;
    private final PostPublishingApplicationService postPublishingApplicationService;
    private final PostModerationApplicationService postModerationApplicationService;
    private final CommentApplicationService commentApplicationService;
    private final PostControllerResponseMapper responseMapper;

    public PostController(
            PostReadApplicationService postReadApplicationService,
            CommentReadApplicationService commentReadApplicationService,
            PostPublishingApplicationService postPublishingApplicationService,
            PostModerationApplicationService postModerationApplicationService,
            CommentApplicationService commentApplicationService,
            PostControllerResponseMapper responseMapper
    ) {
        this.postReadApplicationService = postReadApplicationService;
        this.commentReadApplicationService = commentReadApplicationService;
        this.postPublishingApplicationService = postPublishingApplicationService;
        this.postModerationApplicationService = postModerationApplicationService;
        this.commentApplicationService = commentApplicationService;
        this.responseMapper = responseMapper;
    }

    @GetMapping
    public Result<List<PostSummaryResponse>> list(...) {
        UUID currentUserId = CurrentUser.tryUserUuid(authentication);
        return Result.ok(responseMapper.toPostSummaryResponses(
                postReadApplicationService.listPosts(currentUserId, order, categoryId, tag, subscribed, page, size)
        ));
    }

    @PostMapping
    public Result<CreatePostResponse> create(...) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(responseMapper.toCreatePostResponse(
                postPublishingApplicationService.create(
                        userId,
                        idempotencyKey,
                        request.getTitle(),
                        request.getContent(),
                        request.getCategoryId(),
                        request.getTags()
                )
        ));
    }
}
```

- [ ] **Step 4: Remove controller-only DTO helper methods from the application services**

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadApplicationService.java
@Service
public class PostReadApplicationService implements PostReadQueryApi {

    @Override
    public List<PostSummaryView> listPosts(UUID currentUserId, String order, UUID categoryId, String tag, Boolean subscribed, Integer page, Integer size) {
        // keep existing orchestration
    }

    @Override
    public PostDetailView getPostDetail(UUID currentUserId, UUID postId) {
        // keep existing orchestration
    }

    @Override
    public List<RecentUserCommentView> listRecentCommentsByUser(UUID userId, Integer page, Integer size) {
        // keep existing orchestration
    }

    // delete:
    // - listPostSummaryResponses(...)
    // - listPostSummaryResponsesByIds(...)
    // - getPostDetailResponse(...)
    // - private toPostSummaryResponse(...)
    // - private toPostDetailResponse(...)
}

// backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadApplicationService.java
@Service
public class CommentReadApplicationService implements CommentReadQueryApi {

    @Override
    public List<CommentView> comments(UUID postId, Integer page, Integer size) { ... }

    @Override
    public List<CommentView> replies(UUID postId, UUID commentId, Integer page, Integer size) { ... }

    // delete:
    // - commentResponses(...)
    // - replyResponses(...)
    // - private toResponse(...)
}

// backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java
@Service
public class PostPublishingApplicationService implements PostPublishingActionApi {
    @Override
    public PostCreateResult create(UUID userId, String idempotencyKey, String title, String content, UUID categoryId, List<String> tags) {
        // keep existing idempotency + sanitize + createPostUseCase orchestration
    }

    // delete createPost(...) convenience wrapper
}
```

- [ ] **Step 5: Re-run the focused post/controller tests and verify they pass**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=PostControllerUnitTest,PostPublishingApplicationServiceTest,PostReadApplicationServiceTest test
```

Expected:

- PASS
- no controller depends on DTO-returning application-service methods in the post/comment slice

- [ ] **Step 6: Commit the post/comment boundary cleanup**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostControllerResponseMapper.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/PostReadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/CommentReadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingApplicationServiceTest.java
git commit -m "refactor: clean content post application boundary"
```

### Task 3: Rewire Report And Moderation Through Owner Application Services

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/service/ReportApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/service/ModerationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/ReportController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/ModerationController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/ModerationControllerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/ReportControllerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/ModerationControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/ReportControllerTest.java`

- [ ] **Step 1: Write the failing controller tests against the new owner application services**

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/controller/ReportControllerTest.java
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportApplicationService reportApplicationService;

    private ReportController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportController(reportApplicationService);
    }

    @Test
    void createShouldDelegateRawRequestFieldsToReportApplicationService() {
        UUID reporterId = uuid(7);
        UUID targetId = uuid(21);
        UUID reportId = uuid(31);
        CreateReportRequest request = new CreateReportRequest();
        request.setTargetType("post");
        request.setTargetId(targetId);
        request.setReason("spam");
        request.setDetail("burst");
        when(reportApplicationService.create(reporterId, "post", targetId, "spam", "burst"))
                .thenReturn(reportId);

        Result<CreateReportResponse> result = controller.create(authentication(reporterId), request);

        assertThat(result.getData().getReportId()).isEqualTo(reportId);
        verify(reportApplicationService).create(reporterId, "post", targetId, "spam", "burst");
    }
}
```

```java
// backend/community-app/src/test/java/com/nowcoder/community/content/controller/ModerationControllerTest.java
@Mock
private ModerationApplicationService moderationApplicationService;

@BeforeEach
void setUp() {
    controller = new ModerationController(moderationApplicationService);
}

@Test
void actionShouldDelegateToModerationApplicationService() {
    UUID actorId = uuid(42);
    ModerationActionRequest request = new ModerationActionRequest();
    request.setReportId(REPORT_ID);
    request.setAction("ban");
    request.setReason("abuse");
    request.setDurationSeconds(3600);
    when(moderationApplicationService.takeAction(actorId, request)).thenReturn(ACTION_ID);

    Result<UUID> result = controller.action(authentication(actorId), request);

    assertThat(result.getData()).isEqualTo(ACTION_ID);
    verify(moderationApplicationService).takeAction(actorId, request);
}
```

- [ ] **Step 2: Run the focused moderation/report tests and verify they fail**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=ModerationControllerTest,ReportControllerTest test
```

Expected:

- FAIL because `ReportApplicationService` and `ModerationApplicationService` do not exist yet
- FAIL because the controllers still depend on raw services/use cases

- [ ] **Step 3: Implement the owner application services and rewire the controllers**

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/ReportApplicationService.java
package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class ReportApplicationService {

    private final ReportService reportService;

    public ReportApplicationService(ReportService reportService) {
        this.reportService = reportService;
    }

    public UUID create(UUID reporterId, String rawTargetType, UUID targetId, String reason, String detail) {
        return reportService.createReport(reporterId, parseTargetType(rawTargetType), targetId, reason, detail);
    }

    private int parseTargetType(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase();
        if (s.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "targetType 不能为空");
        }
        if ("post".equals(s) || "帖子".equals(s) || "1".equals(s)) {
            return ReportService.TARGET_TYPE_POST;
        }
        if ("comment".equals(s) || "评论".equals(s) || "2".equals(s)) {
            return ReportService.TARGET_TYPE_COMMENT;
        }
        if ("user".equals(s) || "用户".equals(s) || "3".equals(s)) {
            return ReportService.TARGET_TYPE_USER;
        }
        throw new BusinessException(INVALID_ARGUMENT, "targetType 非法");
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/service/ModerationApplicationService.java
package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.moderation.TakeModerationActionUseCase;
import com.nowcoder.community.content.dto.ModerationActionRequest;
import com.nowcoder.community.content.dto.ModerationActionResponse;
import com.nowcoder.community.content.dto.ReportResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ModerationApplicationService {

    private final ModerationService moderationService;
    private final TakeModerationActionUseCase takeModerationActionUseCase;

    public ModerationApplicationService(
            ModerationService moderationService,
            TakeModerationActionUseCase takeModerationActionUseCase
    ) {
        this.moderationService = moderationService;
        this.takeModerationActionUseCase = takeModerationActionUseCase;
    }

    public List<ReportResponse> listReports(Integer status, Integer targetType, UUID reporterId, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(100, Math.max(1, size));
        return moderationService.listReportResponses(status, targetType, reporterId, p, s);
    }

    public UUID takeAction(UUID actorId, ModerationActionRequest request) {
        return takeModerationActionUseCase.takeAction(
                actorId,
                request.getReportId(),
                request.getAction(),
                request.getReason(),
                request.getDurationSeconds()
        );
    }

    public List<ModerationActionResponse> listActions(UUID actorId, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(100, Math.max(1, size));
        return moderationService.listModerationActionResponses(actorId, p, s);
    }
}
```

```java
// backend/community-app/src/main/java/com/nowcoder/community/content/controller/ModerationController.java
public class ModerationController {

    private final ModerationApplicationService moderationApplicationService;

    public ModerationController(ModerationApplicationService moderationApplicationService) {
        this.moderationApplicationService = moderationApplicationService;
    }

    @GetMapping("/reports")
    public Result<List<ReportResponse>> reports(...) {
        return Result.ok(moderationApplicationService.listReports(status, targetType, reporterId, page, size));
    }

    @PostMapping("/actions")
    public Result<UUID> action(Authentication authentication, @Valid @RequestBody ModerationActionRequest request) {
        UUID actorId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(moderationApplicationService.takeAction(actorId, request));
    }

    @GetMapping("/actions")
    public Result<List<ModerationActionResponse>> actions(...) {
        return Result.ok(moderationApplicationService.listActions(actorId, page, size));
    }
}
```

- [ ] **Step 4: Remove the migrated controllers from the temporary ArchUnit whitelist**

```java
// backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
private static final Set<String> LEGACY_CONTENT_CONTROLLER_APPLICATION_BOUNDARY = Set.of(
        "com.nowcoder.community.content.controller.CategoryController",
        "com.nowcoder.community.content.controller.SubscriptionController",
        "com.nowcoder.community.content.controller.TagController"
);
```

- [ ] **Step 5: Re-run the focused moderation/report/controller tests and verify they pass**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=ModerationControllerTest,ReportControllerTest,ControllerBoundaryArchTest test
```

Expected:

- PASS
- `ReportController` and `ModerationController` are no longer whitelisted
- content-domain controller drift is reduced to the explicitly deferred lightweight controllers

- [ ] **Step 6: Commit the moderation/report convergence**

```bash
cd /home/feng/code/project/community
git add \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/ReportApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/service/ModerationApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/ReportController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/ModerationController.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/ReportControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/controller/ModerationControllerTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java
git commit -m "refactor: route content moderation and reports via application services"
```

### Task 4: Verify The Content Slice And Publish Remaining Rollout Notes

**Files:**
- Modify: `docs/SYSTEM_DESIGN.md`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/ModerationControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/ReportControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostReadApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostPublishingApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/app/moderation/TakeModerationActionUseCaseTest.java`

- [ ] **Step 1: Document the remaining deferred content controllers explicitly**

```markdown
<!-- docs/SYSTEM_DESIGN.md -->
Content phase-1 remaining whitelist:
- `CategoryController`
- `SubscriptionController`
- `TagController`

Reason:
- each currently fronts one low-complexity same-domain use case
- do not add thin `ApplicationService` wrappers only to satisfy naming symmetry
- migrate them only when a second use case, aggregation concern, or cross-domain collaboration need appears
```

- [ ] **Step 2: Run the full focused content convergence suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=\
ControllerBoundaryArchTest,\
DomainBoundaryArchTest,\
PostControllerUnitTest,\
ReportControllerTest,\
ModerationControllerTest,\
PostReadApplicationServiceTest,\
PostPublishingApplicationServiceTest,\
TakeModerationActionUseCaseTest,\
CommentServiceTest \
test
```

Expected:

- PASS
- no new same-domain controller drift in migrated content paths
- foreign-domain collaboration rules remain green

- [ ] **Step 3: Capture the verification checkpoint**

```bash
cd /home/feng/code/project/community
git add docs/SYSTEM_DESIGN.md
git commit -m "docs: record content convergence phase1 rollout state"
```

---

## Self-Review

### Spec coverage

- Same-domain boundary freeze: covered by Task 1
- Post/comment application-layer cleanup: covered by Task 2
- Report/moderation controller convergence: covered by Task 3
- Verification and remaining whitelist governance: covered by Task 4

### Placeholder scan

- No `TODO` / `TBD` placeholders remain
- Every task names exact files, commands, and test targets
- Every code-changing step includes concrete code snippets

### Type consistency

- New owner services are named `*ApplicationService`
- Foreign collaboration interfaces remain `content.api.*`
- Controller mapping is concentrated in `PostControllerResponseMapper`
- `TakeModerationActionUseCase` stays internal and is no longer controller-facing

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-24-content-domain-application-layer-convergence-phase1.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**

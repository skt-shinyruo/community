# Backend Architecture Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden `community-app` DDD boundaries by fixing guardrail coverage, normalizing IM projection packages, routing content API adapters through application services, and making small touched-model behavior improvements.

**Architecture:** Keep external HTTP endpoints, API contracts, Kafka topics, and schemas unchanged. Move inbound/technical adapters into canonical packages and ensure adapters enter same-domain `*ApplicationService` before domain or persistence collaboration. Add narrow application methods and repository ports rather than broad rewrites.

**Tech Stack:** Java 17, Spring Boot 3.2.6, MyBatis, Kafka, ArchUnit, JUnit 5, Mockito, AssertJ, Maven.

---

## File Structure

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`
  - Cover both `..infra..` and `..infrastructure..` packages.
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java`
  - Cover IM infrastructure event adapters after package normalization.
- Move: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotController.java`
  - To `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImPolicySnapshotController.java`.
- Move: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyChangePublisher.java`
  - To `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyChangePublisher.java`.
- Move: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyOutboxEnqueuer.java`
  - To `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyOutboxEnqueuer.java`.
- Move: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandler.java`
  - To `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyKafkaOutboxHandler.java`.
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotService.java`
  - Inline orchestration into `ImPolicySnapshotApplicationService` so foreign APIs are called from application code.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicySnapshotApplicationService.java`
  - Inject `UserModerationQueryApi` and `SocialBlockQueryApi`; build projection snapshots directly.
- Move/update tests under `backend/community-app/src/test/java/com/nowcoder/community/im/...` to match new packages.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/DiscussPost.java`
  - Add small behavior helpers: `isDeleted()` and `requireActive()` or equivalent.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/Comment.java`
  - Add small behavior helpers: `isActive()` and `pointsToPost()` or equivalent.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java`
  - Add `scanAfterId(UUID afterId, int limit)`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentContentRepository.java`
  - Add `getByIdAllowDeleted(UUID commentId)`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java`
  - Implement `scanAfterId` using `DiscussPostMapper.selectDiscussPostsAfterId`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentContentRepository.java`
  - Implement `getByIdAllowDeleted` using `CommentMapper.selectCommentById`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`
  - Add scan/projection methods returning application result records for `PostScanQueryApi` implementation.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostScanResult.java`
  - Application-level result for post scan queries.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEntityResolutionApplicationService.java`
  - Resolve post/comment ownership and root post through domain repositories.
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/ResolvedContentResult.java`
  - Application-level result for content entity resolution.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostScanService.java`
  - Delegate to `PostReadApplicationService` only.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/ContentEntityQueryService.java`
  - Delegate to `ContentEntityResolutionApplicationService` only.
- Update tests for changed constructors and package names.

---

### Task 1: Harden ArchUnit Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java`

- [ ] **Step 1: Update infrastructure package coverage**

Replace the rule selector in `InfraBoundaryArchTest` with both package names:

```java
@ArchTest
static final ArchRule infra_must_not_depend_on_core_domain_implementation_layers =
        classes()
                .that().resideInAnyPackage("..infra..", "..infrastructure..")
                .should(ArchitectureRulesSupport.notDependOnLayers(
                        "not depend on core-domain controller/mapper/dao/entity/config/security/service packages",
                        FOREIGN_IMPLEMENTATION_LAYERS,
                        true,
                        Set.of()
                ));
```

- [ ] **Step 2: Keep listener selectors aligned with canonical infrastructure packages**

Update each `resideInAnyPackage(...)` block in `ListenerBoundaryArchTest` so it includes canonical event/job packages and does not rely on `im.projection`:

```java
classes()
        .that().resideInAnyPackage(
                "..infrastructure.event..",
                "..infrastructure.job..",
                "..infra.job.handlers.."
        )
        .and().haveNameMatching(".*(Listener|Handler|Bridge|Enqueuer|Job)$")
```

The selector already has this shape. If the file still matches this snippet, leave it unchanged and rely on Task 2 package moves to bring IM handlers under coverage.

- [ ] **Step 3: Run architecture tests and observe expected failures before package moves**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest='InfraBoundaryArchTest,ListenerBoundaryArchTest'
```

Expected: this may fail because existing `content.infrastructure.api` classes still depend on mapper/domain collaborators. Keep the failure output; later tasks should remove the violations.

---

### Task 2: Normalize IM Projection Packages

**Files:**
- Move: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotController.java` -> `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImPolicySnapshotController.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyChangePublisher.java` -> `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyChangePublisher.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyOutboxEnqueuer.java` -> `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyOutboxEnqueuer.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicyKafkaOutboxHandler.java` -> `backend/community-app/src/main/java/com/nowcoder/community/im/infrastructure/event/ImPolicyKafkaOutboxHandler.java`
- Move/update corresponding tests from `src/test/java/com/nowcoder/community/im/projection` to `src/test/java/com/nowcoder/community/im/controller` or `src/test/java/com/nowcoder/community/im/infrastructure/event`.
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/application/BlockApplicationServiceTest.java`

- [ ] **Step 1: Move controller package**

Change the package declaration in the moved controller file:

```java
package com.nowcoder.community.im.controller;
```

Keep the class body and endpoint paths unchanged:

```java
@RestController
@RequestMapping("/internal/im/realtime/projections")
public class ImPolicySnapshotController {
    // existing methods unchanged
}
```

- [ ] **Step 2: Move IM event adapter packages**

Change the package declaration in all three moved event adapter files:

```java
package com.nowcoder.community.im.infrastructure.event;
```

The files are:

```text
ImPolicyChangePublisher.java
ImPolicyOutboxEnqueuer.java
ImPolicyKafkaOutboxHandler.java
```

- [ ] **Step 3: Update test package declarations and imports**

Use these package declarations:

```java
// ImPolicySnapshotControllerUnitTest.java and ImPolicySnapshotControllerTest.java
package com.nowcoder.community.im.controller;
```

```java
// ImPolicyKafkaOutboxHandlerTest.java and ImPolicyOutboxEnqueuerTest.java
package com.nowcoder.community.im.infrastructure.event;
```

- [ ] **Step 4: Update social test package-string assertion**

In `BlockApplicationServiceTest`, replace the old IM type string:

```java
.doesNotContain("com.nowcoder.community.im.projection.ImPolicyChangePublisher");
```

with:

```java
.doesNotContain("com.nowcoder.community.im.infrastructure.event.ImPolicyChangePublisher");
```

- [ ] **Step 5: Run focused IM tests**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest='ImPolicy*Test,BlockApplicationServiceTest'
```

Expected: tests compile. `ImPolicySnapshotServiceTest` may still reference the deleted service until Task 3 updates it.

---

### Task 3: Move IM Snapshot Orchestration Into Application Service

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicySnapshotApplicationService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotService.java`
- Modify/move: `backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicySnapshotServiceTest.java` -> `backend/community-app/src/test/java/com/nowcoder/community/im/application/ImPolicySnapshotApplicationServiceTest.java`

- [ ] **Step 1: Replace application service implementation**

Use this full application service body, preserving existing public method names:

```java
package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ImPolicySnapshotApplicationService {

    private final UserModerationQueryApi userModerationQueryApi;
    private final SocialBlockQueryApi socialBlockQueryApi;

    public ImPolicySnapshotApplicationService(
            UserModerationQueryApi userModerationQueryApi,
            SocialBlockQueryApi socialBlockQueryApi
    ) {
        this.userModerationQueryApi = userModerationQueryApi;
        this.socialBlockQueryApi = socialBlockQueryApi;
    }

    public UserMessagingPolicySnapshot userPolicies(UUID afterUserId, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        Instant now = Instant.now();
        List<UserModerationStateView> states = userModerationQueryApi.scanModerationStatesAfterId(afterUserId, normalizedLimit);
        List<UserMessagingPolicyEntry> entries = states.stream()
                .map(state -> toUserPolicyEntry(state, now))
                .toList();

        UUID nextUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).userId();
        boolean hasMore = nextUserId != null
                && entries.size() == normalizedLimit
                && !userModerationQueryApi.scanModerationStatesAfterId(nextUserId, 1).isEmpty();

        return new UserMessagingPolicySnapshot(entries, nextUserId, hasMore);
    }

    public UserBlockRelationSnapshot blockRelations(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<SocialBlockRelationView> views =
                socialBlockQueryApi.scanBlockRelationsAfter(afterBlockerUserId, afterBlockedUserId, normalizedLimit);
        List<UserBlockRelationEntry> entries = views.stream()
                .map(this::toBlockRelationEntry)
                .toList();

        UUID nextBlockerUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockerUserId();
        UUID nextBlockedUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockedUserId();
        boolean hasMore = nextBlockerUserId != null
                && nextBlockedUserId != null
                && entries.size() == normalizedLimit
                && !socialBlockQueryApi.scanBlockRelationsAfter(nextBlockerUserId, nextBlockedUserId, 1).isEmpty();

        return new UserBlockRelationSnapshot(entries, nextBlockerUserId, nextBlockedUserId, hasMore);
    }

    private UserMessagingPolicyEntry toUserPolicyEntry(UserModerationStateView state, Instant now) {
        boolean suspended = state != null && state.banUntil() != null && state.banUntil().isAfter(now);
        boolean muted = state != null && state.muteUntil() != null && state.muteUntil().isAfter(now);
        boolean canSendPrivate = state != null && state.userId() != null && !suspended && !muted;
        return new UserMessagingPolicyEntry(
                state == null ? null : state.userId(),
                state != null && state.userId() != null,
                suspended,
                muted,
                toEpochMillis(state == null ? null : state.muteUntil()),
                toEpochMillis(state == null ? null : state.banUntil()),
                canSendPrivate
        );
    }

    private UserBlockRelationEntry toBlockRelationEntry(SocialBlockRelationView view) {
        return new UserBlockRelationEntry(view.blockerUserId(), view.blockedUserId(), true);
    }

    private int normalizeLimit(int limit) {
        return Math.min(500, Math.max(1, limit));
    }

    private Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
```

- [ ] **Step 2: Delete the old projection helper**

Delete:

```text
backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotService.java
```

- [ ] **Step 3: Update snapshot service unit test to target application service**

Move the test to:

```text
backend/community-app/src/test/java/com/nowcoder/community/im/application/ImPolicySnapshotApplicationServiceTest.java
```

Use this package and constructor assertion:

```java
package com.nowcoder.community.im.application;

// imports unchanged except remove ImPolicySnapshotService references

class ImPolicySnapshotApplicationServiceTest {

    @Test
    void snapshotApplicationServiceShouldExposeOwnerDomainQueryApiConstructor() {
        assertThat(ImPolicySnapshotApplicationService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationQueryApi.class,
                        SocialBlockQueryApi.class
                ));
    }

    // existing userPoliciesShouldProjectOwnerDomainModerationViews and
    // blockRelationsShouldProjectOwnerDomainBlockViews tests should instantiate
    // new ImPolicySnapshotApplicationService(moderationQueryApi, blockQueryApi).
}
```

- [ ] **Step 4: Run focused IM tests**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest='ImPolicy*Test,BlockApplicationServiceTest'
```

Expected: all selected tests pass.

---

### Task 4: Add Content Repository Ports And Domain Helpers

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/DiscussPost.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/Comment.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostContentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentContentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostContentRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentContentRepository.java`

- [ ] **Step 1: Add helpers to `DiscussPost`**

Add constants and methods near the fields/getters:

```java
private static final int STATUS_DELETED = 2;

public boolean isDeleted() {
    return status == STATUS_DELETED;
}

public boolean isActive() {
    return !isDeleted();
}
```

- [ ] **Step 2: Add helpers to `Comment`**

Add constants and methods near the fields/getters:

```java
private static final int STATUS_ACTIVE = 0;

public boolean isActive() {
    return status == STATUS_ACTIVE;
}

public boolean pointsTo(int expectedEntityType) {
    return entityType == expectedEntityType && entityId != null;
}
```

- [ ] **Step 3: Extend post content repository**

Add the scan method to `PostContentRepository`:

```java
List<DiscussPost> scanAfterId(UUID afterId, int limit);
```

- [ ] **Step 4: Implement post scanning in MyBatis repository**

Add this method to `MyBatisPostContentRepository`:

```java
@Override
public List<DiscussPost> scanAfterId(UUID afterId, int limit) {
    int safeLimit = limit <= 0 ? 500 : Math.min(1000, Math.max(1, limit));
    List<DiscussPost> posts = discussPostMapper.selectDiscussPostsAfterId(afterId, safeLimit);
    return posts == null ? List.of() : posts;
}
```

Also replace the deleted check in `getById`:

```java
if (post.isDeleted()) {
    throw new BusinessException(POST_NOT_FOUND);
}
```

- [ ] **Step 5: Extend comment content repository**

Add this method to `CommentContentRepository`:

```java
Comment getByIdAllowDeleted(UUID commentId);
```

- [ ] **Step 6: Implement allow-deleted comment lookup**

Add this method to `MyBatisCommentContentRepository`:

```java
@Override
public Comment getByIdAllowDeleted(UUID commentId) {
    Comment comment = commentMapper.selectCommentById(commentId);
    if (comment == null) {
        throw new BusinessException(COMMENT_NOT_FOUND);
    }
    return comment;
}
```

Also replace active checks with the new helper:

```java
if (comment == null || !comment.isActive()) {
    throw new BusinessException(COMMENT_NOT_FOUND);
}
```

- [ ] **Step 7: Run compile-focused test slice**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest='*ContentRepository*Test,*Post*Test,*Comment*Test' -DfailIfNoTests=false
```

Expected: selected tests compile and pass, or Maven reports no matching tests without failing because `-DfailIfNoTests=false` is set.

---

### Task 5: Route PostScanQueryApi Through Application Service

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/PostScanResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostScanService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/PostScanServiceTest.java`

- [ ] **Step 1: Create application scan result record**

Create `PostScanResult.java`:

```java
package com.nowcoder.community.content.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostScanResult(
        List<PostProjectionResult> items,
        UUID nextAfterId,
        boolean hasMore
) {

    public PostScanResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record PostProjectionResult(
            UUID postId,
            UUID userId,
            UUID categoryId,
            List<String> tags,
            String title,
            String content,
            int type,
            int status,
            Instant createTime,
            Double score
    ) {

        public PostProjectionResult {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
```

- [ ] **Step 2: Add application result import to `PostReadApplicationService`**

Add imports:

```java
import com.nowcoder.community.content.application.result.PostScanResult;
```

- [ ] **Step 3: Add scan methods to `PostReadApplicationService`**

Add these public methods before private helper methods:

```java
public PostScanResult scanPosts(UUID afterId, int limit) {
    int safeLimit = limit <= 0 ? 500 : Math.min(1000, Math.max(1, limit));
    List<DiscussPost> posts = postContentPort.scanAfterId(afterId, safeLimit);
    List<PostScanResult.PostProjectionResult> items = toPostProjectionResults(posts);
    UUID nextAfterId = posts.isEmpty() ? afterId : posts.get(posts.size() - 1).getId();
    return new PostScanResult(items, nextAfterId, posts.size() == safeLimit);
}

public PostScanResult.PostProjectionResult getPostProjectionAllowDeleted(UUID postId) {
    if (postId == null) {
        return null;
    }
    DiscussPost post = postContentPort.getByIdAllowDeleted(postId);
    return toPostProjectionResult(post);
}
```

- [ ] **Step 4: Add projection helpers to `PostReadApplicationService`**

Add these private methods near `assembleSummaries`:

```java
private List<PostScanResult.PostProjectionResult> toPostProjectionResults(List<DiscussPost> posts) {
    if (posts == null || posts.isEmpty()) {
        return List.of();
    }
    List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
    Map<UUID, List<String>> tagsByPostId = tagContentPort.getTagsByPostIds(postIds);
    Map<UUID, List<PostContentBlock>> blocksByPostId = postContentBlockRepository.listByPostIds(postIds);
    Map<UUID, List<String>> safeTagsByPostId = tagsByPostId == null ? Map.of() : tagsByPostId;
    Map<UUID, List<PostContentBlock>> safeBlocksByPostId = blocksByPostId == null ? Map.of() : blocksByPostId;
    return posts.stream()
            .map(post -> toPostProjectionResult(
                    post,
                    safeTagsByPostId.getOrDefault(post.getId(), List.of()),
                    safeBlocksByPostId.getOrDefault(post.getId(), List.of())
            ))
            .toList();
}

private PostScanResult.PostProjectionResult toPostProjectionResult(DiscussPost post) {
    List<String> tags = tagContentPort.getTagsByPostIds(List.of(post.getId())).getOrDefault(post.getId(), List.of());
    List<PostContentBlock> blocks = postContentBlockRepository.listByPostId(post.getId());
    return toPostProjectionResult(post, tags, blocks);
}

private PostScanResult.PostProjectionResult toPostProjectionResult(
        DiscussPost post,
        List<String> tags,
        List<PostContentBlock> blocks
) {
    return new PostScanResult.PostProjectionResult(
            post.getId(),
            post.getUserId(),
            post.getCategoryId(),
            tags,
            textCodec.decodeOnRead(post.getTitle()),
            textCodec.decodeOnRead(postContentBlockTextProjector.fullText(blocks)),
            post.getType(),
            post.getStatus(),
            post.getCreateTime() == null ? null : post.getCreateTime().toInstant(),
            post.getScore()
    );
}
```

If `PostReadApplicationService` does not currently have a `ContentTextCodec` field, add it to the constructor and assign it, matching the existing constructor-injection style.

- [ ] **Step 5: Simplify `PostScanService` adapter**

Replace the class body with delegation only:

```java
package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.content.application.result.PostScanResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PostScanService implements PostScanQueryApi {

    private final PostReadApplicationService postReadApplicationService;

    public PostScanService(PostReadApplicationService postReadApplicationService) {
        this.postReadApplicationService = postReadApplicationService;
    }

    @Override
    public PostScanView scanPosts(UUID afterId, int limit) {
        return toView(postReadApplicationService.scanPosts(afterId, limit));
    }

    @Override
    public PostScanView.PostProjectionView getPostProjectionAllowDeleted(UUID postId) {
        return toView(postReadApplicationService.getPostProjectionAllowDeleted(postId));
    }

    private static PostScanView toView(PostScanResult result) {
        if (result == null) {
            return new PostScanView(List.of(), null, false);
        }
        return new PostScanView(
                result.items().stream().map(PostScanService::toView).toList(),
                result.nextAfterId(),
                result.hasMore()
        );
    }

    private static PostScanView.PostProjectionView toView(PostScanResult.PostProjectionResult result) {
        if (result == null) {
            return null;
        }
        return new PostScanView.PostProjectionView(
                result.postId(),
                result.userId(),
                result.categoryId(),
                result.tags(),
                result.title(),
                result.content(),
                result.type(),
                result.status(),
                result.createTime(),
                result.score()
        );
    }
}
```

- [ ] **Step 6: Update adapter unit test**

Replace `PostScanServiceTest` with a delegation test:

```java
package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.content.application.result.PostScanResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostScanServiceTest {

    @Test
    void scanPostsShouldDelegateToApplicationService() {
        PostReadApplicationService applicationService = mock(PostReadApplicationService.class);
        UUID nextAfterId = uuid(10);
        PostScanResult result = new PostScanResult(List.of(), nextAfterId, false);
        when(applicationService.scanPosts(null, 5)).thenReturn(result);

        PostScanService service = new PostScanService(applicationService);

        PostScanView view = service.scanPosts(null, 5);

        assertThat(view.nextAfterId()).isEqualTo(nextAfterId);
        assertThat(view.hasMore()).isFalse();
        verify(applicationService).scanPosts(null, 5);
    }

    @Test
    void getPostProjectionAllowDeletedShouldDelegateToApplicationService() {
        PostReadApplicationService applicationService = mock(PostReadApplicationService.class);
        UUID postId = uuid(11);
        PostScanResult.PostProjectionResult result = new PostScanResult.PostProjectionResult(
                postId,
                uuid(3),
                uuid(4),
                List.of("search"),
                "title",
                "content",
                1,
                2,
                null,
                2.5
        );
        when(applicationService.getPostProjectionAllowDeleted(postId)).thenReturn(result);

        PostScanService service = new PostScanService(applicationService);

        PostScanView.PostProjectionView view = service.getPostProjectionAllowDeleted(postId);

        assertThat(view.postId()).isEqualTo(postId);
        assertThat(view.tags()).containsExactly("search");
        assertThat(view.status()).isEqualTo(2);
        verify(applicationService).getPostProjectionAllowDeleted(postId);
    }
}
```

- [ ] **Step 7: Run focused post scan tests**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest='PostScanServiceTest,SearchPostProjectionApplicationServiceTest'
```

Expected: selected tests pass.

---

### Task 6: Route ContentEntityQueryApi Through Application Service

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEntityResolutionApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/ResolvedContentResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/ContentEntityQueryService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/ContentEntityQueryServiceTest.java`

- [ ] **Step 1: Create application result record**

Create `ResolvedContentResult.java`:

```java
package com.nowcoder.community.content.application.result;

import java.util.UUID;

public record ResolvedContentResult(UUID entityUserId, UUID postId) {
}
```

- [ ] **Step 2: Create content entity resolution application service**

Create the file with this implementation:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.result.ResolvedContentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

@Service
public class ContentEntityResolutionApplicationService {

    private final PostContentRepository postContentRepository;
    private final CommentContentRepository commentContentRepository;

    public ContentEntityResolutionApplicationService(
            PostContentRepository postContentRepository,
            CommentContentRepository commentContentRepository
    ) {
        this.postContentRepository = postContentRepository;
        this.commentContentRepository = commentContentRepository;
    }

    public ResolvedContentResult resolve(int entityType, UUID entityId) {
        if (entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityId 非法");
        }
        if (entityType == EntityTypes.POST) {
            return resolvePost(entityId);
        }
        if (entityType == EntityTypes.COMMENT) {
            return resolveComment(entityId);
        }
        throw new BusinessException(INVALID_ARGUMENT, "仅支持解析 POST/COMMENT");
    }

    private ResolvedContentResult resolvePost(UUID postId) {
        DiscussPost post = postContentRepository.getById(postId);
        return new ResolvedContentResult(post.getUserId(), post.getId());
    }

    private ResolvedContentResult resolveComment(UUID commentId) {
        Comment comment = commentContentRepository.getByIdAllowDeleted(commentId);
        if (!comment.isActive()) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        UUID postId = resolveRootPostIdByComment(comment, 12);
        if (postId == null) {
            throw new BusinessException(POST_NOT_FOUND, "评论所属帖子不存在");
        }
        postContentRepository.getById(postId);
        return new ResolvedContentResult(comment.getUserId(), postId);
    }

    private UUID resolveRootPostIdByComment(Comment comment, int maxHops) {
        if (comment == null || comment.getId() == null) {
            return null;
        }
        int type = comment.getEntityType();
        UUID id = comment.getEntityId();
        for (int i = 0; i < Math.max(1, maxHops); i++) {
            if (type == EntityTypes.POST) {
                return id;
            }
            if (type != EntityTypes.COMMENT || id == null) {
                return null;
            }
            Comment parent = commentContentRepository.getByIdAllowDeleted(id);
            if (!parent.isActive()) {
                return null;
            }
            type = parent.getEntityType();
            id = parent.getEntityId();
        }
        return null;
    }
}
```

- [ ] **Step 3: Simplify content entity API adapter**

Replace `ContentEntityQueryService` with this delegation-only adapter:

```java
package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.api.query.ContentEntityQueryApi;
import com.nowcoder.community.content.application.ContentEntityResolutionApplicationService;
import com.nowcoder.community.content.application.result.ResolvedContentResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ContentEntityQueryService implements ContentEntityQueryApi {

    private final ContentEntityResolutionApplicationService applicationService;

    public ContentEntityQueryService(ContentEntityResolutionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public ResolvedContentRef resolve(int entityType, UUID entityId) {
        ResolvedContentResult result = applicationService.resolve(entityType, entityId);
        return new ResolvedContentRef(result.entityUserId(), result.postId());
    }
}
```

- [ ] **Step 4: Replace adapter test with delegation test**

Replace `ContentEntityQueryServiceTest` with:

```java
package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.application.ContentEntityResolutionApplicationService;
import com.nowcoder.community.content.application.result.ResolvedContentResult;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentEntityQueryServiceTest {

    @Test
    void resolveShouldDelegateToApplicationService() {
        ContentEntityResolutionApplicationService applicationService = mock(ContentEntityResolutionApplicationService.class);
        ResolvedContentResult result = new ResolvedContentResult(uuid(7), uuid(101));
        when(applicationService.resolve(EntityTypes.POST, uuid(101))).thenReturn(result);

        ContentEntityQueryService service = new ContentEntityQueryService(applicationService);

        ResolvedContentRef resolved = service.resolve(EntityTypes.POST, uuid(101));

        assertThat(resolved.entityUserId()).isEqualTo(uuid(7));
        assertThat(resolved.postId()).isEqualTo(uuid(101));
        verify(applicationService).resolve(EntityTypes.POST, uuid(101));
    }
}
```

- [ ] **Step 5: Add application service unit test for existing behavior**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEntityResolutionApplicationServiceTest.java` by moving the behavior cases from the old adapter test. Replace mapper mocks with `PostContentRepository` and `CommentContentRepository` mocks.

Use this helper pattern:

```java
private ContentEntityResolutionApplicationService service(
        PostContentRepository postRepository,
        CommentContentRepository commentRepository
) {
    return new ContentEntityResolutionApplicationService(postRepository, commentRepository);
}

private Comment activeComment(UUID id, UUID userId, int entityType, UUID entityId) {
    Comment comment = new Comment();
    comment.setId(id);
    comment.setUserId(userId);
    comment.setEntityType(entityType);
    comment.setEntityId(entityId);
    comment.setStatus(0);
    return comment;
}

private DiscussPost activePost(UUID postId, UUID userId) {
    DiscussPost post = new DiscussPost();
    post.setId(postId);
    post.setUserId(userId);
    post.setStatus(0);
    return post;
}
```

For deleted post behavior, mock `postRepository.getById(postId)` to throw `new BusinessException(POST_NOT_FOUND)` and assert the same error code. For missing parent behavior, mock `commentRepository.getByIdAllowDeleted(parentId)` to throw `new BusinessException(COMMENT_NOT_FOUND)` and expect `POST_NOT_FOUND` only if implementation catches missing parents; otherwise adjust implementation to catch `BusinessException` from parent lookup and return `null` inside `resolveRootPostIdByComment`.

- [ ] **Step 6: Run focused content entity tests**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest='ContentEntityQueryServiceTest,ContentEntityResolutionApplicationServiceTest,ContentEntityResolverTest,LikeApplicationServiceTest'
```

Expected: selected tests pass.

---

### Task 7: Final Architecture Verification

**Files:**
- Verify all changed files.

- [ ] **Step 1: Search for stale IM projection package references**

Use Grep for:

```text
com.nowcoder.community.im.projection
```

Expected: no production references. If test references remain only because a file has not been moved, update those tests.

- [ ] **Step 2: Search for direct mapper use in content API adapters**

Use Grep in `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api` for:

```text
Mapper|domain.repository|domain.model
```

Expected: `PostScanService.java` and `ContentEntityQueryService.java` no longer import mapper/domain repository/domain model types. Existing adapters may still import API/application types only.

- [ ] **Step 3: Run architecture tests**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: all ArchUnit tests pass.

- [ ] **Step 4: Run focused regression tests**

Run from `backend`:

```bash
mvn test -pl :community-app -Dtest='ImPolicy*Test,PostScanServiceTest,ContentEntityQueryServiceTest,ContentEntityResolutionApplicationServiceTest,SearchPostProjectionApplicationServiceTest,ContentEntityResolverTest,LikeApplicationServiceTest,BlockApplicationServiceTest'
```

Expected: all selected tests pass.

- [ ] **Step 5: Run module test if time allows**

Run from `backend`:

```bash
mvn test -pl :community-app
```

Expected: module tests pass. If this is too slow or blocked by unrelated failures, record the exact failure and the focused verification results.

---

## Self-Review Notes

- Spec coverage: guardrails covered by Task 1 and Task 7; IM package shape covered by Tasks 2 and 3; content API adapter routing covered by Tasks 5 and 6; touched model improvements covered by Task 4.
- Scope control: no external endpoint, topic, schema, or API interface changes are planned.
- Type consistency: IM application service uses existing `UserModerationQueryApi`, `SocialBlockQueryApi`, and `im.common.projection` records; content adapter interfaces remain unchanged.

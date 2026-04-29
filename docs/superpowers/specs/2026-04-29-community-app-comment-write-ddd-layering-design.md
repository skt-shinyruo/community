# Community App Comment Write DDD Layering Design

**Date:** 2026-04-29
**Status:** Draft
**Owner:** Codex

---

## 1. Problem

The content comment write path has not fully converged on strict DDD Tactical Layering.

`content.application.CommentApplicationService` currently acts mostly as an idempotency wrapper. It delegates the real write use cases to `content.application.port.CommentContentPort`, whose implementation is `content.infrastructure.persistence.CommentService`.

`CommentService` is in the infrastructure persistence package, but it owns business orchestration:

- transaction boundaries for comment creation and update
- actor and argument validation
- user moderation checks
- post existence and comment ownership checks
- reply target validation
- social block cross-domain checks
- text escape and sensitive-word filtering
- direct MyBatis mapper writes
- post comment-count mutation
- post score refresh scheduling
- points and growth cross-domain action calls
- comment-created event payload assembly and publication

This violates the repository rule that application owns use-case orchestration and transaction boundaries while infrastructure owns technical persistence implementation.

## 2. Goals

1. Move comment create and update orchestration into `content.application.CommentApplicationService`.
2. Keep HTTP controllers calling same-domain application services only.
3. Introduce a `content.domain.repository.CommentRepository` persistence contract for comment writes and required reads.
4. Keep MyBatis mapper access inside `content.infrastructure.persistence.MyBatisCommentRepository`.
5. Move comment business rules that do not belong in persistence into `content.domain.service.CommentDomainService` or `CommentPolicy`.
6. Preserve current external behavior for comment creation, reply creation, and comment editing.
7. Preserve `Idempotency-Key` behavior for comment creation.
8. Keep foreign-domain collaboration through owner-domain `api.query` / `api.action` contracts.
9. Add focused tests and guardrails so the write path does not drift back into infrastructure orchestration.

## 3. Non-Goals

This spec does not redesign comment read APIs.

This spec does not change the database schema for `comment` or `discuss_post`.

This spec does not change public HTTP request or response shapes.

This spec does not change the rule that reply creation only targets a first-level comment under the same post.

This spec does not replace `ContentEventPublisher` or the existing content contract event payload format.

This spec does not migrate unrelated legacy `PostContentPort` read-side usage unless it is directly required by the comment write path.

## 4. Current Evidence

`CommentApplicationService` currently:

- implements same-domain owner API `CommentActionApi`
- injects `CommentContentPort`
- wraps `addComment(...)` in `IdempotencyGuard.executeRequired("content:create_comment", ...)`
- delegates `updateComment(...)` directly to `CommentContentPort`

`CommentContentPort` currently mixes read and write operations:

- read-style methods: `listByPost`, `listReplies`, `listRecentCommentsByUser`, `getById`, `assertCommentBelongsToPost`, `getLatestPostActivitiesByPostIds`
- write-style methods: `addComment`, `updateComment`

`CommentService` currently implements `CommentContentPort` from `content.infrastructure.persistence` and directly depends on:

- `CommentMapper`
- `PostContentPort`
- `ContentSanitizer`
- `PostScoreQueuePort`
- `ContentEventPublisher`
- `SocialBlockQueryApi`
- `UserModerationGuard`
- `ContentTextCodec`
- `UserPointsAwardActionApi`
- `GrowthTaskProgressActionApi`

The post write path already shows the desired target style:

- `PostPublishingApplicationService` owns `@Transactional`, idempotency, text sanitizing, repository calls, foreign API calls, domain event publishing, and side-effect scheduling.
- `PostRepository` is the domain persistence contract.
- `MyBatisPostRepository` is the infrastructure persistence implementation.
- `PostPublishingDomainService` owns author-edit and delete rules.

## 5. Design Choice

Use the post write path as the model for the comment write path.

`CommentApplicationService` becomes the only same-domain write use-case entry for comments. It owns the transaction and calls domain repositories, domain services, foreign owner APIs, and event publishers. Infrastructure persistence classes only implement repository contracts around mapper calls.

The existing `CommentContentPort` remains a read-side migration surface at first. Its write methods are removed from the application-facing contract once `CommentApplicationService` no longer delegates writes through it.

## 6. Target Package Shape

Add or update these content-domain types:

```text
com.nowcoder.community.content.application
  CommentApplicationService
  command.CreateCommentCommand
  command.UpdateCommentCommand
  result.CommentCreateResult

com.nowcoder.community.content.domain
  model.Comment
  model.CommentDraft
  model.CommentSnapshot
  repository.CommentRepository
  service.CommentDomainService
  event.CommentDomainEventPublisher
  event.CommentCreatedDomainEvent

com.nowcoder.community.content.infrastructure.persistence
  MyBatisCommentRepository
  mapper.CommentMapper

com.nowcoder.community.content.infrastructure.api
  CommentActionApiAdapter

com.nowcoder.community.content.infrastructure.event
  SpringCommentDomainEventPublisher
  CommentDomainEventBridge
```

`CommentService` should either be removed or reduced to a read-only adapter if existing read-side migration requires a temporary compatibility class. It must not own create/update orchestration after this migration.

## 7. Application Flow

### 7.1 Create Comment

`CommentApplicationService.create(...)` must:

1. Build a `CreateCommentCommand` from controller or owner API input.
2. Execute inside `IdempotencyGuard.executeRequired("content:create_comment", actorUserId, idempotencyKey, CommentCreateResult.class, supplier)`.
3. Open the transaction at the application service method.
4. Validate required actor and post identifiers.
5. Call `UserModerationGuard.assertCanSpeak(actorUserId)`.
6. Load the target post through a content-domain read contract that does not expose mapper types.
7. Resolve comment kind:
   - post comment: `entityType` defaults to `EntityTypes.POST`, `entityId` becomes `postId`, `targetId` is cleared, target user is the post author
   - reply comment: `entityType` is `EntityTypes.COMMENT`, `entityId` must be a first-level comment under the same post, target user is the target comment author, `targetId` defaults to target user when absent
8. Use `SocialBlockQueryApi.isEitherBlocked(actorUserId, targetUserId)` when a target user exists.
9. Sanitize content with the same escape-then-filter order currently used by comments and posts.
10. Ask `CommentDomainService` to create a `CommentDraft`.
11. Persist through `CommentRepository.create(draft)`.
12. Increment the post comment count through an owner-domain content repository or existing transitional post port.
13. Call `UserPointsAwardActionApi.awardCommentCreated(...)`.
14. Call `GrowthTaskProgressActionApi.triggerCommentCreated(...)`.
15. Publish a comment-created domain event.
16. Schedule post score refresh after commit through `PostWriteSideEffectScheduler`.
17. Return `CommentCreateResult`.

The current comment-count behavior remains unchanged: both first-level comments and replies increment `discuss_post.comment_count`.

### 7.2 Update Comment

`CommentApplicationService.update(...)` must:

1. Open the transaction at the application service method.
2. Validate required actor, post, and comment identifiers.
3. Call `UserModerationGuard.assertCanSpeak(actorUserId)`.
4. Load the post to avoid cross-post edits and preserve not-found behavior.
5. Load the comment through `CommentRepository.getRequiredSnapshot(commentId)`.
6. Use `CommentDomainService.assertEditableByAuthor(snapshot, actorUserId, postId, now, parentCommentSnapshot)` to enforce:
   - active comment only
   - author-only edit
   - 15 minute edit window
   - direct comment must belong to the supplied post
   - reply comment's parent must be a first-level comment under the supplied post
7. Sanitize content with the same escape-then-filter order.
8. Persist through `CommentRepository.updateContent(commentId, safeContent, now)`.

Comment update does not currently publish an event or refresh post score. This behavior remains unchanged unless a separate spec changes it.

## 8. Domain Rules

`CommentDomainService` owns comment write rules that are currently embedded in `CommentService`:

- create draft requires a non-null actor user id
- supported entity types are `EntityTypes.POST` and `EntityTypes.COMMENT`
- reply `entityId` is required
- reply target must exist, be active, be a first-level post comment, and belong to the supplied post
- edit requires an active comment
- edit is author-only
- edit window is 15 minutes from comment creation time
- edited direct comments must belong to the supplied post
- edited replies must have a parent first-level comment under the supplied post

The domain layer must not call `SocialBlockQueryApi`, `UserModerationGuard`, `ContentSanitizer`, mapper types, or event publishers. Those are application or infrastructure concerns.

## 9. Repository Contract

`CommentRepository` should expose only persistence operations needed by application and domain decisions:

```java
UUID create(CommentDraft draft);

CommentSnapshot getRequiredSnapshot(UUID commentId);

CommentSnapshot getActiveSnapshot(UUID commentId);

Optional<CommentSnapshot> findActiveSnapshot(UUID commentId);

void updateContent(UUID commentId, String content, Date updateTime);

boolean existsFirstLevelCommentInPost(UUID postId, UUID commentId);
```

The exact method names can be adjusted during implementation, but the contract must keep mapper and dataobject details out of application and domain code.

`MyBatisCommentRepository` adapts `CommentMapper` and owns:

- UUID assignment for new comments
- mapping `CommentDraft` to the persisted `Comment` row object
- mapping persisted rows to `CommentSnapshot`
- translating missing or inactive rows into existing content error behavior where repository methods are named `getRequired...`

## 10. Owner API Compatibility

`CommentApplicationService` should stop implementing `CommentActionApi` directly.

Add `content.infrastructure.api.CommentActionApiAdapter`:

```text
foreign caller -> CommentActionApi -> CommentActionApiAdapter -> CommentApplicationService
same-domain controller -> CommentApplicationService
```

This matches the existing `PostPublishingActionApiAdapter` pattern and keeps `api.action` as a foreign-domain contract rather than a same-domain internal entry.

The adapter may return `UUID` as today to preserve the existing `CommentActionApi` surface. The application service may return `CommentCreateResult`, and the adapter can unwrap `commentId`.

## 11. Event And Side-Effect Model

Create comment should publish a domain event instead of hand-building the external contract event inside persistence.

Target flow:

```text
CommentApplicationService
  -> CommentDomainEventPublisher.commentCreated(commentId)
  -> SpringCommentDomainEventPublisher
  -> CommentDomainEventBridge BEFORE_COMMIT
  -> ContentEventPublisher.publishCommentCreated(CommentPayload)
```

The bridge owns stable `CommentPayload` assembly. It may load required comment and post data through repository/read contracts.

Points and growth action APIs remain synchronous application-level collaborations, matching the current post write path. Their current call order before external event publication should be preserved unless tests prove a reason to change it.

Post score refresh remains an after-commit side effect and should be scheduled through `PostWriteSideEffectScheduler`, not through direct `AfterCommitExecutor` calls in persistence.

## 12. Migration Steps

1. Add failing tests for `CommentApplicationService` create and update orchestration.
2. Add `CreateCommentCommand`, `UpdateCommentCommand`, and `CommentCreateResult`.
3. Add `CommentDraft`, `CommentSnapshot`, `CommentRepository`, and `CommentDomainService`.
4. Implement `MyBatisCommentRepository` around `CommentMapper`.
5. Move create-comment orchestration from `CommentService.addComment` into `CommentApplicationService`.
6. Move update-comment orchestration from `CommentService.updateComment` into `CommentApplicationService`.
7. Remove write methods from `CommentContentPort` and update all callers.
8. Add `CommentActionApiAdapter` and remove `CommentApplicationService implements CommentActionApi`.
9. Add comment domain event publisher and bridge, or use a minimal application-level event publisher adapter if the implementation chooses to defer full domain-event parity.
10. Reduce `CommentService` to read-only behavior or retire it if read-side repository/application code can absorb the remaining methods.
11. Move tests from `content.infrastructure.persistence.CommentServiceTest` to `content.application.CommentApplicationServiceTest` where they assert orchestration behavior.
12. Add or expand ArchUnit guardrails for content write orchestration in infrastructure.

## 13. Tests

Add `CommentApplicationServiceTest` coverage for:

- create post comment succeeds and returns a comment id
- create reply succeeds only when target is a first-level comment under the supplied post
- create reply rejects cross-post targets
- create reply rejects replies-to-replies
- create comment rejects social block relationships
- create comment propagates user/social service unavailable behavior before persistence
- create comment runs points, growth, and event publication in the existing order
- create comment schedules post score refresh after commit
- update comment escapes, filters, and persists content
- update comment rejects non-author edits
- update comment rejects edits after 15 minutes
- update comment rejects comments that do not belong to the supplied post

Keep mapper persistence coverage in `CommentMapperPersistenceTest` or add `MyBatisCommentRepositoryTest` if the repository mapping becomes non-trivial.

Run focused verification:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.content.application.CommentApplicationServiceTest,com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapperPersistenceTest test
```

Run architecture verification:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest,ControllerBoundaryArchTest,DomainBoundaryArchTest test
```

## 14. Guardrails

Add an ArchUnit rule that catches this regression class directly.

Recommended rule:

- classes in `..infrastructure.persistence..` must not be annotated with `@Transactional` unless explicitly whitelisted as a technical repository transaction case
- classes in `..infrastructure.persistence..` must not depend on foreign owner-domain `api.query` / `api.action`
- classes in `..infrastructure.persistence..` must not depend on `ContentEventPublisher` for business event publication

The first implementation can scope this rule to `content.infrastructure.persistence` if a repo-wide rule exposes unrelated migration debt.

## 15. Risks And Decisions

### 15.1 Idempotency Transaction Scope

The current post write path places `@Transactional` on the application method that calls `IdempotencyGuard`. The comment write migration should match this local style.

If replayed idempotency responses do not need a business transaction, this can be refined later with an inner transactional method. Do not introduce a different pattern in this migration.

### 15.2 `PostContentPort` Transitional Use

The clean target is for comment writes to depend on content-domain repository interfaces rather than `application.port.PostContentPort`.

If replacing `PostContentPort` would widen the migration too much, this spec allows a temporary use inside `CommentApplicationService` only. It must not remain in infrastructure orchestration, and the plan should call out the follow-up to replace it with a post repository/read contract.

### 15.3 Comment Domain Event Payload

The long-term preferred shape mirrors post domain events. If implementation cost is high, the first migration may keep `ContentEventPublisher.publishCommentCreated(payload)` in `CommentApplicationService`, because application is allowed to publish events. It must not remain in `infrastructure.persistence.CommentService`.

## 16. Acceptance Criteria

The migration is complete when:

1. `CommentApplicationService` owns create and update transaction boundaries.
2. `CommentApplicationService` no longer delegates writes to `CommentContentPort`.
3. `CommentService` no longer contains `addComment` or `updateComment` business orchestration.
4. No content comment write path calls `CommentMapper` outside `infrastructure.persistence`.
5. Same-domain controllers call `CommentApplicationService`, not same-domain owner APIs.
6. `CommentActionApi` is implemented by an infrastructure API adapter.
7. Current comment create and update behavior is preserved by application-layer tests.
8. ArchUnit or focused tests fail if infrastructure persistence becomes the comment write use-case owner again.

---

## Self-Review

- No open gaps remain.
- The scope is limited to comment create/update write paths.
- Read-side migration is explicitly out of scope except where temporary compatibility is needed.
- The design aligns with the approved strict DDD tactical layering spec and the existing post write implementation style.

# Community App Comment Write DDD Layering Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move content comment create/update orchestration from `infrastructure.persistence.CommentService` into `content.application.CommentApplicationService` under strict DDD Tactical Layering.

**Architecture:** `CommentApplicationService` becomes the write use-case owner and calls domain services, domain repositories, foreign owner APIs, domain events, and after-commit schedulers. `MyBatisCommentRepository` becomes the only mapper-backed comment write adapter. `CommentContentPort` remains read-only for the current read path.

**Tech Stack:** Java 17, Spring Boot, MyBatis, JUnit 5, Mockito, AssertJ, ArchUnit, Maven.

---

## Files

- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/UpdateCommentCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/CommentCreateResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentDraft.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentSnapshot.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/CommentDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/CommentCreatedDomainEvent.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/CommentDomainEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SpringCommentDomainEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridge.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/CommentActionApiAdapter.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/port/CommentContentPort.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/CommentService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/CommentServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`

---

### Task 1: Domain Types And Rules

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/UpdateCommentCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/result/CommentCreateResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentDraft.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentSnapshot.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/CommentDomainService.java`

- [ ] **Step 1: Add application command and result records**

Create `CreateCommentCommand.java`:

```java
package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record CreateCommentCommand(
        UUID userId,
        UUID postId,
        Integer entityType,
        UUID entityId,
        UUID targetId,
        String content
) {
}
```

Create `UpdateCommentCommand.java`:

```java
package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record UpdateCommentCommand(
        UUID userId,
        UUID postId,
        UUID commentId,
        String content
) {
}
```

Create `CommentCreateResult.java`:

```java
package com.nowcoder.community.content.application.result;

import java.util.UUID;

public record CommentCreateResult(UUID commentId) {
}
```

- [ ] **Step 2: Add domain write models**

Create `CommentDraft.java`:

```java
package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record CommentDraft(
        UUID userId,
        int entityType,
        UUID entityId,
        UUID targetId,
        String content,
        Date createTime
) {
}
```

Create `CommentSnapshot.java`:

```java
package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record CommentSnapshot(
        UUID id,
        UUID userId,
        int entityType,
        UUID entityId,
        UUID targetId,
        String content,
        int status,
        Date createTime,
        Date updateTime,
        int editCount
) {
    public boolean active() {
        return status == 0;
    }
}
```

- [ ] **Step 3: Add `CommentDomainService`**

Create `CommentDomainService.java`:

```java
package com.nowcoder.community.content.domain.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

@Service
public class CommentDomainService {

    private static final long EDIT_WINDOW_MILLIS = 15L * 60 * 1000;

    public CreateTarget resolveCreateTarget(
            UUID postId,
            Integer rawEntityType,
            UUID rawEntityId,
            UUID rawTargetId,
            UUID postAuthorUserId,
            CommentSnapshot targetComment
    ) {
        int entityType = rawEntityType == null ? EntityTypes.POST : rawEntityType;
        if (entityType == EntityTypes.POST) {
            return new CreateTarget(EntityTypes.POST, postId, null, postAuthorUserId);
        }
        if (entityType != EntityTypes.COMMENT) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        if (rawEntityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "回复评论时 entityId(commentId) 不能为空");
        }
        if (targetComment == null || !targetComment.active()) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
        if (targetComment.entityType() != EntityTypes.POST || !postId.equals(targetComment.entityId())) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
        UUID targetUserId = targetComment.userId();
        UUID targetId = rawTargetId == null ? targetUserId : rawTargetId;
        return new CreateTarget(EntityTypes.COMMENT, rawEntityId, targetId, targetUserId);
    }

    public CommentDraft createDraft(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID targetId,
            String content,
            Date createTime
    ) {
        if (actorUserId == null || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/entityId 非法");
        }
        if (entityType != EntityTypes.POST && entityType != EntityTypes.COMMENT) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return new CommentDraft(actorUserId, entityType, entityId, targetId, content, createTime == null ? new Date() : createTime);
    }

    public void assertEditableByAuthor(
            CommentSnapshot comment,
            UUID actorUserId,
            UUID postId,
            Date now,
            CommentSnapshot parentComment
    ) {
        if (actorUserId == null || postId == null || comment == null || comment.id() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId/commentId 非法");
        }
        if (!comment.active()) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        if (!actorUserId.equals(comment.userId())) {
            throw new BusinessException(FORBIDDEN, "只能编辑自己的评论");
        }
        if (comment.createTime() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "评论时间非法");
        }
        Date effectiveNow = now == null ? new Date() : now;
        if (effectiveNow.getTime() - comment.createTime().getTime() > EDIT_WINDOW_MILLIS) {
            throw new BusinessException(FORBIDDEN, "已超过可编辑时间（15min）");
        }
        if (comment.entityType() == EntityTypes.POST) {
            if (!postId.equals(comment.entityId())) {
                throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
            }
            return;
        }
        if (comment.entityType() == EntityTypes.COMMENT) {
            if (parentComment == null || parentComment.entityType() != EntityTypes.POST || !postId.equals(parentComment.entityId())) {
                throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
            }
            return;
        }
        throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
    }

    public record CreateTarget(int entityType, UUID entityId, UUID targetId, UUID targetUserId) {
    }
}
```

- [ ] **Step 4: Compile the new domain files**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -DskipTests compile
```

Expected: compilation succeeds for the new standalone records and domain service.

- [ ] **Step 5: Commit domain shell**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/command/UpdateCommentCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/result/CommentCreateResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentDraft.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentSnapshot.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/CommentDomainService.java
git commit -m "feat: add comment write domain model"
```

---

### Task 2: Comment Repository

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentRepository.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/CommentMapperPersistenceTest.java`

- [ ] **Step 1: Add the domain repository contract**

Create `CommentRepository.java`:

```java
package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    UUID create(CommentDraft draft);

    CommentSnapshot getRequiredSnapshot(UUID commentId);

    Optional<CommentSnapshot> findSnapshot(UUID commentId);

    Optional<CommentSnapshot> findActiveSnapshot(UUID commentId);

    void updateContent(UUID commentId, String content, Date updateTime);
}
```

- [ ] **Step 2: Implement `MyBatisCommentRepository`**

Create `MyBatisCommentRepository.java`:

```java
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

@Repository
public class MyBatisCommentRepository implements CommentRepository {

    private final CommentMapper commentMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisCommentRepository(CommentMapper commentMapper) {
        this(commentMapper, new UuidV7Generator());
    }

    MyBatisCommentRepository(CommentMapper commentMapper, UuidV7Generator idGenerator) {
        this.commentMapper = commentMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public UUID create(CommentDraft draft) {
        Comment comment = new Comment();
        comment.setId(idGenerator.next());
        comment.setUserId(draft.userId());
        comment.setEntityType(draft.entityType());
        comment.setEntityId(draft.entityId());
        comment.setTargetId(draft.targetId());
        comment.setContent(draft.content());
        comment.setStatus(0);
        comment.setCreateTime(draft.createTime());
        commentMapper.insertComment(comment);
        return comment.getId();
    }

    @Override
    public CommentSnapshot getRequiredSnapshot(UUID commentId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getStatus() != 0) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        return toSnapshot(comment);
    }

    @Override
    public Optional<CommentSnapshot> findSnapshot(UUID commentId) {
        if (commentId == null) {
            return Optional.empty();
        }
        Comment comment = commentMapper.selectCommentById(commentId);
        return comment == null ? Optional.empty() : Optional.of(toSnapshot(comment));
    }

    @Override
    public Optional<CommentSnapshot> findActiveSnapshot(UUID commentId) {
        if (commentId == null) {
            return Optional.empty();
        }
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getStatus() != 0) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(comment));
    }

    @Override
    public void updateContent(UUID commentId, String content, Date updateTime) {
        int updated = commentMapper.updateCommentContent(commentId, content, updateTime);
        if (updated <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "更新评论失败");
        }
    }

    private static CommentSnapshot toSnapshot(Comment comment) {
        return new CommentSnapshot(
                comment.getId(),
                comment.getUserId(),
                comment.getEntityType(),
                comment.getEntityId(),
                comment.getTargetId(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreateTime(),
                comment.getUpdateTime(),
                comment.getEditCount()
        );
    }
}
```

- [ ] **Step 3: Run mapper persistence coverage**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapperPersistenceTest test
```

Expected: PASS. Existing mapper tests prove insert/update SQL still maps application-assigned UUID rows correctly.

- [ ] **Step 4: Commit repository adapter**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentRepository.java
git commit -m "feat: add comment repository adapter"
```

---

### Task 3: Comment Domain Event Path

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/CommentCreatedDomainEvent.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/CommentDomainEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SpringCommentDomainEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridge.java`

- [ ] **Step 1: Add the domain event and publisher contract**

Create `CommentCreatedDomainEvent.java`:

```java
package com.nowcoder.community.content.domain.event;

import java.time.Instant;
import java.util.UUID;

public record CommentCreatedDomainEvent(
        UUID commentId,
        UUID postId,
        UUID userId,
        int entityType,
        UUID entityId,
        UUID targetUserId,
        String content,
        Instant createTime
) {
}
```

Create `CommentDomainEventPublisher.java`:

```java
package com.nowcoder.community.content.domain.event;

public interface CommentDomainEventPublisher {

    void commentCreated(CommentCreatedDomainEvent event);
}
```

- [ ] **Step 2: Add the Spring publisher adapter**

Create `SpringCommentDomainEventPublisher.java`:

```java
package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SpringCommentDomainEventPublisher implements CommentDomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringCommentDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void commentCreated(CommentCreatedDomainEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("发布领域事件失败：当前不在事务中（CommentCreated, commentId=" + event.commentId() + "）");
        }
        publisher.publishEvent(event);
    }
}
```

- [ ] **Step 3: Add the content contract event bridge**

Create `CommentDomainEventBridge.java`:

```java
package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CommentDomainEventBridge {

    private final ContentEventPublisher eventPublisher;

    public CommentDomainEventBridge(ContentEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onCommentCreated(CommentCreatedDomainEvent event) {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(event.commentId());
        payload.setPostId(event.postId());
        payload.setUserId(event.userId());
        payload.setEntityType(event.entityType());
        payload.setEntityId(event.entityId());
        payload.setTargetUserId(event.targetUserId());
        payload.setContent(event.content());
        payload.setCreateTime(event.createTime());
        eventPublisher.publishCommentCreated(payload);
    }
}
```

- [ ] **Step 4: Compile event wiring**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -DskipTests compile
```

Expected: PASS.

- [ ] **Step 5: Commit event path**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/CommentCreatedDomainEvent.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/CommentDomainEventPublisher.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SpringCommentDomainEventPublisher.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridge.java
git commit -m "feat: add comment domain event bridge"
```

---

### Task 4: Application Write Orchestration

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`

- [ ] **Step 1: Write failing application-layer tests**

Create `CommentApplicationServiceTest.java`:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.content.application.port.ContentSanitizer;
import com.nowcoder.community.content.application.port.PostContentPort;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentApplicationServiceTest {

    private ContentSanitizer sensitiveFilter;
    private IdempotencyGuard idempotencyGuard;
    private UserModerationGuard moderationGuard;
    private CommentDomainService domainService;
    private CommentRepository commentRepository;
    private PostContentPort postContentPort;
    private SocialBlockQueryApi blockQueryApi;
    private UserPointsAwardActionApi pointsAwardService;
    private GrowthTaskProgressActionApi taskProgressTriggerService;
    private CommentDomainEventPublisher domainEventPublisher;
    private PostWriteSideEffectScheduler postWriteSideEffectScheduler;
    private CommentApplicationService service;

    @BeforeEach
    void setUp() {
        sensitiveFilter = mock(ContentSanitizer.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        moderationGuard = mock(UserModerationGuard.class);
        domainService = new CommentDomainService();
        commentRepository = mock(CommentRepository.class);
        postContentPort = mock(PostContentPort.class);
        blockQueryApi = mock(SocialBlockQueryApi.class);
        pointsAwardService = mock(UserPointsAwardActionApi.class);
        taskProgressTriggerService = mock(GrowthTaskProgressActionApi.class);
        domainEventPublisher = mock(CommentDomainEventPublisher.class);
        postWriteSideEffectScheduler = mock(PostWriteSideEffectScheduler.class);
        service = new CommentApplicationService(
                sensitiveFilter,
                idempotencyGuard,
                new ContentTextCodec(new ContentRenderProperties()),
                moderationGuard,
                domainService,
                commentRepository,
                postContentPort,
                blockQueryApi,
                pointsAwardService,
                taskProgressTriggerService,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );
    }

    @Test
    void createPostCommentShouldOwnWriteOrchestration() {
        UUID actorUserId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        UUID commentId = uuid(200);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postAuthorId);

        when(idempotencyGuard.executeRequired(eq("content:create_comment"), eq(actorUserId), anyString(), eq(CommentCreateResult.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<CommentCreateResult>>getArgument(4).get());
        when(postContentPort.getById(postId)).thenReturn(post);
        when(blockQueryApi.isEitherBlocked(actorUserId, postAuthorId)).thenReturn(false);
        when(sensitiveFilter.filter("hello &amp; world")).thenReturn("clean");
        when(commentRepository.create(any(CommentDraft.class))).thenReturn(commentId);

        CommentCreateResult result = service.create(actorUserId, "idem-1", postId, EntityTypes.POST, null, null, "hello & world");

        assertThat(result.commentId()).isEqualTo(commentId);
        verify(idempotencyGuard).executeRequired(eq("content:create_comment"), eq(actorUserId), eq("idem-1"), eq(CommentCreateResult.class), any());
        var inOrder = inOrder(
                moderationGuard,
                postContentPort,
                blockQueryApi,
                commentRepository,
                pointsAwardService,
                taskProgressTriggerService,
                domainEventPublisher,
                postWriteSideEffectScheduler
        );
        inOrder.verify(moderationGuard).assertCanSpeak(actorUserId);
        inOrder.verify(postContentPort).getById(postId);
        inOrder.verify(blockQueryApi).isEitherBlocked(actorUserId, postAuthorId);
        inOrder.verify(commentRepository).create(any(CommentDraft.class));
        inOrder.verify(postContentPort).incrementCommentCount(postId, 1);
        inOrder.verify(pointsAwardService).awardCommentCreated(any(CommentPayload.class));
        inOrder.verify(taskProgressTriggerService).triggerCommentCreated(any(CommentPayload.class));
        inOrder.verify(domainEventPublisher).commentCreated(any(CommentCreatedDomainEvent.class));
        inOrder.verify(postWriteSideEffectScheduler).schedulePostScoreRefresh(postId);
    }

    @Test
    void createReplyShouldRejectCrossPostTargetBeforePersistence() {
        UUID actorUserId = uuid(1);
        UUID postId = uuid(100);
        UUID targetCommentId = uuid(200);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(uuid(2));

        when(idempotencyGuard.executeRequired(eq("content:create_comment"), eq(actorUserId), anyString(), eq(CommentCreateResult.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<CommentCreateResult>>getArgument(4).get());
        when(postContentPort.getById(postId)).thenReturn(post);
        when(commentRepository.findActiveSnapshot(targetCommentId)).thenReturn(Optional.of(new CommentSnapshot(
                targetCommentId,
                uuid(3),
                EntityTypes.POST,
                uuid(999),
                null,
                "target",
                0,
                new Date(),
                null,
                0
        )));

        assertThatThrownBy(() -> service.create(actorUserId, "idem-1", postId, EntityTypes.COMMENT, targetCommentId, null, "hi"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));

        verify(commentRepository, never()).create(any(CommentDraft.class));
        verify(domainEventPublisher, never()).commentCreated(any());
    }

    @Test
    void createCommentShouldRejectEitherBlockedBeforePersistence() {
        UUID actorUserId = uuid(1);
        UUID postId = uuid(100);
        UUID postAuthorId = uuid(2);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(postAuthorId);

        when(idempotencyGuard.executeRequired(eq("content:create_comment"), eq(actorUserId), anyString(), eq(CommentCreateResult.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<CommentCreateResult>>getArgument(4).get());
        when(postContentPort.getById(postId)).thenReturn(post);
        when(blockQueryApi.isEitherBlocked(actorUserId, postAuthorId)).thenReturn(true);

        assertThatThrownBy(() -> service.create(actorUserId, "idem-1", postId, EntityTypes.POST, null, null, "hi"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        verify(commentRepository, never()).create(any(CommentDraft.class));
        verify(domainEventPublisher, never()).commentCreated(any());
    }

    @Test
    void updateCommentShouldSanitizeAndPersistThroughRepository() {
        UUID actorUserId = uuid(1);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        when(postContentPort.getById(postId)).thenReturn(post);
        when(commentRepository.getRequiredSnapshot(commentId)).thenReturn(new CommentSnapshot(
                commentId,
                actorUserId,
                EntityTypes.POST,
                postId,
                null,
                "old",
                0,
                Date.from(Instant.now().minusSeconds(60)),
                null,
                0
        ));
        when(sensitiveFilter.filter("hello &amp; world")).thenReturn("clean");

        service.updateComment(actorUserId, postId, commentId, "hello & world");

        verify(moderationGuard).assertCanSpeak(actorUserId);
        verify(postContentPort).getById(postId);
        verify(commentRepository).updateContent(eq(commentId), eq("clean"), any(Date.class));
    }
}
```

- [ ] **Step 2: Run the new tests and verify they fail**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=com.nowcoder.community.content.application.CommentApplicationServiceTest test
```

Expected: FAIL because `CommentApplicationService` does not have the constructor or `create(...)` method used by the test.

- [ ] **Step 3: Replace `CommentApplicationService` with application-owned orchestration**

Replace `CommentApplicationService.java` with:

```java
package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.content.application.command.CreateCommentCommand;
import com.nowcoder.community.content.application.command.UpdateCommentCommand;
import com.nowcoder.community.content.application.port.ContentSanitizer;
import com.nowcoder.community.content.application.port.PostContentPort;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class CommentApplicationService {

    private static final String CREATE_COMMENT_IDEMPOTENCY_SCOPE = "content:create_comment";

    private final ContentSanitizer sensitiveFilter;
    private final IdempotencyGuard idempotencyGuard;
    private final ContentTextCodec textCodec;
    private final UserModerationGuard moderationGuard;
    private final CommentDomainService domainService;
    private final CommentRepository commentRepository;
    private final PostContentPort postContentPort;
    private final SocialBlockQueryApi blockQueryApi;
    private final UserPointsAwardActionApi pointsAwardService;
    private final GrowthTaskProgressActionApi taskProgressTriggerService;
    private final CommentDomainEventPublisher domainEventPublisher;
    private final PostWriteSideEffectScheduler postWriteSideEffectScheduler;

    public CommentApplicationService(
            ContentSanitizer sensitiveFilter,
            IdempotencyGuard idempotencyGuard,
            ContentTextCodec textCodec,
            UserModerationGuard moderationGuard,
            CommentDomainService domainService,
            CommentRepository commentRepository,
            PostContentPort postContentPort,
            SocialBlockQueryApi blockQueryApi,
            UserPointsAwardActionApi pointsAwardService,
            GrowthTaskProgressActionApi taskProgressTriggerService,
            CommentDomainEventPublisher domainEventPublisher,
            PostWriteSideEffectScheduler postWriteSideEffectScheduler
    ) {
        this.sensitiveFilter = sensitiveFilter;
        this.idempotencyGuard = idempotencyGuard;
        this.textCodec = textCodec;
        this.moderationGuard = moderationGuard;
        this.domainService = domainService;
        this.commentRepository = commentRepository;
        this.postContentPort = postContentPort;
        this.blockQueryApi = blockQueryApi;
        this.pointsAwardService = pointsAwardService;
        this.taskProgressTriggerService = taskProgressTriggerService;
        this.domainEventPublisher = domainEventPublisher;
        this.postWriteSideEffectScheduler = postWriteSideEffectScheduler;
    }

    @Transactional
    public CommentCreateResult create(UUID userId, String idempotencyKey, UUID postId, Integer entityType, UUID entityId, UUID targetId, String content) {
        return create(idempotencyKey, new CreateCommentCommand(userId, postId, entityType, entityId, targetId, content));
    }

    @Transactional
    public CommentCreateResult create(String idempotencyKey, CreateCommentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        UUID userId = command.userId();
        return idempotencyGuard.executeRequired(
                CREATE_COMMENT_IDEMPOTENCY_SCOPE,
                userId,
                idempotencyKey,
                CommentCreateResult.class,
                () -> createOnce(command)
        );
    }

    @Transactional
    public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
        update(new UpdateCommentCommand(userId, postId, commentId, content));
    }

    @Transactional
    public void update(UpdateCommentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        if (command.userId() == null || command.postId() == null || command.commentId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId/commentId 非法");
        }

        moderationGuard.assertCanSpeak(command.userId());
        postContentPort.getById(command.postId());

        CommentSnapshot existing = commentRepository.getRequiredSnapshot(command.commentId());
        CommentSnapshot parent = existing.entityType() == EntityTypes.COMMENT
                ? commentRepository.findSnapshot(existing.entityId()).orElse(null)
                : null;
        Date now = new Date();
        domainService.assertEditableByAuthor(existing, command.userId(), command.postId(), now, parent);
        commentRepository.updateContent(command.commentId(), sanitize(command.content()), now);
    }

    private CommentCreateResult createOnce(CreateCommentCommand command) {
        if (command.userId() == null || command.postId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }

        moderationGuard.assertCanSpeak(command.userId());
        DiscussPost post = postContentPort.getById(command.postId());

        CommentSnapshot targetComment = command.entityType() != null && command.entityType() == EntityTypes.COMMENT
                ? commentRepository.findActiveSnapshot(command.entityId()).orElse(null)
                : null;
        CommentDomainService.CreateTarget target = domainService.resolveCreateTarget(
                command.postId(),
                command.entityType(),
                command.entityId(),
                command.targetId(),
                post.getUserId(),
                targetComment
        );

        if (target.targetUserId() != null && blockQueryApi.isEitherBlocked(command.userId(), target.targetUserId())) {
            throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
        }

        Date createTime = new Date();
        String safeContent = sanitize(command.content());
        CommentDraft draft = domainService.createDraft(command.userId(), target.entityType(), target.entityId(), target.targetId(), safeContent, createTime);
        UUID commentId = commentRepository.create(draft);

        postContentPort.incrementCommentCount(command.postId(), 1);

        CommentCreatedDomainEvent event = new CommentCreatedDomainEvent(
                commentId,
                command.postId(),
                command.userId(),
                target.entityType(),
                target.entityId(),
                target.targetUserId(),
                textCodec.decodeOnRead(safeContent),
                createTime.toInstant()
        );
        CommentPayload payload = toPayload(event);
        pointsAwardService.awardCommentCreated(payload);
        taskProgressTriggerService.triggerCommentCreated(payload);
        domainEventPublisher.commentCreated(event);
        postWriteSideEffectScheduler.schedulePostScoreRefresh(command.postId());

        return new CommentCreateResult(commentId);
    }

    private String sanitize(String value) {
        String trimmed = value == null ? "" : value.trim();
        return sensitiveFilter.filter(textCodec.escapeOnWrite(trimmed));
    }

    private CommentPayload toPayload(CommentCreatedDomainEvent event) {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(event.commentId());
        payload.setPostId(event.postId());
        payload.setUserId(event.userId());
        payload.setEntityType(event.entityType());
        payload.setEntityId(event.entityId());
        payload.setTargetUserId(event.targetUserId());
        payload.setContent(event.content());
        payload.setCreateTime(event.createTime());
        return payload;
    }
}
```

- [ ] **Step 4: Run application tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=com.nowcoder.community.content.application.CommentApplicationServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit application orchestration**

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java
git commit -m "feat: move comment writes to application service"
```

---

### Task 5: Owner API Adapter And Controller Mapping

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/CommentActionApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`

- [ ] **Step 1: Add owner API adapter**

Create `CommentActionApiAdapter.java`:

```java
package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.action.CommentActionApi;
import com.nowcoder.community.content.application.CommentApplicationService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CommentActionApiAdapter implements CommentActionApi {

    private final CommentApplicationService commentApplicationService;

    public CommentActionApiAdapter(CommentApplicationService commentApplicationService) {
        this.commentApplicationService = commentApplicationService;
    }

    @Override
    public UUID addComment(UUID userId, String idempotencyKey, UUID postId, Integer entityType, UUID entityId, UUID targetId, String content) {
        return commentApplicationService.create(userId, idempotencyKey, postId, entityType, entityId, targetId, content).commentId();
    }

    @Override
    public void updateComment(UUID userId, UUID postId, UUID commentId, String content) {
        commentApplicationService.updateComment(userId, postId, commentId, content);
    }
}
```

- [ ] **Step 2: Update controller response mapping**

In `PostController.addComment(...)`, replace the return statement with:

```java
        return Result.ok(commentApplicationService.create(
                userId,
                idempotencyKey,
                postId,
                request.getEntityType(),
                request.getEntityId(),
                request.getTargetId(),
                request.getContent()
        ).commentId());
```

`PostController.updateComment(...)` keeps calling `commentApplicationService.updateComment(...)`.

- [ ] **Step 3: Compile API adapter and controller**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -DskipTests compile
```

Expected: PASS. There is exactly one Spring bean implementing `CommentActionApi`: `CommentActionApiAdapter`.

- [ ] **Step 4: Commit adapter and controller mapping**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/CommentActionApiAdapter.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java
git commit -m "feat: add comment action api adapter"
```

---

### Task 6: Retire Infrastructure Write Orchestration

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/port/CommentContentPort.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/CommentService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/CommentServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java`

- [ ] **Step 1: Make `CommentContentPort` read-only**

Replace `CommentContentPort.java` with:

```java
package com.nowcoder.community.content.application.port;

import com.nowcoder.community.content.domain.model.Comment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CommentContentPort {

    int ENTITY_TYPE_POST = 1;
    int ENTITY_TYPE_COMMENT = 2;

    List<Comment> listByPost(UUID postId, int page, int size);

    List<Comment> listReplies(UUID commentId, int page, int size);

    List<Comment> listRecentCommentsByUser(UUID userId, int page, int size);

    Comment getById(UUID commentId);

    void assertCommentBelongsToPost(UUID postId, UUID commentId);

    Map<UUID, Comment> getLatestPostActivitiesByPostIds(List<UUID> postIds);
}
```

- [ ] **Step 2: Reduce `CommentService` to read behavior**

Replace `CommentService.java` with:

```java
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.port.CommentContentPort;
import com.nowcoder.community.content.application.port.PostContentPort;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;

@Service
public class CommentService implements CommentContentPort {

    public static final int ENTITY_TYPE_POST = EntityTypes.POST;
    public static final int ENTITY_TYPE_COMMENT = EntityTypes.COMMENT;

    private final CommentMapper commentMapper;
    private final PostContentPort postContentPort;

    public CommentService(CommentMapper commentMapper, PostContentPort postContentPort) {
        this.commentMapper = commentMapper;
        this.postContentPort = postContentPort;
    }

    @Override
    public List<Comment> listByPost(UUID postId, int page, int size) {
        if (postId == null) {
            return List.of();
        }
        postContentPort.getById(postId);
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectCommentsByEntity(ENTITY_TYPE_POST, postId, Pagination.safeOffset(p, s), s);
    }

    @Override
    public List<Comment> listReplies(UUID commentId, int page, int size) {
        if (commentId == null) {
            return List.of();
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectCommentsByEntity(ENTITY_TYPE_COMMENT, commentId, Pagination.safeOffset(p, s), s);
    }

    @Override
    public List<Comment> listRecentCommentsByUser(UUID userId, int page, int size) {
        if (userId == null) {
            return List.of();
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectRecentCommentsByUser(userId, Pagination.safeOffset(p, s), s);
    }

    @Override
    public Comment getById(UUID commentId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null || comment.getStatus() != 0) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        return comment;
    }

    @Override
    public void assertCommentBelongsToPost(UUID postId, UUID commentId) {
        if (postId == null || commentId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "postId/commentId 非法");
        }
        postContentPort.getById(postId);
        int count = commentMapper.existsPostComment(postId, commentId);
        if (count <= 0) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
    }

    @Override
    public Map<UUID, Comment> getLatestPostActivitiesByPostIds(List<UUID> postIds) {
        Map<UUID, Comment> map = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return map;
        }

        List<Comment> rows = commentMapper.selectLatestPostActivitiesByPostIds(postIds);
        if (rows == null || rows.isEmpty()) {
            return map;
        }

        for (Comment comment : rows) {
            if (comment == null || comment.getEntityId() == null) {
                continue;
            }
            map.putIfAbsent(comment.getEntityId(), comment);
        }
        return map;
    }
}
```

- [ ] **Step 3: Replace `CommentServiceTest` with read-only coverage**

Replace `CommentServiceTest.java` with:

```java
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.port.PostContentPort;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceTest {

    @Test
    void listRecentCommentsByUserShouldDelegateWithSafePagination() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentPort postContentPort = mock(PostContentPort.class);
        CommentService service = new CommentService(commentMapper, postContentPort);
        UUID userId = uuid(7);
        Comment comment = new Comment();
        comment.setId(uuid(11));
        when(commentMapper.selectRecentCommentsByUser(userId, 5, 5)).thenReturn(List.of(comment));

        List<Comment> rows = service.listRecentCommentsByUser(userId, 1, 5);

        assertThat(rows).hasSize(1);
        verify(commentMapper).selectRecentCommentsByUser(userId, 5, 5);
    }

    @Test
    void listByPostShouldRejectDeletedPostBeforeLoadingComments() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        PostContentPort postContentPort = mock(PostContentPort.class);
        CommentService service = new CommentService(commentMapper, postContentPort);
        UUID postId = uuid(101);
        when(postContentPort.getById(postId)).thenThrow(new BusinessException(POST_NOT_FOUND));

        assertThatThrownBy(() -> service.listByPost(postId, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(POST_NOT_FOUND));

        verify(commentMapper, never()).selectCommentsByEntity(eq(CommentService.ENTITY_TYPE_POST), eq(postId), anyInt(), anyInt());
    }
}
```

- [ ] **Step 4: Update pagination overflow test constructor**

Update `PaginationOffsetOverflowTest.commentServiceShouldNotPassNegativeOffsetWhenPageIsHuge()` to use the new read-only constructor:

```java
        CommentService service = new CommentService(
                commentMapper,
                mock(PostService.class)
        );
```

Remove now-unused imports from `PaginationOffsetOverflowTest`:

```java
import com.nowcoder.community.content.application.ContentTextCodec;
import com.nowcoder.community.content.application.UserModerationGuard;
import com.nowcoder.community.content.application.port.ContentSanitizer;
import com.nowcoder.community.content.application.port.PostScoreQueuePort;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.infrastructure.event.ContentEventPublisher;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
```

- [ ] **Step 5: Run read and application tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=com.nowcoder.community.content.application.CommentApplicationServiceTest,com.nowcoder.community.content.infrastructure.persistence.CommentServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Compile all app code**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -DskipTests compile
```

Expected: PASS with no references to removed `CommentContentPort.addComment` or `CommentContentPort.updateComment`.

- [ ] **Step 7: Commit infrastructure cleanup**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/port/CommentContentPort.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/CommentService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/CommentServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java
git commit -m "refactor: make comment persistence read-only"
```

---

### Task 7: Architecture Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`

- [ ] **Step 1: Add infrastructure-persistence guardrails**

In `DddLayeringArchTest.java`, add imports:

```java
import org.springframework.transaction.annotation.Transactional;
```

Change the static import block from:

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
```

to:

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
```

Add these rules after `application_must_not_depend_on_transport_or_infrastructure`:

```java
    @ArchTest
    static final ArchRule content_infrastructure_persistence_must_not_own_transactions =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().beAnnotatedWith(Transactional.class)
                    .because("content write transaction boundaries belong in application services")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule content_infrastructure_persistence_must_not_call_foreign_owner_apis =
            noClasses()
                    .that().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().dependOnClassesThat().resideInAnyPackage("..api.query..", "..api.action..")
                    .because("foreign synchronous collaboration belongs in application services")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule content_infrastructure_persistence_must_not_publish_content_events =
            noClasses()
                    .that().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().dependOnClassesThat().haveSimpleName("ContentEventPublisher")
                    .because("business event publication belongs in application or event adapters")
                    .allowEmptyShould(true);
```

- [ ] **Step 2: Run architecture tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -Dtest=DddLayeringArchTest,ControllerBoundaryArchTest,DomainBoundaryArchTest test
```

Expected: PASS. The new rules would fail if `CommentService` still had `@Transactional`, foreign API dependencies, or `ContentEventPublisher`.

- [ ] **Step 3: Commit guardrails**

```bash
git add backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java
git commit -m "test: guard content persistence boundaries"
```

---

### Task 8: Final Verification

**Files:**
- Verify all files touched in prior tasks.

- [ ] **Step 1: Search for retired write delegation**

Run:

```bash
cd /home/feng/code/project/community
rg "commentContentPort\\.addComment|commentContentPort\\.updateComment|implements CommentActionApi|publishCommentCreated|awardCommentCreated|triggerCommentCreated|@Transactional" backend/community-app/src/main/java/com/nowcoder/community/content -n
```

Expected:

- no `commentContentPort.addComment`
- no `commentContentPort.updateComment`
- `implements CommentActionApi` appears only in `content.infrastructure.api.CommentActionApiAdapter`
- `publishCommentCreated` appears in `content.infrastructure.event.CommentDomainEventBridge` and `ContentEventPublisher` implementations/interfaces
- `awardCommentCreated` and `triggerCommentCreated` appear in `content.application.CommentApplicationService`
- comment write `@Transactional` appears in `content.application.CommentApplicationService`, not `content.infrastructure.persistence.CommentService`

- [ ] **Step 2: Run focused functional and architecture coverage**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.content.application.CommentApplicationServiceTest,com.nowcoder.community.content.infrastructure.persistence.CommentServiceTest,com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapperPersistenceTest,DddLayeringArchTest,ControllerBoundaryArchTest,DomainBoundaryArchTest test
```

Expected: PASS.

- [ ] **Step 3: Run full community-app suite**

Run:

```bash
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
```

Expected: PASS with `BUILD SUCCESS`.

- [ ] **Step 4: Commit any verification fixes**

If Step 2 or Step 3 required small corrections, commit only those corrections:

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreateCommentCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/command/UpdateCommentCommand.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/result/CommentCreateResult.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/port/CommentContentPort.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/CommentCreatedDomainEvent.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/CommentDomainEventPublisher.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentDraft.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/model/CommentSnapshot.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/CommentRepository.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/CommentDomainService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/CommentActionApiAdapter.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/CommentDomainEventBridge.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/SpringCommentDomainEventPublisher.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/CommentService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisCommentRepository.java \
  backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/CommentServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/infra/pagination/PaginationOffsetOverflowTest.java
git commit -m "fix: complete comment write DDD migration"
```

- [ ] **Step 5: Final status check**

Run:

```bash
cd /home/feng/code/project/community
git status --short
```

Expected: clean working tree.

---

## Self-Review

- Spec coverage: tasks cover application orchestration, repository adapter, domain service, owner API adapter, domain event bridge, `CommentContentPort` cleanup, tests, and ArchUnit guardrails.
- Scope: comment create/update write paths only; comment reads remain on the existing read port.
- Type consistency: `CommentCreateResult`, `CommentDraft`, `CommentSnapshot`, `CommentRepository`, `CommentDomainService.CreateTarget`, and `CommentCreatedDomainEvent` signatures match across tasks.
- Verification: focused unit, mapper, architecture, and full `community-app` Maven commands are included.

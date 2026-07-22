package com.nowcoder.community.content.application;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.content.domain.event.CommentDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDomainEventPublisher;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentThreadDeletion;
import com.nowcoder.community.content.domain.repository.CommentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.CommentDomainService;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommentDeletionCardinalityContractTest {

    private static final UUID ROOT_ID = uuid(8401);
    private static final UUID FIRST_REPLY_ID = uuid(8402);
    private static final UUID SECOND_REPLY_ID = uuid(8403);
    private static final UUID AUTHOR_ID = uuid(8404);
    private static final UUID POST_ID = uuid(8405);

    private CommentRepository repository;
    private PostContentRepository postRepository;
    private PostCounterCache counterCache;
    private CommentPageCache pageCache;
    private CommentDomainEventPublisher eventPublisher;
    private CommentApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(CommentRepository.class);
        postRepository = mock(PostContentRepository.class);
        counterCache = mock(PostCounterCache.class);
        pageCache = mock(CommentPageCache.class);
        eventPublisher = mock(CommentDomainEventPublisher.class);
        service = new CommentApplicationService(
                mock(ContentSanitizer.class),
                mock(IdempotencyGuard.class),
                mock(ContentTextCodec.class),
                mock(UserModerationGuard.class),
                new CommentDomainService(),
                repository,
                postRepository,
                new CommentCacheAfterCommit(counterCache, pageCache),
                mock(SocialBlockQueryApi.class),
                eventPublisher
        );
    }

    @Test
    void persistedDeletionResultMustProduceExactlyOneEventPerFirstDeletedRowInOrder() {
        CommentSnapshot root = root(ROOT_ID, AUTHOR_ID);
        CommentSnapshot firstReply = reply(FIRST_REPLY_ID, uuid(8411), ROOT_ID);
        CommentSnapshot secondReply = reply(SECOND_REPLY_ID, uuid(8412), ROOT_ID);
        when(repository.getRequiredSnapshot(ROOT_ID)).thenReturn(root);
        when(repository.getActiveThreadSnapshots(ROOT_ID)).thenReturn(List.of(root, firstReply, secondReply));
        when(repository.apply(any(CommentThreadDeletion.class)))
                .thenReturn(CommentDeletionResult.applied(List.of(root, firstReply, secondReply)));

        service.deleteByAuthor(AUTHOR_ID, POST_ID, ROOT_ID);

        ArgumentCaptor<CommentDeletedDomainEvent> events = ArgumentCaptor.forClass(CommentDeletedDomainEvent.class);
        verify(eventPublisher, times(3)).commentDeleted(events.capture());
        assertThat(events.getAllValues()).extracting(CommentDeletedDomainEvent::commentId)
                .containsExactly(ROOT_ID, FIRST_REPLY_ID, SECOND_REPLY_ID);
        assertThat(events.getAllValues()).extracting(CommentDeletedDomainEvent::entityType)
                .containsExactly(POST, COMMENT, COMMENT);
        verify(postRepository).incrementCommentCount(POST_ID, -3);
        verify(counterCache).incrementCommentCount(POST_ID, -3L);
    }

    @Test
    void stalePersistenceConflictMustPublishNoEventsAndApplyNoCounters() {
        when(repository.getRequiredSnapshot(ROOT_ID)).thenReturn(root(ROOT_ID, AUTHOR_ID));
        when(repository.getActiveThreadSnapshots(ROOT_ID)).thenReturn(List.of(root(ROOT_ID, AUTHOR_ID)));
        when(repository.apply(any(CommentThreadDeletion.class))).thenReturn(CommentDeletionResult.stale());

        assertThatThrownBy(() -> service.deleteByAuthor(AUTHOR_ID, POST_ID, ROOT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("comment transition stale");

        verifyNoInteractions(eventPublisher);
        verify(postRepository, never()).incrementCommentCount(any(UUID.class), anyInt());
        verify(counterCache, never()).incrementCommentCount(any(UUID.class), anyLong());
        verify(pageCache, never()).evictPost(any(UUID.class));
    }

    @Test
    void duplicateNoOpMustProduceZeroEventsAndZeroCounterChanges() {
        CommentSnapshot root = root(ROOT_ID, AUTHOR_ID);
        when(repository.getRequiredSnapshot(ROOT_ID)).thenReturn(root);
        when(repository.getActiveThreadSnapshots(ROOT_ID)).thenReturn(List.of(root));
        when(repository.apply(any(CommentThreadDeletion.class))).thenReturn(CommentDeletionResult.noOp());

        service.deleteByAuthor(AUTHOR_ID, POST_ID, ROOT_ID);

        verifyNoInteractions(eventPublisher);
        verify(postRepository, never()).incrementCommentCount(any(UUID.class), anyInt());
        verify(counterCache, never()).incrementCommentCount(any(UUID.class), anyLong());
    }

    @Test
    void moderatorDeletionMustPublishFromThePersistedResultRatherThanActorOwnership() {
        UUID moderatorId = uuid(8499);
        CommentSnapshot reply = reply(FIRST_REPLY_ID, AUTHOR_ID, ROOT_ID);
        when(repository.getRequiredSnapshot(FIRST_REPLY_ID)).thenReturn(reply);
        when(repository.apply(any(CommentDeletion.class)))
                .thenReturn(CommentDeletionResult.applied(List.of(reply)));

        service.deleteByModeration(moderatorId, FIRST_REPLY_ID, "hide: spam");

        ArgumentCaptor<CommentDeletedDomainEvent> event = ArgumentCaptor.forClass(CommentDeletedDomainEvent.class);
        verify(eventPublisher).commentDeleted(event.capture());
        assertThat(event.getValue().commentId()).isEqualTo(FIRST_REPLY_ID);
        assertThat(event.getValue().userId()).isEqualTo(AUTHOR_ID);
        assertThat(event.getValue().entityType()).isEqualTo(COMMENT);
    }

    private CommentSnapshot root(UUID id, UUID authorId) {
        return snapshot(id, authorId, id, null, "root", new Date(1_000_000L));
    }

    private CommentSnapshot reply(UUID id, UUID authorId, UUID rootId) {
        return snapshot(id, authorId, rootId, rootId, "reply", new Date(1_000_001L));
    }

    private CommentSnapshot snapshot(
            UUID id,
            UUID authorId,
            UUID rootId,
            UUID parentId,
            String content,
            Date createdAt
    ) {
        RecordComponent[] components = CommentSnapshot.class.getRecordComponents();
        Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = Arrays.stream(components)
                .map(component -> snapshotValue(component, id, authorId, rootId, parentId, content, createdAt))
                .toArray();
        try {
            Constructor<CommentSnapshot> constructor = CommentSnapshot.class.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("cannot build CommentSnapshot fixture", error);
        }
    }

    private Object snapshotValue(
            RecordComponent component,
            UUID id,
            UUID authorId,
            UUID rootId,
            UUID parentId,
            String content,
            Date createdAt
    ) {
        return switch (component.getName()) {
            case "id" -> id;
            case "userId" -> authorId;
            case "postId" -> POST_ID;
            case "rootCommentId" -> rootId;
            case "parentCommentId" -> parentId;
            case "replyToUserId" -> parentId == null ? null : AUTHOR_ID;
            case "content" -> content;
            case "status", "editCount" -> 0;
            case "createTime" -> createdAt;
            case "updateTime", "deletedBy", "deletedReason", "deletedTime" -> null;
            case "version" -> 7L;
            default -> throw new AssertionError("Unhandled CommentSnapshot component: " + component.getName());
        };
    }
}

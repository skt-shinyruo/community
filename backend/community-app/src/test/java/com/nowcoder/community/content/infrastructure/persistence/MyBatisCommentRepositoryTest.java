package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.content.domain.model.CommentDeletionResult;
import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentEdit;
import com.nowcoder.community.content.domain.model.CommentReplyContext;
import com.nowcoder.community.content.domain.model.CommentSnapshot;
import com.nowcoder.community.content.domain.model.CommentThreadDeletion;
import com.nowcoder.community.content.domain.model.CommentTransitionStatus;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.support.CommentTestBuilder.aComment;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisCommentRepositoryTest {

    private static final UUID AUTHOR_ID = uuid(401);
    private static final UUID POST_ID = uuid(402);
    private static final UUID ROOT_ID = uuid(403);
    private static final UUID REPLY_ID = uuid(404);

    @Test
    void commentDraftAndSnapshotShouldExposeThreadIdentityAndVersion() {
        assertThat(recordComponentNames(CommentDraft.class))
                .contains("postId", "rootCommentId", "parentCommentId", "replyToUserId");
        assertThat(recordComponentNames(CommentSnapshot.class))
                .contains("postId", "rootCommentId", "parentCommentId", "replyToUserId", "version");
    }

    @Test
    void createRootShouldMapDraftToDataObjectWithGeneratedIdAndInitialVersion() {
        CommentMapper mapper = mock(CommentMapper.class);
        when(mapper.insert(any(CommentDataObject.class))).thenReturn(1);
        MyBatisCommentRepository repository = repository(mapper);
        Date createdAt = Date.from(Instant.parse("2026-04-29T01:02:04Z"));

        UUID commentId = repository.create(new CommentDraft(
                AUTHOR_ID, POST_ID, null, null, null, "hello", createdAt
        ));

        ArgumentCaptor<CommentDataObject> captor = ArgumentCaptor.forClass(CommentDataObject.class);
        verify(mapper).insert(captor.capture());
        CommentDataObject inserted = captor.getValue();
        assertThat(commentId).isEqualTo(inserted.getId());
        assertThat(inserted.getId().version()).isEqualTo(7);
        assertThat(inserted.getRootCommentId()).isEqualTo(inserted.getId());
        assertThat(inserted.getPostId()).isEqualTo(POST_ID);
        assertThat(inserted.getUserId()).isEqualTo(AUTHOR_ID);
        assertThat(inserted.getContent()).isEqualTo("hello");
        assertThat(inserted.getStatus()).isZero();
        assertThat(inserted.getVersion()).isZero();
        assertThat(inserted.getCreateTime()).isEqualTo(createdAt);
    }

    @Test
    void createNestedReplyShouldInsertAlreadyValidatedDraftWithoutTakingAnotherLock() {
        CommentMapper mapper = mock(CommentMapper.class);
        when(mapper.insert(any(CommentDataObject.class))).thenReturn(1);
        MyBatisCommentRepository repository = repository(mapper);

        repository.create(new CommentDraft(
                AUTHOR_ID, POST_ID, ROOT_ID, REPLY_ID, uuid(405), "reply", new Date(2_000L)
        ));

        ArgumentCaptor<CommentDataObject> inserted = ArgumentCaptor.forClass(CommentDataObject.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getRootCommentId()).isEqualTo(ROOT_ID);
        assertThat(inserted.getValue().getParentCommentId()).isEqualTo(REPLY_ID);
        assertThat(inserted.getValue().getReplyToUserId()).isEqualTo(uuid(405));
        verify(mapper, never()).selectById(any());
        verify(mapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void lockReplyContextShouldReadHintThenLockRootAndNestedDirectParent() {
        CommentMapper mapper = mock(CommentMapper.class);
        UUID directAuthorId = uuid(405);
        CommentDataObject hint = row(REPLY_ID, ROOT_ID, 0, 1L, new Date(2_000L));
        CommentDataObject lockedRoot = row(ROOT_ID, null, 0, 2L, new Date(1_000L));
        CommentDataObject lockedDirect = row(
                REPLY_ID,
                directAuthorId,
                POST_ID,
                ROOT_ID,
                ROOT_ID,
                0,
                3L,
                new Date(2_000L)
        );
        when(mapper.selectById(REPLY_ID)).thenReturn(hint);
        when(mapper.selectByIdForUpdate(ROOT_ID)).thenReturn(lockedRoot);
        when(mapper.selectByIdForUpdate(REPLY_ID)).thenReturn(lockedDirect);

        Optional<CommentReplyContext> context = repository(mapper).lockReplyContext(POST_ID, REPLY_ID);

        assertThat(context).isPresent();
        assertThat(context.orElseThrow().root().id()).isEqualTo(ROOT_ID);
        assertThat(context.orElseThrow().directParent().id()).isEqualTo(REPLY_ID);
        assertThat(context.orElseThrow().directParent().userId()).isEqualTo(directAuthorId);
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectById(REPLY_ID);
        order.verify(mapper).selectByIdForUpdate(ROOT_ID);
        order.verify(mapper).selectByIdForUpdate(REPLY_ID);
    }

    @Test
    void lockReplyContextShouldLockDirectRootOnlyOnceAfterHintRead() {
        CommentMapper mapper = mock(CommentMapper.class);
        CommentDataObject hint = row(ROOT_ID, null, 0, 1L, new Date(1_000L));
        CommentDataObject lockedRoot = row(ROOT_ID, null, 0, 2L, new Date(1_000L));
        when(mapper.selectById(ROOT_ID)).thenReturn(hint);
        when(mapper.selectByIdForUpdate(ROOT_ID)).thenReturn(lockedRoot);

        Optional<CommentReplyContext> context = repository(mapper).lockReplyContext(POST_ID, ROOT_ID);

        assertThat(context).isPresent();
        assertThat(context.orElseThrow().directParent()).isEqualTo(context.orElseThrow().root());
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectById(ROOT_ID);
        order.verify(mapper).selectByIdForUpdate(ROOT_ID);
        verify(mapper).selectByIdForUpdate(ROOT_ID);
    }

    @Test
    void lockReplyContextShouldHideMissingHintAndMissingLockedRoot() {
        CommentMapper missingHintMapper = mock(CommentMapper.class);

        assertThat(repository(missingHintMapper).lockReplyContext(POST_ID, REPLY_ID)).isEmpty();
        verify(missingHintMapper).selectById(REPLY_ID);
        verify(missingHintMapper, never()).selectByIdForUpdate(any());

        CommentMapper missingRootMapper = mock(CommentMapper.class);
        when(missingRootMapper.selectById(REPLY_ID))
                .thenReturn(row(REPLY_ID, ROOT_ID, 0, 1L, new Date(2_000L)));
        when(missingRootMapper.selectByIdForUpdate(REPLY_ID))
                .thenReturn(row(REPLY_ID, ROOT_ID, 0, 1L, new Date(2_000L)));

        assertThat(repository(missingRootMapper).lockReplyContext(POST_ID, REPLY_ID)).isEmpty();
        InOrder order = inOrder(missingRootMapper);
        order.verify(missingRootMapper).selectById(REPLY_ID);
        order.verify(missingRootMapper).selectByIdForUpdate(ROOT_ID);
        order.verify(missingRootMapper).selectByIdForUpdate(REPLY_ID);
    }

    @Test
    void lockReplyContextShouldHideInactiveOrMismatchedLockedFacts() {
        CommentDataObject hint = row(REPLY_ID, ROOT_ID, 0, 1L, new Date(2_000L));
        CommentDataObject activeRoot = row(ROOT_ID, null, 0, 2L, new Date(1_000L));

        CommentMapper inactiveDirectMapper = mock(CommentMapper.class);
        when(inactiveDirectMapper.selectById(REPLY_ID)).thenReturn(hint);
        when(inactiveDirectMapper.selectByIdForUpdate(ROOT_ID)).thenReturn(activeRoot);
        when(inactiveDirectMapper.selectByIdForUpdate(REPLY_ID))
                .thenReturn(row(REPLY_ID, ROOT_ID, 1, 3L, new Date(2_000L)));
        assertThat(repository(inactiveDirectMapper).lockReplyContext(POST_ID, REPLY_ID)).isEmpty();

        CommentMapper wrongThreadMapper = mock(CommentMapper.class);
        when(wrongThreadMapper.selectById(REPLY_ID)).thenReturn(hint);
        when(wrongThreadMapper.selectByIdForUpdate(ROOT_ID)).thenReturn(activeRoot);
        when(wrongThreadMapper.selectByIdForUpdate(REPLY_ID)).thenReturn(row(
                REPLY_ID,
                AUTHOR_ID,
                POST_ID,
                uuid(499),
                ROOT_ID,
                0,
                3L,
                new Date(2_000L)
        ));
        assertThat(repository(wrongThreadMapper).lockReplyContext(POST_ID, REPLY_ID)).isEmpty();

        CommentMapper malformedRootMapper = mock(CommentMapper.class);
        when(malformedRootMapper.selectById(REPLY_ID)).thenReturn(hint);
        when(malformedRootMapper.selectByIdForUpdate(ROOT_ID)).thenReturn(row(
                ROOT_ID,
                AUTHOR_ID,
                POST_ID,
                ROOT_ID,
                uuid(498),
                0,
                2L,
                new Date(1_000L)
        ));
        when(malformedRootMapper.selectByIdForUpdate(REPLY_ID))
                .thenReturn(row(REPLY_ID, ROOT_ID, 0, 3L, new Date(2_000L)));
        assertThat(repository(malformedRootMapper).lockReplyContext(POST_ID, REPLY_ID)).isEmpty();

        CommentMapper wrongPostMapper = mock(CommentMapper.class);
        when(wrongPostMapper.selectById(REPLY_ID)).thenReturn(hint);
        when(wrongPostMapper.selectByIdForUpdate(ROOT_ID)).thenReturn(row(
                ROOT_ID,
                AUTHOR_ID,
                uuid(497),
                ROOT_ID,
                null,
                0,
                2L,
                new Date(1_000L)
        ));
        when(wrongPostMapper.selectByIdForUpdate(REPLY_ID))
                .thenReturn(row(REPLY_ID, ROOT_ID, 0, 3L, new Date(2_000L)));
        assertThat(repository(wrongPostMapper).lockReplyContext(POST_ID, REPLY_ID)).isEmpty();
    }

    @Test
    void getRequiredSnapshotShouldRejectInactiveAndMapEveryPersistedField() {
        CommentMapper mapper = mock(CommentMapper.class);
        MyBatisCommentRepository repository = repository(mapper);
        CommentDataObject inactive = row(ROOT_ID, null, 2, 8L, new Date(1_000L));
        when(mapper.selectById(ROOT_ID)).thenReturn(inactive);

        assertThatThrownBy(() -> repository.getRequiredSnapshot(ROOT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(COMMENT_NOT_FOUND);

        CommentDataObject active = row(ROOT_ID, null, 0, 8L, new Date(1_000L));
        active.setUpdateTime(new Date(2_000L));
        active.setEditCount(2);
        when(mapper.selectById(ROOT_ID)).thenReturn(active);

        CommentSnapshot snapshot = repository.getRequiredSnapshot(ROOT_ID);

        assertThat(snapshot.id()).isEqualTo(ROOT_ID);
        assertThat(snapshot.postId()).isEqualTo(POST_ID);
        assertThat(snapshot.userId()).isEqualTo(AUTHOR_ID);
        assertThat(snapshot.version()).isEqualTo(8L);
        assertThat(snapshot.updateTime()).isEqualTo(new Date(2_000L));
        assertThat(snapshot.editCount()).isEqualTo(2);
    }

    @Test
    void findSnapshotShouldIncludeInactiveWhileFindActiveSnapshotFiltersIt() {
        CommentMapper mapper = mock(CommentMapper.class);
        MyBatisCommentRepository repository = repository(mapper);
        when(mapper.selectById(ROOT_ID)).thenReturn(row(ROOT_ID, null, 2, 3L, new Date(1_000L)));

        Optional<CommentSnapshot> snapshot = repository.findSnapshot(ROOT_ID);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().status()).isEqualTo(2);
        assertThat(repository.findActiveSnapshot(ROOT_ID)).isEmpty();
    }

    @Test
    void editApplyShouldDistinguishAppliedNoOpStaleAndNotFound() {
        Date updatedAt = new Date(2_000L);
        CommentEdit edit = new CommentEdit(ROOT_ID, 7L, "edited", updatedAt);

        CommentMapper appliedMapper = mock(CommentMapper.class);
        when(appliedMapper.selectByIdForUpdate(ROOT_ID))
                .thenReturn(row(ROOT_ID, null, 0, 7L, new Date(1_000L)));
        when(appliedMapper.applyEdit(ROOT_ID, 7L, "edited", updatedAt)).thenReturn(1);
        assertThat(repository(appliedMapper).apply(edit)).isEqualTo(CommentTransitionStatus.APPLIED);

        CommentMapper noOpMapper = mock(CommentMapper.class);
        when(noOpMapper.selectByIdForUpdate(ROOT_ID))
                .thenReturn(row(ROOT_ID, null, 1, 8L, new Date(1_000L)));
        assertThat(repository(noOpMapper).apply(edit)).isEqualTo(CommentTransitionStatus.NO_OP);

        CommentMapper staleMapper = mock(CommentMapper.class);
        when(staleMapper.selectByIdForUpdate(ROOT_ID))
                .thenReturn(row(ROOT_ID, null, 0, 8L, new Date(1_000L)));
        assertThat(repository(staleMapper).apply(edit)).isEqualTo(CommentTransitionStatus.STALE);

        CommentMapper missingMapper = mock(CommentMapper.class);
        assertThat(repository(missingMapper).apply(edit)).isEqualTo(CommentTransitionStatus.NOT_FOUND);
    }

    @Test
    void deletionApplyShouldReturnOnlyTheSnapshotWhoseCasWasApplied() {
        CommentMapper mapper = mock(CommentMapper.class);
        CommentDataObject current = row(REPLY_ID, ROOT_ID, 0, 9L, new Date(2_000L));
        when(mapper.selectByIdForUpdate(REPLY_ID)).thenReturn(current);
        when(mapper.applyDeletion(eq(REPLY_ID), eq(9L), eq(AUTHOR_ID), eq("author_delete"), any(Date.class)))
                .thenReturn(1);
        CommentDeletion deletion = new CommentDeletion(
                REPLY_ID, 9L, AUTHOR_ID, "author_delete", new Date(3_000L)
        );

        CommentDeletionResult result = repository(mapper).apply(deletion);

        assertThat(result.status()).isEqualTo(CommentTransitionStatus.APPLIED);
        assertThat(result.deletedCommentIds()).containsExactly(REPLY_ID);
        assertThat(result.deletedComments().get(0).version()).isEqualTo(9L);
    }

    @Test
    void threadApplyShouldPreserveRootFirstOrderAndRejectAnyStaleMemberBeforeUpdating() {
        CommentDataObject root = row(ROOT_ID, null, 0, 7L, new Date(1_000L));
        CommentDataObject reply = row(REPLY_ID, ROOT_ID, 0, 9L, new Date(2_000L));
        CommentThreadDeletion deletion = CommentThreadDeletion.from(
                new CommentDeletion(ROOT_ID, 7L, AUTHOR_ID, "author_delete", new Date(3_000L)),
                List.of(
                        CommentPersistenceConverter.toSnapshot(root),
                        CommentPersistenceConverter.toSnapshot(reply)
                )
        );

        CommentMapper appliedMapper = mock(CommentMapper.class);
        when(appliedMapper.selectThreadForUpdate(ROOT_ID)).thenReturn(List.of(root, reply));
        when(appliedMapper.applyThreadDeletion(
                eq(ROOT_ID), anyList(), eq(AUTHOR_ID), eq("author_delete"), any(Date.class)
        )).thenReturn(2);

        CommentDeletionResult applied = repository(appliedMapper).apply(deletion);

        assertThat(applied.status()).isEqualTo(CommentTransitionStatus.APPLIED);
        assertThat(applied.deletedCommentIds()).containsExactly(ROOT_ID, REPLY_ID);

        CommentMapper staleMapper = mock(CommentMapper.class);
        CommentDataObject staleReply = row(REPLY_ID, ROOT_ID, 0, 10L, new Date(2_000L));
        when(staleMapper.selectThreadForUpdate(ROOT_ID)).thenReturn(List.of(root, staleReply));

        CommentDeletionResult stale = repository(staleMapper).apply(deletion);

        assertThat(stale.status()).isEqualTo(CommentTransitionStatus.STALE);
        assertThat(stale.deletedCount()).isZero();
        verify(staleMapper, never()).applyThreadDeletion(any(), anyList(), any(), any(), any());
    }

    private static MyBatisCommentRepository repository(CommentMapper mapper) {
        return new MyBatisCommentRepository(
                mapper,
                new UuidV7Generator(Clock.fixed(
                        Instant.parse("2026-04-29T01:02:03Z"),
                        ZoneOffset.UTC
                ))
        );
    }

    private static CommentDataObject row(
            UUID id,
            UUID parentId,
            int status,
            long version,
            Date createdAt
    ) {
        return aComment()
                .id(id)
                .userId(AUTHOR_ID)
                .postId(POST_ID)
                .rootCommentId(parentId == null ? id : ROOT_ID)
                .parentCommentId(parentId)
                .replyToUserId(parentId == null ? null : AUTHOR_ID)
                .status(status)
                .version(version)
                .createTime(createdAt)
                .buildDataObject();
    }

    private static CommentDataObject row(
            UUID id,
            UUID userId,
            UUID postId,
            UUID rootCommentId,
            UUID parentCommentId,
            int status,
            long version,
            Date createdAt
    ) {
        return aComment()
                .id(id)
                .userId(userId)
                .postId(postId)
                .rootCommentId(rootCommentId)
                .parentCommentId(parentCommentId)
                .replyToUserId(parentCommentId == null ? null : AUTHOR_ID)
                .status(status)
                .version(version)
                .createTime(createdAt)
                .buildDataObject();
    }

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}

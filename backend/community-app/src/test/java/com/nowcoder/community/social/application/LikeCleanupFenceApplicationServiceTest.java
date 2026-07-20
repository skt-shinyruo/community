package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.model.LikeTargetState;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.nowcoder.community.social.domain.repository.LikeTargetStateRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LikeCleanupFenceApplicationServiceTest {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final UUID TARGET_ID = uuid(600);
    private static final Instant DELETED_AT = Instant.parse("2026-07-15T08:30:00Z");

    @Test
    void cleanupShouldLockTargetBeforeScanningAndIgnoreDuplicateAndStaleEvents() {
        LikeRepository likeRepository = mock(LikeRepository.class);
        LikeTargetStateRepository targetStateRepository = mock(LikeTargetStateRepository.class);
        SocialDomainEventPublisher publisher = mock(SocialDomainEventPublisher.class);
        LikeTargetState active = LikeTargetState.active(POST, TARGET_ID);
        LikeTargetState deleted = active.applyDeletion("content:post-deleted:600", 42L, DELETED_AT);
        LikeRelation relation = new LikeRelation(uuid(701), uuid(1), POST, TARGET_ID, uuid(2));
        when(targetStateRepository.findForUpdate(POST, TARGET_ID))
                .thenReturn(active, deleted, deleted);
        when(targetStateRepository.saveIfNewer(any(LikeTargetState.class))).thenReturn(true);
        when(likeRepository.scanLikesByEntity(POST, TARGET_ID, ZERO_UUID, 200))
                .thenReturn(List.of(relation));
        when(likeRepository.scanLikesByEntity(POST, TARGET_ID, relation.actorUserId(), 200))
                .thenReturn(List.of());
        when(likeRepository.removeLike(relation))
                .thenReturn(true);
        LikeApplicationService service = newService(likeRepository, targetStateRepository, publisher);
        CleanupDeletedContentLikesCommand first = deletionCommand(42L, "content:post-deleted:600");

        assertThat(service.cleanupDeletedContentLikes(first)).isOne();
        assertThat(service.cleanupDeletedContentLikes(first)).isZero();
        assertThat(service.cleanupDeletedContentLikes(
                deletionCommand(41L, "content:post-deleted:600-stale")
        )).isZero();

        InOrder order = inOrder(targetStateRepository, likeRepository);
        order.verify(targetStateRepository).insertActiveIfAbsent(POST, TARGET_ID);
        order.verify(targetStateRepository).findForUpdate(POST, TARGET_ID);
        order.verify(targetStateRepository).saveIfNewer(any(LikeTargetState.class));
        order.verify(likeRepository).scanLikesByEntity(POST, TARGET_ID, ZERO_UUID, 200);
        verify(likeRepository, times(2)).scanLikesByEntity(anyInt(), any(UUID.class), any(UUID.class), anyInt());
        verify(targetStateRepository, times(3)).insertActiveIfAbsent(POST, TARGET_ID);
        verify(targetStateRepository, times(3)).findForUpdate(POST, TARGET_ID);
        verify(targetStateRepository, times(1)).saveIfNewer(any(LikeTargetState.class));
        verify(likeRepository, times(1)).removeLike(relation);
        verify(publisher, times(1)).publishLikeChanged(org.mockito.ArgumentMatchers.argThat(
                event -> relation.relationInstanceId().equals(event.relationInstanceId())
        ));
    }

    @Test
    void cleanupShouldProcessEveryPageBeyondTwoHundredWhileHoldingTheTargetFence() {
        LikeRepository likeRepository = mock(LikeRepository.class);
        LikeTargetStateRepository targetStateRepository = mock(LikeTargetStateRepository.class);
        SocialDomainEventPublisher publisher = mock(SocialDomainEventPublisher.class);
        List<LikeRelation> relations = IntStream.rangeClosed(1, 401)
                .mapToObj(index -> new LikeRelation(
                        uuid(10_000 + index),
                        uuid(1000 + index),
                        POST,
                        TARGET_ID,
                        uuid(2)
                ))
                .toList();
        List<LikeRelation> firstPage = relations.subList(0, 200);
        List<LikeRelation> secondPage = relations.subList(200, 400);
        List<LikeRelation> thirdPage = relations.subList(400, 401);
        when(targetStateRepository.findForUpdate(POST, TARGET_ID))
                .thenReturn(LikeTargetState.active(POST, TARGET_ID));
        when(targetStateRepository.saveIfNewer(any(LikeTargetState.class))).thenReturn(true);
        when(likeRepository.scanLikesByEntity(POST, TARGET_ID, ZERO_UUID, 200)).thenReturn(firstPage);
        when(likeRepository.scanLikesByEntity(POST, TARGET_ID, firstPage.get(199).actorUserId(), 200))
                .thenReturn(secondPage);
        when(likeRepository.scanLikesByEntity(POST, TARGET_ID, secondPage.get(199).actorUserId(), 200))
                .thenReturn(thirdPage);
        when(likeRepository.scanLikesByEntity(POST, TARGET_ID, thirdPage.get(0).actorUserId(), 200))
                .thenReturn(List.of());
        when(likeRepository.removeLike(any(LikeRelation.class))).thenReturn(true);
        LikeApplicationService service = newService(likeRepository, targetStateRepository, publisher);

        long removed = service.cleanupDeletedContentLikes(
                deletionCommand(42L, "content:post-deleted:600")
        );

        assertThat(removed).isEqualTo(401L);
        verify(likeRepository, times(4))
                .scanLikesByEntity(eq(POST), eq(TARGET_ID), any(UUID.class), eq(200));
        verify(likeRepository, times(401)).removeLike(any(LikeRelation.class));
        ArgumentCaptor<LikeChangedDomainEvent> events = ArgumentCaptor.forClass(LikeChangedDomainEvent.class);
        verify(publisher, times(401)).publishLikeChanged(events.capture());
        assertThat(events.getAllValues())
                .extracting(LikeChangedDomainEvent::relationInstanceId)
                .containsExactlyInAnyOrderElementsOf(relations.stream().map(LikeRelation::relationInstanceId).toList());
    }

    @Test
    void reconciliationShouldRepairLikesWithoutAdvancingTheOwnerDeletionFence() {
        LikeRepository likeRepository = mock(LikeRepository.class);
        LikeTargetStateRepository targetStateRepository = mock(LikeTargetStateRepository.class);
        SocialDomainEventPublisher publisher = mock(SocialDomainEventPublisher.class);
        LikeTargetState deleted = LikeTargetState.active(POST, TARGET_ID)
                .applyDeletion("content:post-deleted:600", 42L, DELETED_AT);
        LikeRelation orphan = new LikeRelation(uuid(702), uuid(1), POST, TARGET_ID, uuid(2));
        when(targetStateRepository.findForUpdate(POST, TARGET_ID)).thenReturn(deleted);
        when(targetStateRepository.saveIfNewer(any(LikeTargetState.class))).thenReturn(true);
        when(likeRepository.scanLikesByEntity(POST, TARGET_ID, ZERO_UUID, 200))
                .thenReturn(List.of(orphan));
        when(likeRepository.scanLikesByEntity(POST, TARGET_ID, orphan.actorUserId(), 200))
                .thenReturn(List.of());
        when(likeRepository.removeLike(orphan))
                .thenReturn(true);
        LikeApplicationService service = newService(likeRepository, targetStateRepository, publisher);

        long removed = service.cleanupDeletedContentLikes(new CleanupDeletedContentLikesCommand(
                POST,
                TARGET_ID,
                "social-like-reconciliation:" + POST + ":" + TARGET_ID + ":42",
                42L,
                DELETED_AT
        ));

        assertThat(removed).isOne();
        verify(targetStateRepository, never()).saveIfNewer(any(LikeTargetState.class));
        verify(likeRepository, times(2))
                .scanLikesByEntity(eq(POST), eq(TARGET_ID), any(UUID.class), eq(200));
        verify(publisher).publishLikeChanged(any());
    }

    @Test
    void creatingLikeShouldAcquireSameTargetFenceBeforeReadingOrWritingRelation() {
        LikeRepository likeRepository = mock(LikeRepository.class);
        LikeTargetStateRepository targetStateRepository = mock(LikeTargetStateRepository.class);
        SocialDomainEventPublisher publisher = mock(SocialDomainEventPublisher.class);
        when(targetStateRepository.findForUpdate(POST, TARGET_ID))
                .thenReturn(LikeTargetState.active(POST, TARGET_ID));
        when(likeRepository.isLiked(uuid(1), POST, TARGET_ID)).thenReturn(true);
        when(likeRepository.addLike(any(LikeRelation.class))).thenReturn(true);
        when(likeRepository.countEntityLikes(POST, TARGET_ID)).thenReturn(1L);
        LikeApplicationService service = newService(likeRepository, targetStateRepository, publisher);

        service.setLike(new SetLikeCommand(uuid(1), POST, TARGET_ID, true, uuid(2), TARGET_ID));

        InOrder order = inOrder(targetStateRepository, likeRepository);
        order.verify(targetStateRepository).insertActiveIfAbsent(POST, TARGET_ID);
        order.verify(targetStateRepository).findForUpdate(POST, TARGET_ID);
        order.verify(likeRepository).findLike(uuid(1), POST, TARGET_ID);
        order.verify(likeRepository).addLike(any(LikeRelation.class));
    }

    @Test
    void deletedTargetShouldRejectNewLikeBeforeRelationWrite() {
        LikeRepository likeRepository = mock(LikeRepository.class);
        LikeTargetStateRepository targetStateRepository = mock(LikeTargetStateRepository.class);
        LikeTargetState deleted = LikeTargetState.active(POST, TARGET_ID)
                .applyDeletion("content:post-deleted:600", 42L, DELETED_AT);
        when(targetStateRepository.findForUpdate(POST, TARGET_ID)).thenReturn(deleted);
        LikeApplicationService service = newService(
                likeRepository,
                targetStateRepository,
                mock(SocialDomainEventPublisher.class)
        );

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(
                uuid(1), POST, TARGET_ID, true, uuid(2), TARGET_ID
        )))
                .isInstanceOf(BusinessException.class);

        verify(targetStateRepository).insertActiveIfAbsent(POST, TARGET_ID);
        verify(targetStateRepository).findForUpdate(POST, TARGET_ID);
        verify(targetStateRepository, never()).saveIfNewer(any());
        verifyNoInteractions(likeRepository);
    }

    private CleanupDeletedContentLikesCommand deletionCommand(long sourceVersion, String sourceEventId) {
        return new CleanupDeletedContentLikesCommand(
                POST,
                TARGET_ID,
                sourceEventId,
                sourceVersion,
                DELETED_AT
        );
    }

    private LikeApplicationService newService(
            LikeRepository likeRepository,
            LikeTargetStateRepository targetStateRepository,
            SocialDomainEventPublisher publisher
    ) {
        return new LikeApplicationService(
                likeRepository,
                mock(BlockRepository.class),
                new LikeDomainService(),
                new BlockDomainService(),
                publisher,
                targetStateRepository,
                new UuidV7Generator(Clock.fixed(DELETED_AT, ZoneOffset.UTC))
        );
    }
}

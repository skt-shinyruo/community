package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.model.LikeTargetState;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.nowcoder.community.social.domain.repository.LikeTargetStateRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LikeApplicationServiceTest {

    private static final Instant ID_TIME = Instant.parse("2026-07-15T08:30:00Z");

    @Test
    void selfLikeShouldBeRejectedForUserEntity() {
        LikeRepository repo = mock(LikeRepository.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class)
        );

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(
                uuid(1), USER, uuid(1), true, uuid(1), null
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        verifyNoInteractions(repo);
    }

    @Test
    void setLikeShouldRejectNullCommand() {
        LikeApplicationService service = newService(
                mock(LikeRepository.class),
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class)
        );

        assertThatThrownBy(() -> service.setLike(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void likeShouldBeForbiddenWhenEitherBlockedOnCreate() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();

        StatefulBlockRepository blockRepository = new StatefulBlockRepository();
        blockRepository.block(uuid(2), uuid(1));
        LikeApplicationService service = newService(repo, blockRepository, publisher);

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(
                uuid(1), POST, uuid(100), true, uuid(2), uuid(100)
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isEqualTo(0);
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void likeLifecycleShouldPersistAndPublishOneInstanceThenRenewItAfterRelike() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        UuidV7Generator idGenerator = new UuidV7Generator(Clock.fixed(ID_TIME, ZoneOffset.UTC));

        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                publisher,
                new StatefulLikeTargetStateRepository(),
                idGenerator
        );

        SetLikeCommand like = new SetLikeCommand(uuid(1), POST, uuid(100), true, uuid(2), uuid(100));
        SetLikeCommand unlike = new SetLikeCommand(uuid(1), POST, uuid(100), false, uuid(2), uuid(100));
        LikeResult r1 = service.setLike(like);
        assertThat(r1.liked()).isTrue();
        assertThat(r1.likeCount()).isEqualTo(1);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);
        assertThat(publisher.snapshot().get(0)).isInstanceOf(LikeChangedDomainEvent.class);
        LikeRelation firstRelation = repo.findLike(uuid(1), POST, uuid(100)).orElseThrow();
        LikeChangedDomainEvent firstCreated = (LikeChangedDomainEvent) publisher.snapshot().get(0);
        assertThat(firstRelation.relationInstanceId()).isEqualTo(firstCreated.relationInstanceId());
        assertThat(firstRelation.relationInstanceId().version()).isEqualTo(7);

        LikeResult repeatedLike = service.setLike(like);
        assertThat(repeatedLike.liked()).isTrue();
        assertThat(repeatedLike.likeCount()).isEqualTo(1);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);

        LikeResult r3 = service.setLike(unlike);
        assertThat(r3.liked()).isFalse();
        assertThat(r3.likeCount()).isEqualTo(0);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(2);
        LikeChangedDomainEvent firstRemoved = (LikeChangedDomainEvent) publisher.snapshot().get(1);
        assertThat(firstRemoved.relationInstanceId()).isEqualTo(firstRelation.relationInstanceId());

        LikeResult r4 = service.setLike(unlike);
        assertThat(r4.liked()).isFalse();
        assertThat(r4.likeCount()).isEqualTo(0);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(2);

        LikeResult reliked = service.setLike(like);
        LikeRelation secondRelation = repo.findLike(uuid(1), POST, uuid(100)).orElseThrow();
        LikeChangedDomainEvent secondCreated = (LikeChangedDomainEvent) publisher.snapshot().get(2);
        assertThat(reliked.liked()).isTrue();
        assertThat(secondRelation.relationInstanceId()).isEqualTo(secondCreated.relationInstanceId());
        assertThat(secondRelation.relationInstanceId()).isNotEqualTo(firstRelation.relationInstanceId());
        assertThat(secondRelation.relationInstanceId().getMostSignificantBits() & 0x0fffL)
                .isEqualTo((firstRelation.relationInstanceId().getMostSignificantBits() & 0x0fffL) + 1L);

        service.setLike(unlike);
        LikeChangedDomainEvent secondRemoved = (LikeChangedDomainEvent) publisher.snapshot().get(3);
        assertThat(secondRemoved.relationInstanceId()).isEqualTo(secondRelation.relationInstanceId());
        assertThat(publisher.snapshot()).hasSize(4);
    }

    @Test
    void likeAndUnlikeShouldShareStableRelationKey() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        LikeApplicationService service = newService(repo, new StatefulBlockRepository(), publisher);

        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true, uuid(2), uuid(100)));
        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false, uuid(2), uuid(100)));

        assertThat(publisher.snapshot()).hasSize(2);
        LikeChangedDomainEvent created = (LikeChangedDomainEvent) publisher.snapshot().get(0);
        LikeChangedDomainEvent removed = (LikeChangedDomainEvent) publisher.snapshot().get(1);
        assertThat(created.relationKey()).isEqualTo(removed.relationKey());
        assertThat(created.relationInstanceId()).isEqualTo(removed.relationInstanceId());
        assertThat(created.relationKey()).isEqualTo("like:" + uuid(1) + ":" + POST + ":" + uuid(100));
    }

    @Test
    void compareAndSetLoserShouldNotAdjustOwnerCountOrPublish() {
        LikeRepository repo = mock(LikeRepository.class);
        SocialDomainEventPublisher publisher = mock(SocialDomainEventPublisher.class);
        LikeRelation existing = new LikeRelation(uuid(800), uuid(1), POST, uuid(100), uuid(2));
        when(repo.findLike(uuid(1), POST, uuid(100))).thenReturn(Optional.of(existing));
        when(repo.removeLike(existing)).thenReturn(false);
        when(repo.isLiked(uuid(1), POST, uuid(100))).thenReturn(true);
        when(repo.countEntityLikes(POST, uuid(100))).thenReturn(1L);
        LikeApplicationService service = newService(repo, new StatefulBlockRepository(), publisher);

        LikeResult result = service.setLike(new SetLikeCommand(
                uuid(1), POST, uuid(100), false, uuid(2), uuid(100)
        ));

        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isOne();
        verify(repo).removeLike(existing);
        verify(repo, never()).incrementUserLikeCount(uuid(2), -1L);
        verifyNoInteractions(publisher);
    }

    @Test
    void deleteLikesByEntityShouldDecrementStoredOwnerCounts() {
        StatefulLikeRepository repo = new StatefulLikeRepository();

        seedLike(repo, uuid(801), uuid(1), POST, uuid(100), uuid(2));
        seedLike(repo, uuid(802), uuid(3), POST, uuid(100), uuid(2));
        assertThat(repo.getUserLikeCount(uuid(2))).isEqualTo(2);

        assertThat(repo.deleteLikesByEntity(POST, uuid(100))).isEqualTo(2);

        assertThat(repo.countEntityLikes(POST, uuid(100))).isZero();
        assertThat(repo.getUserLikeCount(uuid(2))).isZero();
    }

    @Test
    void cleanupShouldEmitLikeRemovedForEachExistingLike() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                publisher
        );

        LikeRelation first = seedLike(repo, uuid(803), uuid(1), POST, uuid(100), uuid(2));
        LikeRelation second = seedLike(repo, uuid(804), uuid(3), POST, uuid(100), uuid(2));

        long removed = service.cleanupDeletedContentLikes(deletionCommand(POST, uuid(100)));

        assertThat(removed).isEqualTo(2L);
        assertThat(repo.countEntityLikes(POST, uuid(100))).isZero();
        assertThat(publisher.snapshot())
                .filteredOn(LikeChangedDomainEvent.class::isInstance)
                .extracting(LikeChangedDomainEvent.class::cast)
                .allSatisfy(event -> assertThat(event.liked()).isFalse())
                .extracting(LikeChangedDomainEvent::relationInstanceId)
                .containsExactlyInAnyOrder(first.relationInstanceId(), second.relationInstanceId());
        assertThat(publisher.snapshot())
                .filteredOn(LikeChangedDomainEvent.class::isInstance)
                .hasSize(2);
    }

    @Test
    void cleanupDeletedContentLikesShouldBeIdempotentForDuplicateDeletionEvents() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                publisher
        );
        seedLike(repo, uuid(805), uuid(1), POST, uuid(100), uuid(2));
        seedLike(repo, uuid(806), uuid(3), POST, uuid(100), uuid(2));
        CleanupDeletedContentLikesCommand command = new CleanupDeletedContentLikesCommand(
                POST,
                uuid(100),
                "content:PostDeleted:" + uuid(100),
                42L,
                Instant.parse("2026-07-15T08:30:00Z")
        );

        long firstRemoved = service.cleanupDeletedContentLikes(command);
        long duplicateRemoved = service.cleanupDeletedContentLikes(command);

        assertThat(firstRemoved).isEqualTo(2L);
        assertThat(duplicateRemoved).isZero();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isZero();
        assertThat(publisher.snapshot())
                .filteredOn(LikeChangedDomainEvent.class::isInstance)
                .hasSize(2);
    }

    @Test
    void reconciliationOfAnExistingDeletionFenceShouldRemoveResidualLikes() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        StatefulLikeTargetStateRepository targetStateRepository = new StatefulLikeTargetStateRepository();
        CleanupDeletedContentLikesCommand command = deletionCommand(POST, uuid(100));
        targetStateRepository.insertActiveIfAbsent(POST, uuid(100));
        targetStateRepository.saveIfNewer(LikeTargetState.active(POST, uuid(100)).applyDeletion(
                command.sourceEventId(),
                command.sourceVersion(),
                command.deletedAt()
        ));
        seedLike(repo, uuid(807), uuid(1), POST, uuid(100), uuid(2));
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                new RecordingSocialDomainEventPublisher(),
                targetStateRepository
        );

        long removed = service.cleanupDeletedContentLikes(new CleanupDeletedContentLikesCommand(
                command.entityType(),
                command.entityId(),
                "social-like-reconciliation:" + command.entityType() + ":" + command.entityId() + ":42",
                command.sourceVersion(),
                command.deletedAt()
        ));

        assertThat(removed).isOne();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isZero();
    }

    @Test
    void cleanupShouldPropagatePublisherFailure() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                new FailingSocialDomainEventPublisher()
        );

        seedLike(repo, uuid(808), uuid(1), POST, uuid(100), uuid(2));

        assertThatThrownBy(() -> service.cleanupDeletedContentLikes(deletionCommand(POST, uuid(100))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");
    }

    @Test
    void likeShouldPropagatePublisherFailure() {
        StatefulLikeRepository repo = new StatefulLikeRepository();

        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                new FailingSocialDomainEventPublisher()
        );

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(
                uuid(1), POST, uuid(100), true, uuid(2), uuid(100)
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");
    }

    @Test
    void setLikeShouldRejectUnsupportedEntityTypeBeforeCollaborators() {
        LikeRepository repo = mock(LikeRepository.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class)
        );

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(
                uuid(1), 999, uuid(100), true, null, null
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        verifyNoInteractions(repo);
    }

    @Test
    void likeQueriesShouldRejectUnsupportedEntityTypeBeforeRepository() {
        LikeRepository repo = mock(LikeRepository.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class)
        );

        assertThatThrownBy(() -> service.isLiked(uuid(1), 999, uuid(100)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.count(999, uuid(100)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.cleanupDeletedContentLikes(deletionCommand(999, uuid(100))))
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
                mock(SocialDomainEventPublisher.class)
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
                mock(SocialDomainEventPublisher.class)
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
                mock(SocialDomainEventPublisher.class)
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

    private LikeApplicationService newService(
            LikeRepository likeRepository,
            BlockRepository blockRepository,
            SocialDomainEventPublisher publisher
    ) {
        return newService(
                likeRepository,
                blockRepository,
                publisher,
                new StatefulLikeTargetStateRepository(),
                new UuidV7Generator(Clock.fixed(ID_TIME, ZoneOffset.UTC))
        );
    }

    private LikeApplicationService newService(
            LikeRepository likeRepository,
            BlockRepository blockRepository,
            SocialDomainEventPublisher publisher,
            LikeTargetStateRepository targetStateRepository
    ) {
        return newService(
                likeRepository,
                blockRepository,
                publisher,
                targetStateRepository,
                new UuidV7Generator(Clock.fixed(ID_TIME, ZoneOffset.UTC))
        );
    }

    private LikeApplicationService newService(
            LikeRepository likeRepository,
            BlockRepository blockRepository,
            SocialDomainEventPublisher publisher,
            LikeTargetStateRepository targetStateRepository,
            UuidV7Generator idGenerator
    ) {
        return new LikeApplicationService(
                likeRepository,
                blockRepository,
                new LikeDomainService(),
                new BlockDomainService(),
                publisher,
                targetStateRepository,
                idGenerator
        );
    }

    private LikeRelation seedLike(
            StatefulLikeRepository repository,
            UUID relationInstanceId,
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId
    ) {
        LikeRelation relation = new LikeRelation(
                relationInstanceId,
                actorUserId,
                entityType,
                entityId,
                entityUserId
        );
        assertThat(repository.addLike(relation)).isTrue();
        if (entityUserId != null) {
            repository.incrementUserLikeCount(entityUserId, 1L);
        }
        return relation;
    }

    private CleanupDeletedContentLikesCommand deletionCommand(int entityType, UUID entityId) {
        return new CleanupDeletedContentLikesCommand(
                entityType,
                entityId,
                "content:deleted:" + entityType + ":" + entityId,
                42L,
                Instant.parse("2026-07-15T08:30:00Z")
        );
    }

    private static final class StatefulLikeRepository implements LikeRepository {

        private final Map<String, Map<UUID, LikeRelation>> entityLikes = new ConcurrentHashMap<>();
        private final Map<UUID, Long> userLikeCounts = new ConcurrentHashMap<>();

        @Override
        public boolean addLike(LikeRelation relation) {
            Map<UUID, LikeRelation> map = entityLikes.computeIfAbsent(
                    entityKey(relation.entityType(), relation.entityId()),
                    ignored -> new ConcurrentHashMap<>()
            );
            return map.putIfAbsent(relation.actorUserId(), relation) == null;
        }

        @Override
        public boolean removeLike(LikeRelation expectedRelation) {
            Map<UUID, LikeRelation> map = entityLikes.get(entityKey(
                    expectedRelation.entityType(),
                    expectedRelation.entityId()
            ));
            return map != null && map.remove(expectedRelation.actorUserId(), expectedRelation);
        }

        @Override
        public Optional<LikeRelation> findLike(UUID userId, int entityType, UUID entityId) {
            Map<UUID, LikeRelation> map = entityLikes.get(entityKey(entityType, entityId));
            return map == null ? Optional.empty() : Optional.ofNullable(map.get(userId));
        }

        @Override
        public long deleteLikesByEntity(int entityType, UUID entityId) {
            Map<UUID, LikeRelation> removed = entityLikes.remove(entityKey(entityType, entityId));
            if (removed == null || removed.isEmpty()) {
                return 0;
            }
            for (LikeRelation relation : removed.values()) {
                if (relation.entityUserId() != null) {
                    incrementUserLikeCount(relation.entityUserId(), -1);
                }
            }
            return removed.size();
        }

        @Override
        public List<LikeRelation> scanLikesByEntity(int entityType, UUID entityId, UUID afterActorUserId, int limit) {
            Map<UUID, LikeRelation> map = entityLikes.get(entityKey(entityType, entityId));
            if (map == null || map.isEmpty()) {
                return List.of();
            }
            UUID cursor = afterActorUserId == null ? new UUID(0L, 0L) : afterActorUserId;
            return map.entrySet().stream()
                    .filter(entry -> entry.getKey().compareTo(cursor) > 0)
                    .sorted(Map.Entry.comparingByKey())
                    .limit(limit)
                    .map(Map.Entry::getValue)
                    .toList();
        }

        @Override
        public List<UUID> scanTargetIdsAfter(int entityType, UUID afterEntityId, int limit) {
            UUID cursor = afterEntityId == null ? new UUID(0L, 0L) : afterEntityId;
            return entityLikes.keySet().stream()
                    .filter(key -> key.startsWith("like:entity:" + entityType + ":"))
                    .map(key -> UUID.fromString(key.substring(key.lastIndexOf(':') + 1)))
                    .filter(entityId -> entityId.compareTo(cursor) > 0)
                    .sorted()
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean isLiked(UUID userId, int entityType, UUID entityId) {
            Map<UUID, LikeRelation> map = entityLikes.get(entityKey(entityType, entityId));
            return map != null && map.containsKey(userId);
        }

        @Override
        public long countEntityLikes(int entityType, UUID entityId) {
            Map<UUID, LikeRelation> map = entityLikes.get(entityKey(entityType, entityId));
            return map == null ? 0 : map.size();
        }

        @Override
        public long incrementUserLikeCount(UUID userId, long delta) {
            return userLikeCounts.merge(userId, Math.max(0, delta), (current, ignored) -> Math.max(0, current + delta));
        }

        @Override
        public long getUserLikeCount(UUID userId) {
            return userLikeCounts.getOrDefault(userId, 0L);
        }

        private String entityKey(int entityType, UUID entityId) {
            return "like:entity:" + entityType + ":" + entityId;
        }
    }

    private static final class StatefulLikeTargetStateRepository implements LikeTargetStateRepository {

        private final Map<String, LikeTargetState> states = new ConcurrentHashMap<>();

        @Override
        public boolean insertActiveIfAbsent(int entityType, UUID entityId) {
            return states.putIfAbsent(key(entityType, entityId), LikeTargetState.active(entityType, entityId)) == null;
        }

        @Override
        public Optional<LikeTargetState> findByTarget(int entityType, UUID entityId) {
            return Optional.ofNullable(states.get(key(entityType, entityId)));
        }

        @Override
        public LikeTargetState findForUpdate(int entityType, UUID entityId) {
            return states.get(key(entityType, entityId));
        }

        @Override
        public boolean saveIfNewer(LikeTargetState state) {
            String key = key(state.entityType(), state.entityId());
            states.compute(key, (ignored, current) -> current == null || state.sourceVersion() > current.sourceVersion()
                    ? state
                    : current);
            return states.get(key) == state;
        }

        @Override
        public List<LikeTargetState> scanDeletedTargetsWithLikesAfter(
                int entityType,
                UUID afterEntityId,
                int limit
        ) {
            return List.of();
        }

        private String key(int entityType, UUID entityId) {
            return entityType + ":" + entityId;
        }
    }

    private static final class StatefulBlockRepository implements BlockRepository {

        private final ConcurrentHashMap<UUID, Set<UUID>> blocks = new ConcurrentHashMap<>();

        @Override
        public boolean block(UUID userId, UUID targetUserId, long version) {
            return blocks.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(targetUserId);
        }

        @Override
        public boolean unblock(UUID userId, UUID targetUserId, long version) {
            Set<UUID> set = blocks.get(userId);
            return set != null && set.remove(targetUserId);
        }

        @Override
        public long nextBlockProjectionVersion() {
            return 1L;
        }

        @Override
        public long currentBlockProjectionVersion() {
            return 1L;
        }

        @Override
        public boolean hasBlocked(UUID userId, UUID targetUserId) {
            Set<UUID> set = blocks.get(userId);
            return set != null && set.contains(targetUserId);
        }

        @Override
        public List<UUID> listBlockedUserIds(UUID userId) {
            Set<UUID> set = blocks.get(userId);
            return set == null ? List.of() : new ArrayList<>(set);
        }

        @Override
        public List<BlockRelation> scanBlocksAfter(UUID afterUserId, UUID afterTargetUserId, int limit) {
            return List.of();
        }

    }

    private static final class RecordingSocialDomainEventPublisher implements SocialDomainEventPublisher {

        private final List<Object> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publishLikeChanged(LikeChangedDomainEvent event) {
            events.add(event);
        }

        @Override
        public void publishFollowCreated(FollowCreatedDomainEvent event) {
            events.add(event);
        }

        @Override
        public void publishBlockRelationChanged(BlockRelationChangedDomainEvent event) {
            events.add(event);
        }

        private List<Object> snapshot() {
            synchronized (events) {
                return List.copyOf(events);
            }
        }
    }

    private static class FailingSocialDomainEventPublisher implements SocialDomainEventPublisher {

        @Override
        public void publishLikeChanged(LikeChangedDomainEvent event) {
            throw new IllegalStateException("publish failed");
        }

        @Override
        public void publishFollowCreated(FollowCreatedDomainEvent event) {
            throw new IllegalStateException("publish failed");
        }

        @Override
        public void publishBlockRelationChanged(BlockRelationChangedDomainEvent event) {
            throw new IllegalStateException("publish failed");
        }
    }
}

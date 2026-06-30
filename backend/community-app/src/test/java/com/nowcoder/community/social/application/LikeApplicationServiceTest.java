package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.model.LikeRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LikeApplicationServiceTest {

    @Test
    void selfLikeShouldBeRejectedForUserEntity() {
        LikeRepository repo = mock(LikeRepository.class);
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class),
                resolver
        );

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(uuid(1), USER, uuid(1), true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_ARGUMENT));

        verifyNoInteractions(repo, resolver);
    }

    @Test
    void likeShouldBeForbiddenWhenEitherBlockedOnCreate() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

        StatefulBlockRepository blockRepository = new StatefulBlockRepository();
        blockRepository.block(uuid(2), uuid(1));
        LikeApplicationService service = newService(repo, blockRepository, publisher, resolver);

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isEqualTo(0);
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void likeShouldBeIdempotentAndPublishOnce() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

        LikeApplicationService service = newService(repo, new StatefulBlockRepository(), publisher, resolver);

        LikeResult r1 = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        assertThat(r1.liked()).isTrue();
        assertThat(r1.likeCount()).isEqualTo(1);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);
        assertThat(publisher.snapshot().get(0)).isInstanceOf(LikeChangedDomainEvent.class);

        LikeResult repeatedLike = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        assertThat(repeatedLike.liked()).isTrue();
        assertThat(repeatedLike.likeCount()).isEqualTo(1);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);

        LikeResult r3 = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));
        assertThat(r3.liked()).isFalse();
        assertThat(r3.likeCount()).isEqualTo(0);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(2);

        LikeResult r4 = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));
        assertThat(r4.liked()).isFalse();
        assertThat(r4.likeCount()).isEqualTo(0);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(2);
    }

    @Test
    void likeAndUnlikeShouldShareStableRelationKey() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));
        LikeApplicationService service = newService(repo, new StatefulBlockRepository(), publisher, resolver);

        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));

        assertThat(publisher.snapshot()).hasSize(2);
        LikeChangedDomainEvent created = (LikeChangedDomainEvent) publisher.snapshot().get(0);
        LikeChangedDomainEvent removed = (LikeChangedDomainEvent) publisher.snapshot().get(1);
        assertThat(created.relationKey()).isEqualTo(removed.relationKey());
        assertThat(created.relationKey()).isEqualTo("like:" + uuid(1) + ":" + POST + ":" + uuid(100));
    }

    @Test
    void unlikeShouldRemoveExistingLikeWhenContentNoLongerResolves() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100)))
                .thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "content not found"));

        LikeApplicationService service = newService(repo, new StatefulBlockRepository(), publisher, resolver);

        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        LikeResult result = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));

        assertThat(result.liked()).isFalse();
        assertThat(result.likeCount()).isEqualTo(0);
        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
    }

    @Test
    void unlikeShouldRemoveExistingLikeWhenContentDomainReportsPostNotFound() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100)))
                .thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)))
                .thenThrow(new BusinessException(ContentErrorCode.POST_NOT_FOUND));

        LikeApplicationService service = newService(repo, new StatefulBlockRepository(), publisher, resolver);

        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        LikeResult result = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));

        assertThat(result.liked()).isFalse();
        assertThat(result.likeCount()).isEqualTo(0);
        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
    }

    @Test
    void unlikeShouldDecrementStoredOwnerCountWhenContentNoLongerResolves() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100)))
                .thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "content not found"));
        LikeApplicationService service = newService(repo, new StatefulBlockRepository(), publisher, resolver);

        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(1);
        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));

        assertThat(service.userLikeCount(uuid(2))).isZero();
    }

    @Test
    void deleteLikesByEntityShouldDecrementStoredOwnerCounts() {
        StatefulLikeRepository repo = new StatefulLikeRepository();

        assertThat(repo.setLike(uuid(1), POST, uuid(100), uuid(2), true)).isTrue();
        assertThat(repo.setLike(uuid(3), POST, uuid(100), uuid(2), true)).isTrue();
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
                publisher,
                mock(ContentEntityResolver.class)
        );

        assertThat(repo.setLike(uuid(1), POST, uuid(100), uuid(2), true)).isTrue();
        assertThat(repo.setLike(uuid(3), POST, uuid(100), uuid(2), true)).isTrue();

        long removed = service.cleanupEntityLikes(POST, uuid(100));

        assertThat(removed).isEqualTo(2L);
        assertThat(repo.countEntityLikes(POST, uuid(100))).isZero();
        assertThat(publisher.snapshot())
                .filteredOn(LikeChangedDomainEvent.class::isInstance)
                .extracting(LikeChangedDomainEvent.class::cast)
                .allSatisfy(event -> assertThat(event.liked()).isFalse());
        assertThat(publisher.snapshot())
                .filteredOn(LikeChangedDomainEvent.class::isInstance)
                .hasSize(2);
    }

    @Test
    void cleanupShouldCompensateRemovedLikeWhenPublisherFailsForCompensatingRepository() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                new FailingSocialDomainEventPublisher(),
                mock(ContentEntityResolver.class)
        );

        assertThat(repo.setLike(uuid(1), POST, uuid(100), uuid(2), true)).isTrue();

        assertThatThrownBy(() -> service.cleanupEntityLikes(POST, uuid(100)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isTrue();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isEqualTo(1);
        assertThat(repo.getUserLikeCount(uuid(2))).isEqualTo(1);
    }

    @Test
    void cleanupShouldRestoreRemovedLikeWhenTransactionRollsBackForCompensatingRepository() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                new RecordingSocialDomainEventPublisher(),
                mock(ContentEntityResolver.class)
        );

        assertThat(repo.setLike(uuid(1), POST, uuid(100), uuid(2), true)).isTrue();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThat(service.cleanupEntityLikes(POST, uuid(100))).isEqualTo(1);
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isTrue();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isEqualTo(1);
        assertThat(repo.getUserLikeCount(uuid(2))).isEqualTo(1);
    }

    @Test
    void likeShouldStillFailWhenContentDomainReportsPostNotFoundOnCreate() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100)))
                .thenThrow(new BusinessException(ContentErrorCode.POST_NOT_FOUND));

        LikeApplicationService service = newService(repo, new StatefulBlockRepository(), publisher, resolver);

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ContentErrorCode.POST_NOT_FOUND));
        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void likeShouldRollbackStateWhenPublisherFailsForCompensatingRepository() {
        StatefulLikeRepository repo = new StatefulLikeRepository();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                new FailingSocialDomainEventPublisher(),
                resolver
        );

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isEqualTo(0);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(0);
    }

    @Test
    void setLikeShouldRejectUnsupportedEntityTypeBeforeCollaborators() {
        LikeRepository repo = mock(LikeRepository.class);
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        LikeApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                mock(SocialDomainEventPublisher.class),
                resolver
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
                mock(ContentEntityResolver.class)
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
                mock(ContentEntityResolver.class)
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
                mock(ContentEntityResolver.class)
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
                mock(ContentEntityResolver.class)
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
            SocialDomainEventPublisher publisher,
            ContentEntityResolver resolver
    ) {
        return new LikeApplicationService(
                likeRepository,
                blockRepository,
                new LikeDomainService(),
                new BlockDomainService(),
                resolver,
                publisher
        );
    }

    private static final class StatefulLikeRepository implements LikeRepository {

        private static final UUID UNKNOWN_OWNER = new UUID(0L, 0L);

        private final Map<String, Map<UUID, UUID>> entityLikes = new ConcurrentHashMap<>();
        private final Map<UUID, Long> userLikeCounts = new ConcurrentHashMap<>();

        @Override
        public boolean addLike(UUID userId, int entityType, UUID entityId) {
            return addLike(userId, entityType, entityId, null);
        }

        @Override
        public boolean addLike(UUID userId, int entityType, UUID entityId, UUID entityUserId) {
            Map<UUID, UUID> map = entityLikes.computeIfAbsent(entityKey(entityType, entityId), ignored -> new ConcurrentHashMap<>());
            return map.putIfAbsent(userId, entityUserId == null ? UNKNOWN_OWNER : entityUserId) == null;
        }

        @Override
        public boolean removeLike(UUID userId, int entityType, UUID entityId) {
            Map<UUID, UUID> map = entityLikes.get(entityKey(entityType, entityId));
            return map != null && map.remove(userId) != null;
        }

        @Override
        public Optional<LikeRelation> findLike(UUID userId, int entityType, UUID entityId) {
            Map<UUID, UUID> map = entityLikes.get(entityKey(entityType, entityId));
            if (map == null || !map.containsKey(userId)) {
                return Optional.empty();
            }
            UUID ownerUserId = map.get(userId);
            return Optional.of(new LikeRelation(userId, entityType, entityId, UNKNOWN_OWNER.equals(ownerUserId) ? null : ownerUserId));
        }

        @Override
        public long deleteLikesByEntity(int entityType, UUID entityId) {
            Map<UUID, UUID> removed = entityLikes.remove(entityKey(entityType, entityId));
            if (removed == null || removed.isEmpty()) {
                return 0;
            }
            for (UUID ownerUserId : removed.values()) {
                if (ownerUserId != null && !UNKNOWN_OWNER.equals(ownerUserId)) {
                    incrementUserLikeCount(ownerUserId, -1);
                }
            }
            return removed.size();
        }

        @Override
        public List<LikeRelation> scanLikesByEntity(int entityType, UUID entityId, UUID afterActorUserId, int limit) {
            Map<UUID, UUID> map = entityLikes.get(entityKey(entityType, entityId));
            if (map == null || map.isEmpty()) {
                return List.of();
            }
            UUID cursor = afterActorUserId == null ? new UUID(0L, 0L) : afterActorUserId;
            return map.entrySet().stream()
                    .filter(entry -> entry.getKey().compareTo(cursor) > 0)
                    .sorted(Map.Entry.comparingByKey())
                    .limit(limit)
                    .map(entry -> new LikeRelation(
                            entry.getKey(),
                            entityType,
                            entityId,
                            UNKNOWN_OWNER.equals(entry.getValue()) ? null : entry.getValue()
                    ))
                    .toList();
        }

        @Override
        public boolean isLiked(UUID userId, int entityType, UUID entityId) {
            Map<UUID, UUID> map = entityLikes.get(entityKey(entityType, entityId));
            return map != null && map.containsKey(userId);
        }

        @Override
        public long countEntityLikes(int entityType, UUID entityId) {
            Map<UUID, UUID> map = entityLikes.get(entityKey(entityType, entityId));
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

        @Override
        public boolean requiresExplicitCompensation() {
            return true;
        }

        private String entityKey(int entityType, UUID entityId) {
            return "like:entity:" + entityType + ":" + entityId;
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

        @Override
        public boolean requiresExplicitCompensation() {
            return true;
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

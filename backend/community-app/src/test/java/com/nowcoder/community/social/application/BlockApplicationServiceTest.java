package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.application.command.BlockCommand;
import com.nowcoder.community.social.application.command.UnblockCommand;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.model.FollowRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.exception.SocialErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockApplicationServiceTest {

    private static final UUID USER_ID_1 = uuid(1);
    private static final UUID USER_ID_2 = uuid(2);

    @Test
    void blockApplicationServiceShouldExposeOwnerDomainConstructorWithoutImTypedFields() {
        assertThat(BlockApplicationService.class.getDeclaredConstructors())
                .anySatisfy(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        BlockRepository.class,
                        FollowRepository.class,
                        BlockDomainService.class,
                        SocialDomainEventPublisher.class
                ));
        assertThat(BlockApplicationService.class.getDeclaredFields())
                .extracting(Field::getType)
                .extracting(Class::getName)
                .noneMatch(typeName -> typeName.startsWith("com.nowcoder.community.im."));
    }

    @Test
    void blockShouldRejectNullCommand() {
        BlockApplicationService service = new BlockApplicationService(
                mock(BlockRepository.class),
                mock(FollowRepository.class),
                new BlockDomainService(),
                mock(SocialDomainEventPublisher.class)
        );

        assertThatThrownBy(() -> service.block(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void unblockShouldRejectNullCommand() {
        BlockApplicationService service = new BlockApplicationService(
                mock(BlockRepository.class),
                mock(FollowRepository.class),
                new BlockDomainService(),
                mock(SocialDomainEventPublisher.class)
        );

        assertThatThrownBy(() -> service.unblock(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void blockShouldRejectSelfWhenUuidValuesMatchButInstancesDiffer() {
        StatefulBlockRepository repo = new StatefulBlockRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        BlockApplicationService service = new BlockApplicationService(repo, new StatefulFollowRepository(), new BlockDomainService(), publisher);
        UUID userId = uuid(1);
        UUID targetUserId = UUID.fromString(userId.toString());

        assertThat(targetUserId)
                .isEqualTo(userId)
                .isNotSameAs(userId);

        assertThatThrownBy(() -> service.block(new BlockCommand(userId, targetUserId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(SocialErrorCode.CANNOT_BLOCK_SELF));

        assertThat(service.hasBlocked(userId, targetUserId)).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void isEitherBlockedShouldIgnoreSameUserWhenUuidValuesMatchButInstancesDiffer() {
        StatefulBlockRepository repo = new StatefulBlockRepository();
        BlockApplicationService service = new BlockApplicationService(repo, new StatefulFollowRepository(), new BlockDomainService(), new RecordingSocialDomainEventPublisher());
        UUID userId = uuid(1);
        UUID sameUserDifferentInstance = UUID.fromString(userId.toString());

        repo.block(userId, sameUserDifferentInstance, 1L);

        assertThat(service.isEitherBlocked(userId, sameUserDifferentInstance)).isFalse();
    }

    @Test
    void blockShouldPublishBlockRelationChangedEventWhenRelationChanges() {
        BlockRepository repository = mock(BlockRepository.class);
        FollowRepository followRepository = mock(FollowRepository.class);
        SocialDomainEventPublisher eventPublisher = mock(SocialDomainEventPublisher.class);
        when(repository.nextBlockProjectionVersion()).thenReturn(81L);
        when(repository.block(USER_ID_1, USER_ID_2, 81L)).thenReturn(true);

        BlockApplicationService service = new BlockApplicationService(repository, followRepository, new BlockDomainService(), eventPublisher);

        service.block(new BlockCommand(USER_ID_1, USER_ID_2));

        ArgumentCaptor<BlockRelationChangedDomainEvent> eventCaptor = ArgumentCaptor.forClass(BlockRelationChangedDomainEvent.class);
        verify(eventPublisher).publishBlockRelationChanged(eventCaptor.capture());
        BlockRelationChangedDomainEvent event = eventCaptor.getValue();
        assertThat(event.blockerUserId()).isEqualTo(USER_ID_1);
        assertThat(event.blockedUserId()).isEqualTo(USER_ID_2);
        assertThat(event.blocked()).isTrue();
        assertThat(event.version()).isEqualTo(81L);
    }

    @Test
    void blockShouldRemoveFollowRelationsInBothDirections() {
        StatefulBlockRepository blockRepository = new StatefulBlockRepository();
        StatefulFollowRepository followRepository = new StatefulFollowRepository();
        BlockApplicationService service = new BlockApplicationService(
                blockRepository,
                followRepository,
                new BlockDomainService(),
                new RecordingSocialDomainEventPublisher()
        );
        followRepository.follow(USER_ID_1, USER, USER_ID_2, 1000L);
        followRepository.follow(USER_ID_2, USER, USER_ID_1, 1001L);

        service.block(new BlockCommand(USER_ID_1, USER_ID_2));

        assertThat(followRepository.hasFollowed(USER_ID_1, USER, USER_ID_2)).isFalse();
        assertThat(followRepository.hasFollowed(USER_ID_2, USER, USER_ID_1)).isFalse();
        assertThat(followRepository.countFollowees(USER_ID_1, USER)).isZero();
        assertThat(followRepository.countFollowers(USER, USER_ID_1)).isZero();
    }

    @Test
    void blockShouldRemoveStaleFollowRelationsWhenBlockAlreadyExists() {
        StatefulBlockRepository blockRepository = new StatefulBlockRepository();
        StatefulFollowRepository followRepository = new StatefulFollowRepository();
        BlockApplicationService service = new BlockApplicationService(
                blockRepository,
                followRepository,
                new BlockDomainService(),
                new RecordingSocialDomainEventPublisher()
        );
        blockRepository.block(USER_ID_1, USER_ID_2, 1L);
        followRepository.follow(USER_ID_1, USER, USER_ID_2, 1000L);
        followRepository.follow(USER_ID_2, USER, USER_ID_1, 1001L);

        service.block(new BlockCommand(USER_ID_1, USER_ID_2));

        assertThat(followRepository.hasFollowed(USER_ID_1, USER, USER_ID_2)).isFalse();
        assertThat(followRepository.hasFollowed(USER_ID_2, USER, USER_ID_1)).isFalse();
    }

    @Test
    void duplicateBlockShouldRestoreStaleFollowCleanupWhenTransactionRollsBack() {
        StatefulBlockRepository blockRepository = new StatefulBlockRepository();
        StatefulFollowRepository followRepository = new StatefulFollowRepository();
        BlockApplicationService service = new BlockApplicationService(
                blockRepository,
                followRepository,
                new BlockDomainService(),
                new RecordingSocialDomainEventPublisher()
        );
        blockRepository.block(USER_ID_1, USER_ID_2, 1L);
        followRepository.follow(USER_ID_1, USER, USER_ID_2, 1000L);
        followRepository.follow(USER_ID_2, USER, USER_ID_1, 1001L);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.block(new BlockCommand(USER_ID_1, USER_ID_2));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        assertThat(followRepository.hasFollowed(USER_ID_1, USER, USER_ID_2)).isTrue();
        assertThat(followRepository.hasFollowed(USER_ID_2, USER, USER_ID_1)).isTrue();
        assertThat(blockRepository.hasBlocked(USER_ID_1, USER_ID_2)).isTrue();
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

    private static final class StatefulFollowRepository implements FollowRepository {

        private final Map<String, Set<UUID>> followees = new ConcurrentHashMap<>();
        private final Map<String, Set<UUID>> followers = new ConcurrentHashMap<>();

        @Override
        public boolean follow(UUID userId, int entityType, UUID entityId, long followTimeMillis) {
            boolean added = followees.computeIfAbsent(followeeKey(userId, entityType), ignored -> ConcurrentHashMap.newKeySet())
                    .add(entityId);
            if (added) {
                followers.computeIfAbsent(followerKey(entityType, entityId), ignored -> ConcurrentHashMap.newKeySet()).add(userId);
            }
            return added;
        }

        @Override
        public boolean unfollow(UUID userId, int entityType, UUID entityId) {
            Set<UUID> followeeSet = followees.get(followeeKey(userId, entityType));
            boolean removed = followeeSet != null && followeeSet.remove(entityId);
            Set<UUID> followerSet = followers.get(followerKey(entityType, entityId));
            if (followerSet != null) {
                followerSet.remove(userId);
            }
            return removed;
        }

        @Override
        public boolean hasFollowed(UUID userId, int entityType, UUID entityId) {
            Set<UUID> set = followees.get(followeeKey(userId, entityType));
            return set != null && set.contains(entityId);
        }

        @Override
        public long countFollowees(UUID userId, int entityType) {
            Set<UUID> set = followees.get(followeeKey(userId, entityType));
            return set == null ? 0 : set.size();
        }

        @Override
        public long countFollowers(int entityType, UUID entityId) {
            Set<UUID> set = followers.get(followerKey(entityType, entityId));
            return set == null ? 0 : set.size();
        }

        @Override
        public List<FollowRelation> listFollowees(UUID userId, int entityType, int offset, int limit) {
            return List.of();
        }

        @Override
        public List<FollowRelation> listFollowers(int entityType, UUID entityId, int offset, int limit) {
            return List.of();
        }

        @Override
        public List<UUID> listFolloweeIds(UUID userId, int entityType, int limit) {
            Set<UUID> set = followees.get(followeeKey(userId, entityType));
            if (set == null || set.isEmpty()) {
                return List.of();
            }
            return set.stream().limit(Math.max(0, limit)).toList();
        }

        @Override
        public boolean requiresExplicitCompensation() {
            return true;
        }

        private String followeeKey(UUID userId, int entityType) {
            return "followee:" + userId + ":" + entityType;
        }

        private String followerKey(int entityType, UUID entityId) {
            return "follower:" + entityType + ":" + entityId;
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
}

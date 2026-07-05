package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.social.application.command.FollowCommand;
import com.nowcoder.community.social.application.command.UnfollowCommand;
import com.nowcoder.community.social.application.result.FollowRelationResult;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.model.BlockRelation;
import com.nowcoder.community.social.domain.model.FollowRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.FollowDomainService;
import com.nowcoder.community.social.exception.SocialErrorCode;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowApplicationServiceTest {

    @Test
    void followShouldRejectSelfWhenUuidValuesMatchButInstancesDiffer() {
        StatefulFollowRepository repo = new StatefulFollowRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        FollowApplicationService service = newService(repo, new StatefulBlockRepository(), publisher);
        UUID actorUserId = uuid(1);
        UUID targetUserId = UUID.fromString(actorUserId.toString());

        assertThat(targetUserId)
                .isEqualTo(actorUserId)
                .isNotSameAs(actorUserId);

        assertThatThrownBy(() -> service.follow(new FollowCommand(actorUserId, USER, targetUserId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(SocialErrorCode.CANNOT_FOLLOW_SELF));

        assertThat(service.hasFollowed(actorUserId, USER, targetUserId)).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void followUseCasesShouldRejectNonUserEntityTypeAtApplicationBoundary() {
        StatefulFollowRepository repo = new StatefulFollowRepository();
        FollowApplicationService service = newService(repo, new StatefulBlockRepository(), new RecordingSocialDomainEventPublisher());
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);

        assertThatThrownBy(() -> service.follow(new FollowCommand(actorUserId, POST, targetUserId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.unfollow(new UnfollowCommand(actorUserId, POST, targetUserId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.hasFollowed(actorUserId, POST, targetUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.followeeCount(actorUserId, POST))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.followerCount(POST, targetUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.listFollowees(actorUserId, POST, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> service.listFollowers(POST, targetUserId, 0, 10))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void followShouldBeForbiddenWhenEitherBlockedOnCreate() {
        StatefulFollowRepository repo = new StatefulFollowRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        StatefulBlockRepository blockRepository = new StatefulBlockRepository();
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);
        blockRepository.block(actorUserId, targetUserId);

        FollowApplicationService service = newService(repo, blockRepository, publisher);

        assertThatThrownBy(() -> service.follow(new FollowCommand(actorUserId, USER, targetUserId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        assertThat(service.hasFollowed(actorUserId, USER, targetUserId)).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void followShouldRejectMissingTargetUserOnCreate() {
        StatefulFollowRepository repo = new StatefulFollowRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);
        when(userLookupQueryApi.getSummaryById(targetUserId)).thenReturn(null);

        FollowApplicationService service = newService(
                repo,
                new StatefulBlockRepository(),
                publisher,
                userLookupQueryApi
        );

        assertThatThrownBy(() -> service.follow(new FollowCommand(actorUserId, USER, targetUserId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));

        assertThat(service.hasFollowed(actorUserId, USER, targetUserId)).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void followShouldBeIdempotentAndPublishOnce() {
        StatefulFollowRepository repo = new StatefulFollowRepository();
        RecordingSocialDomainEventPublisher publisher = new RecordingSocialDomainEventPublisher();
        FollowApplicationService service = newService(repo, new StatefulBlockRepository(), publisher);
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);

        service.follow(new FollowCommand(actorUserId, USER, targetUserId));
        assertThat(service.hasFollowed(actorUserId, USER, targetUserId)).isTrue();
        assertThat(service.followeeCount(actorUserId, USER)).isEqualTo(1);
        assertThat(service.followerCount(USER, targetUserId)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);
        assertThat(publisher.snapshot().get(0)).isInstanceOf(FollowCreatedDomainEvent.class);

        service.follow(new FollowCommand(actorUserId, USER, targetUserId));
        assertThat(service.hasFollowed(actorUserId, USER, targetUserId)).isTrue();
        assertThat(service.followeeCount(actorUserId, USER)).isEqualTo(1);
        assertThat(service.followerCount(USER, targetUserId)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);

        service.unfollow(new UnfollowCommand(actorUserId, USER, targetUserId));
        assertThat(service.hasFollowed(actorUserId, USER, targetUserId)).isFalse();
        assertThat(service.followeeCount(actorUserId, USER)).isEqualTo(0);
        assertThat(service.followerCount(USER, targetUserId)).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(1);
    }

    @Test
    void followCountsAndListsShouldFilterRelationsBlockedByViewer() {
        StatefulFollowRepository repo = new StatefulFollowRepository();
        StatefulBlockRepository blockRepository = new StatefulBlockRepository();
        FollowApplicationService service = newService(repo, blockRepository, new RecordingSocialDomainEventPublisher());
        UUID viewerUserId = uuid(1);
        UUID visibleUserId = uuid(2);
        UUID blockedUserId = uuid(3);
        repo.follow(viewerUserId, USER, visibleUserId, 1000L);
        repo.follow(viewerUserId, USER, blockedUserId, 1001L);
        repo.follow(visibleUserId, USER, viewerUserId, 1002L);
        repo.follow(blockedUserId, USER, viewerUserId, 1003L);
        blockRepository.block(viewerUserId, blockedUserId);

        assertThat(service.followeeCount(viewerUserId, USER)).isEqualTo(1);
        assertThat(service.followerCount(USER, viewerUserId)).isEqualTo(1);
        assertThat(service.listFollowees(viewerUserId, USER, 0, 10))
                .extracting(FollowRelationResult::targetId)
                .containsExactly(visibleUserId);
        assertThat(service.listFollowers(USER, viewerUserId, 0, 10))
                .extracting(FollowRelationResult::targetId)
                .containsExactly(visibleUserId);
    }

    @Test
    void followCountsAndListsShouldFilterRelationsWhenViewerIsBlocked() {
        StatefulFollowRepository repo = new StatefulFollowRepository();
        StatefulBlockRepository blockRepository = new StatefulBlockRepository();
        FollowApplicationService service = newService(repo, blockRepository, new RecordingSocialDomainEventPublisher());
        UUID viewerUserId = uuid(1);
        UUID visibleUserId = uuid(2);
        UUID blockerUserId = uuid(3);
        repo.follow(viewerUserId, USER, visibleUserId, 1000L);
        repo.follow(viewerUserId, USER, blockerUserId, 1001L);
        repo.follow(visibleUserId, USER, viewerUserId, 1002L);
        repo.follow(blockerUserId, USER, viewerUserId, 1003L);
        blockRepository.block(blockerUserId, viewerUserId);

        assertThat(service.followeeCount(viewerUserId, USER)).isEqualTo(1);
        assertThat(service.followerCount(USER, viewerUserId)).isEqualTo(1);
        assertThat(service.listFollowees(viewerUserId, USER, 0, 10))
                .extracting(FollowRelationResult::targetId)
                .containsExactly(visibleUserId);
        assertThat(service.listFollowers(USER, viewerUserId, 0, 10))
                .extracting(FollowRelationResult::targetId)
                .containsExactly(visibleUserId);
    }

    @Test
    void followQueriesShouldUseRepositoryFilteredMethodsWithBoundedPagination() {
        FollowRepository followRepository = mock(FollowRepository.class);
        BlockRepository blockRepository = mock(BlockRepository.class);
        SocialDomainEventPublisher publisher = mock(SocialDomainEventPublisher.class);
        FollowApplicationService service = new FollowApplicationService(
                followRepository,
                blockRepository,
                new FollowDomainService(),
                new BlockDomainService(),
                publisher,
                allowAllUsersLookup()
        );
        UUID viewerUserId = uuid(1);
        UUID visibleUserId = uuid(2);
        when(followRepository.countFolloweesExcludingBlocked(viewerUserId, USER, blockRepository)).thenReturn(1L);
        when(followRepository.countFollowersExcludingBlocked(USER, viewerUserId, blockRepository)).thenReturn(2L);
        when(followRepository.listFolloweesExcludingBlocked(viewerUserId, USER, blockRepository, 20, 10))
                .thenReturn(List.of(new FollowRelation(visibleUserId, Instant.EPOCH)));
        when(followRepository.listFollowersExcludingBlocked(USER, viewerUserId, blockRepository, 10, 5))
                .thenReturn(List.of(new FollowRelation(visibleUserId, Instant.EPOCH)));

        assertThat(service.followeeCount(viewerUserId, USER)).isEqualTo(1);
        assertThat(service.followerCount(USER, viewerUserId)).isEqualTo(2);
        assertThat(service.listFollowees(viewerUserId, USER, 2, 10))
                .extracting(FollowRelationResult::targetId)
                .containsExactly(visibleUserId);
        assertThat(service.listFollowers(USER, viewerUserId, 2, 5))
                .extracting(FollowRelationResult::targetId)
                .containsExactly(visibleUserId);
        verify(followRepository).countFolloweesExcludingBlocked(viewerUserId, USER, blockRepository);
        verify(followRepository).countFollowersExcludingBlocked(USER, viewerUserId, blockRepository);
        verify(followRepository).listFolloweesExcludingBlocked(viewerUserId, USER, blockRepository, 20, 10);
        verify(followRepository).listFollowersExcludingBlocked(USER, viewerUserId, blockRepository, 10, 5);
        verify(followRepository, never()).listFollowees(viewerUserId, USER, 0, Integer.MAX_VALUE);
        verify(followRepository, never()).listFollowers(USER, viewerUserId, 0, Integer.MAX_VALUE);
    }

    @Test
    void followShouldRollbackStateWhenPublisherFailsForCompensatingRepository() {
        StatefulFollowRepository repo = new StatefulFollowRepository();
        FollowApplicationService service = newService(repo, new StatefulBlockRepository(), new FailingSocialDomainEventPublisher());
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);

        assertThatThrownBy(() -> service.follow(new FollowCommand(actorUserId, USER, targetUserId)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(service.hasFollowed(actorUserId, USER, targetUserId)).isFalse();
        assertThat(service.followeeCount(actorUserId, USER)).isEqualTo(0);
        assertThat(service.followerCount(USER, targetUserId)).isEqualTo(0);
    }

    private FollowApplicationService newService(
            FollowRepository followRepository,
            BlockRepository blockRepository,
            SocialDomainEventPublisher publisher
    ) {
        return newService(followRepository, blockRepository, publisher, allowAllUsersLookup());
    }

    private FollowApplicationService newService(
            FollowRepository followRepository,
            BlockRepository blockRepository,
            SocialDomainEventPublisher publisher,
            UserLookupQueryApi userLookupQueryApi
    ) {
        return new FollowApplicationService(
                followRepository,
                blockRepository,
                new FollowDomainService(),
                new BlockDomainService(),
                publisher,
                userLookupQueryApi
        );
    }

    private UserLookupQueryApi allowAllUsersLookup() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenAnswer(invocation -> summary(invocation.getArgument(0)));
        return userLookupQueryApi;
    }

    private UserSummaryView summary(UUID userId) {
        return new UserSummaryView(userId, "user-" + userId, null, 0);
    }

    private static final class StatefulFollowRepository implements FollowRepository {

        private final Map<String, Map<UUID, Long>> followees = new ConcurrentHashMap<>();
        private final Map<String, Map<UUID, Long>> followers = new ConcurrentHashMap<>();

        @Override
        public boolean follow(UUID userId, int entityType, UUID entityId, long followTimeMillis) {
            Map<UUID, Long> followeeMap = followees.computeIfAbsent(followeeKey(userId, entityType), ignored -> new ConcurrentHashMap<>());
            Long existed = followeeMap.putIfAbsent(entityId, followTimeMillis);
            if (existed != null) {
                followers.computeIfAbsent(followerKey(entityType, entityId), ignored -> new ConcurrentHashMap<>()).putIfAbsent(userId, existed);
                return false;
            }
            followers.computeIfAbsent(followerKey(entityType, entityId), ignored -> new ConcurrentHashMap<>()).put(userId, followTimeMillis);
            return true;
        }

        @Override
        public boolean unfollow(UUID userId, int entityType, UUID entityId) {
            Map<UUID, Long> followeeMap = followees.get(followeeKey(userId, entityType));
            boolean removed = followeeMap != null && followeeMap.remove(entityId) != null;
            Map<UUID, Long> followerMap = followers.get(followerKey(entityType, entityId));
            if (followerMap != null) {
                followerMap.remove(userId);
            }
            return removed;
        }

        @Override
        public boolean hasFollowed(UUID userId, int entityType, UUID entityId) {
            Map<UUID, Long> followeeMap = followees.get(followeeKey(userId, entityType));
            return followeeMap != null && followeeMap.containsKey(entityId);
        }

        @Override
        public long countFollowees(UUID userId, int entityType) {
            Map<UUID, Long> map = followees.get(followeeKey(userId, entityType));
            return map == null ? 0 : map.size();
        }

        @Override
        public long countFollowers(int entityType, UUID entityId) {
            Map<UUID, Long> map = followers.get(followerKey(entityType, entityId));
            return map == null ? 0 : map.size();
        }

        @Override
        public List<FollowRelation> listFollowees(UUID userId, int entityType, int offset, int limit) {
            return list(followees.get(followeeKey(userId, entityType)), offset, limit);
        }

        @Override
        public List<FollowRelation> listFollowers(int entityType, UUID entityId, int offset, int limit) {
            return list(followers.get(followerKey(entityType, entityId)), offset, limit);
        }

        @Override
        public boolean requiresExplicitCompensation() {
            return true;
        }

        private List<FollowRelation> list(Map<UUID, Long> map, int offset, int limit) {
            if (map == null || map.isEmpty()) {
                return List.of();
            }
            List<Map.Entry<UUID, Long>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
            int from = Math.max(0, offset);
            int to = Math.min(entries.size(), from + Math.max(0, limit));
            return entries.subList(from, to).stream()
                    .map(entry -> new FollowRelation(entry.getKey(), Instant.ofEpochMilli(entry.getValue())))
                    .toList();
        }

        private String followeeKey(UUID userId, int entityType) {
            return "followee:" + userId + ":" + entityType;
        }

        private String followerKey(int entityType, UUID entityId) {
            return "follower:" + entityType + ":" + entityId;
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

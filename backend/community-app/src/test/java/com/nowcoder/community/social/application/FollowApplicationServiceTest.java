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
import com.nowcoder.community.social.domain.model.FollowRelation;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.FollowDomainService;
import com.nowcoder.community.social.exception.SocialErrorCode;
import com.nowcoder.community.social.infrastructure.event.InMemorySocialDomainEventPublisher;
import com.nowcoder.community.social.infrastructure.persistence.InMemoryBlockRepository;
import com.nowcoder.community.social.infrastructure.persistence.InMemoryFollowRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        FollowApplicationService service = newService(repo, new InMemoryBlockRepository(), publisher);
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
    void followShouldBeForbiddenWhenEitherBlockedOnCreate() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        InMemoryBlockRepository blockRepository = new InMemoryBlockRepository();
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
    void followShouldBeIdempotentAndPublishOnce() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        FollowApplicationService service = newService(repo, new InMemoryBlockRepository(), publisher);
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
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemoryBlockRepository blockRepository = new InMemoryBlockRepository();
        FollowApplicationService service = newService(repo, blockRepository, new InMemorySocialDomainEventPublisher());
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
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemoryBlockRepository blockRepository = new InMemoryBlockRepository();
        FollowApplicationService service = newService(repo, blockRepository, new InMemorySocialDomainEventPublisher());
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
                publisher
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
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        FollowApplicationService service = newService(repo, new InMemoryBlockRepository(), new FailingSocialDomainEventPublisher());
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
            InMemoryFollowRepository followRepository,
            InMemoryBlockRepository blockRepository,
            SocialDomainEventPublisher publisher
    ) {
        return new FollowApplicationService(
                followRepository,
                blockRepository,
                new FollowDomainService(),
                new BlockDomainService(),
                publisher
        );
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

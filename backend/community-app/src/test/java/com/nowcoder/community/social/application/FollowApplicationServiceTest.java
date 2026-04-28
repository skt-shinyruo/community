package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.social.application.command.FollowCommand;
import com.nowcoder.community.social.application.command.UnfollowCommand;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.FollowDomainService;
import com.nowcoder.community.social.exception.SocialErrorCode;
import com.nowcoder.community.social.infrastructure.event.InMemorySocialDomainEventPublisher;
import com.nowcoder.community.social.infrastructure.persistence.InMemoryBlockRepository;
import com.nowcoder.community.social.infrastructure.persistence.InMemoryFollowRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

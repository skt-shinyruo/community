package com.nowcoder.community.social.service;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.exception.SocialErrorCode;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.block.InMemoryBlockRepository;
import com.nowcoder.community.social.event.InMemorySocialEventPublisher;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.follow.InMemoryFollowRepository;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowServiceTest {

    @Test
    void followShouldRejectSelfWhenUuidValuesMatchButInstancesDiffer() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        BlockService blockService = new BlockService(new InMemoryBlockRepository(), new InMemorySocialEventPublisher());
        FollowService service = new FollowService(repo, publisher, blockService);
        UUID actorUserId = uuid(1);
        UUID targetUserId = UUID.fromString(actorUserId.toString());

        assertThat(targetUserId)
                .isEqualTo(actorUserId)
                .isNotSameAs(actorUserId);

        FollowRequest req = new FollowRequest();
        req.setEntityType(3);
        req.setEntityId(targetUserId);
        req.setEntityUserId(targetUserId);

        assertThatThrownBy(() -> service.follow(actorUserId, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(SocialErrorCode.CANNOT_FOLLOW_SELF));

        assertThat(service.hasFollowed(actorUserId, 3, targetUserId)).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void followShouldBeForbiddenWhenEitherBlockedOnCreate() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        InMemoryBlockRepository blockRepository = new InMemoryBlockRepository();
        java.util.UUID actorUserId = uuid(1);
        java.util.UUID targetUserId = uuid(2);
        // 模拟“关注者拉黑了被关注者”
        blockRepository.block(actorUserId, targetUserId);
        BlockService blockService = new BlockService(blockRepository, new InMemorySocialEventPublisher());

        FollowService service = new FollowService(repo, publisher, blockService);

        FollowRequest req = new FollowRequest();
        req.setEntityType(3);
        req.setEntityId(targetUserId);
        req.setEntityUserId(targetUserId);

        assertThatThrownBy(() -> service.follow(actorUserId, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        assertThat(service.hasFollowed(actorUserId, 3, targetUserId)).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void followShouldBeIdempotentAndPublishOnce() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        BlockService blockService = new BlockService(new InMemoryBlockRepository(), new InMemorySocialEventPublisher());
        FollowService service = new FollowService(repo, publisher, blockService);
        java.util.UUID actorUserId = uuid(1);
        java.util.UUID targetUserId = uuid(2);

        FollowRequest req = new FollowRequest();
        req.setEntityType(3);
        req.setEntityId(targetUserId);
        req.setEntityUserId(targetUserId);

        service.follow(actorUserId, req);
        assertThat(service.hasFollowed(actorUserId, 3, targetUserId)).isTrue();
        assertThat(service.followeeCount(actorUserId, 3)).isEqualTo(1);
        assertThat(service.followerCount(3, targetUserId)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);
        assertThat(publisher.snapshot().get(0)).isInstanceOf(FollowPayload.class);

        service.follow(actorUserId, req);
        assertThat(service.hasFollowed(actorUserId, 3, targetUserId)).isTrue();
        assertThat(service.followeeCount(actorUserId, 3)).isEqualTo(1);
        assertThat(service.followerCount(3, targetUserId)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);

        service.unfollow(actorUserId, 3, targetUserId);
        assertThat(service.hasFollowed(actorUserId, 3, targetUserId)).isFalse();
        assertThat(service.followeeCount(actorUserId, 3)).isEqualTo(0);
        assertThat(service.followerCount(3, targetUserId)).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(1);
    }

    @Test
    void followShouldRollbackStateWhenPublisherFailsForCompensatingRepository() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        BlockService blockService = new BlockService(new InMemoryBlockRepository(), new InMemorySocialEventPublisher());
        FollowService service = new FollowService(repo, new FailingSocialEventPublisher(), blockService);
        java.util.UUID actorUserId = uuid(1);
        java.util.UUID targetUserId = uuid(2);

        FollowRequest req = new FollowRequest();
        req.setEntityType(3);
        req.setEntityId(targetUserId);
        req.setEntityUserId(targetUserId);

        assertThatThrownBy(() -> service.follow(actorUserId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(service.hasFollowed(actorUserId, 3, targetUserId)).isFalse();
        assertThat(service.followeeCount(actorUserId, 3)).isEqualTo(0);
        assertThat(service.followerCount(3, targetUserId)).isEqualTo(0);
    }

    private static class FailingSocialEventPublisher implements SocialEventPublisher {

        @Override
        public void publishLikeCreated(LikePayload payload) {
            throw new IllegalStateException("publish failed");
        }

        @Override
        public void publishLikeRemoved(LikePayload payload) {
            throw new IllegalStateException("publish failed");
        }

        @Override
        public void publishFollowCreated(FollowPayload payload) {
            throw new IllegalStateException("publish failed");
        }

        @Override
        public void publishBlockRelationChanged(BlockPayload payload) {
            throw new IllegalStateException("publish failed");
        }
    }
}

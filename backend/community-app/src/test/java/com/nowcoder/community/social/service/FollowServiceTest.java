package com.nowcoder.community.social.service;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.block.InMemoryBlockRepository;
import com.nowcoder.community.social.event.InMemorySocialEventPublisher;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.follow.InMemoryFollowRepository;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowServiceTest {

    @Test
    void followShouldBeForbiddenWhenEitherBlockedOnCreate() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        InMemoryBlockRepository blockRepository = new InMemoryBlockRepository();
        // 模拟“关注者拉黑了被关注者”
        blockRepository.block(1, 2);
        BlockService blockService = new BlockService(blockRepository, new InMemorySocialEventPublisher());

        FollowService service = new FollowService(repo, publisher, blockService);

        FollowRequest req = new FollowRequest();
        req.setEntityType(3);
        req.setEntityId(2);
        req.setEntityUserId(2);

        assertThatThrownBy(() -> service.follow(1, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        assertThat(service.hasFollowed(1, 3, 2)).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void followShouldBeIdempotentAndPublishOnce() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        BlockService blockService = new BlockService(new InMemoryBlockRepository(), new InMemorySocialEventPublisher());
        FollowService service = new FollowService(repo, publisher, blockService);

        FollowRequest req = new FollowRequest();
        req.setEntityType(3);
        req.setEntityId(2);
        req.setEntityUserId(2);

        service.follow(1, req);
        assertThat(service.hasFollowed(1, 3, 2)).isTrue();
        assertThat(service.followeeCount(1, 3)).isEqualTo(1);
        assertThat(service.followerCount(3, 2)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);
        assertThat(publisher.snapshot().get(0)).isInstanceOf(FollowPayload.class);

        service.follow(1, req);
        assertThat(service.hasFollowed(1, 3, 2)).isTrue();
        assertThat(service.followeeCount(1, 3)).isEqualTo(1);
        assertThat(service.followerCount(3, 2)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);

        service.unfollow(1, 3, 2);
        assertThat(service.hasFollowed(1, 3, 2)).isFalse();
        assertThat(service.followeeCount(1, 3)).isEqualTo(0);
        assertThat(service.followerCount(3, 2)).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(1);
    }

    @Test
    void followShouldRollbackStateWhenPublisherFailsForCompensatingRepository() {
        InMemoryFollowRepository repo = new InMemoryFollowRepository();
        BlockService blockService = new BlockService(new InMemoryBlockRepository(), new InMemorySocialEventPublisher());
        FollowService service = new FollowService(repo, new FailingSocialEventPublisher(), blockService);

        FollowRequest req = new FollowRequest();
        req.setEntityType(3);
        req.setEntityId(2);
        req.setEntityUserId(2);

        assertThatThrownBy(() -> service.follow(1, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(service.hasFollowed(1, 3, 2)).isFalse();
        assertThat(service.followeeCount(1, 3)).isEqualTo(0);
        assertThat(service.followerCount(3, 2)).isEqualTo(0);
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

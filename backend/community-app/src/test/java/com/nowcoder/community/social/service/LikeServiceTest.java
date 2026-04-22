package com.nowcoder.community.social.service;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.block.InMemoryBlockRepository;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.event.InMemorySocialEventPublisher;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.like.InMemoryLikeRepository;
import com.nowcoder.community.social.like.LikeService;
import com.nowcoder.community.social.like.dto.LikeRequest;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeServiceTest {

    @Test
    void likeShouldBeForbiddenWhenEitherBlockedOnCreate() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        ContentEntityResolver resolver = Mockito.mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(1, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

        InMemoryBlockRepository blockRepository = new InMemoryBlockRepository();
        // 模拟“被点赞用户拉黑了点赞者”
        blockRepository.block(uuid(2), uuid(1));
        BlockService blockService = new BlockService(blockRepository, new InMemorySocialEventPublisher());

        LikeService service = new LikeService(repo, publisher, resolver, blockService);

        LikeRequest req = new LikeRequest();
        req.setEntityType(1);
        req.setEntityId(uuid(100));
        req.setLiked(true);

        assertThatThrownBy(() -> service.setLike(uuid(1), req))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        assertThat(repo.isLiked(uuid(1), 1, uuid(100))).isFalse();
        assertThat(repo.countEntityLikes(1, uuid(100))).isEqualTo(0);
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void likeShouldBeIdempotentAndPublishOnce() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        ContentEntityResolver resolver = Mockito.mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(1, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

        BlockService blockService = new BlockService(new InMemoryBlockRepository(), new InMemorySocialEventPublisher());
        LikeService service = new LikeService(repo, publisher, resolver, blockService);

        LikeRequest req = new LikeRequest();
        req.setEntityType(1);
        req.setEntityId(uuid(100));
        // 伪造注入：应被服务端 resolve 覆盖
        req.setEntityUserId(uuid(999));
        req.setPostId(uuid(999));
        req.setLiked(true);

        var r1 = service.setLike(uuid(1), req);
        assertThat(r1.isLiked()).isTrue();
        assertThat(r1.getLikeCount()).isEqualTo(1);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);
        assertThat(publisher.snapshot().get(0)).isInstanceOf(LikePayload.class);

        var r2 = service.setLike(uuid(1), req);
        assertThat(r2.isLiked()).isTrue();
        assertThat(r2.getLikeCount()).isEqualTo(1);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);

        req.setLiked(false);
        var r3 = service.setLike(uuid(1), req);
        assertThat(r3.isLiked()).isFalse();
        assertThat(r3.getLikeCount()).isEqualTo(0);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(2);

        var r4 = service.setLike(uuid(1), req);
        assertThat(r4.isLiked()).isFalse();
        assertThat(r4.getLikeCount()).isEqualTo(0);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(2);
    }

    @Test
    void likeShouldRollbackStateWhenPublisherFailsForCompensatingRepository() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        ContentEntityResolver resolver = Mockito.mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(1, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

        BlockService blockService = new BlockService(new InMemoryBlockRepository(), new InMemorySocialEventPublisher());
        LikeService service = new LikeService(repo, new FailingSocialEventPublisher(), resolver, blockService);

        LikeRequest req = new LikeRequest();
        req.setEntityType(1);
        req.setEntityId(uuid(100));
        req.setLiked(true);

        assertThatThrownBy(() -> service.setLike(uuid(1), req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(repo.isLiked(uuid(1), 1, uuid(100))).isFalse();
        assertThat(repo.countEntityLikes(1, uuid(100))).isEqualTo(0);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(0);
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

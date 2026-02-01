package com.nowcoder.community.social.service;

import com.nowcoder.community.common.event.payload.LikePayload;
import com.nowcoder.community.common.internal.dto.EntityResolveResponse;
import com.nowcoder.community.social.event.InMemorySocialEventPublisher;
import com.nowcoder.community.social.like.InMemoryLikeRepository;
import com.nowcoder.community.social.like.LikeService;
import com.nowcoder.community.social.like.dto.LikeRequest;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeServiceTest {

    @Test
    void likeShouldBeIdempotentAndPublishOnce() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        ContentServiceClient contentServiceClient = Mockito.mock(ContentServiceClient.class);
        EntityResolveResponse resolved = new EntityResolveResponse();
        resolved.setEntityType(1);
        resolved.setEntityId(100);
        resolved.setEntityUserId(2);
        resolved.setPostId(100);
        Mockito.when(contentServiceClient.resolveEntity(1, 100)).thenReturn(resolved);

        LikeService service = new LikeService(repo, publisher, contentServiceClient);

        LikeRequest req = new LikeRequest();
        req.setEntityType(1);
        req.setEntityId(100);
        // 伪造注入：应被服务端 resolve 覆盖
        req.setEntityUserId(999);
        req.setPostId(999);
        req.setLiked(true);

        var r1 = service.setLike(1, req);
        assertThat(r1.isLiked()).isTrue();
        assertThat(r1.getLikeCount()).isEqualTo(1);
        assertThat(service.userLikeCount(2)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);
        assertThat(publisher.snapshot().get(0)).isInstanceOf(LikePayload.class);

        var r2 = service.setLike(1, req);
        assertThat(r2.isLiked()).isTrue();
        assertThat(r2.getLikeCount()).isEqualTo(1);
        assertThat(service.userLikeCount(2)).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);

        req.setLiked(false);
        var r3 = service.setLike(1, req);
        assertThat(r3.isLiked()).isFalse();
        assertThat(r3.getLikeCount()).isEqualTo(0);
        assertThat(service.userLikeCount(2)).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(2);

        var r4 = service.setLike(1, req);
        assertThat(r4.isLiked()).isFalse();
        assertThat(r4.getLikeCount()).isEqualTo(0);
        assertThat(service.userLikeCount(2)).isEqualTo(0);
        assertThat(publisher.snapshot()).hasSize(2);
    }
}

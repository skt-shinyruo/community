package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.LikeDomainService;
import com.nowcoder.community.social.infrastructure.event.InMemorySocialDomainEventPublisher;
import com.nowcoder.community.social.infrastructure.persistence.InMemoryBlockRepository;
import com.nowcoder.community.social.infrastructure.persistence.InMemoryLikeRepository;
import com.nowcoder.community.user.api.action.UserPointsAwardActionApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LikeApplicationServiceTest {

    @Test
    void likeShouldBeForbiddenWhenEitherBlockedOnCreate() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

        InMemoryBlockRepository blockRepository = new InMemoryBlockRepository();
        blockRepository.block(uuid(2), uuid(1));
        LikeApplicationService service = newService(repo, blockRepository, publisher, resolver, null, null);

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isEqualTo(0);
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void likeShouldBeIdempotentAndPublishOnce() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

        LikeApplicationService service = newService(repo, new InMemoryBlockRepository(), publisher, resolver, null, null);

        LikeResult r1 = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        assertThat(r1.liked()).isTrue();
        assertThat(r1.likeCount()).isEqualTo(1);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(1);
        assertThat(publisher.snapshot()).hasSize(1);
        assertThat(publisher.snapshot().get(0)).isInstanceOf(LikeChangedDomainEvent.class);

        LikeResult r2 = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        assertThat(r2.liked()).isTrue();
        assertThat(r2.likeCount()).isEqualTo(1);
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
    void likeShouldRollbackStateWhenPublisherFailsForCompensatingRepository() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));

        LikeApplicationService service = newService(
                repo,
                new InMemoryBlockRepository(),
                new FailingSocialDomainEventPublisher(),
                resolver,
                null,
                null
        );

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("publish failed");

        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isEqualTo(0);
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(0);
    }

    @Test
    void likeShouldTriggerLocalSideEffectsBeforePublishingSocialEvent() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        SocialDomainEventPublisher publisher = mock(SocialDomainEventPublisher.class);
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));
        UserPointsAwardActionApi pointsAwardService = mock(UserPointsAwardActionApi.class);
        GrowthTaskProgressActionApi taskProgressTriggerService = mock(GrowthTaskProgressActionApi.class);

        LikeApplicationService service = newService(
                repo,
                new InMemoryBlockRepository(),
                publisher,
                resolver,
                pointsAwardService,
                taskProgressTriggerService
        );

        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));

        ArgumentCaptor<String> pointsEventId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> taskEventId = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = Mockito.inOrder(pointsAwardService, taskProgressTriggerService, publisher);
        inOrder.verify(pointsAwardService).awardLikeCreated(pointsEventId.capture(), any(LikePayload.class));
        inOrder.verify(taskProgressTriggerService).triggerLikeCreated(taskEventId.capture(), any(LikePayload.class));
        inOrder.verify(publisher).publishLikeChanged(any(LikeChangedDomainEvent.class));
        assertThat(pointsEventId.getValue()).startsWith("like-created:");
        assertThat(taskEventId.getValue()).isEqualTo(pointsEventId.getValue());
    }

    @Test
    void likeShouldRollbackStateWhenPointsAwardFailsForCompensatingRepository() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));
        UserPointsAwardActionApi pointsAwardService = mock(UserPointsAwardActionApi.class);
        GrowthTaskProgressActionApi taskProgressTriggerService = mock(GrowthTaskProgressActionApi.class);
        doThrow(new IllegalStateException("award failed")).when(pointsAwardService).awardLikeCreated(anyString(), any(LikePayload.class));

        LikeApplicationService service = newService(
                repo,
                new InMemoryBlockRepository(),
                mock(SocialDomainEventPublisher.class),
                resolver,
                pointsAwardService,
                taskProgressTriggerService
        );

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("award failed");

        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
        assertThat(repo.countEntityLikes(POST, uuid(100))).isEqualTo(0);
        verify(taskProgressTriggerService, never()).triggerLikeCreated(anyString(), any(LikePayload.class));
    }

    private LikeApplicationService newService(
            InMemoryLikeRepository likeRepository,
            InMemoryBlockRepository blockRepository,
            SocialDomainEventPublisher publisher,
            ContentEntityResolver resolver,
            UserPointsAwardActionApi pointsAwardActionApi,
            GrowthTaskProgressActionApi taskProgressActionApi
    ) {
        return new LikeApplicationService(
                likeRepository,
                blockRepository,
                new LikeDomainService(),
                new BlockDomainService(),
                resolver,
                publisher,
                pointsAwardActionApi,
                taskProgressActionApi
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

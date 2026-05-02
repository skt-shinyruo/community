package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.exception.ContentErrorCode;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.growth.api.model.GrowthLikeTaskProgressRequest;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
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
import com.nowcoder.community.user.api.model.UserLikePointsAwardRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void unlikeShouldRemoveExistingLikeWhenContentNoLongerResolves() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100)))
                .thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "content not found"));

        LikeApplicationService service = newService(repo, new InMemoryBlockRepository(), publisher, resolver, null, null);

        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        LikeResult result = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));

        assertThat(result.liked()).isFalse();
        assertThat(result.likeCount()).isEqualTo(0);
        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
    }

    @Test
    void unlikeShouldRemoveExistingLikeWhenContentDomainReportsPostNotFound() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100)))
                .thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)))
                .thenThrow(new BusinessException(ContentErrorCode.POST_NOT_FOUND));

        LikeApplicationService service = newService(repo, new InMemoryBlockRepository(), publisher, resolver, null, null);

        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        LikeResult result = service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));

        assertThat(result.liked()).isFalse();
        assertThat(result.likeCount()).isEqualTo(0);
        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
    }

    @Test
    void unlikeShouldDecrementStoredOwnerCountWhenContentNoLongerResolves() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100)))
                .thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "content not found"));
        LikeApplicationService service = newService(repo, new InMemoryBlockRepository(), publisher, resolver, null, null);

        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));
        assertThat(service.userLikeCount(uuid(2))).isEqualTo(1);
        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));

        assertThat(service.userLikeCount(uuid(2))).isZero();
    }

    @Test
    void deleteLikesByEntityShouldDecrementStoredOwnerCounts() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();

        assertThat(repo.setLike(uuid(1), POST, uuid(100), uuid(2), true)).isTrue();
        assertThat(repo.setLike(uuid(3), POST, uuid(100), uuid(2), true)).isTrue();
        assertThat(repo.getUserLikeCount(uuid(2))).isEqualTo(2);

        assertThat(repo.deleteLikesByEntity(POST, uuid(100))).isEqualTo(2);

        assertThat(repo.countEntityLikes(POST, uuid(100))).isZero();
        assertThat(repo.getUserLikeCount(uuid(2))).isZero();
    }

    @Test
    void likeShouldStillFailWhenContentDomainReportsPostNotFoundOnCreate() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100)))
                .thenThrow(new BusinessException(ContentErrorCode.POST_NOT_FOUND));

        LikeApplicationService service = newService(repo, new InMemoryBlockRepository(), publisher, resolver, null, null);

        assertThatThrownBy(() -> service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ContentErrorCode.POST_NOT_FOUND));
        assertThat(repo.isLiked(uuid(1), POST, uuid(100))).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
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

        ArgumentCaptor<UserLikePointsAwardRequest> pointsRequest = ArgumentCaptor.forClass(UserLikePointsAwardRequest.class);
        ArgumentCaptor<GrowthLikeTaskProgressRequest> growthRequest = ArgumentCaptor.forClass(GrowthLikeTaskProgressRequest.class);
        InOrder inOrder = Mockito.inOrder(pointsAwardService, taskProgressTriggerService, publisher);
        inOrder.verify(pointsAwardService).awardLikeCreated(pointsRequest.capture());
        inOrder.verify(taskProgressTriggerService).triggerLikeCreated(growthRequest.capture());
        inOrder.verify(publisher).publishLikeChanged(any(LikeChangedDomainEvent.class));
        assertThat(pointsRequest.getValue().sourceEventId()).startsWith("like-created-points:");
        assertThat(pointsRequest.getValue().actorUserId()).isEqualTo(uuid(1));
        assertThat(pointsRequest.getValue().entityUserId()).isEqualTo(uuid(2));
        assertThat(growthRequest.getValue().sourceEventId()).isEqualTo("like-created:" + uuid(1) + ":" + POST + ":" + uuid(100));
        assertThat(growthRequest.getValue().actorUserId()).isEqualTo(uuid(1));
        assertThat(growthRequest.getValue().entityUserId()).isEqualTo(uuid(2));
        assertThat(growthRequest.getValue().createTime()).isNotNull();
    }

    @Test
    void likeUnlikeRelikeShouldUseDistinctPointsIdsButStableGrowthId() {
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
        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), false));
        service.setLike(new SetLikeCommand(uuid(1), POST, uuid(100), true));

        ArgumentCaptor<UserLikePointsAwardRequest> pointsCreateRequests = ArgumentCaptor.forClass(UserLikePointsAwardRequest.class);
        ArgumentCaptor<UserLikePointsAwardRequest> pointsRemoveRequests = ArgumentCaptor.forClass(UserLikePointsAwardRequest.class);
        ArgumentCaptor<GrowthLikeTaskProgressRequest> growthRequests = ArgumentCaptor.forClass(GrowthLikeTaskProgressRequest.class);
        verify(pointsAwardService, times(2)).awardLikeCreated(pointsCreateRequests.capture());
        verify(pointsAwardService).awardLikeRemoved(pointsRemoveRequests.capture());
        verify(taskProgressTriggerService, times(2)).triggerLikeCreated(growthRequests.capture());
        List<String> pointsCreateIds = pointsCreateRequests.getAllValues().stream()
                .map(UserLikePointsAwardRequest::sourceEventId)
                .toList();
        assertThat(pointsCreateIds).hasSize(2).doesNotHaveDuplicates();
        assertThat(pointsCreateIds).allSatisfy(id -> assertThat(id).startsWith("like-created-points:"));
        assertThat(pointsRemoveRequests.getValue().sourceEventId()).startsWith("like-removed-points:");
        assertThat(pointsRemoveRequests.getValue().sourceEventId()).isNotIn(pointsCreateIds);
        assertThat(growthRequests.getAllValues())
                .extracting(GrowthLikeTaskProgressRequest::sourceEventId)
                .containsExactly(
                        "like-created:" + uuid(1) + ":" + POST + ":" + uuid(100),
                        "like-created:" + uuid(1) + ":" + POST + ":" + uuid(100)
                );
    }

    @Test
    void likeShouldRollbackStateWhenPointsAwardFailsForCompensatingRepository() {
        InMemoryLikeRepository repo = new InMemoryLikeRepository();
        ContentEntityResolver resolver = mock(ContentEntityResolver.class);
        Mockito.when(resolver.resolve(POST, uuid(100))).thenReturn(new ContentEntityResolver.ResolvedEntity(uuid(2), uuid(100)));
        UserPointsAwardActionApi pointsAwardService = mock(UserPointsAwardActionApi.class);
        GrowthTaskProgressActionApi taskProgressTriggerService = mock(GrowthTaskProgressActionApi.class);
        doThrow(new IllegalStateException("award failed")).when(pointsAwardService).awardLikeCreated(any(UserLikePointsAwardRequest.class));

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
        verify(taskProgressTriggerService, never()).triggerLikeCreated(any(GrowthLikeTaskProgressRequest.class));
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

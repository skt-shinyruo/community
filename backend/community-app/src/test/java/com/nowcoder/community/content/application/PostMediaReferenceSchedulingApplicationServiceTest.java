package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostMediaReferenceSchedulingApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T09:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void deletionSchedulingMustJoinTheExistingPostDeletionTransaction() throws Exception {
        Transactional transactional = PostMediaReferenceSchedulingApplicationService.class
                .getDeclaredMethod("scheduleReleaseForDeletedPost", UUID.class)
                .getAnnotation(Transactional.class);
        if (transactional == null) {
            transactional = PostMediaReferenceSchedulingApplicationService.class
                    .getAnnotation(Transactional.class);
        }

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void deletedPostShouldScheduleEveryUnfinishedReferenceIncludingBindPendingRace() {
        UUID postId = uuid(301);
        UUID ownerId = uuid(7);
        PostMediaAsset bound = asset(uuid(401), ownerId, postId, PostMediaReferenceStatus.BOUND, 3L);
        PostMediaAsset bindPending = asset(uuid(402), ownerId, postId, PostMediaReferenceStatus.BIND_PENDING, 4L);
        PostMediaAsset releasePending = asset(uuid(403), ownerId, postId, PostMediaReferenceStatus.RELEASE_PENDING, 5L);
        PostMediaAsset released = asset(uuid(404), ownerId, postId, PostMediaReferenceStatus.RELEASED, 6L);
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        PostMediaReferenceCommandPublisher publisher = mock(PostMediaReferenceCommandPublisher.class);
        when(repository.listByPostId(postId)).thenReturn(List.of(bound, bindPending, releasePending, released));
        when(repository.requestRelease(eq(bound.id()), any(Date.class))).thenReturn(4L);
        when(repository.requestRelease(eq(bindPending.id()), any(Date.class))).thenReturn(5L);
        PostMediaReferenceSchedulingApplicationService service =
                new PostMediaReferenceSchedulingApplicationService(repository, publisher, CLOCK);

        service.scheduleReleaseForDeletedPost(postId);

        ArgumentCaptor<PostMediaReferenceCommand> commands =
                ArgumentCaptor.forClass(PostMediaReferenceCommand.class);
        verify(publisher, times(3)).publish(commands.capture());
        assertThat(commands.getAllValues()).containsExactly(
                release(bound.id(), 4L, ownerId),
                release(bindPending.id(), 5L, ownerId),
                release(releasePending.id(), 5L, ownerId)
        );
        verify(repository).requestRelease(eq(bound.id()), any(Date.class));
        verify(repository).requestRelease(eq(bindPending.id()), any(Date.class));
    }

    @Test
    void repeatedDeletionShouldReuseCurrentReleaseVersionInsteadOfCreatingAnotherOperation() {
        UUID postId = uuid(302);
        UUID ownerId = uuid(7);
        UUID assetId = uuid(405);
        PostMediaAsset bound = asset(assetId, ownerId, postId, PostMediaReferenceStatus.BOUND, 7L);
        PostMediaAsset pending = asset(assetId, ownerId, postId, PostMediaReferenceStatus.RELEASE_PENDING, 8L);
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        PostMediaReferenceCommandPublisher publisher = mock(PostMediaReferenceCommandPublisher.class);
        when(repository.listByPostId(postId)).thenReturn(List.of(bound), List.of(pending));
        when(repository.requestRelease(eq(assetId), any(Date.class))).thenReturn(8L);
        PostMediaReferenceSchedulingApplicationService service =
                new PostMediaReferenceSchedulingApplicationService(repository, publisher, CLOCK);

        service.scheduleReleaseForDeletedPost(postId);
        service.scheduleReleaseForDeletedPost(postId);

        verify(repository, times(1)).requestRelease(eq(assetId), any(Date.class));
        verify(publisher, times(2)).publish(release(assetId, 8L, ownerId));
    }

    @Test
    void schedulingServiceMustNotOwnOssOrPersistenceImplementationCollaborators() {
        assertThat(List.of(PostMediaReferenceSchedulingApplicationService.class.getDeclaredFields()).stream()
                .map(Field::getType)
                .map(Class::getName))
                .noneMatch(type -> type.equals(PostMediaStoragePort.class.getName())
                        || type.contains(".infrastructure.")
                        || type.contains(".mapper."));
    }

    private PostMediaReferenceCommand release(UUID assetId, long version, UUID actorId) {
        return new PostMediaReferenceCommand(assetId, PostMediaReferenceOperation.RELEASE, version, actorId);
    }

    private PostMediaAsset asset(
            UUID assetId,
            UUID ownerId,
            UUID postId,
            PostMediaReferenceStatus status,
            long version
    ) {
        PostMediaAssetLifecycle lifecycle = switch (status) {
            case BIND_PENDING -> PostMediaAssetLifecycle.UPLOADED;
            case RELEASED -> PostMediaAssetLifecycle.RELEASED;
            default -> PostMediaAssetLifecycle.BOUND;
        };
        return new PostMediaAsset(
                assetId,
                ownerId,
                postId,
                uuid(801),
                uuid(802),
                uuid(803),
                null,
                "asset.bin",
                "application/octet-stream",
                128L,
                PostMediaKind.FILE,
                lifecycle,
                status,
                version,
                Date.from(NOW),
                PostVideoState.NONE,
                "https://cdn.example.com/asset.bin",
                "",
                Date.from(NOW),
                Date.from(NOW)
        );
    }
}

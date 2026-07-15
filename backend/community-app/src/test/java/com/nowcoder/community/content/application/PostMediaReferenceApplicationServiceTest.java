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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PostMediaReferenceApplicationServiceTest {

    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000003001");
    private static final UUID ACTOR_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000003002");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000003003");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-7000-8000-000000003004");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000003005");
    private static final UUID OBJECT_VERSION_ID = UUID.fromString("00000000-0000-7000-8000-000000003006");
    private static final long BIND_VERSION = 7L;
    private static final long RELEASE_VERSION = 8L;
    private static final Instant NOW = Instant.parse("2026-07-15T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void bindShouldRemainRetryableWhenOssFails() {
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        PostMediaAsset pending = asset(PostMediaReferenceStatus.BIND_PENDING, BIND_VERSION, PostMediaAssetLifecycle.UPLOADED);
        RuntimeException ossFailure = new IllegalStateException("oss unavailable");
        when(repository.getRequired(ASSET_ID)).thenReturn(pending);
        when(storage.bindReference(pending, POST_ID, REFERENCE_ID, ACTOR_USER_ID))
                .thenThrow(ossFailure)
                .thenReturn(REFERENCE_ID);
        when(repository.markBound(ASSET_ID, BIND_VERSION, Date.from(NOW))).thenReturn(true);
        PostMediaReferenceApplicationService service = service(repository, storage);

        assertThatThrownBy(() -> service.process(bindCommand()))
                .isSameAs(ossFailure);
        service.process(bindCommand());

        verify(storage, times(2)).bindReference(pending, POST_ID, REFERENCE_ID, ACTOR_USER_ID);
        verify(repository).markBound(ASSET_ID, BIND_VERSION, Date.from(NOW));
    }

    @Test
    void bindShouldReplayTheSameDeterministicReferenceWhenDbFinalizeFails() {
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        PostMediaAsset pending = asset(PostMediaReferenceStatus.BIND_PENDING, BIND_VERSION, PostMediaAssetLifecycle.UPLOADED);
        RuntimeException finalizeFailure = new IllegalStateException("content finalize failed");
        when(repository.getRequired(ASSET_ID)).thenReturn(pending);
        when(storage.bindReference(pending, POST_ID, REFERENCE_ID, ACTOR_USER_ID)).thenReturn(REFERENCE_ID);
        when(repository.markBound(ASSET_ID, BIND_VERSION, Date.from(NOW)))
                .thenThrow(finalizeFailure)
                .thenReturn(true);
        PostMediaReferenceApplicationService service = service(repository, storage);

        assertThatThrownBy(() -> service.process(bindCommand()))
                .isSameAs(finalizeFailure);
        service.process(bindCommand());

        verify(storage, times(2)).bindReference(pending, POST_ID, REFERENCE_ID, ACTOR_USER_ID);
        verify(repository, times(2)).markBound(ASSET_ID, BIND_VERSION, Date.from(NOW));
    }

    @Test
    void replayAfterHandlerCompletionShouldNotRepeatTheRemoteBind() {
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        PostMediaAsset pending = asset(PostMediaReferenceStatus.BIND_PENDING, BIND_VERSION, PostMediaAssetLifecycle.UPLOADED);
        PostMediaAsset completed = asset(PostMediaReferenceStatus.BOUND, BIND_VERSION, PostMediaAssetLifecycle.BOUND);
        when(repository.getRequired(ASSET_ID)).thenReturn(pending, completed);
        when(storage.bindReference(pending, POST_ID, REFERENCE_ID, ACTOR_USER_ID)).thenReturn(REFERENCE_ID);
        when(repository.markBound(ASSET_ID, BIND_VERSION, Date.from(NOW))).thenReturn(true);
        PostMediaReferenceApplicationService service = service(repository, storage);

        service.process(bindCommand());
        service.process(bindCommand());

        verify(storage).bindReference(pending, POST_ID, REFERENCE_ID, ACTOR_USER_ID);
        verify(repository).markBound(ASSET_ID, BIND_VERSION, Date.from(NOW));
    }

    @Test
    void staleOperationVersionShouldNotCallOssOrFinalizeANewerOperation() {
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        PostMediaAsset newerRelease = asset(
                PostMediaReferenceStatus.RELEASE_PENDING,
                RELEASE_VERSION,
                PostMediaAssetLifecycle.BOUND
        );
        when(repository.getRequired(ASSET_ID)).thenReturn(newerRelease);
        PostMediaReferenceApplicationService service = service(repository, storage);

        service.process(bindCommand());

        verify(repository).getRequired(ASSET_ID);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(storage);
    }

    @Test
    void releaseShouldKeepTheReferenceAvailableUntilOssSucceedsAndThenRetryFinalize() {
        PostMediaAssetRepository repository = mock(PostMediaAssetRepository.class);
        PostMediaStoragePort storage = mock(PostMediaStoragePort.class);
        PostMediaAsset pending = asset(
                PostMediaReferenceStatus.RELEASE_PENDING,
                RELEASE_VERSION,
                PostMediaAssetLifecycle.BOUND
        );
        RuntimeException ossFailure = new IllegalStateException("oss release unavailable");
        when(repository.getRequired(ASSET_ID)).thenReturn(pending);
        doThrow(ossFailure)
                .doNothing()
                .when(storage).releaseReference(pending, ACTOR_USER_ID);
        when(repository.markReleased(ASSET_ID, RELEASE_VERSION, Date.from(NOW))).thenReturn(true);
        PostMediaReferenceApplicationService service = service(repository, storage);

        assertThatThrownBy(() -> service.process(releaseCommand()))
                .isSameAs(ossFailure);
        verify(repository, never()).markReleased(eq(ASSET_ID), eq(RELEASE_VERSION), eq(Date.from(NOW)));

        service.process(releaseCommand());

        verify(storage, times(2)).releaseReference(pending, ACTOR_USER_ID);
        verify(repository).markReleased(ASSET_ID, RELEASE_VERSION, Date.from(NOW));
    }

    private static PostMediaReferenceApplicationService service(
            PostMediaAssetRepository repository,
            PostMediaStoragePort storage
    ) {
        return new PostMediaReferenceApplicationService(repository, storage, CLOCK);
    }

    private static PostMediaReferenceCommand bindCommand() {
        return new PostMediaReferenceCommand(
                ASSET_ID,
                PostMediaReferenceOperation.BIND,
                BIND_VERSION,
                ACTOR_USER_ID
        );
    }

    private static PostMediaReferenceCommand releaseCommand() {
        return new PostMediaReferenceCommand(
                ASSET_ID,
                PostMediaReferenceOperation.RELEASE,
                RELEASE_VERSION,
                ACTOR_USER_ID
        );
    }

    private static PostMediaAsset asset(
            PostMediaReferenceStatus status,
            long operationVersion,
            PostMediaAssetLifecycle lifecycle
    ) {
        return new PostMediaAsset(
                ASSET_ID,
                ACTOR_USER_ID,
                POST_ID,
                OBJECT_ID,
                OBJECT_VERSION_ID,
                REFERENCE_ID,
                null,
                "cover.png",
                "image/png",
                256L,
                PostMediaKind.IMAGE,
                lifecycle,
                status,
                operationVersion,
                Date.from(NOW),
                PostVideoState.NONE,
                "https://cdn.example.com/cover.png",
                "",
                Date.from(NOW),
                Date.from(NOW)
        );
    }
}

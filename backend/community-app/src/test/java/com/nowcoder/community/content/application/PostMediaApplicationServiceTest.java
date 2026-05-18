package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.application.command.PreparePostMediaUploadCommand;
import com.nowcoder.community.content.application.PostMediaStoragePort;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostMediaApplicationServiceTest {

    private PostMediaAssetRepository assetRepository;
    private PostMediaStoragePort storagePort;
    private UuidV7Generator idGenerator;
    private PostMediaApplicationService service;

    @BeforeEach
    void setUp() {
        assetRepository = mock(PostMediaAssetRepository.class);
        storagePort = mock(PostMediaStoragePort.class);
        idGenerator = new UuidV7Generator();
        service = new PostMediaApplicationService(assetRepository, storagePort, idGenerator);
    }

    @Test
    void prepareUploadShouldCreateDraftAssetAndReturnUploadSession() {
        UUID userId = uuid(7);
        UUID objectId = uuid(9);
        UUID versionId = uuid(10);
        UUID sessionId = uuid(11);
        when(storagePort.prepareUpload(any(), any())).thenAnswer(invocation -> {
            PostMediaAsset draft = invocation.getArgument(0);
            return new PostMediaUploadSessionResult(
                    draft.id(),
                    sessionId.toString(),
                    "/api/posts/media/" + draft.id() + "/upload",
                    "POST",
                    "file",
                    "uploadId",
                    100 * 1024 * 1024L,
                    "image/png;image/jpeg;image/webp;image/gif;video/mp4;video/webm;application/pdf;application/zip",
                    Instant.parse("2026-05-09T00:10:00Z"),
                    objectId,
                    versionId
            );
        });

        PostMediaUploadSessionResult result = service.prepareUpload(new PreparePostMediaUploadCommand(
                userId,
                "demo.mp4",
                "video/mp4",
                1234,
                "VIDEO",
                "sha256"
        ));

        assertThat(result.assetId()).isNotNull();
        assertThat(result.assetId().version()).isEqualTo(7);
        assertThat(result.uploadUrl()).isEqualTo("/api/posts/media/" + result.assetId() + "/upload");
        var storageDraftCaptor = forClass(PostMediaAsset.class);
        var persistedDraftCaptor = forClass(PostMediaAsset.class);
        InOrder inOrder = inOrder(assetRepository, storagePort);
        inOrder.verify(storagePort).prepareUpload(storageDraftCaptor.capture(), eq("sha256"));
        inOrder.verify(assetRepository).createDraft(persistedDraftCaptor.capture());
        PostMediaAsset storageDraft = storageDraftCaptor.getValue();
        PostMediaAsset persistedDraft = persistedDraftCaptor.getValue();
        assertThat(storageDraft.id()).isEqualTo(result.assetId());
        assertThat(storageDraft.ownerUserId()).isEqualTo(userId);
        assertThat(storageDraft.fileName()).isEqualTo("demo.mp4");
        assertThat(storageDraft.contentType()).isEqualTo("video/mp4");
        assertThat(storageDraft.contentLength()).isEqualTo(1234);
        assertThat(storageDraft.mediaKind()).isEqualTo(PostMediaKind.VIDEO);
        assertThat(storageDraft.lifecycle()).isEqualTo(PostMediaAssetLifecycle.DRAFT);
        assertThat(storageDraft.videoState()).isEqualTo(PostVideoState.NONE);
        assertThat(storageDraft.ossObjectId()).isNull();
        assertThat(storageDraft.ossVersionId()).isNull();
        assertThat(storageDraft.uploadSessionId()).isNull();
        assertThat(persistedDraft.id()).isEqualTo(result.assetId());
        assertThat(persistedDraft.ossObjectId()).isEqualTo(result.ossObjectId());
        assertThat(persistedDraft.ossVersionId()).isEqualTo(result.ossVersionId());
        assertThat(persistedDraft.uploadSessionId()).isEqualTo(sessionId);
    }

    @Test
    void prepareUploadShouldInferImageKindFromContentType() {
        UUID userId = uuid(7);
        when(storagePort.prepareUpload(any(), any())).thenAnswer(invocation -> session(invocation.getArgument(0, PostMediaAsset.class).id()));

        service.prepareUpload(new PreparePostMediaUploadCommand(userId, "cover.png", "image/png", 10, "", ""));

        var captor = forClass(PostMediaAsset.class);
        verify(assetRepository).createDraft(captor.capture());
        assertThat(captor.getValue().mediaKind()).isEqualTo(PostMediaKind.IMAGE);
    }

    @Test
    void prepareUploadShouldRejectInvalidActorAndFileSizeBeforeCreatingDraft() {
        Throwable thrown = catchThrowable(() -> service.prepareUpload(
                new PreparePostMediaUploadCommand(null, "demo.mp4", "video/mp4", 100, "VIDEO", "")
        ));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INVALID_ARGUMENT);
        verifyNoInteractions(assetRepository, storagePort);
    }

    @Test
    void completeUploadShouldRequireOwnerAndDraftThenMarkUploaded() {
        UUID actorUserId = uuid(7);
        UUID assetId = uuid(8);
        UUID uploadSessionId = uuid(11);
        UUID versionId = uuid(12);
        PostMediaAsset draft = draft(assetId, actorUserId, uploadSessionId, PostMediaAssetLifecycle.DRAFT);
        PostMediaUploadContent content = new PostMediaUploadContent(
                () -> new ByteArrayInputStream("media".getBytes()),
                "video/mp4",
                5,
                ""
        );
        when(assetRepository.getRequired(assetId)).thenReturn(draft);
        when(storagePort.completeUpload(draft, uploadSessionId, content)).thenReturn(
                new PostMediaStoragePort.UploadedPostMedia(versionId, "https://cdn.example.com/demo.mp4", "video/mp4", 5)
        );

        service.completeUpload(actorUserId, assetId, uploadSessionId, content);

        InOrder inOrder = inOrder(assetRepository, storagePort);
        inOrder.verify(assetRepository).getRequired(assetId);
        inOrder.verify(storagePort).completeUpload(draft, uploadSessionId, content);
        inOrder.verify(assetRepository).markUploaded(
                org.mockito.ArgumentMatchers.eq(assetId),
                org.mockito.ArgumentMatchers.eq(versionId),
                org.mockito.ArgumentMatchers.eq("https://cdn.example.com/demo.mp4"),
                any(Date.class)
        );
    }

    @Test
    void completeUploadShouldRejectForeignOwner() {
        UUID assetId = uuid(8);
        PostMediaAsset draft = draft(assetId, uuid(7), uuid(11), PostMediaAssetLifecycle.DRAFT);
        when(assetRepository.getRequired(assetId)).thenReturn(draft);

        Throwable thrown = catchThrowable(() -> service.completeUpload(uuid(99), assetId, uuid(11), uploadContent()));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("只能上传自己的媒体资源");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FORBIDDEN);
        verifyNoInteractions(storagePort);
    }

    @Test
    void completeUploadShouldRejectNonDraftAsset() {
        UUID actorUserId = uuid(7);
        UUID assetId = uuid(8);
        when(assetRepository.getRequired(assetId)).thenReturn(
                draft(assetId, actorUserId, uuid(11), PostMediaAssetLifecycle.UPLOADED)
        );

        Throwable thrown = catchThrowable(() -> service.completeUpload(actorUserId, assetId, uuid(11), uploadContent()));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("媒体资源状态不允许上传");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INVALID_ARGUMENT);
        verifyNoInteractions(storagePort);
    }

    private static PostMediaUploadSessionResult session(UUID assetId) {
        return new PostMediaUploadSessionResult(
                assetId,
                uuid(11).toString(),
                "/api/posts/media/" + assetId + "/upload",
                "POST",
                "file",
                "uploadId",
                100 * 1024 * 1024L,
                "image/png;image/jpeg;image/webp;image/gif;video/mp4;video/webm;application/pdf;application/zip",
                Instant.parse("2026-05-09T00:10:00Z"),
                uuid(9),
                uuid(10)
        );
    }

    private static PostMediaUploadContent uploadContent() {
        return new PostMediaUploadContent(
                () -> new ByteArrayInputStream("media".getBytes()),
                "video/mp4",
                5,
                ""
        );
    }

    private static PostMediaAsset draft(UUID assetId, UUID ownerUserId, UUID uploadSessionId, PostMediaAssetLifecycle lifecycle) {
        return new PostMediaAsset(
                assetId,
                ownerUserId,
                null,
                uuid(9),
                uuid(10),
                null,
                uploadSessionId,
                "demo.mp4",
                "video/mp4",
                5,
                PostMediaKind.VIDEO,
                lifecycle,
                PostVideoState.NONE,
                "",
                "",
                new Date(),
                null
        );
    }
}

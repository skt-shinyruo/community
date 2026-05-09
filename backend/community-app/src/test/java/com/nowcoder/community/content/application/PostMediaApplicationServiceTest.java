package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.command.PreparePostMediaUploadCommand;
import com.nowcoder.community.content.application.port.PostMediaStoragePort;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostMediaApplicationServiceTest {

    private PostMediaAssetRepository assetRepository;
    private PostMediaStoragePort storagePort;
    private PostMediaApplicationService service;

    @BeforeEach
    void setUp() {
        assetRepository = mock(PostMediaAssetRepository.class);
        storagePort = mock(PostMediaStoragePort.class);
        service = new PostMediaApplicationService(assetRepository, storagePort);
    }

    @Test
    void prepareUploadShouldCreateDraftAssetAndReturnUploadSession() {
        UUID userId = uuid(7);
        UUID assetId = uuid(8);
        UUID objectId = uuid(9);
        UUID versionId = uuid(10);
        UUID sessionId = uuid(11);
        when(assetRepository.createDraft(any())).thenReturn(assetId);
        when(storagePort.prepareUpload(any(), any())).thenReturn(new PostMediaUploadSessionResult(
                assetId,
                sessionId.toString(),
                "/api/posts/media/" + assetId + "/upload",
                "POST",
                "file",
                "uploadId",
                100 * 1024 * 1024L,
                "image/png;image/jpeg;image/webp;image/gif;video/mp4;video/webm;application/pdf;application/zip",
                Instant.parse("2026-05-09T00:10:00Z"),
                objectId,
                versionId
        ));

        PostMediaUploadSessionResult result = service.prepareUpload(new PreparePostMediaUploadCommand(
                userId,
                "demo.mp4",
                "video/mp4",
                1234,
                "VIDEO",
                "sha256"
        ));

        assertThat(result.assetId()).isEqualTo(assetId);
        assertThat(result.uploadUrl()).isEqualTo("/api/posts/media/" + assetId + "/upload");
        var draftCaptor = forClass(PostMediaAsset.class);
        InOrder inOrder = inOrder(assetRepository, storagePort);
        inOrder.verify(assetRepository).createDraft(draftCaptor.capture());
        inOrder.verify(storagePort).prepareUpload(draftCaptor.capture(), org.mockito.ArgumentMatchers.eq("sha256"));
        PostMediaAsset insertedDraft = draftCaptor.getAllValues().get(0);
        PostMediaAsset storageDraft = draftCaptor.getAllValues().get(1);
        assertThat(insertedDraft.id()).isNull();
        assertThat(insertedDraft.ownerUserId()).isEqualTo(userId);
        assertThat(insertedDraft.fileName()).isEqualTo("demo.mp4");
        assertThat(insertedDraft.contentType()).isEqualTo("video/mp4");
        assertThat(insertedDraft.contentLength()).isEqualTo(1234);
        assertThat(insertedDraft.mediaKind()).isEqualTo(PostMediaKind.VIDEO);
        assertThat(insertedDraft.lifecycle()).isEqualTo(PostMediaAssetLifecycle.DRAFT);
        assertThat(insertedDraft.videoState()).isEqualTo(PostVideoState.NONE);
        assertThat(storageDraft.id()).isEqualTo(assetId);
    }

    @Test
    void prepareUploadShouldInferImageKindFromContentType() {
        UUID userId = uuid(7);
        UUID assetId = uuid(8);
        when(assetRepository.createDraft(any())).thenReturn(assetId);
        when(storagePort.prepareUpload(any(), any())).thenReturn(session(assetId));

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

package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.PostMediaApplicationService;
import com.nowcoder.community.content.application.PostMediaUploadContent;
import com.nowcoder.community.content.application.command.PreparePostMediaUploadCommand;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.controller.dto.PostMediaUploadSessionResponse;
import com.nowcoder.community.content.controller.dto.PreparePostMediaUploadRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostMediaControllerUnitTest {

    private PostMediaApplicationService applicationService;
    private PostMediaController controller;

    @BeforeEach
    void setUp() {
        applicationService = mock(PostMediaApplicationService.class);
        controller = new PostMediaController(applicationService);
    }

    @Test
    void prepareUploadShouldCallSameDomainApplicationServiceAndMapResponse() {
        UUID actorUserId = uuid(7);
        UUID assetId = uuid(8);
        PreparePostMediaUploadRequest request = new PreparePostMediaUploadRequest();
        request.setFileName("demo.mp4");
        request.setContentType("video/mp4");
        request.setContentLength(1234);
        request.setMediaKind("VIDEO");
        request.setChecksumSha256("checksum");
        when(applicationService.prepareUpload(any())).thenReturn(new PostMediaUploadSessionResult(
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
        ));

        Result<PostMediaUploadSessionResponse> result = controller.prepareUpload(authentication(actorUserId), request);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getAssetId()).isEqualTo(assetId);
        assertThat(result.getData().getUpload().getUrl()).isEqualTo("/api/posts/media/" + assetId + "/upload");
        assertThat(result.getData().getUpload().getFileField()).isEqualTo("file");
        assertThat(result.getData().getUpload().getFields()).containsEntry("uploadId", uuid(11).toString());
        assertThat(result.getData().getConstraints().getMimeTypes()).contains("video/mp4", "application/pdf");
        ArgumentCaptor<PreparePostMediaUploadCommand> captor = forClass(PreparePostMediaUploadCommand.class);
        verify(applicationService).prepareUpload(captor.capture());
        assertThat(captor.getValue().actorUserId()).isEqualTo(actorUserId);
        assertThat(captor.getValue().fileName()).isEqualTo("demo.mp4");
        assertThat(captor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(captor.getValue().contentLength()).isEqualTo(1234);
        assertThat(captor.getValue().mediaKind()).isEqualTo("VIDEO");
        assertThat(captor.getValue().checksumSha256()).isEqualTo("checksum");
    }

    @Test
    void uploadShouldAdaptMultipartFileWithoutExposingSpringTypeToApplicationLayer() throws Exception {
        UUID actorUserId = uuid(7);
        UUID assetId = uuid(8);
        UUID uploadId = uuid(11);
        MockMultipartFile file = new MockMultipartFile("file", "demo.mp4", "video/mp4", "media".getBytes());

        Result<Void> result = controller.upload(authentication(actorUserId), assetId, uploadId, file);

        assertThat(result.getCode()).isEqualTo(0);
        ArgumentCaptor<PostMediaUploadContent> captor = forClass(PostMediaUploadContent.class);
        verify(applicationService).completeUpload(
                org.mockito.ArgumentMatchers.eq(actorUserId),
                org.mockito.ArgumentMatchers.eq(assetId),
                org.mockito.ArgumentMatchers.eq(uploadId),
                captor.capture()
        );
        assertThat(captor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(captor.getValue().size()).isEqualTo(5);
        assertThat(captor.getValue().empty()).isFalse();
        assertThat(new String(captor.getValue().openStream().readAllBytes())).isEqualTo("media");
    }

    private static Authentication authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new TestingAuthenticationToken(jwt, null);
    }
}

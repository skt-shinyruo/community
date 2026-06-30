package com.nowcoder.community.content.infrastructure.oss;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.PostMediaUploadContent;
import com.nowcoder.community.content.application.PostMediaStoragePort;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OssPostMediaStorageAdapterTest {

    @Test
    void prepareUploadShouldUseContentPostMediaOwnerContext() {
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        UUID sessionId = uuid(21);
        UUID objectId = uuid(22);
        UUID versionId = uuid(23);
        when(ossClient.prepareUpload(any())).thenReturn(new OssUploadSessionResponse(
                sessionId,
                objectId,
                versionId,
                "PROXY",
                "/api/oss/objects/" + objectId + "/complete",
                Instant.parse("2026-05-09T00:10:00Z")
        ));
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(ossClient);
        PostMediaAsset draft = draft(uuid(1), uuid(2), null, null, null);

        PostMediaUploadSessionResult result = adapter.prepareUpload(draft, "checksum");

        assertThat(result.assetId()).isEqualTo(draft.id());
        assertThat(result.uploadId()).isEqualTo(sessionId.toString());
        assertThat(result.uploadUrl()).isEqualTo("/api/posts/media/" + draft.id() + "/upload");
        assertThat(result.uploadMethod()).isEqualTo("POST");
        assertThat(result.fileField()).isEqualTo("file");
        assertThat(result.uploadIdField()).isEqualTo("uploadId");
        assertThat(result.ossObjectId()).isEqualTo(objectId);
        assertThat(result.ossVersionId()).isEqualTo(versionId);
        var captor = forClass(OssUploadSessionRequest.class);
        verify(ossClient).prepareUpload(captor.capture());
        assertThat(captor.getValue().usage()).isEqualTo("CONTENT_POST_MEDIA");
        assertThat(captor.getValue().ownerService()).isEqualTo("community-app");
        assertThat(captor.getValue().ownerDomain()).isEqualTo("content");
        assertThat(captor.getValue().ownerType()).isEqualTo("post-media-draft");
        assertThat(captor.getValue().ownerId()).isEqualTo(draft.id().toString());
        assertThat(captor.getValue().visibility()).isEqualTo("PUBLIC");
        assertThat(captor.getValue().fileName()).isEqualTo("demo.mp4");
        assertThat(captor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(captor.getValue().checksumSha256()).isEqualTo("checksum");
    }

    @Test
    void completeUploadShouldProxyStreamToOssClient() throws Exception {
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        UUID objectId = uuid(22);
        UUID versionId = uuid(23);
        when(ossClient.completeProxyUpload(any())).thenReturn(new OssMetadataResponse(
                objectId,
                versionId,
                "CONTENT_POST_MEDIA",
                "community-app",
                "content",
                "post-media-draft",
                uuid(1).toString(),
                "PUBLIC",
                "ACTIVE",
                "demo.mp4",
                "video/mp4",
                5,
                "checksum",
                "https://cdn.example.com/demo.mp4"
        ));
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(ossClient);
        PostMediaAsset draft = draft(uuid(1), uuid(2), uuid(21), objectId, versionId);
        PostMediaUploadContent content = new PostMediaUploadContent(
                () -> new ByteArrayInputStream("media".getBytes()),
                "video/mp4",
                5,
                "checksum"
        );

        PostMediaStoragePort.UploadedPostMedia uploaded = adapter.completeUpload(draft, uuid(21), content);

        assertThat(uploaded.versionId()).isEqualTo(versionId);
        assertThat(uploaded.publicUrl()).isEqualTo("https://cdn.example.com/demo.mp4");
        var captor = forClass(OssCompleteUploadRequest.class);
        verify(ossClient).completeProxyUpload(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo(uuid(21));
        assertThat(captor.getValue().objectId()).isEqualTo(objectId);
        assertThat(captor.getValue().versionId()).isEqualTo(versionId);
        assertThat(captor.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(captor.getValue().contentLength()).isEqualTo(5);
        assertThat(new String(captor.getValue().openStream().readAllBytes())).isEqualTo("media");
    }

    @Test
    void bindReferenceShouldUsePostSubjectContext() {
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        UUID referenceId = uuid(31);
        UUID objectId = uuid(22);
        UUID versionId = uuid(23);
        when(ossClient.bindObjectReference(any(), any())).thenReturn(new OssReferenceResponse(
                referenceId,
                objectId,
                versionId,
                "community-app",
                "content",
                "post",
                uuid(40).toString(),
                "POST_MEDIA",
                "ACTIVE",
                null,
                Instant.parse("2026-05-09T00:10:00Z"),
                null
        ));
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(ossClient);
        PostMediaAsset asset = draft(uuid(1), uuid(2), uuid(21), objectId, versionId);

        UUID result = adapter.bindReference(asset, uuid(40), uuid(2));

        assertThat(result).isEqualTo(referenceId);
        var captor = forClass(OssBindReferenceRequest.class);
        verify(ossClient).bindObjectReference(org.mockito.ArgumentMatchers.eq(objectId), captor.capture());
        assertThat(captor.getValue().versionId()).isEqualTo(versionId.toString());
        assertThat(captor.getValue().subjectType()).isEqualTo("post");
        assertThat(captor.getValue().subjectId()).isEqualTo(uuid(40).toString());
        assertThat(captor.getValue().referenceRole()).isEqualTo("POST_MEDIA");
        assertThat(captor.getValue().actorId()).isEqualTo(uuid(2).toString());
    }

    @Test
    void releaseReferenceShouldIgnoreAssetsWithoutReferenceId() {
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(ossClient);

        adapter.releaseReference(draft(uuid(1), uuid(2), uuid(21), uuid(22), uuid(23)), uuid(2));

        verifyNoInteractions(ossClient);
    }

    @Test
    void deleteDraftObjectShouldDeletePreparedObjectByOwner() {
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(ossClient);
        PostMediaAsset asset = draft(uuid(1), uuid(2), uuid(21), uuid(22), uuid(23));

        adapter.deleteDraftObject(asset, uuid(2));

        verify(ossClient).deleteObject(uuid(22), uuid(2).toString());
    }

    @Test
    void prepareUploadShouldRejectNullOssResponse() {
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        when(ossClient.prepareUpload(any())).thenReturn(null);
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(ossClient);

        Throwable thrown = catchThrowable(() -> adapter.prepareUpload(draft(uuid(1), uuid(2), null, null, null), ""));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("签发媒体上传参数失败");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INTERNAL_ERROR);
    }

    private static PostMediaAsset draft(UUID assetId, UUID ownerUserId, UUID uploadSessionId, UUID objectId, UUID versionId) {
        return new PostMediaAsset(
                assetId,
                ownerUserId,
                null,
                objectId,
                versionId,
                null,
                uploadSessionId,
                "demo.mp4",
                "video/mp4",
                12,
                PostMediaKind.VIDEO,
                PostMediaAssetLifecycle.DRAFT,
                PostVideoState.NONE,
                "",
                "",
                new Date(),
                null
        );
    }
}

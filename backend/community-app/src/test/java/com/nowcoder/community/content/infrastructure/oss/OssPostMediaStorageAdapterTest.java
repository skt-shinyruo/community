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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    void completeUploadMustRejectForeignLaterOrContentDriftedMetadata() {
        PostMediaAsset asset = draft(uuid(1), uuid(2), uuid(21), uuid(22), uuid(23));
        AtomicReference<OssMetadataResponse> response = new AtomicReference<>();
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        when(ossClient.completeProxyUpload(any())).thenAnswer(invocation -> response.get());
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(ossClient);
        PostMediaUploadContent content = new PostMediaUploadContent(
                () -> new ByteArrayInputStream("media".getBytes()),
                "video/mp4",
                5,
                "checksum"
        );
        List<OssMetadataResponse> invalidResponses = List.of(
                metadata(asset).objectId(uuid(90)).build(),
                metadata(asset).versionId(uuid(91)).build(),
                metadata(asset).usage("DRIVE_FILE").build(),
                metadata(asset).ownerService("foreign-service").build(),
                metadata(asset).ownerDomain("drive").build(),
                metadata(asset).ownerType("drive-file").build(),
                metadata(asset).ownerId(uuid(92).toString()).build(),
                metadata(asset).status("STAGED").build(),
                metadata(asset).contentType("application/pdf").build(),
                metadata(asset).contentLength(6L).build()
        );

        for (OssMetadataResponse invalid : invalidResponses) {
            response.set(invalid);
            Throwable failure = catchThrowable(() -> adapter.completeUpload(asset, uuid(21), content));
            assertThat(failure)
                    .as("metadata mismatch must be rejected: %s", invalid)
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("上传媒体失败");
        }
    }

    @Test
    void canonicalMetadataMustRejectForeignOrLaterVersionInsteadOfWritingItBack() {
        PostMediaAsset asset = draft(uuid(1), uuid(2), uuid(21), uuid(22), uuid(23));
        AtomicReference<OssMetadataResponse> response = new AtomicReference<>();
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        when(ossClient.getMetadata(asset.ossObjectId())).thenAnswer(invocation -> response.get());
        OssPostMediaStorageAdapter adapter = new OssPostMediaStorageAdapter(ossClient);
        List<OssMetadataResponse> invalidResponses = List.of(
                metadata(asset).objectId(uuid(90)).build(),
                metadata(asset).versionId(uuid(91)).build(),
                metadata(asset).usage("DRIVE_FILE").build(),
                metadata(asset).ownerService("foreign-service").build(),
                metadata(asset).ownerDomain("drive").build(),
                metadata(asset).ownerType("drive-file").build(),
                metadata(asset).ownerId(uuid(92).toString()).build(),
                metadata(asset).status("STAGED").build(),
                metadata(asset).contentType("application/pdf").build(),
                metadata(asset).contentLength(6L).build()
        );

        for (OssMetadataResponse invalid : invalidResponses) {
            response.set(invalid);
            assertThat(adapter.queryCanonicalMetadata(asset).outcome())
                    .as("metadata mismatch must not become canonical: %s", invalid)
                    .isEqualTo(PostMediaStoragePort.CanonicalMetadataOutcome.UNKNOWN);
        }
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
    void bindReferenceShouldForwardCallerSuppliedReferenceId() {
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        UUID requestedReferenceId = uuid(31);
        UUID objectId = uuid(22);
        UUID versionId = uuid(23);
        when(ossClient.bindObjectReference(any(), any())).thenReturn(new OssReferenceResponse(
                requestedReferenceId,
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

        UUID result = adapter.bindReference(asset, uuid(40), requestedReferenceId, uuid(2));

        assertThat(result).isEqualTo(requestedReferenceId);
        var captor = forClass(OssBindReferenceRequest.class);
        verify(ossClient).bindObjectReference(org.mockito.ArgumentMatchers.eq(objectId), captor.capture());
        assertThat(captor.getValue().referenceId()).isEqualTo(requestedReferenceId.toString());
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
                5,
                PostMediaKind.VIDEO,
                PostMediaAssetLifecycle.DRAFT,
                PostVideoState.NONE,
                "",
                "",
                new Date(),
                null
        );
    }

    private static MetadataBuilder metadata(PostMediaAsset asset) {
        return new MetadataBuilder(asset);
    }

    private static final class MetadataBuilder {
        private UUID objectId;
        private UUID versionId;
        private String usage = "CONTENT_POST_MEDIA";
        private String ownerService = "community-app";
        private String ownerDomain = "content";
        private String ownerType = "post-media-draft";
        private String ownerId;
        private String status = "ACTIVE";
        private String contentType;
        private long contentLength;

        private MetadataBuilder(PostMediaAsset asset) {
            this.objectId = asset.ossObjectId();
            this.versionId = asset.ossVersionId();
            this.ownerId = asset.id().toString();
            this.contentType = asset.contentType();
            this.contentLength = asset.contentLength();
        }

        private MetadataBuilder objectId(UUID value) { this.objectId = value; return this; }
        private MetadataBuilder versionId(UUID value) { this.versionId = value; return this; }
        private MetadataBuilder usage(String value) { this.usage = value; return this; }
        private MetadataBuilder ownerService(String value) { this.ownerService = value; return this; }
        private MetadataBuilder ownerDomain(String value) { this.ownerDomain = value; return this; }
        private MetadataBuilder ownerType(String value) { this.ownerType = value; return this; }
        private MetadataBuilder ownerId(String value) { this.ownerId = value; return this; }
        private MetadataBuilder status(String value) { this.status = value; return this; }
        private MetadataBuilder contentType(String value) { this.contentType = value; return this; }
        private MetadataBuilder contentLength(long value) { this.contentLength = value; return this; }

        private OssMetadataResponse build() {
            return new OssMetadataResponse(
                    objectId,
                    versionId,
                    usage,
                    ownerService,
                    ownerDomain,
                    ownerType,
                    ownerId,
                    "PUBLIC",
                    status,
                    "demo.mp4",
                    contentType,
                    contentLength,
                    "checksum",
                    "https://cdn.example.com/demo.mp4"
            );
        }
    }
}

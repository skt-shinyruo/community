package com.nowcoder.community.oss.client;

import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssPublicFileResponse;
import com.nowcoder.community.oss.client.model.OssAccessDecisionResponse;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssGrantObjectAccessRequest;
import com.nowcoder.community.oss.client.model.OssLifecycleResponse;
import com.nowcoder.community.oss.client.model.OssSignedUrlResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityOssClientContractTest {

    @Test
    void clientShouldExposeStableUploadMetadataAndSignedUrlContracts() {
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        UUID sessionId = uuid(3);
        OssUploadSessionRequest request = new OssUploadSessionRequest(
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "7"
        );
        OssUploadSessionResponse upload = new OssUploadSessionResponse(
                sessionId,
                objectId,
                versionId,
                "PROXY",
                "/api/oss/objects/" + objectId + "/complete",
                Instant.parse("2026-05-07T00:15:00Z")
        );
        OssMetadataResponse metadata = new OssMetadataResponse(
                objectId,
                versionId,
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                "7",
                "PUBLIC",
                "ACTIVE",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
        );
        OssSignedUrlResponse signedUrl = new OssSignedUrlResponse(
                "http://localhost:12880/files/signed",
                "GET",
                Instant.parse("2026-05-07T00:05:00Z"),
                "private, max-age=300"
        );
        OssPublicFileResponse publicFile = new OssPublicFileResponse(
                "ok".getBytes(),
                "text/plain",
                2,
                "etag-1",
                "public, max-age=31536000, immutable",
                "avatar.png"
        );
        OssAccessDecisionResponse access = new OssAccessDecisionResponse(
                uuid(4),
                objectId,
                versionId,
                "USER",
                "7",
                "READ",
                Instant.parse("2026-05-07T01:00:00Z"),
                "7",
                Instant.parse("2026-05-07T00:00:00Z"),
                null,
                true
        );
        OssReferenceResponse reference = new OssReferenceResponse(
                uuid(5),
                objectId,
                versionId,
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                "ACTIVE",
                null,
                Instant.parse("2026-05-07T00:00:00Z"),
                null
        );
        OssLifecycleResponse lifecycle = new OssLifecycleResponse(
                objectId,
                versionId,
                "PURGED",
                false,
                true,
                "object purged",
                Instant.parse("2026-05-07T00:10:00Z")
        );
        OssGrantObjectAccessRequest grantRequest = new OssGrantObjectAccessRequest(
                versionId.toString(),
                "USER",
                "7",
                "READ",
                Instant.parse("2026-05-07T01:00:00Z"),
                "7"
        );
        OssBindReferenceRequest bindRequest = new OssBindReferenceRequest(
                versionId.toString(),
                "community-app",
                "user",
                "avatar",
                "7",
                "PRIMARY",
                null,
                "7"
        );

        assertThat(CommunityOssClient.class).isNotNull();
        assertThat(request.usage()).isEqualTo("USER_AVATAR");
        assertThat(upload.uploadMode()).isEqualTo("PROXY");
        assertThat(metadata.currentVersionId()).isEqualTo(versionId);
        assertThat(signedUrl.method()).isEqualTo("GET");
        assertThat(publicFile.contentType()).isEqualTo("text/plain");
        assertThat(access.permission()).isEqualTo("READ");
        assertThat(reference.status()).isEqualTo("ACTIVE");
        assertThat(lifecycle.purged()).isTrue();
        assertThat(grantRequest.permission()).isEqualTo("READ");
        assertThat(bindRequest.referenceRole()).isEqualTo("PRIMARY");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}

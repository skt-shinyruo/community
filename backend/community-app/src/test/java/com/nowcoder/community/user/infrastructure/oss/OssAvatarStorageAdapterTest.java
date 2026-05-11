package com.nowcoder.community.user.infrastructure.oss;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import com.nowcoder.community.user.application.command.CreateAvatarUploadSessionCommand;
import com.nowcoder.community.user.application.result.AvatarUploadSessionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssAvatarStorageAdapterTest {

    @Test
    void createUploadSessionShouldUseDirectCommunityOssSession() {
        UUID userId = uuid(7);
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        UUID sessionId = uuid(3);
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        when(ossClient.prepareUpload(any())).thenReturn(new OssUploadSessionResponse(
                sessionId,
                objectId,
                versionId,
                "PROXY",
                "/api/oss/objects/" + objectId + "/complete",
                Instant.parse("2026-05-07T00:15:00Z")
        ));
        OssAvatarStorageAdapter adapter = new OssAvatarStorageAdapter(ossClient);

        AvatarUploadSessionResult session = adapter.createUploadSession(
                userId,
                new CreateAvatarUploadSessionCommand("picked-avatar.png", "image/png", 6, "sha256-avatar")
        );

        assertThat(session.uploadId()).isEqualTo(sessionId.toString());
        assertThat(session.objectId()).isEqualTo(objectId);
        assertThat(session.versionId()).isEqualTo(versionId);
        assertThat(session.uploadUrl()).isEqualTo("/api/oss/objects/" + objectId + "/complete");
        assertThat(session.uploadMethod()).isEqualTo("POST");
        assertThat(session.fileField()).isEqualTo("file");
        assertThat(session.fields()).containsEntry("sessionId", sessionId.toString());
        assertThat(session.fields()).containsEntry("versionId", versionId.toString());
        assertThat(session.fields()).containsEntry("checksumSha256", "sha256-avatar");
        assertThat(session.mimeTypes()).containsExactly("image/jpeg", "image/png", "image/webp", "image/gif");
        assertThat(session.expiresAt()).isEqualTo(Instant.parse("2026-05-07T00:15:00Z"));

        var captor = forClass(OssUploadSessionRequest.class);
        verify(ossClient).prepareUpload(captor.capture());
        assertThat(captor.getValue().usage()).isEqualTo("USER_AVATAR");
        assertThat(captor.getValue().ownerService()).isEqualTo("community-app");
        assertThat(captor.getValue().ownerDomain()).isEqualTo("user");
        assertThat(captor.getValue().ownerType()).isEqualTo("avatar");
        assertThat(captor.getValue().ownerId()).isEqualTo(userId.toString());
        assertThat(captor.getValue().visibility()).isEqualTo("PUBLIC");
        assertThat(captor.getValue().fileName()).isEqualTo("picked-avatar.png");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
        assertThat(captor.getValue().contentLength()).isEqualTo(6);
        assertThat(captor.getValue().checksumSha256()).isEqualTo("sha256-avatar");
        verify(ossClient, never()).completeProxyUpload(any());
    }

    @Test
    void resolvePublicAvatarUrlShouldRequireUserOwnedActivePublicAvatarObject() {
        UUID userId = uuid(7);
        UUID objectId = uuid(1);
        UUID versionId = uuid(2);
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        when(ossClient.getMetadata(objectId)).thenReturn(new OssMetadataResponse(
                objectId,
                versionId,
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                userId.toString(),
                "PUBLIC",
                "ACTIVE",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
        ));
        OssAvatarStorageAdapter adapter = new OssAvatarStorageAdapter(ossClient);

        String publicUrl = adapter.resolvePublicAvatarUrl(userId, objectId);

        assertThat(publicUrl).isEqualTo("http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png");
        verify(ossClient).getMetadata(objectId);
    }

    @Test
    void resolvePublicAvatarUrlShouldRejectForeignAvatarObject() {
        UUID userId = uuid(7);
        UUID objectId = uuid(1);
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        when(ossClient.getMetadata(objectId)).thenReturn(new OssMetadataResponse(
                objectId,
                uuid(2),
                "USER_AVATAR",
                "community-app",
                "user",
                "avatar",
                uuid(8).toString(),
                "PUBLIC",
                "ACTIVE",
                "avatar.png",
                "image/png",
                6,
                "sha256-avatar",
                "http://localhost:12880/files/" + objectId + "/avatar.png"
        ));
        OssAvatarStorageAdapter adapter = new OssAvatarStorageAdapter(ossClient);

        Throwable thrown = catchThrowable(() -> adapter.resolvePublicAvatarUrl(userId, objectId));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FORBIDDEN);
        verify(ossClient).getMetadata(eq(objectId));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}

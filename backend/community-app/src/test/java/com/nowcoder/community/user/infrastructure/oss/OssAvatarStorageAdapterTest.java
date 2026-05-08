package com.nowcoder.community.user.infrastructure.oss;

import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssAvatarStorageAdapterTest {

    @Test
    void createUploadTokenAndUploadShouldUseCommunityOssClient() {
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
        when(ossClient.completeProxyUpload(any())).thenReturn(new OssMetadataResponse(
                objectId,
                versionId,
                "USER_AVATAR",
                "ACTIVE",
                "image/png",
                6,
                "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
        ));
        Map<String, String> redis = new HashMap<>();
        OssAvatarStorageAdapter adapter = new OssAvatarStorageAdapter(
                ossClient,
                redisTemplate(redis),
                new OssAvatarProperties("http://localhost:12880")
        );

        AvatarUploadTokenResult token = adapter.createUploadToken(userId);
        adapter.upload(userId, token.fileName(), new AvatarUploadContent(
                () -> new ByteArrayInputStream("avatar".getBytes()),
                "image/png",
                6,
                false
        ));

        assertThat(token.provider()).isEqualTo("oss");
        assertThat(token.fileName()).startsWith("avatar/" + userId + "/");
        assertThat(adapter.buildAvatarUrl(token.fileName())).isEqualTo(
                "http://localhost:12880/files/" + objectId + "/" + versionId + "/avatar.png"
        );
        verify(ossClient).prepareUpload(any(OssUploadSessionRequest.class));
        verify(ossClient).completeProxyUpload(any(OssCompleteUploadRequest.class));
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate redisTemplate(Map<String, String> rows) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.get(any())).thenAnswer(invocation -> rows.get(invocation.getArgument(0, String.class)));
        when(operations.getAndDelete(any())).thenAnswer(invocation -> rows.remove(invocation.getArgument(0, String.class)));
        org.mockito.Mockito.doAnswer(invocation -> {
            rows.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            return null;
        }).when(operations).set(any(), any(), any(Long.class), any(TimeUnit.class));
        return redisTemplate;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}

package com.nowcoder.community.user.controller;

import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssPublicFileResponse;
import com.nowcoder.community.user.application.UserFileApplicationService;
import com.nowcoder.community.user.infrastructure.oss.OssAvatarProperties;
import com.nowcoder.community.user.infrastructure.oss.OssAvatarStorageAdapter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilesControllerStorageRoutingTest {

    @Test
    void shouldServeAvatarFilesThroughOssClient() throws Exception {
        UUID userId = uuid(1);
        String fileKey = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        CommunityOssClient ossClient = mock(CommunityOssClient.class);
        when(ossClient.loadPublicFile(fileKey)).thenReturn(new OssPublicFileResponse(
                "ok".getBytes(StandardCharsets.UTF_8),
                "text/plain",
                2,
                "",
                "public, max-age=31536000, immutable",
                "avatar.png"
        ));
        OssAvatarStorageAdapter adapter = new OssAvatarStorageAdapter(
                ossClient,
                mock(StringRedisTemplate.class),
                new OssAvatarProperties("http://localhost:12880")
        );
        FilesController controller = new FilesController(new UserFileApplicationService(adapter));

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/files/" + fileKey);

        ResponseEntity<Resource> resp = controller.get(req);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(resp.getHeaders().getContentLength()).isEqualTo(2);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getInputStream().readAllBytes()).isEqualTo("ok".getBytes(StandardCharsets.UTF_8));
        assertThat(resp.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        verify(ossClient).loadPublicFile(fileKey);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}

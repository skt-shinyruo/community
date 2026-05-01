package com.nowcoder.community.user.controller;

import com.nowcoder.community.user.application.result.AvatarUploadTokenResult;
import com.nowcoder.community.user.config.AvatarStorageProperties;
import com.nowcoder.community.user.application.UserFileApplicationService;
import com.nowcoder.community.user.infrastructure.avatar.AvatarService;
import com.nowcoder.community.user.infrastructure.avatar.AvatarStorageProvider;
import com.nowcoder.community.user.infrastructure.avatar.AvatarStorageRouter;
import com.nowcoder.community.user.infrastructure.avatar.StoredAvatar;
import com.nowcoder.community.user.infrastructure.avatar.UserAvatarStorageAdapter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FilesControllerStorageRoutingTest {

    @Test
    void shouldServeAvatarFilesViaCurrentProviderWhenStorageIsNotLocal() throws Exception {
        UUID userId = uuid(1);
        AvatarStorageProperties props = new AvatarStorageProperties();
        props.setStorage("r2");

        AvatarStorageProvider stub = new AvatarStorageProvider() {
            @Override
            public String provider() {
                return "r2";
            }

            @Override
            public AvatarUploadTokenResult createUploadToken(UUID userId, String fileName) {
                return null;
            }

            @Override
            public void upload(UUID userId, String fileName, MultipartFile file) {
                throw new UnsupportedOperationException("upload not needed");
            }

            @Override
            public String buildAvatarUrl(String fileName) {
                return "http://example.invalid/" + fileName;
            }

            @Override
            public StoredAvatar loadOrNull(String key) {
                return new StoredAvatar(new ByteArrayResource("ok".getBytes(StandardCharsets.UTF_8)), MediaType.TEXT_PLAIN);
            }
        };

        AvatarStorageRouter router = new AvatarStorageRouter(props, List.of(stub));
        UserAvatarStorageAdapter adapter = new UserAvatarStorageAdapter(mock(AvatarService.class), router);
        FilesController controller = new FilesController(new UserFileApplicationService(adapter));

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/files/avatar/" + userId + "/0123456789abcdef0123456789abcdef");

        ResponseEntity<Resource> resp = controller.get(req);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(resp.getHeaders().getContentLength()).isEqualTo(2);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getInputStream().readAllBytes()).isEqualTo("ok".getBytes(StandardCharsets.UTF_8));
        assertThat(resp.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}

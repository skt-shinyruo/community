package com.nowcoder.community.user.infrastructure.avatar;

import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.config.AvatarStorageProperties;
import com.nowcoder.community.user.config.R2Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2AvatarStorageProviderUnitTest {

    @Test
    void uploadShouldPutObjectToBucket() throws IOException {
        UUID userId = uuid(1);
        R2Properties r2 = new R2Properties();
        r2.setBucketName("bucket");

        AvatarStorageProperties avatar = new AvatarStorageProperties();
        avatar.setPublicBaseUrl("http://localhost:12881");

        S3Client s3 = mock(S3Client.class);
        R2AvatarStorageProvider provider = new R2AvatarStorageProvider(r2, avatar, s3);

        String key = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        AvatarUploadContent content = new AvatarUploadContent(
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}),
                "image/png",
                3,
                false
        );

        provider.upload(userId, key, content);

        var reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        var bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(reqCaptor.capture(), bodyCaptor.capture());
        PutObjectRequest req = reqCaptor.getValue();
        assertThat(req.bucket()).isEqualTo("bucket");
        assertThat(req.key()).isEqualTo(key);
        assertThat(req.contentType()).isEqualTo("image/png");
        RequestBody body = bodyCaptor.getValue();
        assertThat(body.contentLength()).isEqualTo(3);
        assertThat(body.contentStreamProvider().newStream().readAllBytes()).containsExactly(1, 2, 3);
    }

    @Test
    void loadOrNullShouldReturnNullWhenNotFound() {
        UUID userId = uuid(1);
        R2Properties r2 = new R2Properties();
        r2.setBucketName("bucket");

        AvatarStorageProperties avatar = new AvatarStorageProperties();
        avatar.setPublicBaseUrl("http://localhost:12881");

        S3Client s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());

        R2AvatarStorageProvider provider = new R2AvatarStorageProvider(r2, avatar, s3);
        assertThat(provider.loadOrNull("avatar/" + userId + "/0123456789abcdef0123456789abcdef")).isNull();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}

package com.nowcoder.community.user.service;

import com.nowcoder.community.user.config.AvatarStorageProperties;
import com.nowcoder.community.user.config.R2Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2AvatarStorageProviderUnitTest {

    @Test
    void uploadShouldPutObjectToBucket() {
        R2Properties r2 = new R2Properties();
        r2.setBucketName("bucket");

        AvatarStorageProperties avatar = new AvatarStorageProperties();
        avatar.setPublicBaseUrl("http://localhost:12881");

        S3Client s3 = mock(S3Client.class);
        R2AvatarStorageProvider provider = new R2AvatarStorageProvider(r2, avatar, s3);

        String key = "avatar/1/0123456789abcdef0123456789abcdef";
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});

        provider.upload(1, key, file);

        var reqCaptor = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(reqCaptor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        PutObjectRequest req = reqCaptor.getValue();
        assertThat(req.bucket()).isEqualTo("bucket");
        assertThat(req.key()).isEqualTo(key);
        assertThat(req.contentType()).isEqualTo("image/png");
    }

    @Test
    void loadOrNullShouldReturnNullWhenNotFound() {
        R2Properties r2 = new R2Properties();
        r2.setBucketName("bucket");

        AvatarStorageProperties avatar = new AvatarStorageProperties();
        avatar.setPublicBaseUrl("http://localhost:12881");

        S3Client s3 = mock(S3Client.class);
        when(s3.getObject(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());

        R2AvatarStorageProvider provider = new R2AvatarStorageProvider(r2, avatar, s3);
        assertThat(provider.loadOrNull("avatar/1/0123456789abcdef0123456789abcdef")).isNull();
    }
}


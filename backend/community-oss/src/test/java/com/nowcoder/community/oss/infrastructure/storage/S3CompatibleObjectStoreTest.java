package com.nowcoder.community.oss.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3CompatibleObjectStoreTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner presigner;

    @Test
    void putAndHeadObjectsThroughS3Client() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/png")
                .contentLength(6L)
                .eTag("etag-1")
                .lastModified(Instant.parse("2026-05-07T00:00:00Z"))
                .build());
        S3CompatibleObjectStore store = new S3CompatibleObjectStore(s3Client, presigner);

        store.put(
                "community-oss",
                "objects/object-1/version-1/avatar.png",
                new ByteArrayInputStream("avatar".getBytes(StandardCharsets.UTF_8)),
                6,
                "image/png"
        );
        ObjectStoreObject metadata = store.head("community-oss", "objects/object-1/version-1/avatar.png").orElseThrow();

        assertThat(metadata.contentType()).isEqualTo("image/png");
        assertThat(metadata.contentLength()).isEqualTo(6);
        assertThat(metadata.etag()).isEqualTo("etag-1");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}

package com.nowcoder.community.oss.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFilesystemObjectStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void putHeadGetAndDeleteObject() throws Exception {
        LocalFilesystemObjectStore store = new LocalFilesystemObjectStore(tempDir, "http://localhost:12880");
        String bucket = "community-oss";
        String key = "objects/00000000-0000-7000-8000-000000000001/00000000-0000-7000-8000-000000000002/avatar.png";
        byte[] bytes = "avatar".getBytes(StandardCharsets.UTF_8);

        store.put(bucket, key, new ByteArrayInputStream(bytes), bytes.length, "image/png");

        Optional<ObjectStoreObject> head = store.head(bucket, key);
        assertThat(head).isPresent();
        assertThat(head.get().contentLength()).isEqualTo(bytes.length);
        assertThat(head.get().contentType()).isEqualTo("image/png");

        StoredObject stored = store.get(bucket, key);
        assertThat(new String(stored.content().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("avatar");
        assertThat(stored.contentType()).isEqualTo("image/png");

        store.delete(bucket, key);

        assertThat(store.head(bucket, key)).isEmpty();
    }

    @Test
    void presignedDownloadUrlShouldUseCanonicalPublicRoute() {
        LocalFilesystemObjectStore store = new LocalFilesystemObjectStore(tempDir, "http://localhost:12880/");
        String key = "objects/00000000-0000-7000-8000-000000000001/00000000-0000-7000-8000-000000000002/avatar.png";

        PresignedObjectUrl url = store.presignDownload("community-oss", key, Duration.ofMinutes(5));

        assertThat(url.url()).isEqualTo(
                "http://localhost:12880/files/00000000-0000-7000-8000-000000000001/00000000-0000-7000-8000-000000000002/avatar.png"
        );
    }
}

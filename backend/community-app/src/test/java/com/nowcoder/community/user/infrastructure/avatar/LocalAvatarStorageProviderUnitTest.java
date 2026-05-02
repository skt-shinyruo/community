package com.nowcoder.community.user.infrastructure.avatar;

import com.nowcoder.community.user.application.AvatarUploadContent;
import com.nowcoder.community.user.config.AvatarStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAvatarStorageProviderUnitTest {

    @TempDir
    private Path tempDir;

    @Test
    void uploadShouldCopyUploadContentBytesToTargetFile() throws Exception {
        UUID userId = uuid(1);
        AvatarStorageProperties properties = new AvatarStorageProperties();
        properties.setFilesBaseDir(tempDir.toString());
        properties.setPublicBaseUrl("http://localhost:12881");
        LocalAvatarStorageProvider provider = new LocalAvatarStorageProvider(properties);

        String key = "avatar/" + userId + "/0123456789abcdef0123456789abcdef";
        AvatarUploadContent content = new AvatarUploadContent(
                () -> new ByteArrayInputStream(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}),
                "image/png",
                4,
                false
        );

        provider.upload(userId, key, content);

        assertThat(Files.readAllBytes(tempDir.resolve(key))).containsExactly((byte) 0x89, 0x50, 0x4E, 0x47);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}

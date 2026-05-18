package com.nowcoder.community.user;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OssAvatarStorageMigrationRetirementTest {

    private static final Path MODULE_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path REPO_ROOT = MODULE_ROOT.getParent().getParent();

    @Test
    void communityAppShouldNotKeepLegacyAvatarStorageInfrastructure() {
        assertThat(containsJavaSource(MODULE_ROOT.resolve("src/main/java/com/nowcoder/community/user/infrastructure/avatar"))).isFalse();
        assertThat(MODULE_ROOT.resolve("src/main/java/com/nowcoder/community/user/infrastructure/oss/OssAvatarStorageAdapter.java")).exists();
    }

    @Test
    void communityAppShouldNotOwnPublicFileEndpoint() throws IOException {
        List<Path> fileEndpointOwners;
        try (var files = Files.walk(MODULE_ROOT.resolve("src/main/java"))) {
            fileEndpointOwners = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::hasPublicFileMapping)
                    .toList();
        }

        assertThat(fileEndpointOwners).isEmpty();
    }

    @Test
    void communityAppConfigurationShouldOnlyExposeOssAvatarClientSettings() throws IOException {
        String yaml = Files.readString(MODULE_ROOT.resolve("src/main/resources/application.yml"));

        assertThat(yaml).doesNotContain(
                "user.avatar",
                "oss.avatar",
                "OSS_AVATAR_PUBLIC_BASE_URL"
        );
        assertThat(yaml).contains(
                "oss:",
                "client:",
                "base-url: ${OSS_CLIENT_BASE_URL:http://community-oss:18090}"
        );
    }

    @Test
    void deployRuntimeConfigsShouldNotPassLegacyAvatarStorageEnvironment() throws IOException {
        assertDoesNotPassOssClientEnv(REPO_ROOT.resolve("deploy/compose.runtime.services.single.yml"));
        assertDoesNotPassOssClientEnv(REPO_ROOT.resolve("deploy/compose.runtime.services.cluster.yml"));
    }

    @Test
    void nacosCommunityAppConfigShouldProvideOssClientBaseUrl() throws IOException {
        String yaml = Files.readString(REPO_ROOT.resolve("deploy/nacos/config/community-app.yaml"));

        assertThat(yaml).contains(
                "oss:",
                "client:",
                "base-url: http://community-oss:18090"
        );
    }

    private boolean hasPublicFileMapping(Path path) {
        try {
            String content = Files.readString(path);
            return content.contains("@GetMapping(\"/files")
                    || content.contains("@RequestMapping(\"/files");
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + path, e);
        }
    }

    private boolean containsJavaSource(Path directory) {
        if (!Files.exists(directory)) {
            return false;
        }
        try (var files = Files.walk(directory)) {
            return files.anyMatch(path -> path.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new IllegalStateException("failed to scan " + directory, e);
        }
    }

    private void assertDoesNotPassOssClientEnv(Path path) throws IOException {
        String content = Files.readString(path);
        assertThat(content)
                .as(path.toString())
                .doesNotContain(
                        "OSS_CLIENT_BASE_URL",
                        "OSS_AVATAR_PUBLIC_BASE_URL"
                );
    }
}

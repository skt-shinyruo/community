package com.nowcoder.community.oss.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OssUploadMigrationForwardOnlyContractTest {

    private static final String FROZEN_V001_SHA256 =
            "b5703e5e872154024fd6a437d8a96a91e5c87f5da218e3911ec4827d08501740";

    @Test
    void publishedV001MustRemainByteForByteFrozen() throws Exception {
        Path baseline = migrationDirectory().resolve("V001__oss_baseline.sql");

        assertThat(sha256(Files.readAllBytes(baseline))).isEqualTo(FROZEN_V001_SHA256);
    }

    @Test
    void uploadIdempotencyAndRecoverySchemaMustArriveInAForwardMigration() throws Exception {
        Path directory = migrationDirectory();
        List<Path> forwardMigrations;
        try (var files = Files.list(directory)) {
            forwardMigrations = files
                    .filter(path -> path.getFileName().toString().matches("V(?!001__)[0-9]+__.+\\.sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        assertThat(forwardMigrations)
                .as("V001 is immutable; REL-MEDIA-06 requires a new forward migration")
                .isNotEmpty();
        String sql = readAll(forwardMigrations).toLowerCase(java.util.Locale.ROOT);
        assertThat(sql).contains("alter table oss_upload_session");
        assertThat(sql).contains("request_id");
        assertThat(sql).contains("updated_at");
        assertThat(sql).contains("last_error");
        assertThat(sql).contains("claim_version");
        assertThat(sql).contains("unique", "request_id");
        assertThat(sql).contains("status", "updated_at", "session_id");
    }

    private static Path migrationDirectory() {
        return repositoryRoot().resolve(
                "backend/community-oss-db-migrations/src/main/resources/db/migration/community-oss");
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("backend/community-oss-db-migrations"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("cannot locate repository root");
    }

    private static String readAll(List<Path> paths) throws IOException {
        StringBuilder result = new StringBuilder();
        for (Path path : paths) {
            result.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
        }
        return result.toString();
    }

    private static String sha256(byte[] content) throws NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}

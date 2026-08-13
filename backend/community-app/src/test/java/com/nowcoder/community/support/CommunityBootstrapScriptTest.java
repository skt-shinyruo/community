package com.nowcoder.community.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityBootstrapScriptTest {

    private static final Path MODULE_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path REPO_ROOT = MODULE_ROOT.getParent().getParent();
    private static final Path CURRENT_SCHEMA = REPO_ROOT.resolve(
            "deploy/database/business/current-state/010_current_schema.sql");

    @Test
    void deploymentSchemaAssertionsShouldReadTheCurrentStateSnapshot() throws IOException {
        assertThat(DeployCommunitySchema.read(REPO_ROOT))
                .isEqualTo(Files.readString(CURRENT_SCHEMA))
                .contains("CREATE DATABASE IF NOT EXISTS `community`", "CREATE TABLE `drive_upload`")
                .doesNotContain("ALTER TABLE", "DROP TABLE", "community_schema_history")
                .doesNotContain("aaa@example.com", "bbb@example.com", "admin@example.com");
    }
}

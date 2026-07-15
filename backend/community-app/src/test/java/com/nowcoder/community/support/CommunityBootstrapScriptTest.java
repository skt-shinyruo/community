package com.nowcoder.community.support;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class CommunityBootstrapScriptTest {

    private static final Path MODULE_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path REPO_ROOT = MODULE_ROOT.getParent().getParent();
    private static final Path COMMUNITY_BASELINE = REPO_ROOT.resolve(
            "backend/community-db-migrations/src/main/resources/db/migration/community/V001__baseline.sql");

    @Test
    void deploymentSchemaAssertionsShouldReadTheImmutableFlywayBaseline() throws IOException {
        assertThat(DeployCommunitySchema.read(REPO_ROOT))
                .isEqualTo(Files.readString(COMMUNITY_BASELINE))
                .doesNotContain("create table if not exists im_")
                .doesNotContain("create table if not exists oss_")
                .doesNotContain("insert into user (");
    }
}

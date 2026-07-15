package com.nowcoder.community.oss.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OssMigrationLayoutTest {

    @Test
    void v001ShouldBeDatabaseAgnosticAndContainOnlyOssOwnedTables() throws Exception {
        String resource = "db/migration/community-oss/V001__oss_baseline.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).doesNotContain("use community_oss");
            assertThat(sql).contains(
                    "create table if not exists oss_object",
                    "create table if not exists oss_object_version",
                    "create table if not exists oss_upload_session",
                    "create table if not exists oss_access_grant",
                    "create table if not exists oss_object_reference",
                    "create table if not exists oss_usage_policy"
            );
            assertThat(sql).doesNotContain("create table if not exists user");
            assertThat(sql).doesNotContain("create table if not exists im_");
        }
        assertThat(getClass().getClassLoader().getResource(
                "db/migration/community-oss/community-oss-schema-manifest.tsv")).isNotNull();
    }
}

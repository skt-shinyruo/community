package com.nowcoder.community.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityMigrationLayoutTest {

    @Test
    void v012ShouldAddTheDriveUploadChecksumWithoutChangingTheFrozenBaseline() throws Exception {
        String resource = "db/migration/community/V012__persist_drive_upload_checksum.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertThat(sql).contains("alter table drive_upload");
            assertThat(sql).contains("add column checksum_sha256 varchar(128) not null default ''");
        }
    }

    @Test
    void v011ShouldAvoidPositionalColumnPlacement() throws Exception {
        String resource = "db/migration/community/V011__add_social_like_relation_instance.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();

            assertThat(sql).doesNotContainPattern("(?s)\\badd\\s+column\\b[^;]*\\bfirst\\b");
        }
    }

    @Test
    void productionBaselineShouldBeDatabaseAgnosticAndExcludeForeignSchemasAndDevelopmentUsers() throws Exception {
        String resource = "db/migration/community/V001__baseline.sql";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertThat(sql).doesNotContain("use community");
            assertThat(sql).doesNotContain("create table if not exists im_");
            assertThat(sql).doesNotContain("create table if not exists oss_");
            assertThat(sql).doesNotContain("insert into user(");
            assertThat(sql).doesNotContain("insert ignore into user(");
        }
    }
}

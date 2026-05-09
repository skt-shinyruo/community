package com.nowcoder.community.drive.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DriveSchemaResourceTest {

    private static final Path REPO_ROOT = Path.of("..", "..").toAbsolutePath().normalize();

    @Test
    void productionSchemaShouldDefineDriveTablesAndIndexes() throws IOException {
        String sql = Files.readString(REPO_ROOT.resolve("deploy/mysql/community/090_schema_drive.sql"));

        assertThat(sql).contains(
                "create table if not exists drive_space",
                "create table if not exists drive_entry",
                "create table if not exists drive_upload",
                "create table if not exists drive_share",
                "create table if not exists drive_share_access",
                "unique key uk_drive_space_user",
                "unique key uk_drive_entry_active_name",
                "unique key uk_drive_share_token"
        );
    }
}

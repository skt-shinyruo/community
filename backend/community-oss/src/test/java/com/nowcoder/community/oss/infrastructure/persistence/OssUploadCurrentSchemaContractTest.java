package com.nowcoder.community.oss.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OssUploadCurrentSchemaContractTest {

    @Test
    void currentSnapshotShouldContainTheCompleteUploadRecoverySchema() throws Exception {
        String sql = Files.readString(currentSchema()).toLowerCase(Locale.ROOT);

        assertThat(sql).contains(
                "create table `oss_upload_session`",
                "`request_id` binary(16) not null",
                "`updated_at` timestamp not null",
                "`last_error` varchar(512)",
                "`claim_version` bigint not null default '0'",
                "unique key `uk_oss_upload_request` (`request_id`)",
                "key `idx_oss_upload_recovery` (`status`,`updated_at`,`session_id`)"
        ).doesNotContain("alter table", "oss_schema_history");
    }

    private static Path currentSchema() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            Path schema = candidate.resolve("deploy/mysql/primary-init/010_current_schema.sql");
            if (Files.isRegularFile(schema)) {
                return schema;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("cannot locate current MySQL schema snapshot");
    }
}

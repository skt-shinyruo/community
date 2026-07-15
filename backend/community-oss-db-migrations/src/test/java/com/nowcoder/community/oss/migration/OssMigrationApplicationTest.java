package com.nowcoder.community.oss.migration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssMigrationApplicationTest {

    @Test
    void productionRunnerShouldExposeOnlyFixedLocationAndHistory() {
        assertThat(OssMigrationRunner.MIGRATION_LOCATION).isEqualTo("classpath:db/migration/community-oss");
        assertThat(OssMigrationRunner.HISTORY_TABLE).isEqualTo("oss_schema_history");
        assertThat(Arrays.stream(OssMigrationRunner.class.getMethods()).map(Method::getName))
                .doesNotContain("clean", "repair");
    }

    @Test
    void productionCliShouldRejectArbitraryLocationsBeforeConnecting() {
        Map<String, String> environment = Map.of(
                "OSS_MIGRATION_JDBC_URL", "jdbc:mysql://127.0.0.1:1/community_oss",
                "OSS_MIGRATION_USERNAME", "migrator",
                "OSS_MIGRATION_LOCATIONS", "filesystem:/tmp/untrusted"
        );

        assertThatThrownBy(() -> OssMigrationApplication.run(new String[]{"migrate"}, environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OSS_MIGRATION_LOCATIONS")
                .hasMessageContaining("not supported");
    }

    @Test
    void baselineShouldRequireTheExactConfirmationBeforeConnecting() {
        Map<String, String> environment = Map.of(
                "OSS_MIGRATION_JDBC_URL", "jdbc:mysql://127.0.0.1:1/community_oss",
                "OSS_MIGRATION_USERNAME", "migrator",
                "OSS_MIGRATION_BASELINE_CONFIRMATION", "yes"
        );

        assertThatThrownBy(() -> OssMigrationApplication.run(new String[]{"baseline"}, environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(OssMigrationRunner.BASELINE_CONFIRMATION);
    }
}

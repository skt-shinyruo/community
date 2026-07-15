package com.nowcoder.community.im.migration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImMigrationApplicationTest {

    @Test
    void productionRunnerShouldExposeOnlyTheImLocationAndHistoryTable() {
        Class<?> runner = ImMigrationReflectionSupport.requireClass(
                ImMigrationReflectionSupport.RUNNER_CLASS);

        assertThat(ImMigrationReflectionSupport.stringConstant(runner, "MIGRATION_LOCATION"))
                .isEqualTo("classpath:db/migration/im-core");
        assertThat(ImMigrationReflectionSupport.stringConstant(runner, "HISTORY_TABLE"))
                .isEqualTo("im_core_schema_history");
        assertThat(Arrays.stream(runner.getMethods()).map(Method::getName))
                .doesNotContain("clean", "repair");
    }

    @Test
    void productionCliShouldRejectArbitraryLocationsBeforeConnecting() {
        Map<String, String> environment = Map.of(
                "IM_MIGRATION_JDBC_URL", "jdbc:mysql://127.0.0.1:1/im_core",
                "IM_MIGRATION_USERNAME", "im_core_migrator",
                "IM_MIGRATION_LOCATIONS", "filesystem:/tmp/untrusted"
        );

        assertThatThrownBy(() -> runApplication("migrate", environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IM_MIGRATION_LOCATIONS")
                .hasMessageContaining("not supported");
    }

    @Test
    void productionCliShouldRejectAnyForeignHistoryTableBeforeConnecting() {
        Map<String, String> environment = Map.of(
                "IM_MIGRATION_JDBC_URL", "jdbc:mysql://127.0.0.1:1/im_core",
                "IM_MIGRATION_USERNAME", "im_core_migrator",
                "IM_MIGRATION_HISTORY_TABLE", "community_schema_history"
        );

        assertThatThrownBy(() -> runApplication("migrate", environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IM_MIGRATION_HISTORY_TABLE")
                .hasMessageContaining("im_core_schema_history");
    }

    @Test
    void baselineShouldRequireAnExactExplicitConfirmationBeforeConnecting() {
        Map<String, String> environment = Map.of(
                "IM_MIGRATION_JDBC_URL", "jdbc:mysql://127.0.0.1:1/im_core",
                "IM_MIGRATION_USERNAME", "im_core_migrator",
                "IM_MIGRATION_BASELINE_CONFIRMATION", "yes"
        );

        assertThatThrownBy(() -> runApplication("baseline", environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("I_HAVE_VERIFIED_THE_IM_CORE_SCHEMA");
    }

    private static void runApplication(String action, Map<String, String> environment) {
        ImMigrationReflectionSupport.invokeStatic(
                ImMigrationReflectionSupport.requireClass(
                        ImMigrationReflectionSupport.APPLICATION_CLASS),
                "run",
                new Class<?>[]{String[].class, Map.class},
                new String[]{action},
                environment
        );
    }
}

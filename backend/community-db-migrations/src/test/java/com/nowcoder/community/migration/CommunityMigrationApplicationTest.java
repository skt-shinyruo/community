package com.nowcoder.community.migration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunityMigrationApplicationTest {

    @Test
    void baselineShouldRequireAnExactExplicitConfirmation() {
        Map<String, String> environment = Map.of(
                "COMMUNITY_MIGRATION_JDBC_URL", "jdbc:mysql://127.0.0.1:1/community",
                "COMMUNITY_MIGRATION_USERNAME", "migrator",
                "COMMUNITY_MIGRATION_BASELINE_CONFIRMATION", "yes"
        );

        assertThatThrownBy(() -> CommunityMigrationApplication.run(new String[]{"baseline"}, environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("I_HAVE_VERIFIED_THE_COMMUNITY_SCHEMA");
    }

    @Test
    void runnerShouldRejectMissingLocationsBeforeConnecting() {
        assertThatThrownBy(() -> CommunityMigrationRunner.forLocations(
                "jdbc:mysql://127.0.0.1:1/community", "migrator", "", "history"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("migration location");
    }

    @Test
    void productionActionsShouldRejectAnArbitraryMigrationLocationBeforeConnecting() {
        Map<String, String> environment = Map.of(
                "COMMUNITY_MIGRATION_JDBC_URL", "jdbc:mysql://127.0.0.1:1/community",
                "COMMUNITY_MIGRATION_USERNAME", "migrator",
                "COMMUNITY_MIGRATION_LOCATIONS", CommunityMigrationRunner.DEVELOPMENT_SEED_LOCATION
        );

        assertThatThrownBy(() -> CommunityMigrationApplication.run(new String[]{"migrate"}, environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMMUNITY_MIGRATION_LOCATIONS")
                .hasMessageContaining("not supported");
    }

    @Test
    void developmentSeedShouldRequireAnExplicitDevelopmentProfileBeforeConnecting() {
        Map<String, String> environment = Map.of(
                "COMMUNITY_MIGRATION_JDBC_URL", "jdbc:mysql://127.0.0.1:1/community",
                "COMMUNITY_MIGRATION_USERNAME", "migrator"
        );

        assertThatThrownBy(() -> CommunityMigrationApplication.run(new String[]{"development-seed"}, environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMMUNITY_MIGRATION_PROFILE")
                .hasMessageContaining("development");
    }
}

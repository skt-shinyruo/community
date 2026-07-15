package com.nowcoder.community.migration;

import java.util.Locale;
import java.util.Map;

public final class CommunityMigrationApplication {

    private CommunityMigrationApplication() {
    }

    public static void main(String[] args) {
        run(args, System.getenv());
    }

    static void run(String[] args, Map<String, String> environment) {
        String action = args == null || args.length == 0 ? "migrate" : args[0].trim().toLowerCase(Locale.ROOT);
        rejectLocationOverride(environment);
        String jdbcUrl = required(environment, "COMMUNITY_MIGRATION_JDBC_URL");
        String username = required(environment, "COMMUNITY_MIGRATION_USERNAME");
        String password = environment.getOrDefault("COMMUNITY_MIGRATION_PASSWORD", "");
        String historyTable = environment.getOrDefault(
                "COMMUNITY_MIGRATION_HISTORY_TABLE", CommunityMigrationRunner.HISTORY_TABLE);
        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                jdbcUrl, username, password, historyTable, CommunityMigrationRunner.MIGRATION_LOCATION);

        switch (action) {
            case "migrate" -> runner.migrate();
            case "validate" -> runner.validate();
            case "baseline" -> runner.baselineAtVersionOne(requireBaselineConfirmation(environment));
            case "development-seed" -> migrateDevelopmentSeed(environment, runner, jdbcUrl, username, password);
            default -> throw new IllegalArgumentException("unsupported migration action: " + action);
        }
    }

    private static void migrateDevelopmentSeed(
            Map<String, String> environment,
            CommunityMigrationRunner productionRunner,
            String jdbcUrl,
            String username,
            String password
    ) {
        if (!"development".equals(environment.get("COMMUNITY_MIGRATION_PROFILE"))) {
            throw new IllegalArgumentException("COMMUNITY_MIGRATION_PROFILE must equal development");
        }
        productionRunner.migrate();
        CommunityMigrationRunner seedRunner = CommunityMigrationRunner.forLocations(
                jdbcUrl,
                username,
                password,
                CommunityMigrationRunner.DEVELOPMENT_SEED_HISTORY_TABLE,
                CommunityMigrationRunner.DEVELOPMENT_SEED_LOCATION
        );
        seedRunner.baselineDevelopmentSeedHistory();
        seedRunner.migrate();
    }

    private static void rejectLocationOverride(Map<String, String> environment) {
        if (environment.containsKey("COMMUNITY_MIGRATION_LOCATIONS")) {
            throw new IllegalArgumentException("COMMUNITY_MIGRATION_LOCATIONS override is not supported");
        }
    }

    private static String requireBaselineConfirmation(Map<String, String> environment) {
        String value = required(environment, "COMMUNITY_MIGRATION_BASELINE_CONFIRMATION");
        if (!CommunityMigrationRunner.BASELINE_CONFIRMATION.equals(value)) {
            throw new IllegalArgumentException(
                    "COMMUNITY_MIGRATION_BASELINE_CONFIRMATION must equal "
                            + CommunityMigrationRunner.BASELINE_CONFIRMATION);
        }
        return value;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

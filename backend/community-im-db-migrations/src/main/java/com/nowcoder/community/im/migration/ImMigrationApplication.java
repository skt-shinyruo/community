package com.nowcoder.community.im.migration;

import java.util.Locale;
import java.util.Map;

public final class ImMigrationApplication {

    private ImMigrationApplication() {
    }

    public static void main(String[] args) {
        run(args, System.getenv());
    }

    static void run(String[] args, Map<String, String> environment) {
        String action = args == null || args.length == 0
                ? "migrate"
                : args[0].trim().toLowerCase(Locale.ROOT);
        rejectOverrides(environment);
        String baselineConfirmation = "baseline".equals(action)
                ? requireBaselineConfirmation(environment)
                : null;
        ImMigrationRunner runner = ImMigrationRunner.standard(
                required(environment, "IM_MIGRATION_JDBC_URL"),
                required(environment, "IM_MIGRATION_USERNAME"),
                environment.getOrDefault("IM_MIGRATION_PASSWORD", "")
        );

        switch (action) {
            case "migrate" -> runner.migrate();
            case "validate" -> runner.validate();
            case "baseline" -> runner.baselineAtVersionOne(baselineConfirmation);
            default -> throw new IllegalArgumentException("unsupported migration action: " + action);
        }
    }

    private static void rejectOverrides(Map<String, String> environment) {
        if (environment.containsKey("IM_MIGRATION_LOCATIONS")) {
            throw new IllegalArgumentException("IM_MIGRATION_LOCATIONS override is not supported");
        }
        String historyTable = environment.get("IM_MIGRATION_HISTORY_TABLE");
        if (historyTable != null && !ImMigrationRunner.HISTORY_TABLE.equals(historyTable.trim())) {
            throw new IllegalArgumentException("IM_MIGRATION_HISTORY_TABLE must equal "
                    + ImMigrationRunner.HISTORY_TABLE);
        }
    }

    private static String requireBaselineConfirmation(Map<String, String> environment) {
        String value = required(environment, "IM_MIGRATION_BASELINE_CONFIRMATION");
        if (!ImMigrationRunner.BASELINE_CONFIRMATION.equals(value)) {
            throw new IllegalArgumentException("IM_MIGRATION_BASELINE_CONFIRMATION must equal "
                    + ImMigrationRunner.BASELINE_CONFIRMATION);
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

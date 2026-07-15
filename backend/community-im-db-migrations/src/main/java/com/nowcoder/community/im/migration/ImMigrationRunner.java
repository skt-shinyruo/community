package com.nowcoder.community.im.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.BaselineResult;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
import java.util.Objects;

public final class ImMigrationRunner {

    public static final String HISTORY_TABLE = "im_core_schema_history";
    public static final String MIGRATION_LOCATION = "classpath:db/migration/im-core";
    public static final String BASELINE_CONFIRMATION = "I_HAVE_VERIFIED_THE_IM_CORE_SCHEMA";

    private final Flyway flyway;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    private ImMigrationRunner(
            String jdbcUrl,
            String username,
            String password,
            String historyTable,
            String... locations
    ) {
        this.jdbcUrl = requireText(jdbcUrl, "jdbcUrl");
        this.username = requireText(username, "username");
        this.password = Objects.toString(password, "");
        String resolvedHistoryTable = requireText(historyTable, "historyTable");
        if (locations == null || locations.length == 0
                || Arrays.stream(locations).anyMatch(location -> location == null || location.isBlank())) {
            throw new IllegalArgumentException("at least one non-blank migration location is required");
        }
        this.flyway = Flyway.configure()
                .dataSource(this.jdbcUrl, this.username, this.password)
                .locations(Arrays.stream(locations).map(String::trim).toArray(String[]::new))
                .table(resolvedHistoryTable)
                .baselineVersion(MigrationVersion.fromVersion("1"))
                .baselineDescription("verified IM Core schema")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .failOnMissingLocations(true)
                .load();
    }

    public static ImMigrationRunner standard(String jdbcUrl, String username, String password) {
        return forLocations(jdbcUrl, username, password, HISTORY_TABLE, MIGRATION_LOCATION);
    }

    static ImMigrationRunner forLocations(
            String jdbcUrl,
            String username,
            String password,
            String historyTable,
            String... locations
    ) {
        return new ImMigrationRunner(jdbcUrl, username, password, historyTable, locations);
    }

    public MigrateResult migrate() {
        return flyway.migrate();
    }

    public void validate() {
        flyway.validate();
    }

    public BaselineResult baselineAtVersionOne(String confirmation) {
        if (!BASELINE_CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("baseline confirmation must equal " + BASELINE_CONFIRMATION);
        }
        ImSchemaVerifier.verifyExactV001(jdbcUrl, username, password);
        return flyway.baseline();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

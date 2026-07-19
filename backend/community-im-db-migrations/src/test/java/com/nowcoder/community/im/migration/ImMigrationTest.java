package com.nowcoder.community.im.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
class ImMigrationTest {

    private static final String HISTORY_TABLE = "im_core_schema_history";
    private static final String VERSION_ONE_SHA256 =
            "d91805a186e2328a50f8eb992a4baf63a68a79f1a7c7b834c9b35d97f93949b1";
    private static final String MANIFEST_SHA256 =
            "58fcaf4d49790fd4827757953ddec3e48515b52b663baa24d486c1504a9e9b26";

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("im_core_migration")
            .withUsername("im_core_migrator")
            .withPassword("im_core_migrator");

    @Test
    void emptyDatabaseShouldMigrateToTheExactTwelveTableLegacyCatalog(@TempDir Path tempDir)
            throws Exception {
        Database migrated = freshDatabase("empty");
        Database legacy = freshDatabase("legacy_catalog");
        applyLegacySchema(legacy, tempDir.resolve("legacy"));

        Object runner = ImMigrationReflectionSupport.newStandardRunner(
                migrated.url(), MYSQL.getUsername(), MYSQL.getPassword());
        Object result = ImMigrationReflectionSupport.invoke(
                runner, "migrate", new Class<?>[0]);

        assertThat(ImMigrationReflectionSupport.migrationsExecuted(result)).isEqualTo(2);
        assertThat(tableNames(migrated))
                .containsExactlyInAnyOrderElementsOf(union(ImSchemaTestSupport.IM_TABLES, HISTORY_TABLE));
        assertThat(ImSchemaTestSupport.captureMysqlExact(
                migrated.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                Set.of(HISTORY_TABLE, "outbox_event")))
                .isEqualTo(ImSchemaTestSupport.captureMysqlExact(
                        legacy.url(), MYSQL.getUsername(), MYSQL.getPassword(), Set.of("outbox_event")));
        assertThat(queryLong(migrated,
                "select current_version from im_membership_version_counter where id = 1"))
                .isZero();
        assertOutboxLeaseFencingSchema(migrated);
        assertCanonicalCatalog(migrated);
    }

    @Test
    void repeatedMigrateShouldBeNoOpAndValidateShouldSucceed() throws Exception {
        Database database = freshDatabase("repeat");
        Object runner = ImMigrationReflectionSupport.newStandardRunner(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());

        Object first = ImMigrationReflectionSupport.invoke(runner, "migrate", new Class<?>[0]);
        Object second = ImMigrationReflectionSupport.invoke(runner, "migrate", new Class<?>[0]);
        ImMigrationReflectionSupport.invoke(runner, "validate", new Class<?>[0]);

        assertThat(ImMigrationReflectionSupport.migrationsExecuted(first)).isEqualTo(2);
        assertThat(ImMigrationReflectionSupport.migrationsExecuted(second)).isZero();
        assertThat(queryLong(database,
                "select count(*) from " + HISTORY_TABLE + " where success = 1"))
                .isEqualTo(2L);
    }

    @Test
    void validateShouldRejectAnAppliedMigrationWhoseChecksumChanged(@TempDir Path tempDir)
            throws Exception {
        Database database = freshDatabase("checksum");
        Path migration = tempDir.resolve("V001__checksum_probe.sql");
        Files.writeString(migration, "create table checksum_probe(id bigint primary key);\n");
        Object runner = ImMigrationReflectionSupport.newRunnerForLocations(
                database.url(),
                MYSQL.getUsername(),
                MYSQL.getPassword(),
                "im_checksum_history",
                "filesystem:" + tempDir.toAbsolutePath()
        );
        ImMigrationReflectionSupport.invoke(runner, "migrate", new Class<?>[0]);

        Files.writeString(migration,
                "create table checksum_probe(id bigint primary key, value varchar(32));\n");

        assertThatThrownBy(() -> ImMigrationReflectionSupport.invoke(
                runner, "validate", new Class<?>[0]))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void versionOneMigrationAndManifestShouldRemainFrozen() throws Exception {
        Path migrationDirectory = ImSchemaTestSupport.findRepositoryRoot().resolve(
                "backend/community-im-db-migrations/src/main/resources/db/migration/im-core");

        assertThat(sha256(migrationDirectory.resolve("V001__im_core_baseline.sql")))
                .isEqualTo(VERSION_ONE_SHA256);
        assertThat(sha256(migrationDirectory.resolve("im-core-schema-manifest.tsv")))
                .isEqualTo(MANIFEST_SHA256);
    }

    @Test
    void exactLegacyBaselineShouldUpgradeAndPreserveImBusinessData(@TempDir Path tempDir)
            throws Exception {
        Database database = freshDatabase("upgrade");
        applyLegacySchema(database, tempDir.resolve("legacy"));
        insertLegacyFixture(database);
        Object runner = ImMigrationReflectionSupport.newRunnerForLocations(
                database.url(),
                MYSQL.getUsername(),
                MYSQL.getPassword(),
                "im_upgrade_history",
                "classpath:db/migration/im-core"
        );

        assertThatThrownBy(() -> ImMigrationReflectionSupport.invoke(
                runner, "migrate", new Class<?>[0]))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");

        String confirmation = ImMigrationReflectionSupport.stringConstant(
                ImMigrationReflectionSupport.requireClass(ImMigrationReflectionSupport.RUNNER_CLASS),
                "BASELINE_CONFIRMATION");
        ImMigrationReflectionSupport.invoke(
                runner, "baselineAtVersionOne", new Class<?>[]{String.class}, confirmation);
        Object result = ImMigrationReflectionSupport.invoke(
                runner, "migrate", new Class<?>[0]);
        ImMigrationReflectionSupport.invoke(runner, "validate", new Class<?>[0]);

        assertThat(ImMigrationReflectionSupport.migrationsExecuted(result)).isEqualTo(1);
        assertThat(queryString(database,
                "select name from im_room where room_id = x'30000000000070008000000000000001'"))
                .isEqualTo("legacy-room");
        assertThat(queryString(database,
                "select content from im_room_message where message_id = "
                        + "x'30000000000070008000000000000004'"))
                .isEqualTo("legacy-message");
        assertThat(queryString(database,
                "select payload from outbox_event where event_id = 'legacy-im-pending'"))
                .isEqualTo("{\"phase\":\"pending\"}");
        assertThat(queryLong(database,
                "select current_version from im_membership_version_counter where id = 1"))
                .isEqualTo(3L);
        assertOutboxLeaseFencingSchema(database);
        assertThat(outboxEventStates(database, "legacy-im-%")).containsExactly(
                new OutboxEventState(
                        "30000000000070008000000000000005",
                        "legacy-im-pending",
                        "{\"phase\":\"pending\"}",
                        "PENDING",
                        3,
                        null,
                        null
                ),
                new OutboxEventState(
                        "30000000000070008000000000000006",
                        "legacy-im-processing",
                        "{\"phase\":\"processing\"}",
                        "PROCESSING",
                        7,
                        null,
                        null
                )
        );
    }

    @Test
    void baselineShouldRejectSchemaDriftBeforeCreatingHistory(@TempDir Path tempDir)
            throws Exception {
        Database database = freshDatabase("drift");
        applyLegacySchema(database, tempDir.resolve("legacy"));
        execute(database, "alter table im_room modify name varchar(64) null");
        Object runner = ImMigrationReflectionSupport.newRunnerForLocations(
                database.url(),
                MYSQL.getUsername(),
                MYSQL.getPassword(),
                "im_upgrade_history",
                "classpath:db/test/im-core-upgrade"
        );
        String confirmation = ImMigrationReflectionSupport.stringConstant(
                ImMigrationReflectionSupport.requireClass(ImMigrationReflectionSupport.RUNNER_CLASS),
                "BASELINE_CONFIRMATION");

        Throwable failure = catchThrowable(() -> ImMigrationReflectionSupport.invoke(
                runner,
                "baselineAtVersionOne",
                new Class<?>[]{String.class},
                confirmation
        ));

        assertThat(failure).isNotNull();
        assertThat(failure.getClass().getName())
                .isEqualTo("com.nowcoder.community.im.migration.ImSchemaMismatchException");
        assertThat(failure).hasMessageContaining("does not exactly match V001");
        assertThat(tableNames(database)).doesNotContain("im_upgrade_history");
    }

    @Test
    void h2FixtureShouldExactlyMatchMigratedMysqlColumnsIndexesOnUpdateAndCounterSeed()
            throws Exception {
        Database mysql = freshDatabase("h2_equivalence");
        Object runner = ImMigrationReflectionSupport.newStandardRunner(
                mysql.url(), MYSQL.getUsername(), MYSQL.getPassword());
        ImMigrationReflectionSupport.invoke(runner, "migrate", new Class<?>[0]);
        String h2Url = "jdbc:h2:mem:im_core_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        try (Connection h2 = DriverManager.getConnection(h2Url, "sa", "")) {
            String h2Sql = Files.readString(ImSchemaTestSupport.h2SchemaFixture());
            ImSchemaTestSupport.executeStatements(h2, h2Sql);

            ImSchemaTestSupport.PortableCatalog mysqlCatalog =
                    ImSchemaTestSupport.captureMysqlPortable(
                            mysql.url(), MYSQL.getUsername(), MYSQL.getPassword(), Set.of(HISTORY_TABLE));
            ImSchemaTestSupport.PortableCatalog h2Catalog =
                    ImSchemaTestSupport.captureH2Portable(h2);

            assertThat(h2Catalog.columns().keySet())
                    .containsExactlyInAnyOrderElementsOf(ImSchemaTestSupport.IM_TABLES);
            assertThat(h2Catalog).isEqualTo(mysqlCatalog);
            assertThat(ImSchemaTestSupport.declaredOnUpdateColumns(h2Sql))
                    .isEqualTo(ImSchemaTestSupport.mysqlOnUpdateColumns(
                            mysql.url(), MYSQL.getUsername(), MYSQL.getPassword()));
            assertThat(queryLong(h2,
                    "select current_version from im_membership_version_counter where id = 1"))
                    .isEqualTo(queryLong(mysql,
                            "select current_version from im_membership_version_counter where id = 1"));
        }
    }

    private static void assertCanonicalCatalog(Database database) {
        Class<?> catalogClass = ImMigrationReflectionSupport.requireClass(
                ImMigrationReflectionSupport.CATALOG_CLASS);
        Object actual = ImMigrationReflectionSupport.invokeStatic(
                catalogClass,
                "capture",
                new Class<?>[]{String.class, String.class, String.class},
                database.url(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        );
        Object withoutHistory = ImMigrationReflectionSupport.invoke(
                actual, "withoutTables", new Class<?>[]{Set.class},
                Set.of(HISTORY_TABLE, "outbox_event"));
        Object canonical = ImMigrationReflectionSupport.invokeStatic(
                catalogClass, "canonical", new Class<?>[0]);
        Object canonicalWithoutOutbox = ImMigrationReflectionSupport.invoke(
                canonical, "withoutTables", new Class<?>[]{Set.class}, Set.of("outbox_event"));
        assertThat(withoutHistory).isEqualTo(canonicalWithoutOutbox);
    }

    private static void applyLegacySchema(Database database, Path directory) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("V001__legacy_im_core.sql"),
                ImSchemaTestSupport.legacySchemaSql());
        Flyway.configure()
                .dataSource(database.url(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + directory.toAbsolutePath())
                .table("legacy_im_schema_history")
                .cleanDisabled(true)
                .load()
                .migrate();
        execute(database, "drop table legacy_im_schema_history");
    }

    private static void insertLegacyFixture(Database database) throws Exception {
        execute(database, "insert into im_room(room_id, name, last_seq) values "
                + "(x'30000000000070008000000000000001', 'legacy-room', 7)");
        execute(database, "insert into im_room_member(room_id, user_id, role, version) values "
                + "(x'30000000000070008000000000000001', "
                + "x'30000000000070008000000000000002', 1, 3)");
        execute(database, "update im_membership_version_counter set current_version = 3 where id = 1");
        execute(database, "insert into im_room_message("
                + "room_id, seq, message_id, from_user_id, content, client_msg_id) values ("
                + "x'30000000000070008000000000000001', 7, "
                + "x'30000000000070008000000000000004', "
                + "x'30000000000070008000000000000002', "
                + "'legacy-message', 'legacy-client-message')");
        execute(database, "insert into outbox_event("
                + "id, event_id, topic, event_key, payload, status, retry_count) values ("
                + "x'30000000000070008000000000000005', 'legacy-im-pending', "
                + "'im.events', 'legacy-room', '{\\\"phase\\\":\\\"pending\\\"}', 'PENDING', 3), ("
                + "x'30000000000070008000000000000006', 'legacy-im-processing', "
                + "'im.events', 'legacy-room', '{\\\"phase\\\":\\\"processing\\\"}', 'PROCESSING', 7)");
    }

    private static Database freshDatabase(String prefix) throws Exception {
        String name = "im_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create database `" + name
                    + "` character set utf8mb4 collate utf8mb4_unicode_ci");
            statement.execute("grant all privileges on `" + name + "`.* to '"
                    + MYSQL.getUsername() + "'@'%'");
        }
        return new Database(name,
                MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + name));
    }

    private static Set<String> tableNames(Database database) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             ResultSet rows = connection.getMetaData().getTables(
                     database.name(), null, "%", new String[]{"TABLE"})) {
            Set<String> names = new java.util.LinkedHashSet<>();
            while (rows.next()) {
                names.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
            return names;
        }
    }

    private static Set<String> columnNames(Database database, String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             ResultSet rows = connection.getMetaData().getColumns(
                     database.name(), null, table, "%")) {
            Set<String> names = new java.util.LinkedHashSet<>();
            while (rows.next()) {
                names.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            return names;
        }
    }

    private static void assertOutboxLeaseFencingSchema(Database database) throws Exception {
        assertThat(columnDefinition(database, "outbox_event", "lease_token"))
                .isEqualTo(new ColumnDefinition("binary(16)", true));
        assertThat(columnDefinition(database, "outbox_event", "processing_lease_until"))
                .isEqualTo(new ColumnDefinition("timestamp", true));
        assertThat(indexColumns(database, "outbox_event", "idx_outbox_processing_lease"))
                .containsExactly("status", "processing_lease_until", "id");
    }

    private static ColumnDefinition columnDefinition(
            Database database,
            String table,
            String column
    ) throws Exception {
        String sql = "select column_type, is_nullable from information_schema.columns "
                + "where table_schema = ? and table_name = ? and column_name = ?";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database.name());
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new ColumnDefinition(
                        rows.getString("column_type").toLowerCase(Locale.ROOT),
                        "YES".equals(rows.getString("is_nullable"))
                );
            }
        }
    }

    private static List<String> indexColumns(
            Database database,
            String table,
            String index
    ) throws Exception {
        String sql = "select column_name from information_schema.statistics "
                + "where table_schema = ? and table_name = ? and index_name = ? order by seq_in_index";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database.name());
            statement.setString(2, table);
            statement.setString(3, index);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> columns = new ArrayList<>();
                while (rows.next()) {
                    columns.add(rows.getString("column_name").toLowerCase(Locale.ROOT));
                }
                return columns;
            }
        }
    }

    private static List<OutboxEventState> outboxEventStates(
            Database database,
            String eventIdPattern
    ) throws Exception {
        String sql = "select hex(id), event_id, payload, status, retry_count, "
                + "hex(lease_token), processing_lease_until from outbox_event "
                + "where event_id like ? order by event_id";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventIdPattern);
            try (ResultSet rows = statement.executeQuery()) {
                List<OutboxEventState> states = new ArrayList<>();
                while (rows.next()) {
                    states.add(new OutboxEventState(
                            rows.getString(1),
                            rows.getString(2),
                            rows.getString(3),
                            rows.getString(4),
                            rows.getInt(5),
                            rows.getString(6),
                            rows.getTimestamp(7)
                    ));
                }
                return states;
            }
        }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static long queryLong(Database database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword())) {
            return queryLong(connection, sql);
        }
    }

    private static long queryLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String queryString(Database database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static void execute(Database database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Set<String> union(Set<String> values, String additional) {
        Set<String> result = new java.util.LinkedHashSet<>(values);
        result.add(additional);
        return result;
    }

    private record Database(String name, String url) {
    }

    private record ColumnDefinition(String type, boolean nullable) {
    }

    private record OutboxEventState(
            String rowId,
            String eventId,
            String payload,
            String status,
            int retryCount,
            String leaseToken,
            Timestamp processingLeaseUntil
    ) {
    }
}

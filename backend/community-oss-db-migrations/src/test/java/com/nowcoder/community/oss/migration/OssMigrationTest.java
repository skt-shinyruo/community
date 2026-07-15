package com.nowcoder.community.oss.migration;

import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class OssMigrationTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("community_oss_migration")
            .withUsername("community_oss_migrator")
            .withPassword("community_oss_migrator");

    @Test
    void emptyDatabaseShouldExactlyMatchCanonicalOssCatalogAndSeedPolicy() throws Exception {
        Database database = freshDatabase("empty");

        var result = OssMigrationRunner.standard(database.url(), MYSQL.getUsername(), MYSQL.getPassword()).migrate();

        assertThat(result.migrationsExecuted).isEqualTo(3);
        assertThat(OssSchemaCatalog.capture(database.url(), MYSQL.getUsername(), MYSQL.getPassword())
                .withoutTables(Set.of(OssMigrationRunner.HISTORY_TABLE, "oss_upload_session")))
                .isEqualTo(OssSchemaCatalog.canonical().withoutTables(Set.of("oss_upload_session")));
        assertThat(tableNames(database)).containsExactlyInAnyOrder(
                "oss_object",
                "oss_object_version",
                "oss_upload_session",
                "oss_access_grant",
                "oss_object_reference",
                "oss_usage_policy",
                OssMigrationRunner.HISTORY_TABLE
        );
        assertThat(queryLong(database, "select count(*) from oss_usage_policy where `usage` = 'DRIVE_FILE'"))
                .isEqualTo(1L);
        assertThat(columnNames(database, "oss_upload_session"))
                .contains("request_id", "updated_at", "last_error", "claim_version");
        assertThat(queryLong(database,
                "select count(*) from oss_upload_session where claim_version = 0"))
                .isZero();
        assertThat(queryLong(database, "select count(distinct index_name) from information_schema.statistics "
                + "where table_schema = database() and table_name = 'oss_upload_session' "
                + "and index_name in ('uk_oss_upload_request', 'idx_oss_upload_recovery')"))
                .isEqualTo(2L);
    }

    @Test
    void repeatedMigrateShouldBeNoOpAndValidateShouldSucceed() throws Exception {
        Database database = freshDatabase("repeat");
        OssMigrationRunner runner = OssMigrationRunner.standard(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());

        assertThat(runner.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(runner.migrate().migrationsExecuted).isZero();
        runner.validate();
        assertThat(queryLong(database,
                "select count(*) from " + OssMigrationRunner.HISTORY_TABLE + " where success = 1"))
                .isEqualTo(3L);
    }

    @Test
    void validateShouldRejectChangedAppliedChecksum(@TempDir Path tempDir) throws Exception {
        Database database = freshDatabase("checksum");
        Path migration = tempDir.resolve("V001__checksum_probe.sql");
        Files.writeString(migration, "create table checksum_probe(id bigint primary key);\n");
        OssMigrationRunner runner = OssMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                "oss_checksum_history", "filesystem:" + tempDir.toAbsolutePath());
        runner.migrate();

        Files.writeString(migration, "create table checksum_probe(id bigint primary key, value varchar(32));\n");

        assertThatThrownBy(runner::validate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void legacyBaselineShouldUpgradeAndPreserveObjectUploadAndReferenceData(@TempDir Path tempDir) throws Exception {
        Database database = freshDatabase("upgrade");
        applyLegacySchema(database, tempDir);
        insertLegacyFixture(database);
        OssMigrationRunner runner = OssMigrationRunner.standard(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());

        assertThatThrownBy(runner::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");
        runner.baselineAtVersionOne(OssMigrationRunner.BASELINE_CONFIRMATION);
        assertThat(runner.migrate().migrationsExecuted).isEqualTo(2);
        runner.validate();

        assertThat(queryString(database, "select owner_id from oss_object where owner_id = 'post-7'"))
                .isEqualTo("post-7");
        assertThat(queryString(database, "select expected_file_name from oss_upload_session"))
                .isEqualTo("photo.png");
        assertThat(queryString(database, "select subject_id from oss_object_reference"))
                .isEqualTo("post-7");
        assertThat(columnNames(database, "oss_upload_session"))
                .contains("request_id", "updated_at", "last_error", "claim_version");
        assertThat(queryLong(database,
                "select count(*) from oss_upload_session where request_id = session_id and updated_at is not null"))
                .isEqualTo(1L);
        assertThat(queryString(database, "select last_error from oss_upload_session"))
                .isEmpty();
        assertThat(queryLong(database, "select claim_version from oss_upload_session"))
                .isZero();
    }

    @Test
    void mismatchedLegacyCatalogShouldBeRejectedBeforeHistoryCreation(@TempDir Path tempDir) throws Exception {
        Database database = freshDatabase("mismatch");
        applyLegacySchema(database, tempDir);
        execute(database, "alter table oss_object modify latest_content_length bigint not null default 7");
        OssMigrationRunner runner = OssMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                "oss_upgrade_history", "classpath:db/test/community-oss-upgrade");

        assertThatThrownBy(() -> runner.baselineAtVersionOne(OssMigrationRunner.BASELINE_CONFIRMATION))
                .isInstanceOf(OssSchemaMismatchException.class)
                .hasMessageContaining("does not exactly match V001")
                .hasMessageContaining("columns/defaults");
        assertThat(tableNames(database)).doesNotContain("oss_upgrade_history");
    }

    private static void applyLegacySchema(Database database, Path tempDir) throws Exception {
        Path source = findRepositoryRoot().resolve("deploy/mysql/community_oss/010_schema.sql");
        Path migrationDirectory = Files.createDirectories(tempDir.resolve("legacy-oss"));
        String sql = Files.readString(source).replaceAll("(?im)^\\s*use\\s+`?community_oss`?\\s*;\\s*", "");
        Files.writeString(migrationDirectory.resolve("V001__legacy_oss_schema.sql"), sql);
        OssMigrationRunner.forLocations(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                        "legacy_oss_history", "filesystem:" + migrationDirectory.toAbsolutePath())
                .migrate();
        execute(database, "drop table legacy_oss_history");
        assertThat(OssSchemaCatalog.capture(database.url(), MYSQL.getUsername(), MYSQL.getPassword()))
                .isEqualTo(OssSchemaCatalog.canonical());
    }

    private static void insertLegacyFixture(Database database) throws Exception {
        execute(database, "insert into oss_object(object_id, `usage`, owner_service, owner_domain, owner_type, "
                + "owner_id, visibility, status, current_version_id, created_by) values "
                + "(x'20000000000070008000000000000001', 'POST_MEDIA', 'community-app', 'content', "
                + "'post', 'post-7', 'PRIVATE', 'ACTIVE', x'20000000000070008000000000000002', 'actor-7')");
        execute(database, "insert into oss_object_version(version_id, object_id, version_no, storage_backend, "
                + "storage_bucket, storage_key, status, file_name, content_type, content_length) values "
                + "(x'20000000000070008000000000000002', x'20000000000070008000000000000001', 1, "
                + "'S3_COMPATIBLE', 'community-oss', 'objects/post-7/photo.png', 'ACTIVE', 'photo.png', "
                + "'image/png', 42)");
        execute(database, "insert into oss_upload_session(session_id, object_id, version_id, upload_mode, "
                + "owner_service, owner_domain, owner_type, owner_id, expected_file_name, expected_content_type, "
                + "expected_content_length, status, expires_at, created_by) values "
                + "(x'20000000000070008000000000000003', x'20000000000070008000000000000001', "
                + "x'20000000000070008000000000000002', 'PROXY', 'community-app', 'content', 'post', "
                + "'post-7', 'photo.png', 'image/png', 42, 'COMPLETED', '2027-01-01 00:00:00', 'actor-7')");
        execute(database, "insert into oss_object_reference(reference_id, object_id, version_id, subject_service, "
                + "subject_domain, subject_type, subject_id, reference_role, status) values "
                + "(x'20000000000070008000000000000004', x'20000000000070008000000000000001', "
                + "x'20000000000070008000000000000002', 'community-app', 'content', 'post-media', "
                + "'post-7', 'PRIMARY', 'ACTIVE')");
    }

    private static Path findRepositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("deploy/mysql/community_oss"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("cannot locate deploy/mysql/community_oss");
    }

    private static Database freshDatabase(String prefix) throws Exception {
        String name = "oss_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create database `" + name + "` character set utf8mb4 collate utf8mb4_unicode_ci");
            statement.execute("grant all privileges on `" + name + "`.* to '" + MYSQL.getUsername() + "'@'%'");
        }
        return new Database(name, MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + name));
    }

    private static Set<String> tableNames(Database database) throws Exception {
        try (Connection connection = DriverManager.getConnection(database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             ResultSet rows = connection.getMetaData().getTables(database.name(), null, "%", new String[]{"TABLE"})) {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            while (rows.next()) {
                names.add(rows.getString("TABLE_NAME").toLowerCase());
            }
            return names;
        }
    }

    private static Set<String> columnNames(Database database, String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             ResultSet rows = connection.getMetaData().getColumns(database.name(), null, table, "%")) {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            while (rows.next()) {
                names.add(rows.getString("COLUMN_NAME").toLowerCase());
            }
            return names;
        }
    }

    private static long queryLong(Database database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String queryString(Database database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static void execute(Database database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private record Database(String name, String url) {
    }
}

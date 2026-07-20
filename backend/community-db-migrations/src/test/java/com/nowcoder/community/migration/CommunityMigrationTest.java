package com.nowcoder.community.migration;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CommunityMigrationTest {

    private static final String VERSION_ONE_SHA256 =
            "3c2c2541d68342d92c4ccd840dbde8aabe2e6bcad53ef64cc0251b148b63a829";
    private static final String MANIFEST_SHA256 =
            "afac5a24716bd1c339b51963f028c3af3ac1430a3f8ac94d4485b0eb47f3e3e6";

    private static final List<String> LEGACY_COMMUNITY_SCHEMA_FILES = List.of(
            "010_schema_shared.sql",
            "011_schema_demo_metadata.sql",
            "020_schema_identity.sql",
            "031_schema_growth_wallet.sql",
            "032_schema_growth_market.sql",
            "033_schema_growth_task.sql",
            "040_schema_content_core.sql",
            "050_schema_social.sql",
            "060_schema_notice.sql",
            "080_schema_search.sql",
            "090_schema_drive.sql"
    );

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("community_migration")
            .withUsername("community_migrator")
            .withPassword("community_migrator");

    @Test
    void emptyDatabaseShouldApplyCurrentMigrationsWhileKeepingTheVersionOneCatalogFrozen() throws Exception {
        Database database = freshDatabase("empty");

        var result = CommunityMigrationRunner.standard(database.url(), MYSQL.getUsername(), MYSQL.getPassword())
                .migrate();

        assertThat(result.migrationsExecuted).isEqualTo(9);
        CommunitySchemaCatalog migratedCatalog = CommunitySchemaCatalog
                .capture(database.url(), MYSQL.getUsername(), MYSQL.getPassword())
                .withoutTables(Set.of(
                        CommunityMigrationRunner.HISTORY_TABLE,
                        "social_like_target_state",
                        "post_media_asset",
                        "auth_refresh_token",
                        "outbox_event",
                        "comment"
                ));
        assertThat(migratedCatalog)
                .isEqualTo(CommunitySchemaCatalog.canonical().withoutTables(Set.of(
                        "post_media_asset",
                        "auth_refresh_token",
                        "outbox_event",
                        "comment"
                )));
        assertThat(tableNames(database)).contains("social_like_target_state");
        assertThat(columnNames(database, "post_media_asset")).contains(
                "reference_status", "reference_operation_version", "reference_updated_at",
                "upload_status", "upload_operation_version", "upload_updated_at");
        assertThat(columnNames(database, "auth_refresh_token")).contains("security_version");
        assertThat(columnNames(database, "comment")).contains("version");
        assertOutboxLeaseFencingSchema(database);
        assertThat(tableNames(database)).doesNotContain(
                "im_conversation",
                "im_message",
                "oss_object",
                "nacos_config_info",
                "xxl_job_info"
        );
        assertThat(queryLong(database, "select count(*) from user")).isZero();
        assertThat(queryLong(database, "select count(*) from task_template")).isEqualTo(4L);
        assertThat(queryLong(database, "select count(*) from category")).isEqualTo(3L);
        assertThat(queryStrings(database, "select name from category order by position"))
                .containsExactly("公告", "技术", "兴趣");
    }

    @Test
    void repeatedMigrateShouldBeANoOpAndValidateShouldSucceed() throws Exception {
        Database database = freshDatabase("repeat");
        CommunityMigrationRunner runner = CommunityMigrationRunner.standard(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());

        assertThat(runner.migrate().migrationsExecuted).isEqualTo(9);
        assertThat(runner.migrate().migrationsExecuted).isZero();
        runner.validate();

        assertThat(queryLong(database,
                "select count(*) from " + CommunityMigrationRunner.HISTORY_TABLE + " where success = 1"))
                .isEqualTo(9L);
    }

    @Test
    void validateShouldRejectAnAppliedMigrationWhoseChecksumChanged(@TempDir Path tempDir) throws Exception {
        Database database = freshDatabase("checksum");
        Path migration = tempDir.resolve("V001__checksum_probe.sql");
        Files.writeString(migration, "create table checksum_probe(id bigint primary key);\n");
        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                "checksum_schema_history", "filesystem:" + tempDir.toAbsolutePath());
        runner.migrate();

        Files.writeString(migration, "create table checksum_probe(id bigint primary key, value varchar(32));\n");

        assertThatThrownBy(runner::validate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void versionOneMigrationAndManifestShouldRemainFrozen() throws Exception {
        Path migrationDirectory = findRepositoryRoot().resolve(
                "backend/community-db-migrations/src/main/resources/db/migration/community");

        assertThat(sha256(migrationDirectory.resolve("V001__baseline.sql")))
                .isEqualTo(VERSION_ONE_SHA256);
        assertThat(sha256(migrationDirectory.resolve("community-schema-manifest.tsv")))
                .isEqualTo(MANIFEST_SHA256);
    }

    @Test
    void h2FixtureShouldDeclareTheMigratedOutboxLeaseFencingShape() throws Exception {
        String schema = Files.readString(findRepositoryRoot().resolve(
                "backend/community-app/src/test/resources/schema.sql"));

        assertThat(schema).contains(
                "  status varchar(32) not null,\n"
                        + "  lease_token binary(16) null,\n"
                        + "  processing_lease_until timestamp null,\n"
                        + "  retry_count int not null default 0,");
        assertThat(schema).contains(
                "create index if not exists idx_outbox_processing_lease "
                        + "on outbox_event(status, processing_lease_until, id);");
        assertThat(schema).contains(
                "-- Runtime states: P=PROCESSING, S=SUCCESS, I=INDETERMINATE.\n"
                        + "  status varchar(16) not null,");
    }

    @Test
    void v008StateShouldQuarantineOnlyProcessingIdempotencyRows(@TempDir Path tempDir) throws Exception {
        Database database = freshDatabase("v008_idempotency");
        Path v008Directory = prepareMigrationDirectoryThroughVersionEight(tempDir);
        CommunityMigrationRunner.forLocations(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                        "community_v008_history", "filesystem:" + v008Directory.toAbsolutePath())
                .migrate();
        insertV008IdempotencyFixture(database);

        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                "community_v008_history", CommunityMigrationRunner.MIGRATION_LOCATION);

        assertThat(runner.migrate().migrationsExecuted).isEqualTo(1);
        runner.validate();

        assertThat(idempotencyRows(database)).containsExactlyInAnyOrder(
                new IdempotencyRow(
                        "legacy-p", "legacy-p-key", "legacy-p-hash", "I", "legacy-p-response",
                        null, Timestamp.valueOf("2035-01-01 00:00:01")
                ),
                new IdempotencyRow(
                        "legacy-s", "legacy-s-key", "legacy-s-hash", "S", "legacy-s-response",
                        Timestamp.valueOf("2035-01-01 00:00:12"), Timestamp.valueOf("2035-01-01 00:00:02")
                ),
                new IdempotencyRow(
                        "legacy-i", "legacy-i-key", "legacy-i-hash", "I", "legacy-i-response",
                        Timestamp.valueOf("2035-01-01 00:00:13"), Timestamp.valueOf("2035-01-01 00:00:03")
                )
        );
        assertThat(queryString(database,
                "select payload from outbox_event where event_id = 'v009-business-row'"))
                .isEqualTo("{\"preserve\":true}");
    }

    @Test
    void completeLegacyBaselineShouldUpgradeAndPreserveRealBusinessData(@TempDir Path tempDir) throws Exception {
        Database database = freshDatabase("upgrade");
        applyLegacyCommunitySchema(database, tempDir);
        insertUpgradeFixture(database);
        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                "community_upgrade_history", "classpath:db/test/community-upgrade");

        assertThatThrownBy(runner::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");

        runner.baselineAtVersionOne(CommunityMigrationRunner.BASELINE_CONFIRMATION);
        assertThat(runner.migrate().migrationsExecuted).isEqualTo(1);
        runner.validate();

        assertThat(queryString(database, "select username from user where username = 'migration-user'"))
                .isEqualTo("migration-user");
        assertThat(queryLong(database, "select balance from wallet_account where account_type = 'AVAILABLE'"))
                .isEqualTo(4242L);
        assertThat(queryString(database, "select title from discuss_post where title = 'migration-post'"))
                .isEqualTo("migration-post");
        assertThat(queryString(database, "select payload from outbox_event where event_id = 'migration-event'"))
                .isEqualTo("{\"preserve\":true}");
        assertThat(columnNames(database, "user")).contains("migration_probe");
    }

    @Test
    void legacyBaselineUpgradeShouldQuarantineResidualProcessingRows(@TempDir Path tempDir) throws Exception {
        Database database = freshDatabase("upgrade_idempotency");
        applyLegacyCommunitySchema(database, tempDir);
        insertUpgradeFixture(database);
        insertLegacyIdempotencyProcessingRow(database);
        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                "community_upgrade_idempotency_history", CommunityMigrationRunner.MIGRATION_LOCATION);

        assertThatThrownBy(runner::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");

        runner.baselineAtVersionOne(CommunityMigrationRunner.BASELINE_CONFIRMATION);
        assertThat(runner.migrate().migrationsExecuted).isEqualTo(8);
        runner.validate();

        IdempotencyRow residual = idempotencyRows(database).stream()
                .filter(row -> row.idemKey().equals("upgrade-p-key"))
                .findFirst()
                .orElseThrow();
        assertThat(residual.status()).isEqualTo("I");
        assertThat(residual.processingExpiresAt()).isNull();
        assertThat(residual.requestHash()).isEqualTo("upgrade-p-hash");
        assertThat(residual.responseJson()).isEqualTo("upgrade-p-response");
        assertThat(queryString(database, "select username from user where username = 'migration-user'"))
                .isEqualTo("migration-user");
        assertThat(queryString(database,
                "select payload from outbox_event where event_id = 'migration-event'"))
                .isEqualTo("{\"preserve\":true}");
        assertThat(queryLong(database,
                "select count(*) from community_upgrade_idempotency_history where success = 1"))
                .isEqualTo(9L);
    }

    @Test
    void waveThreeMigrationsShouldUpgradeVersionOneDataWithoutTrustingLegacyRefreshSessions(
            @TempDir Path tempDir
    ) throws Exception {
        Database database = freshDatabase("wave_three_upgrade");
        Path versionOneDirectory = prepareVersionOneDirectory(tempDir);
        CommunityMigrationRunner.forLocations(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                        CommunityMigrationRunner.HISTORY_TABLE,
                        "filesystem:" + versionOneDirectory.toAbsolutePath())
                .migrate();

        execute(database, "insert into auth_refresh_token(" +
                "token_hash, user_id, family_id, expires_at, state" +
                ") values (repeat('a', 64), x'10000000000070008000000000000001', " +
                "'legacy-family-active', date_add(current_timestamp, interval 1 day), 'ACTIVE')");
        execute(database, "insert into auth_refresh_token(" +
                "token_hash, user_id, family_id, expires_at, state, pending_expires_at" +
                ") values (repeat('b', 64), x'10000000000070008000000000000001', " +
                "'legacy-family-pending', date_add(current_timestamp, interval 1 day), " +
                "'PENDING_ROTATION', date_add(current_timestamp, interval 5 minute))");
        execute(database, "insert into post_media_asset(" +
                "id, owner_user_id, post_id, oss_object_id, oss_reference_id, file_name, " +
                "content_type, content_length, media_kind, lifecycle, video_state" +
                ") values (" +
                "x'10000000000070008000000000000011', x'10000000000070008000000000000001', " +
                "x'10000000000070008000000000000021', x'10000000000070008000000000000031', " +
                "x'10000000000070008000000000000041', 'bound.png', 'image/png', 10, " +
                "'IMAGE', 'BOUND', 'NONE'), (" +
                "x'10000000000070008000000000000012', x'10000000000070008000000000000001', " +
                "null, x'10000000000070008000000000000032', null, 'draft.png', 'image/png', 11, " +
                "'IMAGE', 'UPLOADED', 'NONE')");
        execute(database, "insert into outbox_event(" +
                "id, event_id, topic, event_key, payload, status, retry_count" +
                ") values (" +
                "x'10000000000070008000000000000052', 'migration-lease-pending', " +
                "'migration.topic', 'pending', '{\"phase\":\"pending\"}', 'PENDING', 2), (" +
                "x'10000000000070008000000000000053', 'migration-lease-processing', " +
                "'migration.topic', 'processing', '{\"phase\":\"processing\"}', 'PROCESSING', 5)");

        var result = CommunityMigrationRunner.standard(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword())
                .migrate();

        String mediaReleaseEventId = "content-media-reference:"
                + "10000000-0000-7000-8000-000000000011:1:RELEASE";
        execute(database, "insert into outbox_event(" +
                "id, event_id, topic, event_key, payload, status" +
                ") values (x'10000000000070008000000000000051', '" + mediaReleaseEventId + "', " +
                "'content.media.reference', 'media-reference', '{}', 'NEW')");

        assertThat(result.migrationsExecuted).isEqualTo(8);
        assertOutboxLeaseFencingSchema(database);
        assertThat(outboxEventStates(database, "migration-lease-%")).containsExactly(
                new OutboxEventState(
                        "10000000000070008000000000000052",
                        "migration-lease-pending",
                        "{\"phase\":\"pending\"}",
                        "PENDING",
                        2,
                        null,
                        null
                ),
                new OutboxEventState(
                        "10000000000070008000000000000053",
                        "migration-lease-processing",
                        "{\"phase\":\"processing\"}",
                        "PROCESSING",
                        5,
                        null,
                        null
                )
        );
        assertThat(queryString(database,
                "select event_id from outbox_event where event_key = 'media-reference'"))
                .isEqualTo(mediaReleaseEventId);
        assertThat(tableNames(database)).contains("social_like_target_state");
        assertThat(columnNames(database, "post_media_asset")).contains(
                "reference_status", "reference_operation_version", "reference_updated_at",
                "upload_status", "upload_operation_version", "upload_updated_at");
        assertThat(queryStrings(database,
                "select reference_status from post_media_asset order by file_name"))
                .containsExactly("BOUND", "UNBOUND");
        assertThat(columnNames(database, "auth_refresh_token")).contains("security_version");
        assertThat(columnNames(database, "comment")).contains("version");
        assertThat(queryStrings(database,
                "select upload_status from post_media_asset order by file_name"))
                .containsExactly("COMPLETED", "COMPLETED");
        assertThat(queryStrings(database,
                "select state from auth_refresh_token order by family_id"))
                .containsExactly("REVOKED", "REVOKED");
        assertThat(queryLong(database,
                "select count(*) from auth_refresh_token where security_version = 0"))
                .isEqualTo(2L);
        assertThat(queryLong(database,
                "select count(*) from auth_refresh_token where pending_expires_at is null"))
                .isEqualTo(2L);
        assertThat(queryLong(database,
                "select count(*) from auth_refresh_token where revoked_at is not null"))
                .isEqualTo(2L);
        assertThat(queryLong(database,
                "select count(*) from auth_refresh_token where security_version is null"))
                .isZero();
        assertThat(queryLong(database,
                "select count(*) from auth_refresh_token_family_revocation " +
                        "where family_id like 'legacy-family-%'"))
                .isEqualTo(2L);
        assertThat(queryLong(database,
                "select count(*) from " + CommunityMigrationRunner.HISTORY_TABLE + " where success = 1"))
                .isEqualTo(9L);
    }

    @Test
    void baselineShouldRejectEveryCatalogMismatchBeforeCreatingHistory(@TempDir Path tempDir) throws Exception {
        Map<String, String> corruptions = new LinkedHashMap<>();
        corruptions.put("missing table", "drop table drive_share_access");
        corruptions.put("additional owner table", "create table unexpected_community_owner(id bigint primary key)");
        corruptions.put("wrong column", "alter table wallet_account modify balance int not null default 0");
        corruptions.put("wrong default", "alter table wallet_account alter column balance set default 7");
        corruptions.put("wrong index", "alter table social_like drop index idx_like_entity");

        Path versionOneDirectory = prepareVersionOneDirectory(tempDir);
        for (Map.Entry<String, String> corruption : corruptions.entrySet()) {
            Database database = freshDatabase("mismatch");
            CommunityMigrationRunner.forLocations(
                            database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                            CommunityMigrationRunner.HISTORY_TABLE,
                            "filesystem:" + versionOneDirectory.toAbsolutePath())
                    .migrate();
            execute(database, "drop table " + CommunityMigrationRunner.HISTORY_TABLE);
            execute(database, corruption.getValue());
            CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                    database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                    "community_upgrade_history", "classpath:db/test/community-upgrade");

            assertThatThrownBy(() -> runner.baselineAtVersionOne(CommunityMigrationRunner.BASELINE_CONFIRMATION))
                    .as(corruption.getKey())
                    .isInstanceOf(CommunitySchemaMismatchException.class)
                    .hasMessageContaining("does not exactly match V001");
            assertThat(tableNames(database)).doesNotContain("community_upgrade_history");
        }
    }

    @Test
    void explicitDevelopmentSeedShouldUseItsFixedLocationOnly() throws Exception {
        Database database = freshDatabase("development_seed");
        Map<String, String> environment = Map.of(
                "COMMUNITY_MIGRATION_JDBC_URL", database.url(),
                "COMMUNITY_MIGRATION_USERNAME", MYSQL.getUsername(),
                "COMMUNITY_MIGRATION_PASSWORD", MYSQL.getPassword(),
                "COMMUNITY_MIGRATION_PROFILE", "development"
        );

        CommunityMigrationApplication.run(new String[]{"development-seed"}, environment);

        assertThat(queryLong(database, "select count(*) from user")).isEqualTo(3L);
        assertThat(tableNames(database)).contains(
                CommunityMigrationRunner.HISTORY_TABLE,
                CommunityMigrationRunner.DEVELOPMENT_SEED_HISTORY_TABLE
        );
    }

    private static void applyLegacyCommunitySchema(Database database, Path tempDir) throws Exception {
        Path sourceDirectory = findRepositoryRoot().resolve("deploy/mysql/community");
        Path migrationDirectory = Files.createDirectories(tempDir.resolve("legacy-community"));
        int version = 1;
        for (String filename : LEGACY_COMMUNITY_SCHEMA_FILES) {
            String sql = Files.readString(sourceDirectory.resolve(filename))
                    .replaceAll("(?im)^\\s*use\\s+`?community`?\\s*;\\s*", "");
            String migrationName = "V%03d__%s".formatted(version++, filename);
            Files.writeString(migrationDirectory.resolve(migrationName), sql);
        }
        CommunityMigrationRunner.forLocations(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                        "legacy_schema_history", "filesystem:" + migrationDirectory.toAbsolutePath())
                .migrate();
        execute(database, "drop table legacy_schema_history");
        assertThat(CommunitySchemaCatalog.capture(database.url(), MYSQL.getUsername(), MYSQL.getPassword()))
                .isEqualTo(CommunitySchemaCatalog.canonical());
    }

    private static Path prepareVersionOneDirectory(Path tempDir) throws Exception {
        Path versionOneDirectory = Files.createDirectories(tempDir.resolve("version-one"));
        Path versionOne = findRepositoryRoot().resolve(
                "backend/community-db-migrations/src/main/resources/db/migration/community/V001__baseline.sql");
        Files.writeString(versionOneDirectory.resolve("V001__baseline.sql"), Files.readString(versionOne));
        return versionOneDirectory;
    }

    private static Path prepareMigrationDirectoryThroughVersionEight(Path tempDir) throws Exception {
        Path sourceDirectory = findRepositoryRoot().resolve(
                "backend/community-db-migrations/src/main/resources/db/migration/community");
        Path migrationDirectory = Files.createDirectories(tempDir.resolve("community-v008"));
        for (int version = 1; version <= 8; version++) {
            String prefix = "V%03d__".formatted(version);
            try (var migrations = Files.list(sourceDirectory)) {
                Path migration = migrations
                        .filter(path -> path.getFileName().toString().startsWith(prefix))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("missing migration " + prefix));
                Files.copy(migration, migrationDirectory.resolve(migration.getFileName()));
            }
        }
        return migrationDirectory;
    }

    private static Path findRepositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("deploy/mysql/community"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("cannot locate deploy/mysql/community from working directory");
    }

    private static void insertUpgradeFixture(Database database) throws Exception {
        execute(database, "insert into user(id, username, status, policy_version, security_version) values "
                + "(x'10000000000070008000000000000001', 'migration-user', 1, 11, 13)");
        execute(database, "insert into wallet_account(account_id, owner_type, owner_id, account_type, balance, status) "
                + "values (x'10000000000070008000000000000002', 'USER', "
                + "x'10000000000070008000000000000001', 'AVAILABLE', 4242, 'ACTIVE')");
        execute(database, "insert into discuss_post(id, user_id, title, status) values "
                + "(x'10000000000070008000000000000003', "
                + "x'10000000000070008000000000000001', 'migration-post', 0)");
        execute(database, "insert into outbox_event(id, event_id, topic, event_key, payload, status) values "
                + "(x'10000000000070008000000000000004', 'migration-event', 'migration.topic', "
                + "'migration-key', '{\\\"preserve\\\":true}', 'NEW')");
    }

    private static void insertV008IdempotencyFixture(Database database) throws Exception {
        execute(database, "insert into http_idempotency(" +
                "id, operation, user_id, idem_key, request_hash, status, response_json, " +
                "processing_expires_at, success_expires_at" +
                ") values " +
                "(x'20000000000070008000000000000001', 'legacy-p', " +
                "x'20000000000070008000000000000011', 'legacy-p-key', 'legacy-p-hash', 'P', " +
                "'legacy-p-response', '2035-01-01 00:00:11', '2035-01-01 00:00:01'), " +
                "(x'20000000000070008000000000000002', 'legacy-s', " +
                "x'20000000000070008000000000000012', 'legacy-s-key', 'legacy-s-hash', 'S', " +
                "'legacy-s-response', '2035-01-01 00:00:12', '2035-01-01 00:00:02'), " +
                "(x'20000000000070008000000000000003', 'legacy-i', " +
                "x'20000000000070008000000000000013', 'legacy-i-key', 'legacy-i-hash', 'I', " +
                "'legacy-i-response', '2035-01-01 00:00:13', '2035-01-01 00:00:03')");
        execute(database, "insert into outbox_event(" +
                "id, event_id, topic, event_key, payload, status" +
                ") values (x'20000000000070008000000000000021', 'v009-business-row', " +
                "'migration.topic', 'v009-business-key', '{\"preserve\":true}', 'NEW')");
    }

    private static void insertLegacyIdempotencyProcessingRow(Database database) throws Exception {
        execute(database, "insert into http_idempotency(" +
                "id, operation, user_id, idem_key, request_hash, status, response_json, " +
                "processing_expires_at, success_expires_at" +
                ") values (x'30000000000070008000000000000001', 'upgrade-p', " +
                "x'30000000000070008000000000000011', 'upgrade-p-key', 'upgrade-p-hash', 'P', " +
                "'upgrade-p-response', '2035-02-01 00:00:11', '2035-02-01 00:00:01')");
    }

    private static List<IdempotencyRow> idempotencyRows(Database database) throws Exception {
        String sql = "select operation, idem_key, request_hash, status, response_json, " +
                "processing_expires_at, success_expires_at from http_idempotency order by idem_key";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<IdempotencyRow> values = new ArrayList<>();
            while (rows.next()) {
                values.add(new IdempotencyRow(
                        rows.getString(1),
                        rows.getString(2),
                        rows.getString(3),
                        rows.getString(4),
                        rows.getString(5),
                        rows.getTimestamp(6),
                        rows.getTimestamp(7)
                ));
            }
            return values;
        }
    }

    private static Database freshDatabase(String prefix) throws Exception {
        String name = "community_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create database `" + name + "` character set utf8mb4 collate utf8mb4_unicode_ci");
            statement.execute("grant all privileges on `" + name + "`.* to '"
                    + MYSQL.getUsername() + "'@'%'");
        }
        String url = MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + name);
        return new Database(name, url);
    }

    private static Set<String> tableNames(Database database) throws Exception {
        try (Connection connection = DriverManager.getConnection(database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             ResultSet resultSet = connection.getMetaData().getTables(database.name(), null, "%", new String[]{"TABLE"})) {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            while (resultSet.next()) {
                names.add(resultSet.getString("TABLE_NAME").toLowerCase());
            }
            return names;
        }
    }

    private static Set<String> columnNames(Database database, String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             ResultSet resultSet = connection.getMetaData().getColumns(database.name(), null, table, "%")) {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            while (resultSet.next()) {
                names.add(resultSet.getString("COLUMN_NAME").toLowerCase());
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
                        rows.getString("column_type").toLowerCase(),
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
                    columns.add(rows.getString("column_name").toLowerCase());
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
        try (Connection connection = DriverManager.getConnection(database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String queryString(Database database, String sql) throws Exception {
        return queryStrings(database, sql).get(0);
    }

    private static List<String> queryStrings(Database database, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
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

    private record IdempotencyRow(
            String operation,
            String idemKey,
            String requestHash,
            String status,
            String responseJson,
            Timestamp processingExpiresAt,
            Timestamp successExpiresAt
    ) {
    }
}

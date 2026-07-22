package com.nowcoder.community.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.exception.FlywayValidateException;
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
import java.sql.SQLException;
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
import static org.assertj.core.api.Assertions.catchThrowable;

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

        assertThat(result.migrationsExecuted).isEqualTo(14);
        CommunitySchemaCatalog migratedCatalog = CommunitySchemaCatalog
                .capture(database.url(), MYSQL.getUsername(), MYSQL.getPassword())
                .withoutTables(Set.of(
                        CommunityMigrationRunner.HISTORY_TABLE,
                        "social_like_target_state",
                        "post_media_asset",
                        "auth_refresh_token",
                        "outbox_event",
                        "comment",
                        "moderation_action",
                        "social_like",
                        "drive_upload",
                        "market_wallet_action",
                        "notice_record"
                ));
        assertThat(migratedCatalog)
                .isEqualTo(CommunitySchemaCatalog.canonical().withoutTables(Set.of(
                        "post_media_asset",
                        "auth_refresh_token",
                        "outbox_event",
                        "comment",
                        "moderation_action",
                        "social_like",
                        "drive_upload",
                        "market_wallet_action",
                        "notice_record"
                )));
        assertModerationActionSchema(database);
        assertSocialLikeLifecycleSchema(database);
        assertThat(tableNames(database)).contains("social_like_target_state");
        assertThat(columnNames(database, "post_media_asset")).contains(
                "reference_status", "reference_operation_version", "reference_updated_at",
                "upload_status", "upload_operation_version", "upload_updated_at");
        assertThat(columnNames(database, "auth_refresh_token")).contains("security_version");
        assertThat(columnNames(database, "comment")).contains("version");
        assertThat(columnMetadata(database, "drive_upload").get("checksum_sha256"))
                .isEqualTo(new ColumnMetadata("varchar(128)", false, ""));
        assertThat(columnDefinition(database, "notice_record", "content"))
                .isEqualTo(new ColumnDefinition("mediumtext", true));
        assertMarketWalletActionLeaseFencingSchema(database);
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

        assertThat(runner.migrate().migrationsExecuted).isEqualTo(14);
        assertThat(runner.migrate().migrationsExecuted).isZero();
        runner.validate();

        assertThat(queryLong(database,
                "select count(*) from " + CommunityMigrationRunner.HISTORY_TABLE + " where success = 1"))
                .isEqualTo(14L);
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
    void h2FixtureShouldDeclareTheMarketWalletActionLeaseFencingShape() throws Exception {
        String schema = Files.readString(findRepositoryRoot().resolve(
                "backend/community-app/src/test/resources/schema.sql"));

        assertThat(schema).contains(
                "  processing_lease_until timestamp null default null,\n"
                        + "  lease_token binary(16) null,\n"
                        + "  create_time timestamp null default current_timestamp,");
        assertThat(schema).contains(
                "create index if not exists idx_market_wallet_action_processing_lease "
                        + "on market_wallet_action(status, processing_lease_until, action_id);");
    }

    @Test
    void h2FixtureShouldDeclareNoticeContentWithoutAVarcharCap() throws Exception {
        String schema = Files.readString(findRepositoryRoot().resolve(
                "backend/community-app/src/test/resources/schema.sql"));

        assertThat(schema).contains(
                "create table if not exists notice_record (\n"
                        + "  id binary(16) primary key,\n"
                        + "  sender_user_id binary(16),\n"
                        + "  recipient_user_id binary(16) not null,\n"
                        + "  topic varchar(64) not null,\n"
                        + "  content clob,");
    }

    @Test
    void v014ShouldWidenNoticeContentAndPreserveExistingValues(@TempDir Path tempDir)
            throws Exception {
        Database database = freshDatabase("v014_notice_content");
        String historyTable = "community_v014_notice_content_history";
        Path v013Directory = prepareMigrationDirectoryThroughVersion(tempDir, 13);
        CommunityMigrationRunner.forLocations(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                        historyTable, "filesystem:" + v013Directory.toAbsolutePath())
                .migrate();
        execute(database, "insert into notice_record(" +
                "id, recipient_user_id, topic, content, status, create_time" +
                ") values " +
                "(x'71000000000070008000000000000001', " +
                "x'71000000000070008000000000000011', 'capacity-null', null, 0, current_timestamp), " +
                "(x'71000000000070008000000000000002', " +
                "x'71000000000070008000000000000011', 'capacity-full', repeat('x', 4000), 0, current_timestamp)");
        assertThat(columnDefinition(database, "notice_record", "content"))
                .isEqualTo(new ColumnDefinition("varchar(4000)", true));

        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                historyTable, CommunityMigrationRunner.MIGRATION_LOCATION);

        assertThat(runner.migrate().migrationsExecuted).isEqualTo(1);
        runner.validate();

        assertThat(columnDefinition(database, "notice_record", "content"))
                .isEqualTo(new ColumnDefinition("mediumtext", true));
        assertThat(queryLong(database,
                "select count(*) from notice_record where topic = 'capacity-null' and content is null"))
                .isEqualTo(1L);
        assertThat(queryString(database,
                "select content from notice_record where topic = 'capacity-full'"))
                .isEqualTo("x".repeat(4_000));
    }

    @Test
    void v013ShouldFenceNewClaimsAndRequeueStrandedProcessingRows(@TempDir Path tempDir)
            throws Exception {
        Database database = freshDatabase("v013_wallet");
        String historyTable = "community_v013_wallet_action_history";
        Path v012Directory = prepareMigrationDirectoryThroughVersion(tempDir, 12);
        CommunityMigrationRunner.forLocations(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                        historyTable, "filesystem:" + v012Directory.toAbsolutePath())
                .migrate();
        insertLegacyWalletActionLeaseFixture(database);

        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                historyTable, CommunityMigrationRunner.MIGRATION_LOCATION);

        assertThat(runner.migrate().migrationsExecuted).isEqualTo(2);
        runner.validate();

        assertMarketWalletActionLeaseFencingSchema(database);
        assertThat(walletActionLeaseStates(database)).containsExactly(
                new WalletActionLeaseState(
                        "70000000000070008000000000000001",
                        "PENDING",
                        2,
                        null,
                        null,
                        null
                ),
                new WalletActionLeaseState(
                        "70000000000070008000000000000002",
                        "RETRYING",
                        6,
                        null,
                        null,
                        null
                )
        );
        assertThat(queryString(database,
                "select next_retry_at from market_wallet_action "
                        + "where action_id = x'70000000000070008000000000000002'"))
                .isNotBlank()
                .isNotEqualTo("2035-01-01 00:00:05");
    }

    @Test
    void h2FixtureShouldDeclareTheUniqueModerationActionReportIndex() throws Exception {
        String schema = Files.readString(findRepositoryRoot().resolve(
                "backend/community-app/src/test/resources/schema.sql"));

        assertThat(schema).contains(
                "create unique index if not exists uk_moderation_action_report "
                        + "on moderation_action(report_id);");
    }

    @Test
    void h2FixtureShouldDeclareTheSocialLikeLifecycleIdentity() throws Exception {
        String schema = Files.readString(findRepositoryRoot().resolve(
                "backend/community-app/src/test/resources/schema.sql"));

        assertThat(schema).contains(
                "  relation_instance_id binary(16) not null,\n"
                        + "  user_id binary(16) not null,");
        assertThat(schema).contains(
                "create unique index if not exists uk_social_like_relation_instance "
                        + "on social_like(relation_instance_id);");
    }

    @Test
    void v011ShouldGiveEveryExistingLikeADistinctLifecycleIdentity(@TempDir Path tempDir)
            throws Exception {
        Database database = freshDatabase("v011_social_like");
        String historyTable = "community_v011_social_like_history";
        Path v010Directory = prepareMigrationDirectoryThroughVersionTen(tempDir);
        CommunityMigrationRunner.forLocations(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                        historyTable, "filesystem:" + v010Directory.toAbsolutePath())
                .migrate();
        insertSocialLikeFixture(database);
        List<SocialLikeBusinessRow> before = socialLikeBusinessRows(database);

        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                historyTable, CommunityMigrationRunner.MIGRATION_LOCATION);

        assertThat(runner.migrate().migrationsExecuted).isEqualTo(4);
        runner.validate();

        assertThat(socialLikeBusinessRows(database)).containsExactlyElementsOf(before);
        assertThat(queryStrings(database,
                "select hex(relation_instance_id) from social_like "
                        + "order by user_id, entity_type, entity_id"))
                .hasSize(3)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
        assertSocialLikeLifecycleSchema(database);
    }

    @Test
    void v010ShouldReplaceTheReportIndexWithoutTouchingNullActions(@TempDir Path tempDir) throws Exception {
        Database database = freshDatabase("v010_success");
        String historyTable = "community_v010_success_history";
        Path v009Directory = prepareMigrationDirectoryThroughVersionNine(tempDir);
        CommunityMigrationRunner.forLocations(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                        historyTable, "filesystem:" + v009Directory.toAbsolutePath())
                .migrate();
        insertDistinctAndNullModerationActions(database);
        List<ModerationActionRow> before = moderationActionRows(database);

        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                historyTable, CommunityMigrationRunner.MIGRATION_LOCATION);

        assertThat(runner.migrate().migrationsExecuted).isEqualTo(5);
        runner.validate();

        assertThat(moderationActionRows(database)).containsExactlyElementsOf(before);
        assertThat(indexDefinition(database, "moderation_action", "uk_moderation_action_report"))
                .isEqualTo(new IndexDefinition(true, List.of("report_id")));
        assertThat(indexDefinition(database, "moderation_action", "idx_moderation_action_report"))
                .isNull();
        assertThat(indexDefinition(database, "moderation_action", "idx_moderation_action_actor"))
                .isEqualTo(new IndexDefinition(false, List.of("actor_id", "create_time")));

        execute(database, "insert into moderation_action(" +
                "id, report_id, actor_id, action, reason, duration_seconds, create_time" +
                ") values (x'40000000000070008000000000000005', null, " +
                "x'40000000000070008000000000000025', 'NOTE', 'third null', 0, " +
                "'2035-01-01 00:00:05')");
        assertThat(queryLong(database,
                "select count(*) from moderation_action where report_id is null"))
                .isEqualTo(3L);
    }

    @Test
    void v010ShouldFailAtomicallyWhenAReportAlreadyHasMultipleActions(@TempDir Path tempDir)
            throws Exception {
        Database database = freshDatabase("v010_duplicate");
        String historyTable = "community_v010_duplicate_history";
        Path v009Directory = prepareMigrationDirectoryThroughVersionNine(tempDir);
        CommunityMigrationRunner.forLocations(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                        historyTable, "filesystem:" + v009Directory.toAbsolutePath())
                .migrate();
        insertConflictingModerationActions(database);
        List<ModerationActionRow> before = moderationActionRows(database);

        CommunityMigrationRunner runner = CommunityMigrationRunner.forLocations(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword(),
                historyTable, CommunityMigrationRunner.MIGRATION_LOCATION);

        Throwable failure = catchThrowable(runner::migrate);
        assertThat(failure).isInstanceOf(FlywayException.class);
        assertThat(rootCause(failure)).isInstanceOf(SQLException.class);
        SQLException duplicateKeyFailure = (SQLException) rootCause(failure);
        assertThat(duplicateKeyFailure.getSQLState()).isEqualTo("23000");
        assertThat(duplicateKeyFailure.getErrorCode()).isEqualTo(1062);
        assertThat(duplicateKeyFailure.getMessage())
                .contains("Duplicate entry")
                .contains("uk_moderation_action_report");

        List<MigrationHistoryRow> historyAfterFailure = migrationHistoryRows(database, historyTable);
        assertThat(historyAfterFailure).hasSize(10);
        assertThat(historyAfterFailure)
                .filteredOn(row -> "010".equals(row.version()))
                .containsExactly(new MigrationHistoryRow(
                        "010", "enforce unique moderation action report", false));
        assertThat(historyAfterFailure).filteredOn(MigrationHistoryRow::success).hasSize(9);
        assertFailedV010PreservedModerationState(database, before);

        Throwable retryFailure = catchThrowable(runner::migrate);
        assertThat(retryFailure)
                .isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("Detected failed migration to version 010");
        assertThat(migrationHistoryRows(database, historyTable))
                .containsExactlyElementsOf(historyAfterFailure);
        assertFailedV010PreservedModerationState(database, before);
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

        assertThat(runner.migrate().migrationsExecuted).isEqualTo(6);
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
        assertThat(runner.migrate().migrationsExecuted).isEqualTo(13);
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
        assertThat(driveUploadRows(database)).containsExactly(new DriveUploadRow(
                "10000000000070008000000000000005",
                "10000000000070008000000000000015",
                null,
                "migration-upload.bin",
                42L,
                "application/octet-stream",
                "",
                "10000000000070008000000000000025",
                "10000000000070008000000000000035",
                "10000000000070008000000000000045",
                "PREPARED",
                "10000000000070008000000000000001",
                Timestamp.valueOf("2035-04-01 00:00:01"),
                Timestamp.valueOf("2035-04-01 00:00:02"),
                Timestamp.valueOf("2035-04-02 00:00:00")
        ));
        assertSocialLikeFixtureBackfilled(database);
        assertThat(queryLong(database,
                "select count(*) from community_upgrade_idempotency_history where success = 1"))
                .isEqualTo(14L);
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
        insertSocialLikeFixture(database);

        var result = CommunityMigrationRunner.standard(
                        database.url(), MYSQL.getUsername(), MYSQL.getPassword())
                .migrate();

        String mediaReleaseEventId = "content-media-reference:"
                + "10000000-0000-7000-8000-000000000011:1:RELEASE";
        execute(database, "insert into outbox_event(" +
                "id, event_id, topic, event_key, payload, status" +
                ") values (x'10000000000070008000000000000051', '" + mediaReleaseEventId + "', " +
                "'content.media.reference', 'media-reference', '{}', 'NEW')");

        assertThat(result.migrationsExecuted).isEqualTo(13);
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
        assertSocialLikeFixtureBackfilled(database);
        assertThat(queryLong(database,
                "select count(*) from " + CommunityMigrationRunner.HISTORY_TABLE + " where success = 1"))
                .isEqualTo(14L);
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
        return prepareMigrationDirectoryThroughVersion(tempDir, 8);
    }

    private static Path prepareMigrationDirectoryThroughVersionNine(Path tempDir) throws Exception {
        return prepareMigrationDirectoryThroughVersion(tempDir, 9);
    }

    private static Path prepareMigrationDirectoryThroughVersionTen(Path tempDir) throws Exception {
        return prepareMigrationDirectoryThroughVersion(tempDir, 10);
    }

    private static Path prepareMigrationDirectoryThroughVersion(Path tempDir, int latestVersion) throws Exception {
        Path sourceDirectory = findRepositoryRoot().resolve(
                "backend/community-db-migrations/src/main/resources/db/migration/community");
        Path migrationDirectory = Files.createDirectories(
                tempDir.resolve("community-v%03d".formatted(latestVersion)));
        for (int version = 1; version <= latestVersion; version++) {
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
        execute(database, "insert into drive_upload(" +
                "upload_id, space_id, parent_id, name, size_bytes, mime_type, object_id, version_id, " +
                "oss_session_id, status, created_by, created_at, updated_at, expires_at" +
                ") values (" +
                "x'10000000000070008000000000000005', " +
                "x'10000000000070008000000000000015', null, 'migration-upload.bin', 42, " +
                "'application/octet-stream', x'10000000000070008000000000000025', " +
                "x'10000000000070008000000000000035', x'10000000000070008000000000000045', " +
                "'PREPARED', x'10000000000070008000000000000001', " +
                "'2035-04-01 00:00:01', '2035-04-01 00:00:02', '2035-04-02 00:00:00')");
        insertSocialLikeFixture(database);
    }

    private static void insertLegacyWalletActionLeaseFixture(Database database) throws Exception {
        execute(database, "insert into market_wallet_action(" +
                "action_id, order_id, action_type, request_id, wallet_biz_id, actor_user_id, " +
                "amount, status, retry_count, next_retry_at, processing_lease_until, " +
                "create_time, update_time" +
                ") values " +
                "(x'70000000000070008000000000000001', " +
                "x'70000000000070008000000000000011', 'ESCROW', 'lease-pending', " +
                "'lease-pending-biz', x'70000000000070008000000000000021', " +
                "100, 'PENDING', 2, null, null, " +
                "'2035-01-01 00:00:01', '2035-01-01 00:00:02'), " +
                "(x'70000000000070008000000000000002', " +
                "x'70000000000070008000000000000012', 'RELEASE', 'lease-processing', " +
                "'lease-processing-biz', x'70000000000070008000000000000022', " +
                "200, 'PROCESSING', 5, '2035-01-01 00:00:05', '2035-01-01 01:00:00', " +
                "'2035-01-01 00:00:03', '2035-01-01 00:00:04')");
    }

    private static List<DriveUploadRow> driveUploadRows(Database database) throws Exception {
        String sql = "select hex(upload_id), hex(space_id), hex(parent_id), name, size_bytes, mime_type, "
                + "checksum_sha256, hex(object_id), hex(version_id), hex(oss_session_id), status, "
                + "hex(created_by), created_at, updated_at, expires_at from drive_upload order by upload_id";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<DriveUploadRow> uploads = new ArrayList<>();
            while (rows.next()) {
                uploads.add(new DriveUploadRow(
                        rows.getString(1),
                        rows.getString(2),
                        rows.getString(3),
                        rows.getString(4),
                        rows.getLong(5),
                        rows.getString(6),
                        rows.getString(7),
                        rows.getString(8),
                        rows.getString(9),
                        rows.getString(10),
                        rows.getString(11),
                        rows.getString(12),
                        rows.getTimestamp(13),
                        rows.getTimestamp(14),
                        rows.getTimestamp(15)
                ));
            }
            return uploads;
        }
    }

    private static List<WalletActionLeaseState> walletActionLeaseStates(Database database)
            throws Exception {
        String sql = "select hex(action_id), status, retry_count, hex(wallet_txn_id), "
                + "processing_lease_until, hex(lease_token) "
                + "from market_wallet_action order by action_id";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<WalletActionLeaseState> states = new ArrayList<>();
            while (rows.next()) {
                states.add(new WalletActionLeaseState(
                        rows.getString(1),
                        rows.getString(2),
                        rows.getInt(3),
                        rows.getString(4),
                        rows.getTimestamp(5),
                        rows.getString(6)
                ));
            }
            return states;
        }
    }

    private static void insertSocialLikeFixture(Database database) throws Exception {
        execute(database, "insert into social_like(" +
                "user_id, entity_type, entity_id, entity_user_id, created_at" +
                ") values " +
                "(x'60000000000070008000000000000001', 1, " +
                "x'60000000000070008000000000000011', " +
                "x'60000000000070008000000000000021', '2035-03-01 00:00:01'), " +
                "(x'60000000000070008000000000000001', 1, " +
                "x'60000000000070008000000000000012', " +
                "x'60000000000070008000000000000022', '2035-03-01 00:00:02'), " +
                "(x'60000000000070008000000000000002', 1, " +
                "x'60000000000070008000000000000011', " +
                "x'60000000000070008000000000000021', '2035-03-01 00:00:03')");
    }

    private static void assertSocialLikeFixtureBackfilled(Database database) throws Exception {
        assertThat(socialLikeBusinessRows(database)).containsExactly(
                new SocialLikeBusinessRow(
                        "60000000000070008000000000000001", 1,
                        "60000000000070008000000000000011",
                        "60000000000070008000000000000021",
                        Timestamp.valueOf("2035-03-01 00:00:01")
                ),
                new SocialLikeBusinessRow(
                        "60000000000070008000000000000001", 1,
                        "60000000000070008000000000000012",
                        "60000000000070008000000000000022",
                        Timestamp.valueOf("2035-03-01 00:00:02")
                ),
                new SocialLikeBusinessRow(
                        "60000000000070008000000000000002", 1,
                        "60000000000070008000000000000011",
                        "60000000000070008000000000000021",
                        Timestamp.valueOf("2035-03-01 00:00:03")
                )
        );
        assertThat(queryStrings(database,
                "select hex(relation_instance_id) from social_like "
                        + "order by user_id, entity_type, entity_id"))
                .hasSize(3)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
        assertSocialLikeLifecycleSchema(database);
    }

    private static List<SocialLikeBusinessRow> socialLikeBusinessRows(Database database)
            throws Exception {
        String sql = "select hex(user_id), entity_type, hex(entity_id), hex(entity_user_id), "
                + "created_at from social_like order by user_id, entity_type, entity_id";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<SocialLikeBusinessRow> likes = new ArrayList<>();
            while (rows.next()) {
                likes.add(new SocialLikeBusinessRow(
                        rows.getString(1),
                        rows.getInt(2),
                        rows.getString(3),
                        rows.getString(4),
                        rows.getTimestamp(5)
                ));
            }
            return likes;
        }
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

    private static void insertDistinctAndNullModerationActions(Database database) throws Exception {
        execute(database, "insert into moderation_action(" +
                "id, report_id, actor_id, action, reason, duration_seconds, create_time" +
                ") values " +
                "(x'40000000000070008000000000000001', x'40000000000070008000000000000011', " +
                "x'40000000000070008000000000000021', 'WARN', 'first report', 0, " +
                "'2035-01-01 00:00:01'), " +
                "(x'40000000000070008000000000000002', x'40000000000070008000000000000012', " +
                "x'40000000000070008000000000000022', 'MUTE', 'second report', 60, " +
                "'2035-01-01 00:00:02'), " +
                "(x'40000000000070008000000000000003', null, " +
                "x'40000000000070008000000000000023', 'NOTE', 'first null', 0, " +
                "'2035-01-01 00:00:03'), " +
                "(x'40000000000070008000000000000004', null, " +
                "x'40000000000070008000000000000024', 'NOTE', 'second null', 0, " +
                "'2035-01-01 00:00:04')");
    }

    private static void insertConflictingModerationActions(Database database) throws Exception {
        execute(database, "insert into moderation_action(" +
                "id, report_id, actor_id, action, reason, duration_seconds, create_time" +
                ") values " +
                "(x'50000000000070008000000000000001', x'50000000000070008000000000000011', " +
                "x'50000000000070008000000000000021', 'WARN', 'first conflict', 0, " +
                "'2035-02-01 00:00:01'), " +
                "(x'50000000000070008000000000000002', x'50000000000070008000000000000011', " +
                "x'50000000000070008000000000000022', 'MUTE', 'second conflict', 120, " +
                "'2035-02-01 00:00:02')");
    }

    private static List<ModerationActionRow> moderationActionRows(Database database) throws Exception {
        String sql = "select hex(id), hex(report_id), hex(actor_id), action, reason, " +
                "duration_seconds, create_time from moderation_action order by id";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<ModerationActionRow> actions = new ArrayList<>();
            while (rows.next()) {
                actions.add(new ModerationActionRow(
                        rows.getString(1),
                        rows.getString(2),
                        rows.getString(3),
                        rows.getString(4),
                        rows.getString(5),
                        rows.getInt(6),
                        rows.getTimestamp(7)
                ));
            }
            return actions;
        }
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

    private static void assertModerationActionSchema(Database database) throws Exception {
        assertThat(columnNames(database, "moderation_action")).containsExactlyInAnyOrder(
                "id", "report_id", "actor_id", "action", "reason", "duration_seconds", "create_time");
        assertThat(columnDefinition(database, "moderation_action", "id"))
                .isEqualTo(new ColumnDefinition("binary(16)", false));
        assertThat(columnDefinition(database, "moderation_action", "report_id"))
                .isEqualTo(new ColumnDefinition("binary(16)", true));
        assertThat(columnDefinition(database, "moderation_action", "actor_id"))
                .isEqualTo(new ColumnDefinition("binary(16)", false));
        assertThat(columnDefinition(database, "moderation_action", "action"))
                .isEqualTo(new ColumnDefinition("varchar(32)", false));
        assertThat(columnDefinition(database, "moderation_action", "reason"))
                .isEqualTo(new ColumnDefinition("varchar(255)", true));
        assertThat(columnDefinition(database, "moderation_action", "duration_seconds"))
                .isEqualTo(new ColumnDefinition("int", true));
        assertThat(columnDefinition(database, "moderation_action", "create_time"))
                .isEqualTo(new ColumnDefinition("timestamp", true));
        assertThat(indexNames(database, "moderation_action")).containsExactlyInAnyOrder(
                "primary", "uk_moderation_action_report", "idx_moderation_action_actor");
        assertThat(indexDefinition(database, "moderation_action", "PRIMARY"))
                .isEqualTo(new IndexDefinition(true, List.of("id")));
        assertThat(indexDefinition(database, "moderation_action", "uk_moderation_action_report"))
                .isEqualTo(new IndexDefinition(true, List.of("report_id")));
        assertThat(indexDefinition(database, "moderation_action", "idx_moderation_action_actor"))
                .isEqualTo(new IndexDefinition(false, List.of("actor_id", "create_time")));
        assertThat(indexDefinition(database, "moderation_action", "idx_moderation_action_report"))
                .isNull();
    }

    private static void assertOutboxLeaseFencingSchema(Database database) throws Exception {
        assertThat(columnDefinition(database, "outbox_event", "lease_token"))
                .isEqualTo(new ColumnDefinition("binary(16)", true));
        assertThat(columnDefinition(database, "outbox_event", "processing_lease_until"))
                .isEqualTo(new ColumnDefinition("timestamp", true));
        assertThat(indexColumns(database, "outbox_event", "idx_outbox_processing_lease"))
                .containsExactly("status", "processing_lease_until", "id");
    }

    private static void assertMarketWalletActionLeaseFencingSchema(Database database) throws Exception {
        assertThat(columnDefinition(database, "market_wallet_action", "lease_token"))
                .isEqualTo(new ColumnDefinition("binary(16)", true));
        assertThat(indexColumns(
                database,
                "market_wallet_action",
                "idx_market_wallet_action_processing_lease"
        )).containsExactly("status", "processing_lease_until", "action_id");
    }

    private static void assertSocialLikeLifecycleSchema(Database database) throws Exception {
        assertThat(columnMetadata(database, "social_like")).isEqualTo(Map.of(
                "user_id", new ColumnMetadata("binary(16)", false, null),
                "entity_type", new ColumnMetadata("int", false, null),
                "entity_id", new ColumnMetadata("binary(16)", false, null),
                "entity_user_id", new ColumnMetadata("binary(16)", true, null),
                "created_at", new ColumnMetadata("timestamp", false, "current_timestamp"),
                "relation_instance_id", new ColumnMetadata("binary(16)", false, null)
        ));
        assertThat(indexNames(database, "social_like")).containsExactlyInAnyOrder(
                "primary", "idx_like_entity", "idx_like_entity_user",
                "uk_social_like_relation_instance");
        assertThat(indexDefinition(database, "social_like", "PRIMARY"))
                .isEqualTo(new IndexDefinition(
                        true, List.of("user_id", "entity_type", "entity_id")));
        assertThat(indexDefinition(database, "social_like", "idx_like_entity"))
                .isEqualTo(new IndexDefinition(false, List.of("entity_type", "entity_id")));
        assertThat(indexDefinition(database, "social_like", "idx_like_entity_user"))
                .isEqualTo(new IndexDefinition(
                        false, List.of("entity_type", "entity_id", "user_id")));
        assertThat(indexDefinition(database, "social_like", "uk_social_like_relation_instance"))
                .isEqualTo(new IndexDefinition(true, List.of("relation_instance_id")));
    }

    private static Map<String, ColumnMetadata> columnMetadata(
            Database database,
            String table
    ) throws Exception {
        String sql = "select column_name, column_type, is_nullable, column_default "
                + "from information_schema.columns "
                + "where table_schema = ? and table_name = ? order by ordinal_position";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database.name());
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                Map<String, ColumnMetadata> columns = new LinkedHashMap<>();
                while (rows.next()) {
                    String defaultValue = rows.getString("column_default");
                    columns.put(
                            rows.getString("column_name").toLowerCase(),
                            new ColumnMetadata(
                                    rows.getString("column_type").toLowerCase(),
                                    "YES".equals(rows.getString("is_nullable")),
                                    defaultValue == null ? null : defaultValue.toLowerCase()
                            )
                    );
                }
                return Map.copyOf(columns);
            }
        }
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
        IndexDefinition definition = indexDefinition(database, table, index);
        return definition == null ? List.of() : definition.columns();
    }

    private static Set<String> indexNames(Database database, String table) throws Exception {
        String sql = "select distinct index_name from information_schema.statistics "
                + "where table_schema = ? and table_name = ? order by index_name";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database.name());
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                Set<String> names = new java.util.LinkedHashSet<>();
                while (rows.next()) {
                    names.add(rows.getString("index_name").toLowerCase());
                }
                return names;
            }
        }
    }

    private static IndexDefinition indexDefinition(
            Database database,
            String table,
            String index
    ) throws Exception {
        String sql = "select non_unique, column_name from information_schema.statistics "
                + "where table_schema = ? and table_name = ? and index_name = ? order by seq_in_index";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database.name());
            statement.setString(2, table);
            statement.setString(3, index);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> columns = new ArrayList<>();
                boolean unique = false;
                while (rows.next()) {
                    if (columns.isEmpty()) {
                        unique = rows.getInt("non_unique") == 0;
                    }
                    columns.add(rows.getString("column_name").toLowerCase());
                }
                return columns.isEmpty() ? null : new IndexDefinition(unique, List.copyOf(columns));
            }
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private static List<MigrationHistoryRow> migrationHistoryRows(
            Database database,
            String historyTable
    ) throws Exception {
        String sql = "select version, description, success from " + historyTable + " order by installed_rank";
        try (Connection connection = DriverManager.getConnection(
                database.url(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            List<MigrationHistoryRow> history = new ArrayList<>();
            while (rows.next()) {
                history.add(new MigrationHistoryRow(
                        rows.getString("version"),
                        rows.getString("description"),
                        rows.getBoolean("success")
                ));
            }
            return history;
        }
    }

    private static void assertFailedV010PreservedModerationState(
            Database database,
            List<ModerationActionRow> expectedRows
    ) throws Exception {
        assertThat(moderationActionRows(database)).containsExactlyElementsOf(expectedRows);
        assertThat(indexDefinition(database, "moderation_action", "idx_moderation_action_report"))
                .isEqualTo(new IndexDefinition(false, List.of("report_id", "create_time")));
        assertThat(indexDefinition(database, "moderation_action", "uk_moderation_action_report"))
                .isNull();
        assertThat(indexDefinition(database, "moderation_action", "idx_moderation_action_actor"))
                .isEqualTo(new IndexDefinition(false, List.of("actor_id", "create_time")));
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

    private record ColumnMetadata(String type, boolean nullable, String defaultValue) {
    }

    private record IndexDefinition(boolean unique, List<String> columns) {
    }

    private record MigrationHistoryRow(String version, String description, boolean success) {
    }

    private record ModerationActionRow(
            String rowId,
            String reportId,
            String actorId,
            String action,
            String reason,
            int durationSeconds,
            Timestamp createTime
    ) {
    }

    private record SocialLikeBusinessRow(
            String userId,
            int entityType,
            String entityId,
            String entityUserId,
            Timestamp createdAt
    ) {
    }

    private record DriveUploadRow(
            String uploadId,
            String spaceId,
            String parentId,
            String name,
            long sizeBytes,
            String mimeType,
            String checksumSha256,
            String objectId,
            String versionId,
            String ossSessionId,
            String status,
            String createdBy,
            Timestamp createdAt,
            Timestamp updatedAt,
            Timestamp expiresAt
    ) {
    }

    private record WalletActionLeaseState(
            String actionId,
            String status,
            int retryCount,
            String walletTxnId,
            Timestamp processingLeaseUntil,
            String leaseToken
    ) {
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

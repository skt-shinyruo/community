-- The synchronous Mock Data Studio CLI no longer uses the legacy async-job
-- or AI configuration tables. Their historical rows are intentionally removed.

SET @community_migration_lock = GET_LOCK('community:forward-schema-migration', 60);
SET @community_migration_sql = IF(
  @community_migration_lock = 1,
  'DO 0',
  'SELECT * FROM `__community_migration_lock_timeout__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  CAST(@community_migration_version AS BINARY) = CAST('023' AS BINARY)
    AND CAST(@community_migration_script AS BINARY)
      = CAST('V023__drop_legacy_mock_data_tables.sql' AS BINARY),
  'DO 0',
  'SELECT * FROM `__community_migration_identity_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_existing_checksum = (
  SELECT `checksum_sha256`
  FROM `community_forward_schema_history`
  WHERE CAST(`version` AS BINARY) = CAST(@community_migration_version AS BINARY)
);
SET @community_migration_sql = IF(
  @community_existing_checksum IS NULL
    OR CAST(@community_existing_checksum AS BINARY) = CAST(@community_migration_checksum AS BINARY),
  'DO 0',
  'SELECT * FROM `__community_migration_checksum_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;
SET @community_migration_needed = IF(@community_existing_checksum IS NULL, 1, 0);

SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR (
    EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'demo_batch'
    )
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'demo_entity_ref'
    )
  ),
  'DO 0',
  'SELECT * FROM `__community_migration_base_schema_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'DROP TABLE IF EXISTS `demo_job`, `ai_config`',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_legacy_mock_table_count = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name IN ('demo_job', 'ai_config')
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR @community_legacy_mock_table_count = 0,
  'DO 0',
  'SELECT * FROM `__community_migration_structure_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

INSERT INTO `community_forward_schema_history` (
  `version`, `description`, `script`, `checksum_sha256`, `installed_by`
)
SELECT
  @community_migration_version,
  @community_migration_description,
  @community_migration_script,
  @community_migration_checksum,
  CURRENT_USER()
WHERE @community_migration_needed = 1;

DO RELEASE_LOCK('community:forward-schema-migration');

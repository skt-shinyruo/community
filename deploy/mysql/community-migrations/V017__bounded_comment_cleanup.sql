-- Add the covering index used by resumable, bounded root-comment cleanup.
-- Every step is guarded because MySQL commits DDL implicitly; an interrupted
-- run can safely resume and records history only after structural validation.

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
  CAST(@community_migration_version AS BINARY) = CAST('017' AS BINARY)
    AND CAST(@community_migration_script AS BINARY) = CAST('V017__bounded_comment_cleanup.sql' AS BINARY),
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
  @community_migration_needed = 0 OR EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'comment'
  ),
  'DO 0',
  'SELECT * FROM `__community_migration_base_schema_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'comment'
      AND index_name = 'idx_comment_root_cleanup'
  ),
  'ALTER TABLE `comment` ADD INDEX `idx_comment_root_cleanup` (`root_comment_id`,`status`,`create_time`,`id`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_comment_cleanup_index = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'comment'
    AND index_name = 'idx_comment_root_cleanup'
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0
    OR CAST(@community_comment_cleanup_index AS BINARY)
      = CAST('root_comment_id,status,create_time,id' AS BINARY),
  'DO 0',
  'SELECT * FROM `__community_migration_index_mismatch__`'
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

-- Add a durable per-order deadline for wallet recovery scans. This prevents
-- repeatedly failing or missing actions from monopolizing the oldest batch.

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
  CAST(@community_migration_version AS BINARY) = CAST('019' AS BINARY)
    AND CAST(@community_migration_script AS BINARY) = CAST('V019__market_wallet_recovery_schedule.sql' AS BINARY),
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
    WHERE table_schema = DATABASE() AND table_name = 'market_order'
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
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'market_order'
      AND column_name = 'wallet_recovery_next_attempt_at'
  ),
  'ALTER TABLE `market_order` ADD COLUMN `wallet_recovery_next_attempt_at` timestamp NULL DEFAULT NULL AFTER `auto_confirm_next_attempt_at`',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'market_order'
      AND index_name = 'idx_market_order_wallet_recovery'
  ),
  'ALTER TABLE `market_order` ADD INDEX `idx_market_order_wallet_recovery` (`status`,`wallet_recovery_next_attempt_at`,`order_id`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_wallet_recovery_column_count = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'market_order'
    AND column_name = 'wallet_recovery_next_attempt_at'
    AND data_type = 'timestamp'
    AND is_nullable = 'YES'
);
SET @community_wallet_recovery_index = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'market_order'
    AND index_name = 'idx_market_order_wallet_recovery'
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0
    OR (@community_wallet_recovery_column_count = 1
      AND CAST(@community_wallet_recovery_index AS BINARY)
        = CAST('status,wallet_recovery_next_attempt_at,order_id' AS BINARY)),
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

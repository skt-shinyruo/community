-- Make paged IM policy snapshots replayable at one durable owner version.

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
  CAST(@community_migration_version AS BINARY) = CAST('020' AS BINARY)
    AND CAST(@community_migration_script AS BINARY)
      = CAST('V020__im_policy_snapshot_version_history.sql' AS BINARY),
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
      WHERE table_schema = DATABASE() AND table_name = 'user'
    )
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'user_policy_version_counter'
    )
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'social_block'
    )
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'social_block_version_counter'
    )
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'social_block_version_log'
    )
  ),
  'DO 0',
  'SELECT * FROM `__community_migration_base_schema_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'user_policy_version_log'
  ),
  'CREATE TABLE `user_policy_version_log` (
     `version` bigint NOT NULL,
     `user_id` binary(16) NOT NULL,
     `user_exists` tinyint(1) NOT NULL,
     `mute_until` timestamp NULL DEFAULT NULL,
     `ban_until` timestamp NULL DEFAULT NULL,
     `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
     PRIMARY KEY (`version`),
     KEY `idx_user_policy_version_user` (`user_id`,`version`)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user_policy_version_log'
      AND index_name = 'idx_user_policy_version_user'
  ),
  'ALTER TABLE `user_policy_version_log`
     ADD INDEX `idx_user_policy_version_user` (`user_id`,`version`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'social_block_version_log'
      AND index_name = 'idx_social_block_version_pair'
  ),
  'ALTER TABLE `social_block_version_log`
     ADD INDEX `idx_social_block_version_pair` (`user_id`,`target_user_id`,`version`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Legacy rows with version 0 predate the owner clocks. Allocate deterministic,
-- globally unique versions before recording their initial history state.
SET @community_user_policy_base = GREATEST(
  COALESCE((SELECT MAX(`current_version`) FROM `user_policy_version_counter`), 0),
  COALESCE((SELECT MAX(`policy_version`) FROM `user`), 0),
  COALESCE((SELECT MAX(`version`) FROM `user_policy_version_log`), 0)
);
SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'UPDATE `user` users
     INNER JOIN (
       SELECT `id`,
              @community_user_policy_base + ROW_NUMBER() OVER (ORDER BY `id`) AS `version`
       FROM `user`
       WHERE `policy_version` <= 0
     ) backfill ON backfill.`id` = users.`id`
     SET users.`policy_version` = backfill.`version`',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'INSERT INTO `user_policy_version_log`(
     `version`,`user_id`,`user_exists`,`mute_until`,`ban_until`,`occurred_at`
   )
   SELECT users.`policy_version`, users.`id`, 1, users.`mute_until`, users.`ban_until`,
          COALESCE(users.`create_time`, CURRENT_TIMESTAMP)
   FROM `user` users
   LEFT JOIN `user_policy_version_log` history
     ON history.`version` = users.`policy_version`
   WHERE history.`version` IS NULL',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'UPDATE `user_policy_version_counter`
   SET `current_version` = GREATEST(
     `current_version`,
     COALESCE((SELECT MAX(`policy_version`) FROM `user`), 0),
     COALESCE((SELECT MAX(`version`) FROM `user_policy_version_log`), 0)
   )
   WHERE `id` = 1',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_block_policy_base = GREATEST(
  COALESCE((SELECT MAX(`current_version`) FROM `social_block_version_counter`), 0),
  COALESCE((SELECT MAX(`version`) FROM `social_block`), 0),
  COALESCE((SELECT MAX(`version`) FROM `social_block_version_log`), 0)
);
SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'UPDATE `social_block` blocks
     INNER JOIN (
       SELECT `user_id`, `target_user_id`,
              @community_block_policy_base
                + ROW_NUMBER() OVER (ORDER BY `user_id`,`target_user_id`) AS `version`
       FROM `social_block`
       WHERE `version` <= 0
     ) backfill
       ON backfill.`user_id` = blocks.`user_id`
      AND backfill.`target_user_id` = blocks.`target_user_id`
     SET blocks.`version` = backfill.`version`',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'INSERT INTO `social_block_version_log`(
     `version`,`user_id`,`target_user_id`,`active`,`occurred_at`
   )
   SELECT blocks.`version`, blocks.`user_id`, blocks.`target_user_id`, 1,
          COALESCE(blocks.`created_at`, CURRENT_TIMESTAMP)
   FROM `social_block` blocks
   LEFT JOIN `social_block_version_log` history ON history.`version` = blocks.`version`
   WHERE history.`version` IS NULL',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'UPDATE `social_block_version_counter`
   SET `current_version` = GREATEST(
     `current_version`,
     COALESCE((SELECT MAX(`version`) FROM `social_block`), 0),
     COALESCE((SELECT MAX(`version`) FROM `social_block_version_log`), 0)
   )
   WHERE `id` = 1',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_user_policy_log_columns = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_policy_version_log'
    AND column_name IN ('version','user_id','user_exists','mute_until','ban_until','occurred_at')
);
SET @community_user_policy_log_index = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'user_policy_version_log'
    AND index_name = 'idx_user_policy_version_user'
);
SET @community_block_policy_log_index = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'social_block_version_log'
    AND index_name = 'idx_social_block_version_pair'
);
SET @community_user_policy_missing_history = (
  SELECT COUNT(*)
  FROM `user` users
  LEFT JOIN `user_policy_version_log` history
    ON history.`version` = users.`policy_version`
   AND history.`user_id` = users.`id`
   AND history.`user_exists` = 1
   AND history.`mute_until` <=> users.`mute_until`
   AND history.`ban_until` <=> users.`ban_until`
  WHERE users.`policy_version` <= 0 OR history.`version` IS NULL
);
SET @community_block_policy_missing_history = (
  SELECT COUNT(*)
  FROM `social_block` blocks
  LEFT JOIN `social_block_version_log` history
    ON history.`version` = blocks.`version`
   AND history.`user_id` = blocks.`user_id`
   AND history.`target_user_id` = blocks.`target_user_id`
   AND history.`active` = 1
  WHERE blocks.`version` <= 0 OR history.`version` IS NULL
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR (
    @community_user_policy_log_columns = 6
    AND CAST(@community_user_policy_log_index AS BINARY)
      = CAST('user_id,version' AS BINARY)
    AND CAST(@community_block_policy_log_index AS BINARY)
      = CAST('user_id,target_user_id,version' AS BINARY)
    AND @community_user_policy_missing_history = 0
    AND @community_block_policy_missing_history = 0
  ),
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

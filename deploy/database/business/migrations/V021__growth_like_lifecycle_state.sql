-- Fence like-task progress by relation instance and owner lifecycle version so
-- delayed create/remove deliveries cannot overwrite newer lifecycle state.

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
  CAST(@community_migration_version AS BINARY) = CAST('021' AS BINARY)
    AND CAST(@community_migration_script AS BINARY)
      = CAST('V021__growth_like_lifecycle_state.sql' AS BINARY),
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
      WHERE table_schema = DATABASE() AND table_name = 'user_task_event_log'
    )
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'task_template'
    )
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'social_like'
    )
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'social_like_relation_version'
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
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'growth_like_task_lifecycle_state'
  ),
  'CREATE TABLE `growth_like_task_lifecycle_state` (
     `recipient_user_id` binary(16) NOT NULL,
     `relation_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
     `relation_instance_id` binary(16) DEFAULT NULL,
     `source_version` bigint NOT NULL DEFAULT 0,
     `active` tinyint(1) DEFAULT NULL,
     `source_event_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
     `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     PRIMARY KEY (`recipient_user_id`,`relation_key`),
     KEY `idx_growth_like_task_lifecycle_instance` (`relation_instance_id`)
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
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
      AND table_name = 'growth_like_task_lifecycle_state'
      AND index_name = 'idx_growth_like_task_lifecycle_instance'
  ),
  'ALTER TABLE `growth_like_task_lifecycle_state`
     ADD INDEX `idx_growth_like_task_lifecycle_instance` (`relation_instance_id`)',
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
      AND table_name = 'user_task_event_log'
      AND index_name = 'idx_user_task_event_source'
  ),
  'ALTER TABLE `user_task_event_log`
     ADD INDEX `idx_user_task_event_source` (`user_id`,`source_event_id`,`task_code`,`period_key`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Seed only active relations that already contributed under the legacy stable
-- relation key. Rows without a contribution may still have a queued legacy
-- create, which must remain eligible for normal consumer processing.
SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'INSERT IGNORE INTO `growth_like_task_lifecycle_state`(
     `recipient_user_id`,`relation_key`,`relation_instance_id`,`source_version`,
     `active`,`source_event_id`,`update_time`
   )
   SELECT likes.`entity_user_id`,
          CONCAT(
            ''like:'', LOWER(BIN_TO_UUID(likes.`user_id`)), '':'', likes.`entity_type`,
            '':'', LOWER(BIN_TO_UUID(likes.`entity_id`))
          ),
          likes.`relation_instance_id`,
          GREATEST(COALESCE(versions.`current_version`, 4611686018427387904), 4611686018427387904),
          1,
          CONCAT(''migration:V021:active-like:'', LOWER(HEX(likes.`relation_instance_id`))),
          CURRENT_TIMESTAMP
   FROM `social_like` likes
   LEFT JOIN `social_like_relation_version` versions
     ON versions.`actor_user_id` = likes.`user_id`
    AND versions.`entity_type` = likes.`entity_type`
    AND versions.`entity_id` = likes.`entity_id`
   WHERE likes.`entity_user_id` IS NOT NULL
     AND likes.`user_id` <> likes.`entity_user_id`
     AND EXISTS (
       SELECT 1
       FROM `user_task_event_log` logs
       INNER JOIN `task_template` templates ON templates.`task_code` = logs.`task_code`
       WHERE logs.`user_id` = likes.`entity_user_id`
         AND logs.`source_event_id` = CONCAT(
           ''like:'', LOWER(BIN_TO_UUID(likes.`user_id`)), '':'', likes.`entity_type`,
           '':'', LOWER(BIN_TO_UUID(likes.`entity_id`))
         )
         AND templates.`trigger_event_type` = ''LikeCreated''
     )',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_growth_lifecycle_table_contract = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'growth_like_task_lifecycle_state'
    AND engine = 'InnoDB'
    AND table_collation = 'utf8mb4_unicode_ci'
);
SET @community_growth_lifecycle_column_count = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'growth_like_task_lifecycle_state'
);
SET @community_growth_lifecycle_column_contracts = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'growth_like_task_lifecycle_state'
    AND (
      (`column_name` = 'recipient_user_id'
        AND `column_type` = 'binary(16)'
        AND `is_nullable` = 'NO'
        AND `column_default` IS NULL)
      OR (`column_name` = 'relation_key'
        AND `column_type` = 'varchar(255)'
        AND `is_nullable` = 'NO'
        AND `collation_name` = 'utf8mb4_unicode_ci'
        AND `column_default` IS NULL)
      OR (`column_name` = 'relation_instance_id'
        AND `column_type` = 'binary(16)'
        AND `is_nullable` = 'YES'
        AND `column_default` IS NULL)
      OR (`column_name` = 'source_version'
        AND `column_type` = 'bigint'
        AND `is_nullable` = 'NO'
        AND CAST(`column_default` AS CHAR) = '0')
      OR (`column_name` = 'active'
        AND `column_type` = 'tinyint(1)'
        AND `is_nullable` = 'YES'
        AND `column_default` IS NULL)
      OR (`column_name` = 'source_event_id'
        AND `column_type` = 'varchar(128)'
        AND `is_nullable` = 'YES'
        AND `collation_name` = 'utf8mb4_unicode_ci'
        AND `column_default` IS NULL)
      OR (`column_name` = 'update_time'
        AND `data_type` = 'timestamp'
        AND `is_nullable` = 'YES'
        AND UPPER(CAST(`column_default` AS CHAR)) = 'CURRENT_TIMESTAMP'
        AND UPPER(`extra`) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
    )
);
SET @community_growth_lifecycle_primary_key = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'growth_like_task_lifecycle_state'
    AND index_name = 'PRIMARY'
);
SET @community_growth_lifecycle_instance_index = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'growth_like_task_lifecycle_state'
    AND index_name = 'idx_growth_like_task_lifecycle_instance'
    AND non_unique = 1
    AND sub_part IS NULL
    AND is_visible = 'YES'
    AND index_type = 'BTREE'
);
SET @community_user_task_event_source_index = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'user_task_event_log'
    AND index_name = 'idx_user_task_event_source'
    AND non_unique = 1
    AND sub_part IS NULL
    AND is_visible = 'YES'
    AND index_type = 'BTREE'
);
SET @community_growth_lifecycle_missing_active_backfill = (
  SELECT COUNT(*)
  FROM `social_like` likes
  LEFT JOIN `social_like_relation_version` versions
    ON versions.`actor_user_id` = likes.`user_id`
   AND versions.`entity_type` = likes.`entity_type`
   AND versions.`entity_id` = likes.`entity_id`
  LEFT JOIN `growth_like_task_lifecycle_state` lifecycle
    ON lifecycle.`recipient_user_id` = likes.`entity_user_id`
   AND lifecycle.`relation_key` = CONCAT(
     'like:', LOWER(BIN_TO_UUID(likes.`user_id`)), ':', likes.`entity_type`,
     ':', LOWER(BIN_TO_UUID(likes.`entity_id`))
   )
  WHERE likes.`entity_user_id` IS NOT NULL
    AND likes.`user_id` <> likes.`entity_user_id`
    AND EXISTS (
      SELECT 1
      FROM `user_task_event_log` logs
      INNER JOIN `task_template` templates ON templates.`task_code` = logs.`task_code`
      WHERE logs.`user_id` = likes.`entity_user_id`
        AND logs.`source_event_id` = CONCAT(
          'like:', LOWER(BIN_TO_UUID(likes.`user_id`)), ':', likes.`entity_type`,
          ':', LOWER(BIN_TO_UUID(likes.`entity_id`))
        )
        AND templates.`trigger_event_type` = 'LikeCreated'
    )
    AND (
      lifecycle.`recipient_user_id` IS NULL
      OR NOT (lifecycle.`relation_instance_id` <=> likes.`relation_instance_id`)
      OR lifecycle.`source_version`
        < GREATEST(COALESCE(versions.`current_version`, 4611686018427387904), 4611686018427387904)
      OR NOT (lifecycle.`active` <=> 1)
    )
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR (
    @community_growth_lifecycle_table_contract = 1
    AND @community_growth_lifecycle_column_count = 7
    AND @community_growth_lifecycle_column_contracts = 7
    AND CAST(@community_growth_lifecycle_primary_key AS BINARY)
      = CAST('recipient_user_id,relation_key' AS BINARY)
    AND CAST(@community_growth_lifecycle_instance_index AS BINARY)
      = CAST('relation_instance_id' AS BINARY)
    AND CAST(@community_user_task_event_source_index AS BINARY)
      = CAST('user_id,source_event_id,task_code,period_key' AS BINARY)
    AND @community_growth_lifecycle_missing_active_backfill = 0
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

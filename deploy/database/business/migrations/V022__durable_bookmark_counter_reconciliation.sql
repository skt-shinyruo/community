-- Persist bookmark-counter repair work in the bookmark transaction so a
-- failed after-commit Redis dirty mark cannot permanently lose reconciliation.

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
  CAST(@community_migration_version AS BINARY) = CAST('022' AS BINARY)
    AND CAST(@community_migration_script AS BINARY)
      = CAST('V022__durable_bookmark_counter_reconciliation.sql' AS BINARY),
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
      WHERE table_schema = DATABASE() AND table_name = 'post_bookmark'
    )
    AND EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'post_counter_snapshot'
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
    WHERE table_schema = DATABASE()
      AND table_name = 'post_bookmark_counter_reconciliation'
  ),
  'CREATE TABLE `post_bookmark_counter_reconciliation` (
     `post_id` binary(16) NOT NULL,
     `revision` bigint unsigned NOT NULL DEFAULT 1,
     `pending` tinyint(1) NOT NULL DEFAULT 1,
     `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     PRIMARY KEY (`post_id`),
     KEY `idx_post_bookmark_counter_reconcile_scan` (`pending`,`updated_at`,`post_id`)
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
      AND table_name = 'post_bookmark_counter_reconciliation'
      AND index_name = 'idx_post_bookmark_counter_reconcile_scan'
  ),
  'ALTER TABLE `post_bookmark_counter_reconciliation`
     ADD INDEX `idx_post_bookmark_counter_reconcile_scan` (`pending`,`updated_at`,`post_id`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Queue any discrepancy that may predate this durable marker. Matching rows do
-- not need work, while missing snapshots with bookmarks must be rebuilt.
SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'INSERT INTO `post_bookmark_counter_reconciliation`(`post_id`,`revision`,`pending`,`updated_at`)
   SELECT candidates.`post_id`, 1, 1, CURRENT_TIMESTAMP
   FROM (
     SELECT `post_id` FROM `post_bookmark`
     UNION
     SELECT `post_id` FROM `post_counter_snapshot`
   ) candidates
   LEFT JOIN `post_counter_snapshot` snapshots
     ON snapshots.`post_id` = candidates.`post_id`
   LEFT JOIN (
     SELECT `post_id`, COUNT(*) AS `bookmark_count`
     FROM `post_bookmark`
     GROUP BY `post_id`
   ) facts ON facts.`post_id` = candidates.`post_id`
   WHERE snapshots.`post_id` IS NULL
      OR snapshots.`bookmark_count` <> COALESCE(facts.`bookmark_count`, 0)
   ON DUPLICATE KEY UPDATE
     `revision` = `revision` + 1,
     `pending` = 1,
     `updated_at` = CURRENT_TIMESTAMP',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_bookmark_reconciliation_table_contract = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'post_bookmark_counter_reconciliation'
    AND engine = 'InnoDB'
    AND table_collation = 'utf8mb4_unicode_ci'
);
SET @community_bookmark_reconciliation_column_count = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'post_bookmark_counter_reconciliation'
);
SET @community_bookmark_reconciliation_column_contracts = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'post_bookmark_counter_reconciliation'
    AND (
      (`column_name` = 'post_id'
        AND `column_type` = 'binary(16)'
        AND `is_nullable` = 'NO'
        AND `column_default` IS NULL)
      OR (`column_name` = 'revision'
        AND `column_type` = 'bigint unsigned'
        AND `is_nullable` = 'NO'
        AND CAST(`column_default` AS CHAR) = '1')
      OR (`column_name` = 'pending'
        AND `column_type` = 'tinyint(1)'
        AND `is_nullable` = 'NO'
        AND CAST(`column_default` AS CHAR) = '1')
      OR (`column_name` = 'updated_at'
        AND `data_type` = 'timestamp'
        AND `is_nullable` = 'NO'
        AND UPPER(CAST(`column_default` AS CHAR)) = 'CURRENT_TIMESTAMP'
        AND UPPER(`extra`) LIKE '%ON UPDATE CURRENT_TIMESTAMP%')
    )
);
SET @community_bookmark_reconciliation_primary_key = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'post_bookmark_counter_reconciliation'
    AND index_name = 'PRIMARY'
);
SET @community_bookmark_reconciliation_scan_index = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'post_bookmark_counter_reconciliation'
    AND index_name = 'idx_post_bookmark_counter_reconcile_scan'
    AND non_unique = 1
    AND sub_part IS NULL
    AND is_visible = 'YES'
    AND index_type = 'BTREE'
);
SET @community_bookmark_reconciliation_unqueued_mismatch = (
  SELECT COUNT(*)
  FROM (
    SELECT `post_id` FROM `post_bookmark`
    UNION
    SELECT `post_id` FROM `post_counter_snapshot`
  ) candidates
  LEFT JOIN `post_counter_snapshot` snapshots
    ON snapshots.`post_id` = candidates.`post_id`
  LEFT JOIN (
    SELECT `post_id`, COUNT(*) AS `bookmark_count`
    FROM `post_bookmark`
    GROUP BY `post_id`
  ) facts ON facts.`post_id` = candidates.`post_id`
  LEFT JOIN `post_bookmark_counter_reconciliation` reconciliation
    ON reconciliation.`post_id` = candidates.`post_id`
   AND reconciliation.`pending` = 1
  WHERE (
      snapshots.`post_id` IS NULL
      OR snapshots.`bookmark_count` <> COALESCE(facts.`bookmark_count`, 0)
    )
    AND reconciliation.`post_id` IS NULL
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR (
    @community_bookmark_reconciliation_table_contract = 1
    AND @community_bookmark_reconciliation_column_count = 4
    AND @community_bookmark_reconciliation_column_contracts = 4
    AND CAST(@community_bookmark_reconciliation_primary_key AS BINARY)
      = CAST('post_id' AS BINARY)
    AND CAST(@community_bookmark_reconciliation_scan_index AS BINARY)
      = CAST('pending,updated_at,post_id' AS BINARY)
    AND @community_bookmark_reconciliation_unqueued_mismatch = 0
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

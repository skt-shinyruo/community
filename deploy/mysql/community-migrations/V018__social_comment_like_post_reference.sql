-- Persist the owning post for POST and COMMENT likes. The denormalized owner
-- reference lets post deletion fence and clean both relation kinds without an
-- unbounded join. USER likes intentionally keep post_id nullable.
--
-- Every step is guarded because MySQL commits DDL implicitly. Backfills are
-- idempotent, and history is recorded only after data and index validation, so
-- an interrupted run can safely resume.

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
  CAST(@community_migration_version AS BINARY) = CAST('018' AS BINARY)
    AND CAST(@community_migration_script AS BINARY) = CAST('V018__social_comment_like_post_reference.sql' AS BINARY),
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

SET @community_required_base_tables = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name IN ('comment', 'social_like')
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR @community_required_base_tables = 2,
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
      AND table_name = 'social_like'
      AND column_name = 'post_id'
  ),
  'ALTER TABLE `social_like` ADD COLUMN `post_id` binary(16) DEFAULT NULL AFTER `entity_id`',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Entity type 1 is POST. Re-applying the canonical value also repairs a
-- partially completed or manually corrupted pre-history backfill.
SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'UPDATE `social_like` SET `post_id` = `entity_id` WHERE `entity_type` = 1 AND (`post_id` IS NULL OR `post_id` <> `entity_id`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Entity type 2 is COMMENT. Comment rows are soft-deleted, so the owner post
-- remains available for both active and tombstoned legacy comments.
SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'UPDATE `social_like` likes INNER JOIN `comment` comments ON comments.`id` = likes.`entity_id` SET likes.`post_id` = comments.`post_id` WHERE likes.`entity_type` = 2 AND (likes.`post_id` IS NULL OR likes.`post_id` <> comments.`post_id`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_invalid_content_like_count = (
  SELECT COUNT(*)
  FROM `social_like` likes
  LEFT JOIN `comment` comments
    ON likes.`entity_type` = 2 AND comments.`id` = likes.`entity_id`
  WHERE (likes.`entity_type` = 1
      AND (likes.`post_id` IS NULL OR likes.`post_id` <> likes.`entity_id`))
     OR (likes.`entity_type` = 2
      AND (likes.`post_id` IS NULL
        OR comments.`id` IS NULL
        OR likes.`post_id` <> comments.`post_id`))
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR @community_invalid_content_like_count = 0,
  'DO 0',
  'SELECT * FROM `__community_migration_social_like_post_backfill_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'social_like'
      AND index_name = 'idx_like_post_entity_user'
  ),
  'ALTER TABLE `social_like` ADD INDEX `idx_like_post_entity_user` (`entity_type`,`post_id`,`entity_id`,`user_id`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_like_post_column_count = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'social_like'
    AND column_name = 'post_id'
    AND column_type = 'binary(16)'
    AND is_nullable = 'YES'
);
SET @community_like_post_index = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'social_like'
    AND index_name = 'idx_like_post_entity_user'
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0
    OR (@community_like_post_column_count = 1
      AND CAST(@community_like_post_index AS BINARY)
        = CAST('entity_type,post_id,entity_id,user_id' AS BINARY)),
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

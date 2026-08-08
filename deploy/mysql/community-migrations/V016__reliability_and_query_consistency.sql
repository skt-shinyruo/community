-- Forward-only transition from the last current-state-only release.
-- Every DDL operation is guarded because MySQL commits DDL implicitly. If the
-- client or server stops midway, no success row is written and the same file
-- can safely resume after the already-applied operations.

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
  CAST(@community_migration_version AS BINARY) = CAST('016' AS BINARY)
    AND CAST(@community_migration_script AS BINARY) = CAST('V016__reliability_and_query_consistency.sql' AS BINARY),
  'DO 0',
  'SELECT * FROM `__community_migration_identity_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

CREATE TABLE IF NOT EXISTS `community_forward_schema_history` (
  `version` varchar(32) NOT NULL,
  `description` varchar(255) NOT NULL,
  `script` varchar(255) NOT NULL,
  `checksum_sha256` char(64) NOT NULL,
  `installed_by` varchar(255) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`),
  UNIQUE KEY `uk_community_forward_schema_history_script` (`script`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    AND table_name IN (
      'auth_refresh_token',
      'auth_refresh_token_family_revocation',
      'comment',
      'discuss_post',
      'notice_record',
      'post_counter_snapshot',
      'post_score_snapshot',
      'social_block',
      'social_follow',
      'social_like'
    )
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR @community_required_base_tables = 10,
  'DO 0',
  'SELECT * FROM `__community_migration_base_schema_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Refresh-token rotation fencing and bounded cleanup support.
SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'auth_refresh_token'
      AND column_name = 'rotation_lease_id'
  ),
  'ALTER TABLE `auth_refresh_token` ADD COLUMN `rotation_lease_id` binary(16) DEFAULT NULL AFTER `pending_expires_at`',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'auth_refresh_token'
      AND index_name = 'idx_refresh_expires'
  ),
  'ALTER TABLE `auth_refresh_token` ADD INDEX `idx_refresh_expires` (`expires_at`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'auth_refresh_token_family_revocation'
      AND column_name = 'expires_at'
  ),
  'ALTER TABLE `auth_refresh_token_family_revocation` ADD COLUMN `expires_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'UPDATE `auth_refresh_token_family_revocation` r LEFT JOIN (SELECT `family_id`, MAX(`expires_at`) AS `max_expires_at` FROM `auth_refresh_token` GROUP BY `family_id`) t ON t.`family_id` = r.`family_id` SET r.`expires_at` = GREATEST(r.`expires_at`, COALESCE(t.`max_expires_at`, r.`expires_at`))',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'UPDATE `auth_refresh_token` SET `state` = ''ACTIVE'', `pending_expires_at` = NULL WHERE `state` = ''PENDING_ROTATION'' AND `rotation_lease_id` IS NULL',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'auth_refresh_token_family_revocation'
      AND index_name = 'idx_refresh_family_revocation_expires'
  ),
  'ALTER TABLE `auth_refresh_token_family_revocation` ADD INDEX `idx_refresh_family_revocation_expires` (`expires_at`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'auth_refresh_token_family_lock'
  ),
  'CREATE TABLE `auth_refresh_token_family_lock` (`family_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `retain_until` timestamp NOT NULL, PRIMARY KEY (`family_id`), KEY `idx_refresh_family_lock_retention` (`retain_until`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Query indexes used by keyset feeds and recent-comment lookups.
SET @community_discuss_post_index_changes = CONCAT_WS(', ',
  IF(NOT EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'discuss_post' AND index_name = 'idx_discuss_post_feed_latest'
  ), 'ADD INDEX `idx_discuss_post_feed_latest` (`status`,`type`,`create_time`,`id`)', NULL),
  IF(NOT EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'discuss_post' AND index_name = 'idx_discuss_post_feed_hot'
  ), 'ADD INDEX `idx_discuss_post_feed_hot` (`status`,`type`,`score`,`create_time`,`id`)', NULL),
  IF(NOT EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'discuss_post' AND index_name = 'idx_discuss_post_category_latest'
  ), 'ADD INDEX `idx_discuss_post_category_latest` (`category_id`,`status`,`type`,`create_time`,`id`)', NULL),
  IF(NOT EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'discuss_post' AND index_name = 'idx_discuss_post_category_hot'
  ), 'ADD INDEX `idx_discuss_post_category_hot` (`category_id`,`status`,`type`,`score`,`create_time`,`id`)', NULL),
  IF(NOT EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'discuss_post' AND index_name = 'idx_discuss_post_user_latest'
  ), 'ADD INDEX `idx_discuss_post_user_latest` (`user_id`,`status`,`type`,`create_time`,`id`)', NULL),
  IF(NOT EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'discuss_post' AND index_name = 'idx_discuss_post_author_recent'
  ), 'ADD INDEX `idx_discuss_post_author_recent` (`user_id`,`status`,`create_time`,`id`)', NULL),
  IF(EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'discuss_post' AND index_name = 'idx_discuss_post_user_id'
  ), 'DROP INDEX `idx_discuss_post_user_id`', NULL),
  IF(EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'discuss_post' AND index_name = 'idx_discuss_post_category_id'
  ), 'DROP INDEX `idx_discuss_post_category_id`', NULL)
);
SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND @community_discuss_post_index_changes <> '',
  CONCAT('ALTER TABLE `discuss_post` ', @community_discuss_post_index_changes),
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'comment' AND index_name = 'idx_comment_user_recent'
  ),
  'ALTER TABLE `comment` ADD INDEX `idx_comment_user_recent` (`user_id`,`status`,`create_time`,`id`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Counter snapshot fencing prevents an older multi-instance flush from
-- overwriting a newer snapshot after its Redis dirty marker was confirmed.
SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'post_counter_snapshot'
      AND column_name = 'flush_revision'
  ),
  'ALTER TABLE `post_counter_snapshot` ADD COLUMN `flush_revision` bigint unsigned NOT NULL DEFAULT 0 AFTER `bookmark_count`',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'post_score_snapshot'
      AND column_name = 'flush_revision'
  ),
  'ALTER TABLE `post_score_snapshot` ADD COLUMN `flush_revision` bigint unsigned NOT NULL DEFAULT 0 AFTER `rank_version`',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Like lifecycle events use a durable per-relation sequence. The high base
-- reserves a separate ordering range above all legacy epoch-millisecond
-- versions, so the first post-upgrade event supersedes historical state.
SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'social_like_relation_version'
  ),
  'CREATE TABLE `social_like_relation_version` (`actor_user_id` binary(16) NOT NULL, `entity_type` int NOT NULL, `entity_id` binary(16) NOT NULL, `current_version` bigint NOT NULL DEFAULT 4611686018427387904, `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`actor_user_id`,`entity_type`,`entity_id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'UPDATE `social_like_relation_version` SET `current_version` = 4611686018427387904 WHERE `current_version` < 4611686018427387904',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Persistent like-notice projection state and lookup index.
SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'notice_like_projection_state'
  ),
  'CREATE TABLE `notice_like_projection_state` (`recipient_user_id` binary(16) NOT NULL, `source_relation_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL, `relation_instance_id` binary(16) DEFAULT NULL, `source_version` bigint NOT NULL DEFAULT 0, `active` tinyint(1) DEFAULT NULL, `source_event_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (`recipient_user_id`,`source_relation_key`), KEY `idx_notice_like_projection_state_instance` (`relation_instance_id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'notice_record' AND index_name = 'idx_notice_record_like_relation'
  ),
  'ALTER TABLE `notice_record` ADD INDEX `idx_notice_record_like_relation` (`recipient_user_id`,`topic`,`source_relation_key`,`status`)',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Canonical user-pair mutex and cleanup of pre-existing block/follow conflicts.
SET @community_migration_sql = IF(
  @community_migration_needed = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'social_user_pair_lock'
  ),
  'CREATE TABLE `social_user_pair_lock` (`first_user_id` binary(16) NOT NULL, `second_user_id` binary(16) NOT NULL, PRIMARY KEY (`first_user_id`,`second_user_id`), CONSTRAINT `chk_social_user_pair_distinct` CHECK (`first_user_id` <> `second_user_id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_migration_sql = IF(
  @community_migration_needed = 1,
  'DELETE f FROM `social_follow` f INNER JOIN `social_block` b ON f.`entity_type` = 3 AND ((f.`user_id` = b.`user_id` AND f.`entity_id` = b.`target_user_id`) OR (f.`user_id` = b.`target_user_id` AND f.`entity_id` = b.`user_id`))',
  'DO 0'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

-- Verify pre-existing names cannot hide incompatible definitions.
SET @community_verified_structures = (
  SELECT COUNT(*)
  FROM (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'auth_refresh_token'
      AND column_name = 'rotation_lease_id' AND column_type = 'binary(16)' AND is_nullable = 'YES'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'auth_refresh_token_family_revocation'
      AND column_name = 'expires_at' AND data_type = 'timestamp' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'auth_refresh_token_family_lock'
      AND column_name = 'family_id' AND column_type = 'varchar(64)' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'auth_refresh_token_family_lock'
      AND column_name = 'retain_until' AND data_type = 'timestamp' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'notice_like_projection_state'
      AND column_name = 'source_version' AND data_type = 'bigint' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'notice_like_projection_state'
      AND column_name = 'recipient_user_id' AND column_type = 'binary(16)' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'notice_like_projection_state'
      AND column_name = 'source_relation_key' AND column_type = 'varchar(255)' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'notice_like_projection_state'
      AND column_name = 'relation_instance_id' AND column_type = 'binary(16)' AND is_nullable = 'YES'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'notice_like_projection_state'
      AND column_name = 'active' AND data_type = 'tinyint' AND is_nullable = 'YES'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'notice_like_projection_state'
      AND column_name = 'source_event_id' AND column_type = 'varchar(128)' AND is_nullable = 'YES'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'notice_like_projection_state'
      AND column_name = 'update_time' AND data_type = 'timestamp' AND is_nullable = 'YES'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'social_user_pair_lock'
      AND column_name = 'first_user_id' AND column_type = 'binary(16)' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'social_user_pair_lock'
      AND column_name = 'second_user_id' AND column_type = 'binary(16)' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'post_counter_snapshot'
      AND column_name = 'flush_revision' AND column_type = 'bigint unsigned' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'post_score_snapshot'
      AND column_name = 'flush_revision' AND column_type = 'bigint unsigned' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'social_like_relation_version'
      AND column_name = 'actor_user_id' AND column_type = 'binary(16)' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'social_like_relation_version'
      AND column_name = 'entity_type' AND data_type = 'int' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'social_like_relation_version'
      AND column_name = 'entity_id' AND column_type = 'binary(16)' AND is_nullable = 'NO'
    UNION ALL
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'social_like_relation_version'
      AND column_name = 'current_version' AND data_type = 'bigint' AND is_nullable = 'NO'
  ) required_structures
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR @community_verified_structures = 19,
  'DO 0',
  'SELECT * FROM `__community_migration_structure_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_required_primary_keys = (
  SELECT COUNT(*)
  FROM (
    SELECT table_name,
           GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',') AS column_signature
    FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE()
      AND constraint_name = 'PRIMARY'
      AND table_name IN ('auth_refresh_token_family_lock', 'notice_like_projection_state', 'social_like_relation_version', 'social_user_pair_lock')
    GROUP BY table_name
  ) primary_keys
  WHERE CONCAT(table_name, ':', column_signature) IN (
    'auth_refresh_token_family_lock:family_id',
    'notice_like_projection_state:recipient_user_id,source_relation_key',
    'social_like_relation_version:actor_user_id,entity_type,entity_id',
    'social_user_pair_lock:first_user_id,second_user_id'
  )
);
SET @community_required_pair_constraint = (
  SELECT COUNT(*)
  FROM information_schema.table_constraints
  WHERE table_schema = DATABASE()
    AND table_name = 'social_user_pair_lock'
    AND constraint_name = 'chk_social_user_pair_distinct'
    AND constraint_type = 'CHECK'
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0
    OR (@community_required_primary_keys = 4 AND @community_required_pair_constraint = 1),
  'DO 0',
  'SELECT * FROM `__community_migration_constraint_mismatch__`'
);
PREPARE community_migration_statement FROM @community_migration_sql;
EXECUTE community_migration_statement;
DEALLOCATE PREPARE community_migration_statement;

SET @community_required_index_count = (
  SELECT COUNT(*)
  FROM (
    SELECT table_name,
           index_name,
           GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS column_signature
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND CONCAT(table_name, ':', index_name) IN (
        'auth_refresh_token:idx_refresh_expires',
        'auth_refresh_token_family_lock:idx_refresh_family_lock_retention',
        'auth_refresh_token_family_revocation:idx_refresh_family_revocation_expires',
        'comment:idx_comment_user_recent',
        'discuss_post:idx_discuss_post_author_recent',
        'discuss_post:idx_discuss_post_category_hot',
        'discuss_post:idx_discuss_post_category_latest',
        'discuss_post:idx_discuss_post_feed_hot',
        'discuss_post:idx_discuss_post_feed_latest',
        'discuss_post:idx_discuss_post_user_latest',
        'notice_like_projection_state:idx_notice_like_projection_state_instance',
        'notice_record:idx_notice_record_like_relation'
      )
    GROUP BY table_name, index_name
  ) required_indexes
  WHERE CONCAT(table_name, ':', index_name, ':', column_signature) IN (
    'auth_refresh_token:idx_refresh_expires:expires_at',
    'auth_refresh_token_family_lock:idx_refresh_family_lock_retention:retain_until',
    'auth_refresh_token_family_revocation:idx_refresh_family_revocation_expires:expires_at',
    'comment:idx_comment_user_recent:user_id,status,create_time,id',
    'discuss_post:idx_discuss_post_author_recent:user_id,status,create_time,id',
    'discuss_post:idx_discuss_post_category_hot:category_id,status,type,score,create_time,id',
    'discuss_post:idx_discuss_post_category_latest:category_id,status,type,create_time,id',
    'discuss_post:idx_discuss_post_feed_hot:status,type,score,create_time,id',
    'discuss_post:idx_discuss_post_feed_latest:status,type,create_time,id',
    'discuss_post:idx_discuss_post_user_latest:user_id,status,type,create_time,id',
    'notice_like_projection_state:idx_notice_like_projection_state_instance:relation_instance_id',
    'notice_record:idx_notice_record_like_relation:recipient_user_id,topic,source_relation_key,status'
  )
);
SET @community_migration_sql = IF(
  @community_migration_needed = 0 OR @community_required_index_count = 12,
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

#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${REPO_ROOT}"

container="community-forward-migration-$$-${RANDOM}"
cleanup() {
  docker rm -f "${container}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run -d --name "${container}" \
  -e MYSQL_ROOT_PASSWORD=rootpass \
  -e COMMUNITY_MYSQL_USER=community \
  -e COMMUNITY_MYSQL_PASSWORD=communitypass \
  -e COMMUNITY_MIGRATION_USERNAME=community_migrator \
  -e COMMUNITY_MIGRATION_PASSWORD=communitymigratorpass \
  -e IM_MYSQL_USER=im_core \
  -e IM_MYSQL_PASSWORD=imcorepass \
  -e OSS_MYSQL_USER=community_oss \
  -e OSS_MYSQL_PASSWORD=communityosspass \
  -e MOCK_DATA_STUDIO_DB_USER=mock_data_studio \
  -e MOCK_DATA_STUDIO_DB_PASSWORD=mockdatastudiopass \
  -v "${REPO_ROOT}/deploy/database/business/init/001_create_databases.sh:/docker-entrypoint-initdb.d/001_create_databases.sh:ro" \
  -v "${REPO_ROOT}/deploy/database/business/current-state/010_current_schema.sql:/docker-entrypoint-initdb.d/010_current_schema.sql:ro" \
  -v "${REPO_ROOT}/deploy/scripts/run-community-migrations.sh:/migration/run-community-migrations.sh:ro" \
  -v "${REPO_ROOT}/deploy/database/business/migrations:/migrations:ro" \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null

for _ in $(seq 1 90); do
  if test "$(docker exec -e MYSQL_PWD=rootpass "${container}" \
      mysql -uroot -Nse "select count(*) from information_schema.tables where table_schema = 'community' and table_name in ('auth_refresh_token','auth_refresh_token_family_revocation','comment','discuss_post','notice_record','social_block','social_follow')" \
      2>/dev/null || true)" = "7"; then
    break
  fi
  sleep 2
done
test "$(docker exec -e MYSQL_PWD=rootpass "${container}" \
  mysql -uroot -Nse "select count(*) from information_schema.tables where table_schema = 'community' and table_name in ('auth_refresh_token','auth_refresh_token_family_revocation','comment','discuss_post','notice_record','social_block','social_follow')")" = "7"
for _ in $(seq 1 90); do
  if docker exec -e MYSQL_PWD=rootpass "${container}" \
      mysqladmin ping -h127.0.0.1 -uroot --silent >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
docker exec -e MYSQL_PWD=rootpass "${container}" \
  mysqladmin ping -h127.0.0.1 -uroot --silent >/dev/null

root_mysql=(docker exec -i -e MYSQL_PWD=rootpass "${container}" mysql -uroot --default-character-set=utf8mb4 community)

# Recreate the last snapshot-only shape, including data that violated the new
# mutual exclusion between a block and either-direction USER follow.
"${root_mysql[@]}" <<'SQL'
ALTER TABLE auth_refresh_token DROP INDEX idx_refresh_expires, DROP COLUMN rotation_lease_id;
ALTER TABLE auth_refresh_token_family_revocation
  DROP INDEX idx_refresh_family_revocation_expires,
  DROP COLUMN expires_at;
ALTER TABLE comment DROP INDEX idx_comment_user_recent;
ALTER TABLE comment DROP INDEX idx_comment_root_cleanup;
ALTER TABLE discuss_post
  DROP INDEX idx_discuss_post_feed_latest,
  DROP INDEX idx_discuss_post_feed_hot,
  DROP INDEX idx_discuss_post_category_latest,
  DROP INDEX idx_discuss_post_category_hot,
  DROP INDEX idx_discuss_post_user_latest,
  DROP INDEX idx_discuss_post_author_recent,
  ADD INDEX idx_discuss_post_user_id (user_id),
  ADD INDEX idx_discuss_post_category_id (category_id);
ALTER TABLE notice_record DROP INDEX idx_notice_record_like_relation;
ALTER TABLE post_counter_snapshot DROP COLUMN flush_revision;
ALTER TABLE post_score_snapshot DROP COLUMN flush_revision;
ALTER TABLE social_like DROP INDEX idx_like_post_entity_user, DROP COLUMN post_id;
ALTER TABLE market_order
  DROP INDEX idx_market_order_wallet_recovery,
  DROP COLUMN wallet_recovery_next_attempt_at;
ALTER TABLE user_task_event_log DROP INDEX idx_user_task_event_source;
DROP TABLE user_policy_version_log;
DROP TABLE growth_like_task_lifecycle_state;
DROP TABLE post_bookmark_counter_reconciliation;
DROP TABLE notice_like_projection_state;
DROP TABLE social_user_pair_lock;
-- Simulate a previous client stopping after one non-transactional DDL step.
ALTER TABLE comment ADD INDEX idx_comment_user_recent (user_id, status, create_time, id);
INSERT INTO auth_refresh_token(
  token_hash, user_id, family_id, security_version, expires_at, state, pending_expires_at, revoked_at
)
VALUES (
  'migration-pending-token',
  UNHEX('00000000000000000000000000000001'),
  'migration-pending-family',
  1,
  DATE_ADD(NOW(), INTERVAL 7 DAY),
  'PENDING_ROTATION',
  DATE_ADD(NOW(), INTERVAL 5 MINUTE),
  NULL
);
INSERT INTO auth_refresh_token(
  token_hash, user_id, family_id, security_version, expires_at, state, pending_expires_at, revoked_at
)
VALUES (
  'migration-revoked-token',
  UNHEX('00000000000000000000000000000001'),
  'migration-family',
  1,
  DATE_ADD(NOW(), INTERVAL 7 DAY),
  'REVOKED',
  NULL,
  NOW()
);
INSERT INTO auth_refresh_token_family_revocation(family_id, revoked_at)
VALUES ('migration-family', NOW());
INSERT INTO user(id, username, password, email, status, create_time, policy_version)
VALUES
  (UNHEX('00000000000000000000000000000001'), 'migration-user-1', 'encoded', 'migration-user-1@example.com', 1, NOW(), 0),
  (UNHEX('00000000000000000000000000000002'), 'migration-user-2', 'encoded', 'migration-user-2@example.com', 1, NOW(), 0);
INSERT INTO social_block(user_id, target_user_id, created_at, version)
VALUES (UNHEX('00000000000000000000000000000001'), UNHEX('00000000000000000000000000000002'), NOW(), 0);
INSERT INTO social_follow(user_id, entity_type, entity_id, created_at)
VALUES
  (UNHEX('00000000000000000000000000000001'), 3, UNHEX('00000000000000000000000000000002'), NOW()),
  (UNHEX('00000000000000000000000000000002'), 3, UNHEX('00000000000000000000000000000001'), NOW());
INSERT INTO comment(id, post_id, user_id, root_comment_id, content, status, version)
VALUES (
  UNHEX('00000000000000000000000000000031'),
  UNHEX('00000000000000000000000000000030'),
  UNHEX('00000000000000000000000000000003'),
  UNHEX('00000000000000000000000000000031'),
  'migration comment',
  0,
  1
);
INSERT INTO social_like(
  relation_instance_id, user_id, entity_type, entity_id, entity_user_id, created_at
)
VALUES
  (UNHEX('00000000000000000000000000000041'), UNHEX('00000000000000000000000000000011'), 1, UNHEX('00000000000000000000000000000030'), UNHEX('00000000000000000000000000000003'), NOW()),
  (UNHEX('00000000000000000000000000000042'), UNHEX('00000000000000000000000000000012'), 2, UNHEX('00000000000000000000000000000031'), UNHEX('00000000000000000000000000000003'), NOW()),
  (UNHEX('00000000000000000000000000000043'), UNHEX('00000000000000000000000000000013'), 3, UNHEX('00000000000000000000000000000003'), UNHEX('00000000000000000000000000000003'), NOW());
INSERT INTO user_task_event_log(id, user_id, task_code, period_key, source_event_id, create_time)
VALUES (
  UNHEX('00000000000000000000000000000051'),
  UNHEX('00000000000000000000000000000003'),
  'LIFETIME_RECEIVE_LIKE',
  'LIFETIME',
  'like:00000000-0000-0000-0000-000000000011:1:00000000-0000-0000-0000-000000000030',
  NOW()
);
INSERT INTO discuss_post(id, user_id, title, status, create_time)
VALUES (
  UNHEX('00000000000000000000000000000030'),
  UNHEX('00000000000000000000000000000003'),
  'migration post',
  0,
  NOW()
);
INSERT INTO post_bookmark(user_id, post_id, create_time)
VALUES (
  UNHEX('00000000000000000000000000000011'),
  UNHEX('00000000000000000000000000000030'),
  NOW()
);
SQL

run_migrations() {
  docker run --rm \
    --network "container:${container}" \
    --user 1000:1000 \
    --read-only \
    --tmpfs /tmp:rw,noexec,nosuid,size=16m \
    --cap-drop ALL \
    --security-opt no-new-privileges:true \
    -e DB_PRIMARY_HOST=127.0.0.1 \
    -e COMMUNITY_MYSQL_DATABASE=community \
    -e COMMUNITY_MIGRATION_USERNAME=community_migrator \
    -e COMMUNITY_MIGRATION_PASSWORD=communitymigratorpass \
    -v "${REPO_ROOT}/deploy/scripts/run-community-migrations.sh:/migration/run-community-migrations.sh:ro" \
    -v "${REPO_ROOT}/deploy/database/business/migrations:/migrations:ro" \
    mysql:8.0 bash /migration/run-community-migrations.sh
}

run_migrations

test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '016'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '017'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '018'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '019'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '020'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '021'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '022'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.statistics where table_schema = 'community' and table_name = 'comment' and index_name = 'idx_comment_root_cleanup'")" -eq 4
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.statistics where table_schema = 'community' and table_name = 'social_like' and index_name = 'idx_like_post_entity_user'")" -eq 4
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.columns where table_schema = 'community' and table_name = 'market_order' and column_name = 'wallet_recovery_next_attempt_at'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.statistics where table_schema = 'community' and table_name = 'market_order' and index_name = 'idx_market_order_wallet_recovery'")" -eq 3
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.tables where table_schema = 'community' and table_name = 'user_policy_version_log'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.statistics where table_schema = 'community' and table_name = 'user_policy_version_log' and index_name = 'idx_user_policy_version_user'")" -eq 2
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.statistics where table_schema = 'community' and table_name = 'social_block_version_log' and index_name = 'idx_social_block_version_pair'")" -eq 3
test "$("${root_mysql[@]}" -Nse "select count(*) from user where policy_version > 0")" -eq 2
test "$("${root_mysql[@]}" -Nse "select count(distinct policy_version) from user")" -eq 2
test "$("${root_mysql[@]}" -Nse "select count(*) from user users join user_policy_version_log history on history.version = users.policy_version and history.user_id = users.id and history.user_exists = 1")" -eq 2
test "$("${root_mysql[@]}" -Nse "select count(*) from user_policy_version_counter where id = 1 and current_version >= (select max(policy_version) from user)")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from social_block where version > 0")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from social_block blocks join social_block_version_log history on history.version = blocks.version and history.user_id = blocks.user_id and history.target_user_id = blocks.target_user_id and history.active = 1")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from social_block_version_counter where id = 1 and current_version >= (select max(version) from social_block)")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from social_like where entity_type = 1 and post_id = entity_id")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from social_like likes join comment comments on comments.id = likes.entity_id where likes.entity_type = 2 and likes.post_id = comments.post_id")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from social_like where entity_type = 3 and post_id is null")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from social_follow where entity_type = 3")" -eq 0
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.tables where table_schema = 'community' and table_name in ('notice_like_projection_state','social_user_pair_lock')")" -eq 2
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.columns where table_schema = 'community' and table_name = 'auth_refresh_token' and column_name = 'rotation_lease_id'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from auth_refresh_token where token_hash = 'migration-pending-token' and state = 'ACTIVE' and pending_expires_at is null and rotation_lease_id is null")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from auth_refresh_token_family_revocation where family_id = 'migration-family' and expires_at > now()")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.columns where table_schema = 'community' and table_name in ('post_counter_snapshot','post_score_snapshot') and column_name = 'flush_revision' and column_type = 'bigint unsigned'")" -eq 2
test "$("${root_mysql[@]}" -Nse "select count(*) from information_schema.statistics where table_schema = 'community' and table_name = 'user_task_event_log' and index_name = 'idx_user_task_event_source'")" -eq 4
test "$("${root_mysql[@]}" -Nse "select count(*) from growth_like_task_lifecycle_state where recipient_user_id = unhex('00000000000000000000000000000003') and relation_instance_id = unhex('00000000000000000000000000000041') and active = 1 and source_version >= 4611686018427387904")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from post_bookmark_counter_reconciliation where post_id = unhex('00000000000000000000000000000030') and pending = 1 and revision >= 1")" -eq 1

# Exact replay only validates the immutable checksum and leaves one history row.
run_migrations
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '016'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '017'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '018'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '019'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '020'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '021'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '022'")" -eq 1

# A fresh current snapshot already has the target structures. Removing only the
# transition marker must make the idempotent migration a no-op and re-mark it.
"${root_mysql[@]}" -e "delete from community_forward_schema_history where version in ('016', '017', '018', '019', '020', '021', '022')"
run_migrations
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '016'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '017'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '018'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '019'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '020'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '021'")" -eq 1
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '022'")" -eq 1

# A legacy orphan COMMENT like has no trustworthy owning post. Fail closed
# before recording V018 instead of leaving a content relation with NULL post_id.
"${root_mysql[@]}" -e "delete from community_forward_schema_history where version = '018'; insert into social_like(relation_instance_id, user_id, entity_type, entity_id, post_id, entity_user_id, created_at) values (unhex('00000000000000000000000000000044'), unhex('00000000000000000000000000000014'), 2, unhex('00000000000000000000000000000032'), null, unhex('00000000000000000000000000000003'), now())"
if run_migrations >/dev/null 2>&1; then
  echo 'orphan COMMENT like backfill unexpectedly succeeded' >&2
  exit 1
fi
"${root_mysql[@]}" -e "delete from social_like where relation_instance_id = unhex('00000000000000000000000000000044')"
run_migrations
test "$("${root_mysql[@]}" -Nse "select count(*) from community_forward_schema_history where version = '018'")" -eq 1

# Runtime DML credentials cannot execute DDL.
if docker exec -e MYSQL_PWD=communitypass "${container}" \
    mysql -ucommunity community -e 'create table runtime_must_not_ddl(id int)' >/dev/null 2>&1; then
  echo 'community runtime account unexpectedly has DDL privileges' >&2
  exit 1
fi

# Applied migration files are immutable: a different checksum must fail closed.
"${root_mysql[@]}" -e "update community_forward_schema_history set checksum_sha256 = repeat('0', 64) where version = '016'"
if run_migrations >/dev/null 2>&1; then
  echo 'migration checksum mismatch unexpectedly succeeded' >&2
  exit 1
fi

migration_checksum="$(sha256sum deploy/database/business/migrations/V016__reliability_and_query_consistency.sql | awk '{print $1}')"
"${root_mysql[@]}" -e "update community_forward_schema_history set checksum_sha256 = '${migration_checksum}' where version = '016'; insert into community_forward_schema_history(version, description, script, checksum_sha256, installed_by) values ('999', 'missing', 'V999__missing.sql', repeat('9', 64), 'test')"
if run_migrations >/dev/null 2>&1; then
  echo 'database history missing from the reviewed migration set unexpectedly succeeded' >&2
  exit 1
fi

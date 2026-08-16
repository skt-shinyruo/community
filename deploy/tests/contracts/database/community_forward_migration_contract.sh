#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${REPO_ROOT}"

runner="deploy/scripts/run-community-migrations.sh"
migration="deploy/database/business/migrations/V016__reliability_and_query_consistency.sql"
bounded_comment_migration="deploy/database/business/migrations/V017__bounded_comment_cleanup.sql"
social_like_post_migration="deploy/database/business/migrations/V018__social_comment_like_post_reference.sql"
market_wallet_recovery_migration="deploy/database/business/migrations/V019__market_wallet_recovery_schedule.sql"
im_policy_snapshot_migration="deploy/database/business/migrations/V020__im_policy_snapshot_version_history.sql"
growth_like_lifecycle_migration="deploy/database/business/migrations/V021__growth_like_lifecycle_state.sql"
bookmark_counter_reconciliation_migration="deploy/database/business/migrations/V022__durable_bookmark_counter_reconciliation.sql"
legacy_mock_tables_migration="deploy/database/business/migrations/V023__drop_legacy_mock_data_tables.sql"

bash -n "${runner}"
test -x "${runner}"
test -f "${migration}"
test -f "${bounded_comment_migration}"
test -f "${social_like_post_migration}"
test -f "${market_wallet_recovery_migration}"
test -f "${im_policy_snapshot_migration}"
test -f "${growth_like_lifecycle_migration}"
test -f "${bookmark_counter_reconciliation_migration}"
test -f "${legacy_mock_tables_migration}"

grep -Fq 'readonly MIGRATION_DIRECTORY="/migrations"' "${runner}"
grep -Fq 'sha256sum "${migration_file}"' "${runner}"
grep -Fq 'duplicate migration version' "${runner}"
grep -Fq 'database history does not exactly match the reviewed migration set' "${runner}"
grep -Fq 'COMMUNITY_MIGRATION_PASSWORD is required' "${runner}"

grep -Fq "GET_LOCK('community:forward-schema-migration', 60)" "${migration}"
grep -Fq 'community_forward_schema_history' "${migration}"
grep -Fq '__community_migration_checksum_mismatch__' "${migration}"
grep -Fq '__community_migration_identity_mismatch__' "${migration}"
grep -Fq 'WHERE @community_migration_needed = 1' "${migration}"
grep -Fq "RELEASE_LOCK('community:forward-schema-migration')" "${migration}"

grep -Fq "GET_LOCK('community:forward-schema-migration', 60)" "${bounded_comment_migration}"
grep -Fq '__community_migration_checksum_mismatch__' "${bounded_comment_migration}"
grep -Fq '__community_migration_identity_mismatch__' "${bounded_comment_migration}"
grep -Fq 'idx_comment_root_cleanup' "${bounded_comment_migration}"
grep -Fq 'root_comment_id,status,create_time,id' "${bounded_comment_migration}"
grep -Fq 'WHERE @community_migration_needed = 1' "${bounded_comment_migration}"
grep -Fq "RELEASE_LOCK('community:forward-schema-migration')" "${bounded_comment_migration}"

grep -Fq "GET_LOCK('community:forward-schema-migration', 60)" "${social_like_post_migration}"
grep -Fq '__community_migration_checksum_mismatch__' "${social_like_post_migration}"
grep -Fq '__community_migration_identity_mismatch__' "${social_like_post_migration}"
grep -Fq '__community_migration_social_like_post_backfill_mismatch__' "${social_like_post_migration}"
grep -Fq 'UPDATE `social_like` SET `post_id` = `entity_id`' "${social_like_post_migration}"
grep -Fq 'INNER JOIN `comment` comments ON comments.`id` = likes.`entity_id`' "${social_like_post_migration}"
grep -Fq 'idx_like_post_entity_user' "${social_like_post_migration}"
grep -Fq 'entity_type,post_id,entity_id,user_id' "${social_like_post_migration}"
grep -Fq 'WHERE @community_migration_needed = 1' "${social_like_post_migration}"
grep -Fq "RELEASE_LOCK('community:forward-schema-migration')" "${social_like_post_migration}"

grep -Fq "GET_LOCK('community:forward-schema-migration', 60)" "${market_wallet_recovery_migration}"
grep -Fq '__community_migration_checksum_mismatch__' "${market_wallet_recovery_migration}"
grep -Fq '__community_migration_identity_mismatch__' "${market_wallet_recovery_migration}"
grep -Fq 'wallet_recovery_next_attempt_at' "${market_wallet_recovery_migration}"
grep -Fq 'idx_market_order_wallet_recovery' "${market_wallet_recovery_migration}"
grep -Fq 'status,wallet_recovery_next_attempt_at,order_id' "${market_wallet_recovery_migration}"
grep -Fq 'WHERE @community_migration_needed = 1' "${market_wallet_recovery_migration}"
grep -Fq "RELEASE_LOCK('community:forward-schema-migration')" "${market_wallet_recovery_migration}"

grep -Fq "GET_LOCK('community:forward-schema-migration', 60)" "${im_policy_snapshot_migration}"
grep -Fq '__community_migration_checksum_mismatch__' "${im_policy_snapshot_migration}"
grep -Fq '__community_migration_identity_mismatch__' "${im_policy_snapshot_migration}"
grep -Fq 'user_policy_version_log' "${im_policy_snapshot_migration}"
grep -Fq 'idx_user_policy_version_user' "${im_policy_snapshot_migration}"
grep -Fq 'social_block_version_log' "${im_policy_snapshot_migration}"
grep -Fq 'idx_social_block_version_pair' "${im_policy_snapshot_migration}"
grep -Fq 'ROW_NUMBER() OVER' "${im_policy_snapshot_migration}"
grep -Fq 'WHERE @community_migration_needed = 1' "${im_policy_snapshot_migration}"
grep -Fq "RELEASE_LOCK('community:forward-schema-migration')" "${im_policy_snapshot_migration}"

grep -Fq "GET_LOCK('community:forward-schema-migration', 60)" "${growth_like_lifecycle_migration}"
grep -Fq '__community_migration_checksum_mismatch__' "${growth_like_lifecycle_migration}"
grep -Fq '__community_migration_identity_mismatch__' "${growth_like_lifecycle_migration}"
grep -Fq 'growth_like_task_lifecycle_state' "${growth_like_lifecycle_migration}"
grep -Fq 'idx_user_task_event_source' "${growth_like_lifecycle_migration}"
grep -Fq 'migration:V021:active-like:' "${growth_like_lifecycle_migration}"
grep -Fq 'WHERE @community_migration_needed = 1' "${growth_like_lifecycle_migration}"
grep -Fq "RELEASE_LOCK('community:forward-schema-migration')" "${growth_like_lifecycle_migration}"

grep -Fq "GET_LOCK('community:forward-schema-migration', 60)" "${bookmark_counter_reconciliation_migration}"
grep -Fq '__community_migration_checksum_mismatch__' "${bookmark_counter_reconciliation_migration}"
grep -Fq '__community_migration_identity_mismatch__' "${bookmark_counter_reconciliation_migration}"
grep -Fq 'post_bookmark_counter_reconciliation' "${bookmark_counter_reconciliation_migration}"
grep -Fq 'idx_post_bookmark_counter_reconcile_scan' "${bookmark_counter_reconciliation_migration}"
grep -Fq 'snapshots.`bookmark_count` <> COALESCE(facts.`bookmark_count`, 0)' "${bookmark_counter_reconciliation_migration}"
grep -Fq 'WHERE @community_migration_needed = 1' "${bookmark_counter_reconciliation_migration}"
grep -Fq "RELEASE_LOCK('community:forward-schema-migration')" "${bookmark_counter_reconciliation_migration}"

grep -Fq "GET_LOCK('community:forward-schema-migration', 60)" "${legacy_mock_tables_migration}"
grep -Fq '__community_migration_checksum_mismatch__' "${legacy_mock_tables_migration}"
grep -Fq '__community_migration_identity_mismatch__' "${legacy_mock_tables_migration}"
grep -Fq 'DROP TABLE IF EXISTS `demo_job`, `ai_config`' "${legacy_mock_tables_migration}"
grep -Fq "table_name IN ('demo_job', 'ai_config')" "${legacy_mock_tables_migration}"
grep -Fq 'WHERE @community_migration_needed = 1' "${legacy_mock_tables_migration}"
grep -Fq "RELEASE_LOCK('community:forward-schema-migration')" "${legacy_mock_tables_migration}"

for required_change in \
  rotation_lease_id \
  idx_refresh_expires \
  idx_refresh_family_revocation_expires \
  idx_discuss_post_feed_latest \
  idx_discuss_post_feed_hot \
  idx_discuss_post_category_latest \
  idx_discuss_post_category_hot \
  idx_discuss_post_user_latest \
  idx_discuss_post_author_recent \
  idx_comment_user_recent \
  post_counter_snapshot \
  post_score_snapshot \
  flush_revision \
  notice_like_projection_state \
  idx_notice_record_like_relation \
  social_like_relation_version \
  4611686018427387904 \
  social_user_pair_lock; do
  grep -Fq "${required_change}" "${migration}"
done

grep -Fq 'DELETE f FROM `social_follow` f INNER JOIN `social_block` b' "${migration}"
grep -Fq "SET \`state\` = ''ACTIVE''" "${migration}"

single_config="$(mktemp)"
cluster_config="$(mktemp)"
trap 'rm -f "${single_config}" "${cluster_config}"' EXIT

./deploy/deployment.sh config --stack single \
  --env-file deploy/stacks/single/.env.example --no-observability >"${single_config}"
./deploy/deployment.sh config --stack cluster \
  --env-file deploy/stacks/cluster/.env.example --no-observability >"${cluster_config}"

for rendered in "${single_config}" "${cluster_config}"; do
  migration_service="$(awk '
    $0 == "  community-db-migrations:" { in_service = 1; print; next }
    in_service && /^  [^ ]/ { exit }
    in_service { print }
  ' "${rendered}")"
  grep -Fq 'image: mysql:8.0' <<<"${migration_service}"
  grep -Fq 'read_only: true' <<<"${migration_service}"
  grep -Fq 'no-new-privileges:true' <<<"${migration_service}"
  grep -Fq 'COMMUNITY_MIGRATION_USERNAME: community_migrator' <<<"${migration_service}"
  grep -Fq 'target: /migrations' <<<"${migration_service}"
done

cluster_migration_service="$(awk '
  $0 == "  community-db-migrations:" { in_service = 1; print; next }
  in_service && /^  [^ ]/ { exit }
  in_service { print }
' "${cluster_config}")"
grep -A2 -E '^      mysql-replication-bootstrap:$' <<<"${cluster_migration_service}" \
  | grep -Fq 'condition: service_completed_successfully'

#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${REPO_ROOT}"

schema="deploy/database/business/current-state/010_current_schema.sql"
single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
single_production="$(mktemp)"
trap 'rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}" "${single_production}"' EXIT

./deploy/deployment.sh config --topology single --scope infra \
  --env-file deploy/stacks/single/.env.example --no-observability >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full \
  --env-file deploy/stacks/single/.env.example --no-observability >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra \
  --env-file deploy/stacks/cluster/.env.example --no-observability >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full \
  --env-file deploy/stacks/cluster/.env.example --no-observability >"${cluster_full}"
DEPLOYMENT_ENVIRONMENT=production \
  ./deploy/deployment.sh config --topology single --scope infra \
    --env-file deploy/stacks/single/.env.example --no-observability >"${single_production}"

grep -Fq 'CREATE DATABASE IF NOT EXISTS `community`' "${schema}"
grep -Fq 'CREATE TABLE `drive_upload`' "${schema}"
grep -Fq 'KEY `idx_drive_share_owner_time` (`created_by`,`created_at`,`share_id`)' "${schema}"
grep -Fq '`auto_confirm_next_attempt_at` timestamp NULL DEFAULT NULL' "${schema}"
grep -Fq '`wallet_recovery_next_attempt_at` timestamp NULL DEFAULT NULL' "${schema}"
grep -Fq 'KEY `idx_market_listing_public_page` (`create_time`,`listing_id`,`status`)' "${schema}"
grep -Fq 'KEY `idx_market_inventory_listing_page` (`listing_id`,`inventory_unit_id`)' "${schema}"
grep -Fq 'KEY `idx_market_order_auto_confirm` (`auto_confirm_next_attempt_at`,`order_id`,`status`,`auto_confirm_at`)' "${schema}"
grep -Fq 'KEY `idx_market_order_wallet_recovery` (`status`,`wallet_recovery_next_attempt_at`,`order_id`)' "${schema}"
grep -Fq 'CREATE TABLE `social_like_target_state`' "${schema}"
grep -Fq 'CREATE TABLE `social_like_relation_version`' "${schema}"
grep -Fq '`post_id` binary(16) DEFAULT NULL' "${schema}"
grep -Fq 'KEY `idx_like_post_entity_user` (`entity_type`,`post_id`,`entity_id`,`user_id`)' "${schema}"
grep -Fq '`current_version` bigint NOT NULL DEFAULT '\''4611686018427387904'\''' "${schema}"
grep -Fq 'CREATE TABLE `social_user_pair_lock`' "${schema}"
grep -Fq 'CREATE TABLE `user_policy_version_log`' "${schema}"
grep -Fq 'KEY `idx_user_policy_version_user` (`user_id`,`version`)' "${schema}"
grep -Fq 'KEY `idx_social_block_version_pair` (`user_id`,`target_user_id`,`version`)' "${schema}"
grep -Fq 'CREATE TABLE `notice_like_projection_state`' "${schema}"
grep -Fq '`rotation_lease_id` binary(16) DEFAULT NULL' "${schema}"
grep -Fq 'KEY `idx_refresh_family_revocation_expires` (`expires_at`)' "${schema}"
grep -Fq 'KEY `idx_discuss_post_feed_latest` (`status`,`type`,`create_time`,`id`)' "${schema}"
grep -Fq 'KEY `idx_comment_root_cleanup` (`root_comment_id`,`status`,`create_time`,`id`)' "${schema}"
grep -Fq 'KEY `idx_comment_user_recent` (`user_id`,`status`,`create_time`,`id`)' "${schema}"
test "$(grep -Fc '`flush_revision` bigint unsigned NOT NULL DEFAULT '\''0'\''' "${schema}")" -eq 2
grep -Fq 'CREATE TABLE `wallet_test_credit_quota`' "${schema}"
grep -Fq 'CONSTRAINT `ck_wallet_test_credit_granted_nonnegative` CHECK ((`granted_amount` >= 0))' "${schema}"
grep -Fq 'CONSTRAINT `ck_wallet_test_credit_discarded_nonnegative` CHECK ((`discarded_amount` >= 0))' "${schema}"
grep -Fq 'INSERT INTO `category` VALUES' "${schema}"
grep -Fq 'INSERT INTO `task_template` VALUES' "${schema}"

if rg -n -i 'alter[[:space:]]+table|drop[[:space:]]+table|gtid_purged|definer|schema_history|aaa@example|bbb@example|admin@example' "${schema}"; then
  echo 'current schema contains evolution DDL, history metadata, or development users' >&2
  exit 1
fi

business_ddl_files="$(rg -l -i '^create[[:space:]]+table' deploy/database --glob '*.sql' \
  | grep -Ev '/(nacos|xxl-job|migrations)/' || true)"
if [ "${business_ddl_files}" != "${schema}" ]; then
  echo "current-state business DDL must exist only in ${schema}; found: ${business_ddl_files}" >&2
  exit 1
fi

for rendered in "${single_infra}" "${cluster_infra}"; do
  grep -Fq 'target: /docker-entrypoint-initdb.d/010_current_schema.sql' "${rendered}"
  grep -Eq '^  community-dev-seed:$' "${rendered}"
  grep -Eq '^  community-db-migrations:$' "${rendered}"
  grep -Fq 'image: mysql:8.0' "${rendered}"
  grep -Fq 'COMMUNITY_MIGRATION_USERNAME: community_migrator' "${rendered}"
  grep -Fq 'target: /migration/run-community-migrations.sh' "${rendered}"
  grep -Fq 'target: /migrations' "${rendered}"
  grep -Fq 'DEPLOYMENT_ENVIRONMENT must equal development' "${rendered}"
  grep -Fq 'target: /seed/090_seed_identity.sql' "${rendered}"
done

test "$(grep -Fc 'target: /docker-entrypoint-initdb.d/010_current_schema.sql' "${single_infra}")" -eq 1
test "$(grep -Fc 'target: /docker-entrypoint-initdb.d/010_current_schema.sql' "${cluster_infra}")" -eq 1
grep -Fq 'DEPLOYMENT_ENVIRONMENT: production' "${single_production}"

if rg -n -i 'flyway|community-(oss-|im-)?db-migrations' backend/pom.xml backend/*/pom.xml backend/community-im/*/pom.xml; then
  echo 'backend reactor still contains a Flyway module' >&2
  exit 1
fi

grep -Fq "revoke all privileges, grant option from '\${MYSQL_USER_ESCAPED}'@'%';" \
  deploy/database/business/init/001_create_databases.sh
grep -Fq 'grant select, insert, update, delete on \`${MYSQL_DATABASE_ESCAPED}\`.*' \
  deploy/database/business/init/001_create_databases.sh
grep -Fq "revoke all privileges, grant option from '\${COMMUNITY_MIGRATION_USERNAME_ESCAPED}'@'%';" \
  deploy/database/business/init/001_create_databases.sh
grep -Fq 'grant select, insert, update, delete, create, alter, index' \
  deploy/database/business/init/001_create_databases.sh
grep -Fq 'COMMUNITY_MIGRATION_USERNAME=community_migrator' deploy/stacks/single/.env.example
grep -Fq 'COMMUNITY_MIGRATION_USERNAME=community_migrator' deploy/stacks/cluster/.env.example

for rendered in "${single_full}" "${cluster_full}"; do
  if awk '
    /^  community-app(-[123])?:$/ { in_service = 1; next }
    in_service && /^  [^ ]/ { in_service = 0 }
    in_service { print }
  ' "${rendered}" | rg -n 'COMMUNITY_MIGRATION_(USERNAME|PASSWORD)'; then
    echo 'community-app must not receive DDL migration credentials' >&2
    exit 1
  fi
done

awk '
  $0 == "  community-app:" { in_service = 1; next }
  in_service && /^  [^ ]/ { exit }
  in_service { print }
' "${single_full}" | grep -A2 -E '^      community-dev-seed:$' \
  | grep -Fq 'condition: service_completed_successfully'
awk '
  $0 == "  community-app:" { in_service = 1; next }
  in_service && /^  [^ ]/ { exit }
  in_service { print }
' "${single_full}" | grep -A2 -E '^      community-db-migrations:$' \
  | grep -Fq 'condition: service_completed_successfully'
for service in community-app-1 community-app-2 community-app-3; do
  awk -v service="${service}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service { print }
  ' "${cluster_full}" | grep -A2 -E '^      community-dev-seed:$' \
    | grep -Fq 'condition: service_completed_successfully'
  awk -v service="${service}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service { print }
  ' "${cluster_full}" | grep -A2 -E '^      community-db-migrations:$' \
    | grep -Fq 'condition: service_completed_successfully'
done

grep -Fq 'reset-mysql' deploy/deployment.sh
grep -Fq 'mysql_replica_1_data' deploy/deployment.sh
grep -Fq 'mysql_replica_2_data' deploy/deployment.sh

#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

schema="deploy/mysql/primary-init/010_current_schema.sql"
single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
single_production="$(mktemp)"
trap 'rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}" "${single_production}"' EXIT

./deploy/deployment.sh config --topology single --scope infra \
  --env-file deploy/.env.single.example --no-observability >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full \
  --env-file deploy/.env.single.example --no-observability >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra \
  --env-file deploy/.env.cluster.example --no-observability >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full \
  --env-file deploy/.env.cluster.example --no-observability >"${cluster_full}"
DEPLOYMENT_ENVIRONMENT=production \
  ./deploy/deployment.sh config --topology single --scope infra \
    --env-file deploy/.env.single.example --no-observability >"${single_production}"

grep -Fq 'CREATE DATABASE IF NOT EXISTS `community`' "${schema}"
grep -Fq 'CREATE TABLE `drive_upload`' "${schema}"
grep -Fq 'KEY `idx_drive_share_owner_time` (`created_by`,`created_at`,`share_id`)' "${schema}"
grep -Fq '`auto_confirm_next_attempt_at` timestamp NULL DEFAULT NULL' "${schema}"
grep -Fq 'KEY `idx_market_listing_public_page` (`create_time`,`listing_id`,`status`)' "${schema}"
grep -Fq 'KEY `idx_market_inventory_listing_page` (`listing_id`,`inventory_unit_id`)' "${schema}"
grep -Fq 'KEY `idx_market_order_auto_confirm` (`auto_confirm_next_attempt_at`,`order_id`,`status`,`auto_confirm_at`)' "${schema}"
grep -Fq 'CREATE TABLE `social_like_target_state`' "${schema}"
grep -Fq 'CREATE TABLE `wallet_test_credit_quota`' "${schema}"
grep -Fq 'CONSTRAINT `ck_wallet_test_credit_granted_nonnegative` CHECK ((`granted_amount` >= 0))' "${schema}"
grep -Fq 'CONSTRAINT `ck_wallet_test_credit_discarded_nonnegative` CHECK ((`discarded_amount` >= 0))' "${schema}"
grep -Fq 'INSERT INTO `category` VALUES' "${schema}"
grep -Fq 'INSERT INTO `task_template` VALUES' "${schema}"

if rg -n -i 'alter[[:space:]]+table|drop[[:space:]]+table|gtid_purged|definer|schema_history|aaa@example|bbb@example|admin@example' "${schema}"; then
  echo 'current schema contains evolution DDL, history metadata, or development users' >&2
  exit 1
fi

business_ddl_files="$(rg -l -i '^create[[:space:]]+table' deploy/mysql --glob '*.sql' \
  | grep -Ev '/(nacos|xxl-job)/' || true)"
if [ "${business_ddl_files}" != "${schema}" ]; then
  echo "business DDL must exist only in ${schema}; found: ${business_ddl_files}" >&2
  exit 1
fi

for rendered in "${single_infra}" "${cluster_infra}"; do
  grep -Fq 'target: /docker-entrypoint-initdb.d/010_current_schema.sql' "${rendered}"
  grep -Eq '^  community-dev-seed:$' "${rendered}"
  grep -Fq 'image: mysql:8.0' "${rendered}"
  grep -Fq 'DEPLOYMENT_ENVIRONMENT must equal development' "${rendered}"
  grep -Fq 'target: /seed/090_seed_identity.sql' "${rendered}"
done

test "$(grep -Fc 'target: /docker-entrypoint-initdb.d/010_current_schema.sql' "${single_infra}")" -eq 1
test "$(grep -Fc 'target: /docker-entrypoint-initdb.d/010_current_schema.sql' "${cluster_infra}")" -eq 1
grep -Fq 'DEPLOYMENT_ENVIRONMENT: production' "${single_production}"

for rendered in "${single_infra}" "${cluster_infra}"; do
  if rg -n -i 'flyway|db-migrations|migration_(username|password|jdbc_url|history_table)' "${rendered}"; then
    echo 'rendered topology still contains a migration deployable or credential' >&2
    exit 1
  fi
done

if rg -n -i 'flyway|community-(oss-|im-)?db-migrations' backend/pom.xml backend/*/pom.xml backend/community-im/*/pom.xml; then
  echo 'backend reactor still contains a Flyway module' >&2
  exit 1
fi
if rg -n 'MIGRATION_(USERNAME|PASSWORD)|grant all privileges' \
    deploy/mysql/primary-init/001_create_databases.sh deploy/.env.single.example deploy/.env.cluster.example; then
  echo 'migration credentials or DDL grants remain' >&2
  exit 1
fi

grep -Fq "revoke all privileges, grant option from '\${MYSQL_USER_ESCAPED}'@'%';" \
  deploy/mysql/primary-init/001_create_databases.sh
grep -Fq 'grant select, insert, update, delete on \`${MYSQL_DATABASE_ESCAPED}\`.*' \
  deploy/mysql/primary-init/001_create_databases.sh

awk '
  $0 == "  community-app:" { in_service = 1; next }
  in_service && /^  [^ ]/ { exit }
  in_service { print }
' "${single_full}" | grep -A2 -E '^      community-dev-seed:$' \
  | grep -Fq 'condition: service_completed_successfully'
for service in community-app-1 community-app-2 community-app-3; do
  awk -v service="${service}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service { print }
  ' "${cluster_full}" | grep -A2 -E '^      community-dev-seed:$' \
    | grep -Fq 'condition: service_completed_successfully'
done

grep -Fq 'reset-mysql' deploy/deployment.sh
grep -Fq 'mysql_replica_1_data' deploy/deployment.sh
grep -Fq 'mysql_replica_2_data' deploy/deployment.sh

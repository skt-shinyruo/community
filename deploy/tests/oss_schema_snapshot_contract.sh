#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

schema="deploy/mysql/primary-init/010_current_schema.sql"
single_full="$(mktemp)"
cluster_full="$(mktemp)"
trap 'rm -f "${single_full}" "${cluster_full}"' EXIT

./deploy/deployment.sh config --topology single --scope full \
  --env-file deploy/.env.single.example --no-observability >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope full \
  --env-file deploy/.env.cluster.example --no-observability >"${cluster_full}"

grep -Fq 'CREATE DATABASE IF NOT EXISTS `community_oss`' "${schema}"
for table in oss_object oss_object_version oss_upload_session oss_access_grant oss_object_reference oss_usage_policy; do
  grep -Fq "CREATE TABLE \`${table}\`" "${schema}"
done
grep -Fq '`request_id` binary(16) NOT NULL' "${schema}"
grep -Fq '`claim_version` bigint NOT NULL DEFAULT '\''0'\''' "${schema}"
grep -Fq 'UNIQUE KEY `uk_oss_upload_request` (`request_id`)' "${schema}"
grep -Fq 'KEY `idx_oss_upload_recovery` (`status`,`updated_at`,`session_id`)' "${schema}"
grep -Fq 'INSERT INTO `oss_usage_policy` VALUES' "${schema}"

if rg -n 'OSS_MIGRATION|oss_schema_history|community-oss-db-migrations' deploy backend \
    --glob '!deploy/tests/**' --glob '!**/src/test/**' --glob '!**/target/**'; then
  echo 'OSS migration surface remains' >&2
  exit 1
fi

awk '
  $0 == "  community-oss:" { in_service = 1; next }
  in_service && /^  [^ ]/ { exit }
  in_service { print }
' "${single_full}" | grep -A2 -E '^      community-db-user-bootstrap:$' \
  | grep -Fq 'condition: service_completed_successfully'

awk '
  $0 == "  community-oss-1:" { in_service = 1; next }
  in_service && /^  [^ ]/ { exit }
  in_service { print }
' "${cluster_full}" | grep -A2 -E '^      mysql-replication-bootstrap:$' \
  | grep -Fq 'condition: service_completed_successfully'

grep -Fq 'OSS_DB_URL: jdbc:mysql://mysql:3306/community_oss?' "${single_full}"
grep -Fq 'OSS_DB_URL: jdbc:mysql://mysql-primary:3306/community_oss?' "${cluster_full}"
grep -Fq 'grant select, insert, update, delete on \`${OSS_MYSQL_DATABASE_ESCAPED}\`.*' \
  deploy/mysql/primary-init/001_create_databases.sh

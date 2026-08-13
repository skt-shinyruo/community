#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${REPO_ROOT}"

schema="deploy/database/business/current-state/010_current_schema.sql"
single_full="$(mktemp)"
cluster_full="$(mktemp)"
trap 'rm -f "${single_full}" "${cluster_full}"' EXIT

./deploy/deployment.sh config --topology single --scope full \
  --env-file deploy/stacks/single/.env.example --no-observability >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope full \
  --env-file deploy/stacks/cluster/.env.example --no-observability >"${cluster_full}"

grep -Fq 'CREATE DATABASE IF NOT EXISTS `im_core`' "${schema}"
for table in im_room im_room_member im_room_message im_conversation im_private_message im_user_room_inbox im_user_conversation_inbox; do
  grep -Fq "CREATE TABLE \`${table}\`" "${schema}"
done
grep -Fq '`lease_token` binary(16) DEFAULT NULL' "${schema}"
grep -Fq '`processing_lease_until` timestamp NULL DEFAULT NULL' "${schema}"
grep -Fq 'KEY `idx_outbox_processing_lease` (`status`,`processing_lease_until`,`id`)' "${schema}"
grep -Fq 'INSERT INTO `im_membership_version_counter` VALUES (1,0)' "${schema}"

if rg -n 'IM_MIGRATION|im_core_schema_history|community-im-db-migrations' deploy backend \
    --glob '!deploy/tests/**' --glob '!**/src/test/**' --glob '!**/target/**'; then
  echo 'IM migration surface remains' >&2
  exit 1
fi

awk '
  $0 == "  im-core:" { in_service = 1; next }
  in_service && /^  [^ ]/ { exit }
  in_service { print }
' "${single_full}" | grep -A2 -E '^      community-db-user-bootstrap:$' \
  | grep -Fq 'condition: service_completed_successfully'

for service in im-core-1 im-core-2 im-core-3; do
  awk -v service="${service}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service { print }
  ' "${cluster_full}" | grep -A2 -E '^      mysql-replication-bootstrap:$' \
    | grep -Fq 'condition: service_completed_successfully'
done

grep -Fq 'IM_CORE_DB_URL: jdbc:mysql://mysql:3306/im_core?' "${single_full}"
grep -Fq 'IM_CORE_DB_URL: jdbc:mysql://mysql-primary:3306/im_core?' "${cluster_full}"
grep -Fq 'grant select, insert, update, delete on \`${IM_MYSQL_DATABASE_ESCAPED}\`.*' \
  deploy/database/business/init/001_create_databases.sh

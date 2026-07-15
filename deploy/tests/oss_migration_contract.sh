#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
trap 'rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}"' EXIT

require_text() {
  local needle="$1"
  local file="$2"
  local message="$3"
  if ! grep -Fq -- "${needle}" "${file}"; then
    echo "${message}" >&2
    exit 1
  fi
}

require_text '<module>community-oss-db-migrations</module>' backend/pom.xml \
  'community-oss-db-migrations is not part of the backend reactor'
require_text 'community-oss-db-migrations' deploy/Dockerfile.oss-db-migration \
  'OSS migration image does not package the dedicated runner'

./deploy/deployment.sh config --topology single --scope infra \
  --env-file deploy/.env.single.example --no-observability >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full \
  --env-file deploy/.env.single.example --no-observability >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra \
  --env-file deploy/.env.cluster.example --no-observability >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full \
  --env-file deploy/.env.cluster.example --no-observability >"${cluster_full}"

for rendered in "${single_infra}" "${cluster_infra}"; do
  require_text '  community-oss-db-migrations:' "${rendered}" \
    'OSS migration service is missing from the infra topology'
  require_text 'OSS_MIGRATION_USERNAME: community_oss_migrator' "${rendered}" \
    'OSS migration service does not use its dedicated DDL account'
  require_text 'OSS_MIGRATION_HISTORY_TABLE: oss_schema_history' "${rendered}" \
    'OSS migration service does not use the owner-specific history table'
  if grep -F '/docker-entrypoint-initdb.d/100_schema_community_oss.sql' "${rendered}" >/dev/null; then
    echo 'OSS final-state schema is still mounted into MySQL first-boot init' >&2
    exit 1
  fi
done

assert_service_waits_for_migration() {
  local service="$1"
  local rendered="$2"
  awk -v service="${service}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service { print }
  ' "${rendered}" \
    | grep -A8 -E '^      community-oss-db-migrations:$' \
    | grep -Fq 'condition: service_completed_successfully'
}

assert_service_waits_for_migration community-oss "${single_full}"
for service in community-oss-1 community-oss-2 community-oss-3; do
  assert_service_waits_for_migration "${service}" "${cluster_full}"
done

require_text 'OSS_MIGRATION_USERNAME' deploy/mysql/primary-init/001_create_databases.sh \
  'database bootstrap does not create a dedicated OSS migration account'
require_text 'OSS_MIGRATION_USERNAME=community_oss_migrator' deploy/.env.single.example \
  'single topology does not declare the OSS migration account'
require_text 'OSS_MIGRATION_USERNAME=community_oss_migrator' deploy/.env.cluster.example \
  'cluster topology does not declare the OSS migration account'

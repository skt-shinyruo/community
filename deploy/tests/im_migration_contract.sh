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

require_text '<module>community-im-db-migrations</module>' backend/pom.xml \
  'community-im-db-migrations is not part of the backend reactor'
require_text 'community-im-db-migrations' deploy/Dockerfile.im-db-migration \
  'IM migration image does not package the dedicated runner'
require_text 'community-im-db-migrations.jar' deploy/Dockerfile.im-db-migration \
  'IM migration image does not execute the dedicated runner JAR'

./deploy/deployment.sh config --topology single --scope infra \
  --env-file deploy/.env.single.example --no-observability >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full \
  --env-file deploy/.env.single.example --no-observability >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra \
  --env-file deploy/.env.cluster.example --no-observability >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full \
  --env-file deploy/.env.cluster.example --no-observability >"${cluster_full}"

for rendered in "${single_infra}" "${cluster_infra}"; do
  require_text '  community-im-db-migrations:' "${rendered}" \
    'IM migration service is missing from the infra topology'
  require_text 'IM_MIGRATION_USERNAME: im_core_migrator' "${rendered}" \
    'IM migration service does not use its dedicated DDL account'
  require_text 'IM_MIGRATION_HISTORY_TABLE: im_core_schema_history' "${rendered}" \
    'IM migration service does not use the owner-specific history table'
  if grep -F '/docker-entrypoint-initdb.d/070_schema_im_core.sql' "${rendered}" >/dev/null; then
    echo 'IM final-state schema is still mounted into MySQL first-boot init' >&2
    exit 1
  fi
  if grep -F '/bootstrap/community/001_bootstrap.sh' "${rendered}" >/dev/null; then
    echo 'a helper bootstrap still replays the IM final-state schema outside Flyway' >&2
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
    | grep -A8 -E '^      community-im-db-migrations:$' \
    | grep -Fq 'condition: service_completed_successfully'
}

assert_service_waits_for_migration im-core "${single_full}"
for service in im-core-1 im-core-2 im-core-3; do
  assert_service_waits_for_migration "${service}" "${cluster_full}"
done

require_text 'IM_CORE_DB_USER: im_core' "${single_full}" \
  'single IM Core runtime no longer uses the DML-only account'
require_text 'IM_CORE_DB_USER: im_core' "${cluster_full}" \
  'cluster IM Core runtime no longer uses the DML-only account'
require_text 'IM_MIGRATION_USERNAME' deploy/mysql/primary-init/001_create_databases.sh \
  'database bootstrap does not create a dedicated IM migration account'
require_text "revoke all privileges, grant option from '\${IM_MYSQL_USER_ESCAPED}'@'%';" \
  deploy/mysql/primary-init/001_create_databases.sh \
  'database bootstrap does not constrain the IM runtime account to DML'
require_text 'grant all privileges on \`${IM_MYSQL_DATABASE_ESCAPED}\`.* to '\''${IM_MIGRATION_USERNAME_ESCAPED}'\''@'\''%'\'';' \
  deploy/mysql/primary-init/001_create_databases.sh \
  'database bootstrap does not grant IM DDL to the migration account'
require_text 'IM_MIGRATION_USERNAME=im_core_migrator' deploy/.env.single.example \
  'single topology does not declare the IM migration account'
require_text 'IM_MIGRATION_USERNAME=im_core_migrator' deploy/.env.cluster.example \
  'cluster topology does not declare the IM migration account'

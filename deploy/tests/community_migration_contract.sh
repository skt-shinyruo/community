#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
single_production_infra="$(mktemp)"
trap 'rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}" "${single_production_infra}"' EXIT

grep -Fq '<module>community-db-migrations</module>' backend/pom.xml

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
    --env-file deploy/.env.single.example --no-observability >"${single_production_infra}"

for rendered in "${single_infra}" "${cluster_infra}"; do
  grep -E '^  community-db-user-bootstrap:$' "${rendered}"
  grep -E '^  community-db-migrations:$' "${rendered}"
  grep -E '^  community-dev-seed:$' "${rendered}"
  grep -F 'COMMUNITY_MIGRATION_USERNAME: community_migrator' "${rendered}"
  grep -F 'COMMUNITY_MIGRATION_HISTORY_TABLE: community_schema_history' "${rendered}"
  grep -F 'COMMUNITY_MIGRATION_PROFILE: development' "${rendered}"
  grep -F 'community-db-migrations.jar development-seed' "${rendered}"

  if grep -F '/docker-entrypoint-initdb.d/010_schema_shared.sql' "${rendered}" >/dev/null; then
    echo 'Community final-state schema is still mounted into MySQL first-boot init' >&2
    exit 1
  fi
  if grep -F '/docker-entrypoint-initdb.d/090_seed_identity.sql' "${rendered}" >/dev/null; then
    echo 'Community development seed is still coupled to MySQL first-boot init' >&2
    exit 1
  fi
done

# The seed runner must receive the real deployment environment. A hard-coded
# development profile would let an accidentally enabled seed run in production.
grep -A30 -E '^  community-dev-seed:$' "${single_production_infra}" \
  | grep -F 'COMMUNITY_MIGRATION_PROFILE: production'

grep -A8 -E '^      community-db-migrations:$' "${single_full}" \
  | grep -F 'condition: service_completed_successfully'
for service in community-app-1 community-app-2 community-app-3; do
  awk -v service="${service}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service { print }
  ' "${cluster_full}" \
    | grep -A8 -E '^      community-db-migrations:$' \
    | grep -F 'condition: service_completed_successfully'
done

if rg -n '^\s*- MYSQL_(USER|PASSWORD)=' \
    deploy/compose.infra.mysql.single.yml deploy/compose.infra.mysql.cluster.yml; then
  echo 'The official MySQL image must not auto-grant DDL privileges to the application account' >&2
  exit 1
fi

rg -Fq 'COMMUNITY_MIGRATION_USERNAME' deploy/mysql/primary-init/001_create_databases.sh
rg -Fq 'revoke all privileges, grant option' deploy/mysql/primary-init/001_create_databases.sh
rg -Fq 'COMMUNITY_MIGRATION_USERNAME=community_migrator' deploy/.env.single.example
rg -Fq 'COMMUNITY_MIGRATION_USERNAME=community_migrator' deploy/.env.cluster.example

#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
trap 'rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}"' EXIT

./deploy/deployment.sh config --topology single --scope infra --env-file deploy/.env.single.example --no-observability >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full --env-file deploy/.env.single.example --no-observability >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra --env-file deploy/.env.cluster.example --no-observability >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full --env-file deploy/.env.cluster.example --no-observability >"${cluster_full}"

grep -E '^  garage:$' "${single_infra}"
grep -F 'dxflrs/garage:v2.2.0' "${single_infra}"
grep -E 'GARAGE_REPLICATION_FACTOR: "?1"?' "${single_infra}"
grep -E '^  community-oss:$' "${single_full}"
grep -F 'OSS_OBJECT_STORE_ENDPOINT: http://garage:3900' "${single_full}"
grep -F 'OSS_DB_URL: jdbc:mysql://mysql:3306/community_oss' "${single_full}"

grep -E '^  garage-1:$' "${cluster_infra}"
grep -E '^  garage-2:$' "${cluster_infra}"
grep -E '^  garage-3:$' "${cluster_infra}"
grep -E 'GARAGE_REPLICATION_FACTOR: "?3"?' "${cluster_infra}"
grep -E '^  community-oss-1:$' "${cluster_full}"
grep -E '^  community-oss-2:$' "${cluster_full}"
grep -E '^  community-oss-3:$' "${cluster_full}"
grep -F 'OSS_OBJECT_STORE_ENDPOINT: http://garage:3900' "${cluster_full}"
grep -F 'OSS_DB_URL: jdbc:mysql://mysql-primary:3306/community_oss' "${cluster_full}"

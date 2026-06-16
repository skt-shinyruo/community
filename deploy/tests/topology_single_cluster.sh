#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

help_output="$(./deploy/deployment.sh --help 2>&1)"
printf '%s\n' "${help_output}" | grep -F -- '--topology <single|cluster>'
if printf '%s\n' "${help_output}" | grep -F -- '--topology <dev|ha>' >/dev/null 2>&1; then
  echo "old topology help text is still visible" >&2
  exit 1
fi

single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
dev_err="$(mktemp)"
ha_err="$(mktemp)"
trap 'rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}" "${dev_err}" "${ha_err}"' EXIT

./deploy/deployment.sh config --topology single --scope infra --env-file deploy/.env.single.example >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full --env-file deploy/.env.single.example >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra --env-file deploy/.env.cluster.example >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full --env-file deploy/.env.cluster.example >"${cluster_full}"

grep -F 'name: community-single' "${single_infra}"
grep -E '^  mysql:$' "${single_infra}"
grep -E '^  nacos:$' "${single_infra}"
grep -A40 -E '^  nacos:$' "${single_infra}" | grep -F 'healthcheck:'
grep -E '^  nacos-config-bootstrap:$' "${single_infra}"
grep -A24 -E '^  nacos-config-bootstrap:$' "${single_infra}" | grep -F '/deploy/nacos'
grep -A24 -E '^  nacos-config-bootstrap:$' "${single_infra}" | grep -F 'target: /nacos'
grep -A24 -E '^  nacos-config-bootstrap:$' "${single_infra}" | grep -F 'read_only: true'
grep -E '^  community-gateway:$' "${single_full}"
grep -A4 -E '^      nacos:$' "${single_full}" | grep -F 'condition: service_healthy'
grep -A6 -E '^      nacos-config-bootstrap:$' "${single_full}" | grep -F 'condition: service_completed_successfully'
grep -A4 -E '^      community-gateway:$' "${single_full}" | grep -F 'condition: service_healthy'
grep -E 'KAFKA_TOPIC_REPLICATION_FACTOR: "?1"?' "${single_infra}"
grep -A80 -E '^  im-realtime:$' "${single_full}" | grep -F 'SPRING_DATA_REDIS_HOST: redis'
grep -A80 -E '^  im-realtime:$' "${single_full}" | grep -F 'SPRING_DATA_REDIS_PORT: "6379"'

if grep -F 'XXL_JOB_ADMIN_ADDRESSES: http://nginx:8081/xxl-job-admin' "${single_full}" >/dev/null 2>&1; then
  echo "single community-app must use the direct XXL-JOB admin service address, not nginx" >&2
  exit 1
fi

grep -F 'name: community-cluster' "${cluster_infra}"
grep -E '^  mysql-primary:$' "${cluster_infra}"
grep -E '^  nacos-1:$' "${cluster_infra}"
grep -A40 -E '^  nacos-1:$' "${cluster_infra}" | grep -F 'healthcheck:'
grep -E '^  nacos-config-bootstrap:$' "${cluster_infra}"
grep -A24 -E '^  nacos-config-bootstrap:$' "${cluster_infra}" | grep -F '/deploy/nacos'
grep -A24 -E '^  nacos-config-bootstrap:$' "${cluster_infra}" | grep -F 'target: /nacos'
grep -A24 -E '^  nacos-config-bootstrap:$' "${cluster_infra}" | grep -F 'read_only: true'
grep -E '^  community-gateway-1:$' "${cluster_full}"
grep -A4 -E '^      nacos-1:$' "${cluster_full}" | grep -F 'condition: service_healthy'
grep -A6 -E '^      nacos-config-bootstrap:$' "${cluster_full}" | grep -F 'condition: service_completed_successfully'
grep -A4 -E '^      community-gateway-1:$' "${cluster_full}" | grep -F 'condition: service_healthy'
grep -E 'KAFKA_TOPIC_REPLICATION_FACTOR: "?3"?' "${cluster_infra}"
for worker in 1 2 3; do
  grep -A80 -E "^  im-realtime-${worker}:$" "${cluster_full}" | grep -F 'SPRING_DATA_REDIS_CLUSTER_NODES: redis-1:6379,redis-2:6379,redis-3:6379,redis-4:6379,redis-5:6379,redis-6:6379'
done
if grep -F 'XXL_JOB_ADMIN_ADDRESSES: http://nginx:8081/xxl-job-admin' "${cluster_full}" >/dev/null 2>&1; then
  echo "cluster community-app must use direct XXL-JOB admin service addresses, not nginx" >&2
  exit 1
fi

if ./deploy/deployment.sh config --topology dev --scope infra --env-file deploy/.env.single.example >/dev/null 2>"${dev_err}"; then
  echo "expected old topology dev to fail" >&2
  exit 1
fi
grep -F 'unsupported topology: dev' "${dev_err}"

if ./deploy/deployment.sh config --topology ha --scope infra --env-file deploy/.env.cluster.example >/dev/null 2>"${ha_err}"; then
  echo "expected old topology ha to fail" >&2
  exit 1
fi
grep -F 'unsupported topology: ha' "${ha_err}"

#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
trap 'rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}"' EXIT

assert_service_environment_value() {
  local config_file="$1"
  local service="$2"
  local expected="$3"

  if awk -v service="${service}" -v expected="${expected}" '
      $0 == "  " service ":" { in_service = 1; next }
      in_service && /^  [^ ]/ { exit }
      in_service && $0 == "      " expected { found = 1 }
      END { exit !found }
    ' "${config_file}"; then
    return 0
  fi

  echo "${service} must define ${expected}" >&2
  return 1
}

./deploy/deployment.sh config --topology single --scope infra --env-file deploy/.env.single.example --no-observability >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full --env-file deploy/.env.single.example --no-observability >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra --env-file deploy/.env.cluster.example --no-observability >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full --env-file deploy/.env.cluster.example --no-observability >"${cluster_full}"

grep -F 'client_max_body_size ${NGINX_CLIENT_MAX_BODY_SIZE};' deploy/nginx/nginx.single.conf
grep -F 'client_max_body_size ${NGINX_CLIENT_MAX_BODY_SIZE};' deploy/nginx/nginx.cluster.conf
grep -F 'NGINX_CLIENT_MAX_BODY_SIZE=10g' deploy/.env.single.example
grep -F 'NGINX_CLIENT_MAX_BODY_SIZE=10g' deploy/.env.cluster.example

grep -E '^  garage:$' "${single_infra}"
grep -F 'dxflrs/garage:v2.2.0' "${single_infra}"
if grep -F -- '--single-node' "${single_infra}"; then
  echo "single garage compose must use the supported v2.2 server command" >&2
  exit 1
fi
grep -F 'garage-init:' "${single_infra}"
grep -F 'layout assign' deploy/scripts/bootstrap-garage.sh
grep -F 'layout apply' deploy/scripts/bootstrap-garage.sh
grep -F 'bucket create' deploy/scripts/bootstrap-garage.sh
grep -F 'key import' deploy/scripts/bootstrap-garage.sh
grep -F 'bucket allow' deploy/scripts/bootstrap-garage.sh
if grep -F 'CMD-SHELL' "${single_infra}" | grep -F 'garage' >/dev/null 2>&1; then
  echo "single garage healthcheck must not require a shell inside the distroless image" >&2
  exit 1
fi
grep -F 'GARAGE_DEFAULT_ACCESS_KEY: GK000000000000000000000001' "${single_infra}"
grep -F 'OSS_OBJECT_STORE_ACCESS_KEY: GK000000000000000000000001' "${single_full}"
grep -F 'region: garage' deploy/nacos/config/community-oss.yaml
grep -E 'GARAGE_REPLICATION_FACTOR: "?1"?' "${single_infra}"
grep -E '^  community-oss:$' "${single_full}"
assert_service_environment_value "${single_full}" community-oss 'METRICS_BASIC_AUTH_USERNAME: prometheus'
assert_service_environment_value "${single_full}" community-oss 'METRICS_BASIC_AUTH_PASSWORD: dev-prometheus-pass'
grep -A4 -E '^      garage-init:$' "${single_full}" | grep -F 'condition: service_completed_successfully'
grep -F 'endpoint: http://garage:3900' deploy/nacos/config/community-oss.yaml
grep -F 'OSS_DB_URL: jdbc:mysql://mysql:3306/community_oss' "${single_full}"
grep -F 'default.conf.template' "${single_full}"
grep -E 'NGINX_CLIENT_MAX_BODY_SIZE: "?10g"?' "${single_full}"
grep -F 'max-file-size: 10GB' deploy/nacos/config/community-oss.yaml
grep -F 'max-request-size: 10GB' deploy/nacos/config/community-oss.yaml

grep -E '^  garage-1:$' "${cluster_infra}"
grep -E '^  garage-2:$' "${cluster_infra}"
grep -E '^  garage-3:$' "${cluster_infra}"
grep -F 'garage-init:' "${cluster_infra}"
grep -F 'GARAGE_DEFAULT_ACCESS_KEY: GK000000000000000000000001' "${cluster_infra}"
grep -F 'OSS_OBJECT_STORE_ACCESS_KEY: GK000000000000000000000001' "${cluster_full}"
grep -F 'region: garage' deploy/nacos/config/community-oss.yaml
grep -E 'GARAGE_REPLICATION_FACTOR: "?3"?' "${cluster_infra}"
grep -E '^  community-oss-1:$' "${cluster_full}"
grep -A4 -E '^      garage-init:$' "${cluster_full}" | grep -F 'condition: service_completed_successfully'
grep -E '^  community-oss-2:$' "${cluster_full}"
grep -E '^  community-oss-3:$' "${cluster_full}"
metrics_environment_failure=0
for service in community-oss-1 community-oss-2 community-oss-3; do
  assert_service_environment_value \
    "${cluster_full}" "${service}" 'METRICS_BASIC_AUTH_USERNAME: prometheus' || metrics_environment_failure=1
  assert_service_environment_value \
    "${cluster_full}" "${service}" 'METRICS_BASIC_AUTH_PASSWORD: dev-prometheus-pass' || metrics_environment_failure=1
done
if [ "${metrics_environment_failure}" -ne 0 ]; then
  exit 1
fi
grep -F 'endpoint: http://garage:3900' deploy/nacos/config/community-oss.yaml
grep -F 'OSS_DB_URL: jdbc:mysql://mysql-primary:3306/community_oss' "${cluster_full}"
grep -F 'default.conf.template' "${cluster_full}"
grep -E 'NGINX_CLIENT_MAX_BODY_SIZE: "?10g"?' "${cluster_full}"
grep -F 'max-file-size: 10GB' deploy/nacos/config/community-oss.yaml
grep -F 'max-request-size: 10GB' deploy/nacos/config/community-oss.yaml

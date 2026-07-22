#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"
unset IM_SESSION_TICKET_HMAC_SECRET IM_SESSION_TICKET_ISSUER IM_SESSION_TICKET_AUDIENCE

help_output="$(./deploy/deployment.sh --help 2>&1)"
printf '%s\n' "${help_output}" | grep -F -- '--topology <single|cluster>'
printf '%s\n' "${help_output}" | grep -F -- 'Custom project names require an independent network topology'
if printf '%s\n' "${help_output}" | grep -F -- '--topology <dev|ha>' >/dev/null 2>&1; then
  echo "old topology help text is still visible" >&2
  exit 1
fi

single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
single_ticket_override_full="$(mktemp)"
cluster_ticket_override_full="$(mktemp)"
single_missing_ticket_env="$(mktemp)"
cluster_missing_ticket_env="$(mktemp)"
single_missing_ticket_err="$(mktemp)"
cluster_missing_ticket_err="$(mktemp)"
single_legacy_env="$(mktemp)"
cluster_legacy_env="$(mktemp)"
single_legacy_full="$(mktemp)"
cluster_legacy_full="$(mktemp)"
custom_single_env="$(mktemp)"
custom_single_full="$(mktemp)"
custom_cluster_env="$(mktemp)"
custom_cluster_full="$(mktemp)"
environment_override_full="$(mktemp)"
custom_project_err="$(mktemp)"
compose_invocation="$(mktemp)"
sentinel="$(mktemp)"
rm -f "${compose_invocation}" "${sentinel}"
fake_bin="$(mktemp -d)"
dev_err="$(mktemp)"
ha_err="$(mktemp)"
trap 'rm -rf "${fake_bin}"; rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}" "${single_ticket_override_full}" "${cluster_ticket_override_full}" "${single_missing_ticket_env}" "${cluster_missing_ticket_env}" "${single_missing_ticket_err}" "${cluster_missing_ticket_err}" "${single_legacy_env}" "${cluster_legacy_env}" "${single_legacy_full}" "${cluster_legacy_full}" "${custom_single_env}" "${custom_single_full}" "${custom_cluster_env}" "${custom_cluster_full}" "${environment_override_full}" "${custom_project_err}" "${compose_invocation}" "${sentinel}" "${dev_err}" "${ha_err}"' EXIT

service_environment_value() {
  local rendered_config="$1"
  local service="$2"
  local variable="$3"
  awk -v service="${service}" -v variable="${variable}" '
    $0 == "  " service ":" {
      in_service = 1
      next
    }
    in_service && /^  [^ ]/ {
      exit
    }
    in_service && $1 == variable ":" {
      gsub(/"/, "", $2)
      print $2
      exit
    }
  ' "${rendered_config}"
}

environment_file_value() {
  local env_file="$1"
  local variable="$2"
  awk -v variable="${variable}" '
    index($0, variable "=") == 1 {
      print substr($0, length(variable) + 2)
      exit
    }
  ' "${env_file}"
}

assert_environment_value_for_services() {
  local rendered_config="$1"
  local variable="$2"
  local expected="$3"
  local expected_source="$4"
  shift 4

  local service
  local actual
  for service in "$@"; do
    actual="$(service_environment_value "${rendered_config}" "${service}" "${variable}")"
    if [[ -z "${actual}" ]]; then
      echo "${service} must receive ${variable}" >&2
      return 1
    fi
  done
  if [[ -z "${expected}" ]]; then
    echo "${expected_source} must define ${variable}" >&2
    return 1
  fi
  for service in "$@"; do
    actual="$(service_environment_value "${rendered_config}" "${service}" "${variable}")"
    if [[ "${actual}" != "${expected}" ]]; then
      echo "${service} must receive ${variable} from ${expected_source}" >&2
      return 1
    fi
  done
}

assert_ticket_runtime_environment() {
  local rendered_config="$1"
  local env_file="$2"
  shift 2

  local variable
  local expected
  for variable in IM_SESSION_TICKET_HMAC_SECRET IM_SESSION_TICKET_ISSUER IM_SESSION_TICKET_AUDIENCE; do
    expected="$(environment_file_value "${env_file}" "${variable}")"
    assert_environment_value_for_services \
      "${rendered_config}" "${variable}" "${expected}" "${env_file}" "$@"
  done
}

assert_ticket_runtime_values() {
  local rendered_config="$1"
  local ticket_secret="$2"
  local ticket_issuer="$3"
  local ticket_audience="$4"
  shift 4

  assert_environment_value_for_services "${rendered_config}" \
    IM_SESSION_TICKET_HMAC_SECRET "${ticket_secret}" "ticket sentinel override" "$@"
  assert_environment_value_for_services "${rendered_config}" \
    IM_SESSION_TICKET_ISSUER "${ticket_issuer}" "ticket sentinel override" "$@"
  assert_environment_value_for_services "${rendered_config}" \
    IM_SESSION_TICKET_AUDIENCE "${ticket_audience}" "ticket sentinel override" "$@"
}

assert_distinct_ticket_secret() {
  local env_file="$1"
  local access_secret
  local ticket_secret
  local ticket_secret_bytes
  local LC_ALL=C

  access_secret="$(environment_file_value "${env_file}" JWT_HMAC_SECRET)"
  ticket_secret="$(environment_file_value "${env_file}" IM_SESSION_TICKET_HMAC_SECRET)"
  if [[ -z "${access_secret}" || -z "${ticket_secret}" ]]; then
    echo "${env_file} must define access and IM session ticket secrets" >&2
    return 1
  fi
  if [[ "${access_secret}" == "${ticket_secret}" ]]; then
    echo "${env_file} must use distinct access and IM session ticket secrets" >&2
    return 1
  fi
  ticket_secret_bytes="${#ticket_secret}"
  if (( ticket_secret_bytes < 32 )); then
    echo "${env_file} IM session ticket secret must be at least 32 UTF-8 bytes" >&2
    return 1
  fi
}

without_ticket_secret() {
  awk '!/^IM_SESSION_TICKET_HMAC_SECRET=/' "$1"
}

without_topology_values() {
  awk '!/^(COMMUNITY_NETWORK_SUBNET|COMMUNITY_NETWORK_DYNAMIC_RANGE|NGINX_STATIC_IP|COMMUNITY_GATEWAY_STATIC_IP|COMMUNITY_GATEWAY_[123]_STATIC_IP|GATEWAY_TRUSTED_PROXY_CIDRS|COMMUNITY_APP_TRUSTED_PROXY_CIDRS)=/' "$1"
}

with_custom_single_topology() {
  awk '
    /^COMMUNITY_VOLUME_NAMESPACE=/ { print "COMMUNITY_VOLUME_NAMESPACE=community_single_smoke"; next }
    /^COMMUNITY_NETWORK_SUBNET=/ { print "COMMUNITY_NETWORK_SUBNET=172.40.0.0/24"; next }
    /^COMMUNITY_NETWORK_DYNAMIC_RANGE=/ { print "COMMUNITY_NETWORK_DYNAMIC_RANGE=172.40.0.128/25"; next }
    /^NGINX_STATIC_IP=/ { print "NGINX_STATIC_IP=172.40.0.10"; next }
    /^COMMUNITY_GATEWAY_STATIC_IP=/ { print "COMMUNITY_GATEWAY_STATIC_IP=172.40.0.20"; next }
    /^GATEWAY_TRUSTED_PROXY_CIDRS=/ { print "GATEWAY_TRUSTED_PROXY_CIDRS=172.40.0.10/32"; next }
    /^COMMUNITY_APP_TRUSTED_PROXY_CIDRS=/ { print "COMMUNITY_APP_TRUSTED_PROXY_CIDRS=172.40.0.20/32"; next }
    { print }
  ' "$1"
}

with_custom_cluster_topology() {
  awk '
    /^COMMUNITY_VOLUME_NAMESPACE=/ { print "COMMUNITY_VOLUME_NAMESPACE=community_cluster_smoke"; next }
    /^COMMUNITY_NETWORK_SUBNET=/ { print "COMMUNITY_NETWORK_SUBNET=172.43.0.0/24"; next }
    /^COMMUNITY_NETWORK_DYNAMIC_RANGE=/ { print "COMMUNITY_NETWORK_DYNAMIC_RANGE=172.43.0.128/25"; next }
    /^NGINX_STATIC_IP=/ { print "NGINX_STATIC_IP=172.43.0.10"; next }
    /^COMMUNITY_GATEWAY_1_STATIC_IP=/ { print "COMMUNITY_GATEWAY_1_STATIC_IP=172.43.0.20"; next }
    /^COMMUNITY_GATEWAY_2_STATIC_IP=/ { print "COMMUNITY_GATEWAY_2_STATIC_IP=172.43.0.21"; next }
    /^COMMUNITY_GATEWAY_3_STATIC_IP=/ { print "COMMUNITY_GATEWAY_3_STATIC_IP=172.43.0.22"; next }
    /^GATEWAY_TRUSTED_PROXY_CIDRS=/ { print "GATEWAY_TRUSTED_PROXY_CIDRS=172.43.0.10/32"; next }
    /^COMMUNITY_APP_TRUSTED_PROXY_CIDRS=/ { print "COMMUNITY_APP_TRUSTED_PROXY_CIDRS=172.43.0.20/32,172.43.0.21/32,172.43.0.22/32"; next }
    { print }
  ' "$1"
}

rendered_network_value() {
  local rendered_config="$1"
  local variable="$2"
  awk -v variable="${variable}" '
    $1 == variable ":" { print $2; exit }
    $1 == "-" && $2 == variable ":" { print $3; exit }
  ' "${rendered_config}"
}

rendered_service_ipv4_address() {
  local rendered_config="$1"
  local service="$2"
  awk -v service="${service}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service && $1 == "ipv4_address:" { print $2; exit }
  ' "${rendered_config}"
}

./deploy/deployment.sh config --topology single --scope infra --env-file deploy/.env.single.example >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full --env-file deploy/.env.single.example >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra --env-file deploy/.env.cluster.example >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full --env-file deploy/.env.cluster.example >"${cluster_full}"

assert_ticket_runtime_environment "${single_full}" deploy/.env.single.example \
  community-im-gateway im-realtime
assert_ticket_runtime_environment "${cluster_full}" deploy/.env.cluster.example \
  community-im-gateway-1 community-im-gateway-2 community-im-gateway-3 \
  im-realtime-1 im-realtime-2 im-realtime-3
assert_distinct_ticket_secret deploy/.env.single.example
assert_distinct_ticket_secret deploy/.env.cluster.example

ticket_sentinel_secret="topology-test-im-session-ticket-secret-override-20260722"
ticket_sentinel_issuer="topology-test-im-session-ticket-issuer"
ticket_sentinel_audience="topology-test-im-session-ticket-audience"
IM_SESSION_TICKET_HMAC_SECRET="${ticket_sentinel_secret}" \
IM_SESSION_TICKET_ISSUER="${ticket_sentinel_issuer}" \
IM_SESSION_TICKET_AUDIENCE="${ticket_sentinel_audience}" \
  ./deploy/deployment.sh config --topology single --scope full \
    --env-file deploy/.env.single.example >"${single_ticket_override_full}"
IM_SESSION_TICKET_HMAC_SECRET="${ticket_sentinel_secret}" \
IM_SESSION_TICKET_ISSUER="${ticket_sentinel_issuer}" \
IM_SESSION_TICKET_AUDIENCE="${ticket_sentinel_audience}" \
  ./deploy/deployment.sh config --topology cluster --scope full \
    --env-file deploy/.env.cluster.example >"${cluster_ticket_override_full}"
assert_ticket_runtime_values "${single_ticket_override_full}" \
  "${ticket_sentinel_secret}" "${ticket_sentinel_issuer}" "${ticket_sentinel_audience}" \
  community-im-gateway im-realtime
assert_ticket_runtime_values "${cluster_ticket_override_full}" \
  "${ticket_sentinel_secret}" "${ticket_sentinel_issuer}" "${ticket_sentinel_audience}" \
  community-im-gateway-1 community-im-gateway-2 community-im-gateway-3 \
  im-realtime-1 im-realtime-2 im-realtime-3

without_ticket_secret deploy/.env.single.example >"${single_missing_ticket_env}"
without_ticket_secret deploy/.env.cluster.example >"${cluster_missing_ticket_env}"
if env -u IM_SESSION_TICKET_HMAC_SECRET \
  -u IM_SESSION_TICKET_ISSUER \
  -u IM_SESSION_TICKET_AUDIENCE \
  ./deploy/deployment.sh config --topology single --scope full \
    --env-file "${single_missing_ticket_env}" >/dev/null 2>"${single_missing_ticket_err}"; then
  echo "expected single topology without an IM session ticket secret to fail" >&2
  exit 1
fi
grep -F 'IM_SESSION_TICKET_HMAC_SECRET is required' "${single_missing_ticket_err}" >/dev/null
if env -u IM_SESSION_TICKET_HMAC_SECRET \
  -u IM_SESSION_TICKET_ISSUER \
  -u IM_SESSION_TICKET_AUDIENCE \
  ./deploy/deployment.sh config --topology cluster --scope full \
    --env-file "${cluster_missing_ticket_env}" >/dev/null 2>"${cluster_missing_ticket_err}"; then
  echo "expected cluster topology without an IM session ticket secret to fail" >&2
  exit 1
fi
grep -F 'IM_SESSION_TICKET_HMAC_SECRET is required' "${cluster_missing_ticket_err}" >/dev/null

without_topology_values deploy/.env.single.example >"${single_legacy_env}"
without_topology_values deploy/.env.cluster.example >"${cluster_legacy_env}"
printf '%s\n' "DEPLOYMENT_TEST_SENTINEL=\$(touch ${sentinel})" >>"${single_legacy_env}"
./deploy/deployment.sh config --topology single --scope full --env-file "${single_legacy_env}" >"${single_legacy_full}"
./deploy/deployment.sh config --topology cluster --scope full --env-file "${cluster_legacy_env}" >"${cluster_legacy_full}"
test ! -e "${sentinel}"
test "$(rendered_network_value "${single_legacy_full}" subnet)" = "172.30.0.0/24"
test "$(rendered_network_value "${single_legacy_full}" ip_range)" = "172.30.0.128/25"
test "$(rendered_service_ipv4_address "${single_legacy_full}" nginx)" = "172.30.0.10"
test "$(rendered_service_ipv4_address "${single_legacy_full}" community-gateway)" = "172.30.0.20"
test "$(service_environment_value "${single_legacy_full}" community-gateway GATEWAY_TRUSTED_PROXY_CIDRS)" = "172.30.0.10/32"
test "$(service_environment_value "${single_legacy_full}" community-app COMMUNITY_APP_TRUSTED_PROXY_CIDRS)" = "172.30.0.20/32"
test "$(rendered_network_value "${cluster_legacy_full}" subnet)" = "172.31.0.0/24"
test "$(rendered_network_value "${cluster_legacy_full}" ip_range)" = "172.31.0.128/25"
test "$(rendered_service_ipv4_address "${cluster_legacy_full}" nginx)" = "172.31.0.10"
for gateway_number in 1 2 3; do
  test "$(rendered_service_ipv4_address "${cluster_legacy_full}" "community-gateway-${gateway_number}")" = "172.31.0.$((gateway_number + 19))"
done

cat >"${fake_bin}/docker" <<EOF
#!/usr/bin/env bash
touch "${compose_invocation}"
exit 99
EOF
chmod +x "${fake_bin}/docker"
if PATH="${fake_bin}:${PATH}" ./deploy/deployment.sh config --topology single --scope full \
  --env-file deploy/.env.single.example -p community-single-smoke \
  >/dev/null 2>"${custom_project_err}"; then
  echo "expected a custom project with the default topology to fail" >&2
  exit 1
fi
test ! -e "${compose_invocation}"
grep -F 'custom project' "${custom_project_err}"
grep -F 'independent topology' "${custom_project_err}"

with_custom_single_topology deploy/.env.single.example >"${custom_single_env}"
cat >>"${custom_single_env}" <<'EOF'
COMMUNITY_VOLUME_NAMESPACE=community_single_last
COMMUNITY_NETWORK_SUBNET=172.42.0.0/24
COMMUNITY_NETWORK_DYNAMIC_RANGE=172.42.0.128/25
NGINX_STATIC_IP=172.42.0.10
COMMUNITY_GATEWAY_STATIC_IP=172.42.0.20
GATEWAY_TRUSTED_PROXY_CIDRS=172.42.0.10/32
COMMUNITY_APP_TRUSTED_PROXY_CIDRS=172.42.0.20/32
EOF
./deploy/deployment.sh config --topology single --scope full --env-file "${custom_single_env}" \
  -p community-single-smoke >"${custom_single_full}"
grep -F 'name: community-single-smoke' "${custom_single_full}"
test "$(rendered_network_value "${custom_single_full}" subnet)" = "172.42.0.0/24"
test "$(rendered_network_value "${custom_single_full}" ip_range)" = "172.42.0.128/25"
test "$(rendered_service_ipv4_address "${custom_single_full}" nginx)" = "172.42.0.10"
test "$(rendered_service_ipv4_address "${custom_single_full}" community-gateway)" = "172.42.0.20"
test "$(service_environment_value "${custom_single_full}" community-gateway GATEWAY_TRUSTED_PROXY_CIDRS)" = "172.42.0.10/32"
test "$(service_environment_value "${custom_single_full}" community-app COMMUNITY_APP_TRUSTED_PROXY_CIDRS)" = "172.42.0.20/32"
grep -F 'name: community_single_last_mysql_primary_data' "${custom_single_full}"

with_custom_cluster_topology deploy/.env.cluster.example >"${custom_cluster_env}"
./deploy/deployment.sh config --topology cluster --scope full --env-file "${custom_cluster_env}" \
  -p community-cluster-smoke >"${custom_cluster_full}"
grep -F 'name: community-cluster-smoke' "${custom_cluster_full}"
test "$(rendered_network_value "${custom_cluster_full}" subnet)" = "172.43.0.0/24"
test "$(rendered_network_value "${custom_cluster_full}" ip_range)" = "172.43.0.128/25"
test "$(rendered_service_ipv4_address "${custom_cluster_full}" nginx)" = "172.43.0.10"
for gateway_number in 1 2 3; do
  test "$(rendered_service_ipv4_address "${custom_cluster_full}" "community-gateway-${gateway_number}")" = "172.43.0.$((gateway_number + 19))"
done
test "$(service_environment_value "${custom_cluster_full}" community-gateway-1 GATEWAY_TRUSTED_PROXY_CIDRS)" = "172.43.0.10/32"
test "$(service_environment_value "${custom_cluster_full}" community-app-1 COMMUNITY_APP_TRUSTED_PROXY_CIDRS)" = "172.43.0.20/32,172.43.0.21/32,172.43.0.22/32"
grep -F 'name: community_cluster_smoke_mysql_primary_data' "${custom_cluster_full}"

COMMUNITY_VOLUME_NAMESPACE=community_single_environment \
COMMUNITY_NETWORK_SUBNET=172.41.0.0/24 \
COMMUNITY_NETWORK_DYNAMIC_RANGE=172.41.0.128/25 \
NGINX_STATIC_IP=172.41.0.10 \
COMMUNITY_GATEWAY_STATIC_IP=172.41.0.20 \
GATEWAY_TRUSTED_PROXY_CIDRS=172.41.0.10/32 \
COMMUNITY_APP_TRUSTED_PROXY_CIDRS=172.41.0.20/32 \
  ./deploy/deployment.sh config --topology single --scope full \
    --env-file deploy/.env.single.example -p community-single-environment \
    >"${environment_override_full}"
test "$(rendered_network_value "${environment_override_full}" subnet)" = "172.41.0.0/24"
test "$(rendered_service_ipv4_address "${environment_override_full}" nginx)" = "172.41.0.10"
test "$(rendered_service_ipv4_address "${environment_override_full}" community-gateway)" = "172.41.0.20"
test "$(service_environment_value "${environment_override_full}" community-gateway GATEWAY_TRUSTED_PROXY_CIDRS)" = "172.41.0.10/32"
test "$(service_environment_value "${environment_override_full}" community-app COMMUNITY_APP_TRUSTED_PROXY_CIDRS)" = "172.41.0.20/32"
grep -F 'name: community_single_environment_mysql_primary_data' "${environment_override_full}"

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
single_worker_slot="$(service_environment_value "${single_full}" im-realtime IM_ROOM_FANOUT_WORKER_INBOX_SLOT)"
test "${single_worker_slot}" = "0"

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
declare -A seen_worker_slots=()
for worker in 1 2 3; do
  grep -A80 -E "^  im-realtime-${worker}:$" "${cluster_full}" | grep -F 'SPRING_DATA_REDIS_CLUSTER_NODES: redis-1:6379,redis-2:6379,redis-3:6379,redis-4:6379,redis-5:6379,redis-6:6379'
  worker_slot="$(service_environment_value "${cluster_full}" "im-realtime-${worker}" IM_ROOM_FANOUT_WORKER_INBOX_SLOT)"
  expected_slot="$((worker - 1))"
  test "${worker_slot}" = "${expected_slot}"
  if [[ -n "${seen_worker_slots[${worker_slot}]:-}" ]]; then
    echo "cluster im-realtime worker inbox slots must be unique" >&2
    exit 1
  fi
  seen_worker_slots["${worker_slot}"]=1
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

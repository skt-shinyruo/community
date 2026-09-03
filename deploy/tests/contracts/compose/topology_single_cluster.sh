#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${REPO_ROOT}"
unset JWT_ACCESS_PUBLIC_KEY JWT_ACCESS_PRIVATE_KEY JWT_SERVICE_HMAC_SECRET
unset AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET AUTH_PASSWORD_RESET_QUOTA_HMAC_SECRET
unset IM_SESSION_TICKET_HMAC_SECRET IM_SESSION_TICKET_ISSUER IM_SESSION_TICKET_AUDIENCE

help_output="$(./deploy/deployment.sh --help 2>&1)"
printf '%s\n' "${help_output}" | grep -F -- '--stack <infra|single|cluster>'
printf '%s\n' "${help_output}" | grep -F -- 'Custom project names require an independent network topology'
for legacy_option in --topology --scope --host-access; do
  if printf '%s\n' "${help_output}" | grep -F -- "${legacy_option}" >/dev/null 2>&1; then
    echo "legacy deployment option is still visible: ${legacy_option}" >&2
    exit 1
  fi
done

single_full="$(mktemp)"
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
legacy_option_err="$(mktemp)"
trap 'rm -rf "${fake_bin}"; rm -f "${single_full}" "${cluster_full}" "${single_ticket_override_full}" "${cluster_ticket_override_full}" "${single_missing_ticket_env}" "${cluster_missing_ticket_env}" "${single_missing_ticket_err}" "${cluster_missing_ticket_err}" "${single_legacy_env}" "${cluster_legacy_env}" "${single_legacy_full}" "${cluster_legacy_full}" "${custom_single_env}" "${custom_single_full}" "${custom_cluster_env}" "${custom_cluster_full}" "${environment_override_full}" "${custom_project_err}" "${compose_invocation}" "${sentinel}" "${legacy_option_err}"' EXIT

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

service_dependency_condition() {
  local rendered_config="$1"
  local service="$2"
  local dependency="$3"
  awk -v service="${service}" -v dependency="${dependency}" '
    $0 == "  " service ":" {
      in_service = 1
      next
    }
    in_service && /^  [^ ]/ {
      exit
    }
    in_service && $0 == "    depends_on:" {
      in_dependencies = 1
      next
    }
    in_dependencies && $0 == "      " dependency ":" {
      in_dependency = 1
      next
    }
    in_dependency && $1 == "condition:" {
      print $2
      exit
    }
    in_dependency && /^      [^ ]/ {
      exit
    }
  ' "${rendered_config}"
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

assert_environment_absent_for_services() {
  local rendered_config="$1"
  local variable="$2"
  shift 2

  local service
  local actual
  for service in "$@"; do
    actual="$(service_environment_value "${rendered_config}" "${service}" "${variable}")"
    if [[ -n "${actual}" ]]; then
      echo "${service} must not receive ${variable}" >&2
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

assert_distinct_secret_group() {
  local env_file="$1"
  local label="$2"
  shift 2
  local LC_ALL=C
  local key value other_key other_value
  local -a keys=("$@")
  local -A values=()

  for key in "${keys[@]}"; do
    value="$(environment_file_value "${env_file}" "${key}")"
    if [[ -z "${value}" ]]; then
      echo "${env_file} must define ${label}: ${key}" >&2
      return 1
    fi
    if (( ${#value} < 32 )); then
      echo "${env_file} ${label} secrets must be at least 32 UTF-8 bytes" >&2
      return 1
    fi
    values["${key}"]="${value}"
  done
  for key in "${keys[@]}"; do
    for other_key in "${keys[@]}"; do
      [[ "${key}" < "${other_key}" ]] || continue
      value="${values[${key}]}"
      other_value="${values[${other_key}]}"
      if [[ "${value}" == "${other_value}" ]]; then
        echo "${env_file} ${label} secrets must be distinct" >&2
        return 1
      fi
    done
  done
}

assert_required_environment_values() {
  local stack="$1"
  local source_env_file="$2"
  shift 2

  local variable missing_env_file error_file
  for variable in "$@"; do
    missing_env_file="$(mktemp)"
    error_file="$(mktemp)"
    awk -v variable="${variable}" 'index($0, variable "=") != 1' \
      "${source_env_file}" >"${missing_env_file}"
    if env -u "${variable}" \
      ./deploy/deployment.sh config --stack "${stack}" \
        --env-file "${missing_env_file}" >/dev/null 2>"${error_file}"; then
      rm -f "${missing_env_file}" "${error_file}"
      echo "expected ${stack} stack without ${variable} to fail" >&2
      return 1
    fi
    grep -F "${variable} is required" "${error_file}" >/dev/null
    rm -f "${missing_env_file}" "${error_file}"
  done
}

assert_nacos_auth_environment() {
  local rendered_config="$1"
  local env_file="$2"
  shift 2

  local variable
  local expected
  for variable in NACOS_AUTH_TOKEN NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE; do
    expected="$(environment_file_value "${env_file}" "${variable}")"
    assert_environment_value_for_services \
      "${rendered_config}" "${variable}" "${expected}" "${env_file}" "$@"
  done
}

assert_community_app_runtime_environment() {
  local rendered_config="$1"
  local env_file="$2"
  shift 2

  local variable
  local expected
  local variables=(
    AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET
    AUTH_PASSWORD_RESET_QUOTA_HMAC_SECRET
    AUTH_PASSWORD_RESET_TTL_SECONDS
    AUTH_PASSWORD_RESET_REQUEST_WINDOW_SECONDS
    AUTH_PASSWORD_RESET_MAX_REQUESTS_PER_EMAIL
    AUTH_PASSWORD_RESET_MAX_REQUESTS_PER_IP
    AUTH_REGISTRATION_DRAFT_TTL_SECONDS
    AUTH_REGISTRATION_REQUEST_WINDOW_SECONDS
    AUTH_REGISTRATION_MAX_REQUESTS_PER_USERNAME
    AUTH_REGISTRATION_MAX_REQUESTS_PER_EMAIL
    AUTH_REGISTRATION_MAX_REQUESTS_PER_IP
    AUTH_REGISTRATION_RESEND_WINDOW_SECONDS
    AUTH_REGISTRATION_RESEND_MAX_REQUESTS_PER_REGISTRATION
    AUTH_REGISTRATION_RESEND_MAX_REQUESTS_PER_EMAIL
    AUTH_REGISTRATION_RESEND_MAX_REQUESTS_PER_IP
    MARKET_ORDER_AUTO_CONFIRM_BATCH_SIZE
    SEARCH_REINDEX_PAGE_SIZE
    SEARCH_REINDEX_LOCK_TTL
    SEARCH_INDEX_KEEP_HISTORY
    WALLET_TEST_CREDITS_ENABLED
    WALLET_TEST_CREDIT_GRANT_ENABLED
    WALLET_TEST_CREDIT_DISCARD_ENABLED
    WALLET_TEST_CREDIT_MAX_GRANT_PER_REQUEST
    WALLET_TEST_CREDIT_MAX_DISCARD_PER_REQUEST
    WALLET_TEST_CREDIT_GRANT_QUOTA_PER_USER
    WALLET_TEST_CREDIT_DISCARD_QUOTA_PER_USER
  )

  for variable in "${variables[@]}"; do
    expected="$(environment_file_value "${env_file}" "${variable}")"
    assert_environment_value_for_services \
      "${rendered_config}" "${variable}" "${expected}" "${env_file}" "$@"
  done
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
    /^NACOS_HOST_PORT=/ { print "NACOS_HOST_PORT=48848"; next }
    /^MAILHOG_UI_HOST_PORT=/ { print "MAILHOG_UI_HOST_PORT=48025"; next }
    /^FRONTEND_HOST_PORT=/ { print "FRONTEND_HOST_PORT=42881"; next }
    /^NGINX_API_PORT=/ { print "NGINX_API_PORT=42880"; next }
    /^ELASTICSEARCH_PORT=/ { print "ELASTICSEARCH_PORT=42888"; next }
    /^KIBANA_PORT=/ { print "KIBANA_PORT=42889"; next }
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
    /^NACOS_HOST_PORT=/ { print "NACOS_HOST_PORT=58848"; next }
    /^MAILHOG_UI_HOST_PORT=/ { print "MAILHOG_UI_HOST_PORT=58025"; next }
    /^FRONTEND_HOST_PORT=/ { print "FRONTEND_HOST_PORT=53881"; next }
    /^NGINX_API_PORT=/ { print "NGINX_API_PORT=53880"; next }
    /^GARAGE_S3_HOST_PORT=/ { print "GARAGE_S3_HOST_PORT=53900"; next }
    /^GARAGE_ADMIN_HOST_PORT=/ { print "GARAGE_ADMIN_HOST_PORT=53903"; next }
    /^ELASTICSEARCH_PORT=/ { print "ELASTICSEARCH_PORT=53888"; next }
    /^KIBANA_PORT=/ { print "KIBANA_PORT=53889"; next }
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

./deploy/deployment.sh config --stack single --env-file deploy/stacks/single/.env.example >"${single_full}"
./deploy/deployment.sh config --stack cluster --env-file deploy/stacks/cluster/.env.example >"${cluster_full}"

single_access_public_key="$(environment_file_value deploy/stacks/single/.env.example JWT_ACCESS_PUBLIC_KEY)"
single_access_private_key="$(environment_file_value deploy/stacks/single/.env.example JWT_ACCESS_PRIVATE_KEY)"
single_service_secret="$(environment_file_value deploy/stacks/single/.env.example JWT_SERVICE_HMAC_SECRET)"
single_password_reset_identifier_secret="$(environment_file_value deploy/stacks/single/.env.example AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET)"
assert_environment_value_for_services "${single_full}" JWT_ACCESS_PUBLIC_KEY \
  "${single_access_public_key}" deploy/stacks/single/.env.example \
  community-app community-oss community-gateway community-im-gateway im-core im-realtime
assert_environment_value_for_services "${single_full}" JWT_ACCESS_PRIVATE_KEY \
  "${single_access_private_key}" deploy/stacks/single/.env.example community-app
assert_environment_absent_for_services "${single_full}" JWT_ACCESS_PRIVATE_KEY \
  community-oss community-gateway community-im-gateway im-core im-realtime
assert_environment_value_for_services "${single_full}" JWT_SERVICE_HMAC_SECRET \
  "${single_service_secret}" deploy/stacks/single/.env.example \
  community-app community-oss im-core im-realtime
assert_environment_absent_for_services "${single_full}" JWT_SERVICE_HMAC_SECRET \
  community-gateway community-im-gateway
assert_environment_value_for_services "${single_full}" AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET \
  "${single_password_reset_identifier_secret}" deploy/stacks/single/.env.example community-app
test "$(grep -Fc 'AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET:' "${single_full}")" -eq 1

cluster_access_public_key="$(environment_file_value deploy/stacks/cluster/.env.example JWT_ACCESS_PUBLIC_KEY)"
cluster_access_private_key="$(environment_file_value deploy/stacks/cluster/.env.example JWT_ACCESS_PRIVATE_KEY)"
cluster_service_secret="$(environment_file_value deploy/stacks/cluster/.env.example JWT_SERVICE_HMAC_SECRET)"
cluster_password_reset_identifier_secret="$(environment_file_value deploy/stacks/cluster/.env.example AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET)"
assert_environment_value_for_services "${cluster_full}" JWT_ACCESS_PUBLIC_KEY \
  "${cluster_access_public_key}" deploy/stacks/cluster/.env.example \
  community-app-1 community-app-2 community-app-3 \
  community-oss-1 community-oss-2 community-oss-3 \
  community-gateway-1 community-gateway-2 community-gateway-3 \
  community-im-gateway-1 community-im-gateway-2 community-im-gateway-3 \
  im-core-1 im-core-2 im-core-3 im-realtime-1 im-realtime-2 im-realtime-3
assert_environment_value_for_services "${cluster_full}" JWT_ACCESS_PRIVATE_KEY \
  "${cluster_access_private_key}" deploy/stacks/cluster/.env.example \
  community-app-1 community-app-2 community-app-3
assert_environment_absent_for_services "${cluster_full}" JWT_ACCESS_PRIVATE_KEY \
  community-oss-1 community-oss-2 community-oss-3 \
  community-gateway-1 community-gateway-2 community-gateway-3 \
  community-im-gateway-1 community-im-gateway-2 community-im-gateway-3 \
  im-core-1 im-core-2 im-core-3 im-realtime-1 im-realtime-2 im-realtime-3
assert_environment_value_for_services "${cluster_full}" JWT_SERVICE_HMAC_SECRET \
  "${cluster_service_secret}" deploy/stacks/cluster/.env.example \
  community-app-1 community-app-2 community-app-3 \
  community-oss-1 community-oss-2 community-oss-3 \
  im-core-1 im-core-2 im-core-3 im-realtime-1 im-realtime-2 im-realtime-3
assert_environment_absent_for_services "${cluster_full}" JWT_SERVICE_HMAC_SECRET \
  community-gateway-1 community-gateway-2 community-gateway-3 \
  community-im-gateway-1 community-im-gateway-2 community-im-gateway-3
assert_environment_value_for_services "${cluster_full}" AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET \
  "${cluster_password_reset_identifier_secret}" deploy/stacks/cluster/.env.example \
  community-app-1 community-app-2 community-app-3
if grep -E '(^|[^A-Z0-9_])JWT_HMAC_SECRET([^A-Z0-9_]|$)' \
  "${single_full}" "${cluster_full}" >/dev/null; then
  echo "rendered service topologies must not expose the retired JWT_HMAC_SECRET" >&2
  exit 1
fi

assert_ticket_runtime_environment "${single_full}" deploy/stacks/single/.env.example \
  community-im-gateway im-realtime
assert_ticket_runtime_environment "${cluster_full}" deploy/stacks/cluster/.env.example \
  community-im-gateway-1 community-im-gateway-2 community-im-gateway-3 \
  im-realtime-1 im-realtime-2 im-realtime-3
for env_file in deploy/stacks/single/.env.example deploy/stacks/cluster/.env.example; do
  assert_distinct_secret_group "${env_file}" "IM session ticket" \
    JWT_SERVICE_HMAC_SECRET IM_SESSION_TICKET_HMAC_SECRET
  assert_distinct_secret_group "${env_file}" "password-reset identifier" \
    JWT_SERVICE_HMAC_SECRET AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET
  assert_distinct_secret_group "${env_file}" "password-reset quota" \
    JWT_SERVICE_HMAC_SECRET AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET \
    AUTH_PASSWORD_RESET_QUOTA_HMAC_SECRET
done
assert_nacos_auth_environment "${single_full}" deploy/stacks/single/.env.example nacos
assert_nacos_auth_environment "${cluster_full}" deploy/stacks/cluster/.env.example nacos-1 nacos-2 nacos-3
assert_community_app_runtime_environment "${single_full}" deploy/stacks/single/.env.example community-app
assert_community_app_runtime_environment "${cluster_full}" deploy/stacks/cluster/.env.example \
  community-app-1 community-app-2 community-app-3
assert_environment_value_for_services "${single_full}" KAFKA_BOOTSTRAP_SERVERS \
  "$(environment_file_value deploy/stacks/single/.env.example KAFKA_BOOTSTRAP_SERVERS)" \
  deploy/stacks/single/.env.example community-app im-core im-realtime
assert_environment_value_for_services "${cluster_full}" KAFKA_BOOTSTRAP_SERVERS \
  "$(environment_file_value deploy/stacks/cluster/.env.example KAFKA_BOOTSTRAP_SERVERS)" \
  deploy/stacks/cluster/.env.example \
  community-app-1 community-app-2 community-app-3 \
  im-core-1 im-core-2 im-core-3 im-realtime-1 im-realtime-2 im-realtime-3
test "$(service_dependency_condition "${single_full}" community-app kafka-init)" = \
  "service_completed_successfully"
for app_number in 1 2 3; do
  test "$(service_dependency_condition "${cluster_full}" "community-app-${app_number}" kafka-init)" = \
    "service_completed_successfully"
done
for variable in WALLET_TEST_CREDITS_ENABLED WALLET_TEST_CREDIT_GRANT_ENABLED WALLET_TEST_CREDIT_DISCARD_ENABLED; do
  test "$(environment_file_value deploy/stacks/single/.env.example "${variable}")" = "true"
  test "$(environment_file_value deploy/stacks/cluster/.env.example "${variable}")" = "false"
done
for stack in single cluster; do
  assert_required_environment_values "${stack}" "deploy/stacks/${stack}/.env.example" \
    NACOS_AUTH_TOKEN NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE
  assert_required_environment_values "${stack}" "deploy/stacks/${stack}/.env.example" \
    JWT_ACCESS_PUBLIC_KEY JWT_ACCESS_PRIVATE_KEY JWT_SERVICE_HMAC_SECRET
  assert_required_environment_values "${stack}" "deploy/stacks/${stack}/.env.example" \
    AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET AUTH_PASSWORD_RESET_QUOTA_HMAC_SECRET
done

ticket_sentinel_secret="topology-test-im-session-ticket-secret-override-20260722"
ticket_sentinel_issuer="topology-test-im-session-ticket-issuer"
ticket_sentinel_audience="topology-test-im-session-ticket-audience"
IM_SESSION_TICKET_HMAC_SECRET="${ticket_sentinel_secret}" \
IM_SESSION_TICKET_ISSUER="${ticket_sentinel_issuer}" \
IM_SESSION_TICKET_AUDIENCE="${ticket_sentinel_audience}" \
  ./deploy/deployment.sh config --stack single \
    --env-file deploy/stacks/single/.env.example >"${single_ticket_override_full}"
IM_SESSION_TICKET_HMAC_SECRET="${ticket_sentinel_secret}" \
IM_SESSION_TICKET_ISSUER="${ticket_sentinel_issuer}" \
IM_SESSION_TICKET_AUDIENCE="${ticket_sentinel_audience}" \
  ./deploy/deployment.sh config --stack cluster \
    --env-file deploy/stacks/cluster/.env.example >"${cluster_ticket_override_full}"
assert_ticket_runtime_values "${single_ticket_override_full}" \
  "${ticket_sentinel_secret}" "${ticket_sentinel_issuer}" "${ticket_sentinel_audience}" \
  community-im-gateway im-realtime
assert_ticket_runtime_values "${cluster_ticket_override_full}" \
  "${ticket_sentinel_secret}" "${ticket_sentinel_issuer}" "${ticket_sentinel_audience}" \
  community-im-gateway-1 community-im-gateway-2 community-im-gateway-3 \
  im-realtime-1 im-realtime-2 im-realtime-3

without_ticket_secret deploy/stacks/single/.env.example >"${single_missing_ticket_env}"
without_ticket_secret deploy/stacks/cluster/.env.example >"${cluster_missing_ticket_env}"
if env -u IM_SESSION_TICKET_HMAC_SECRET \
  -u IM_SESSION_TICKET_ISSUER \
  -u IM_SESSION_TICKET_AUDIENCE \
  ./deploy/deployment.sh config --stack single \
    --env-file "${single_missing_ticket_env}" >/dev/null 2>"${single_missing_ticket_err}"; then
  echo "expected single topology without an IM session ticket secret to fail" >&2
  exit 1
fi
grep -F 'IM_SESSION_TICKET_HMAC_SECRET is required' "${single_missing_ticket_err}" >/dev/null
if env -u IM_SESSION_TICKET_HMAC_SECRET \
  -u IM_SESSION_TICKET_ISSUER \
  -u IM_SESSION_TICKET_AUDIENCE \
  ./deploy/deployment.sh config --stack cluster \
    --env-file "${cluster_missing_ticket_env}" >/dev/null 2>"${cluster_missing_ticket_err}"; then
  echo "expected cluster topology without an IM session ticket secret to fail" >&2
  exit 1
fi
grep -F 'IM_SESSION_TICKET_HMAC_SECRET is required' "${cluster_missing_ticket_err}" >/dev/null

without_topology_values deploy/stacks/single/.env.example >"${single_legacy_env}"
without_topology_values deploy/stacks/cluster/.env.example >"${cluster_legacy_env}"
printf '%s\n' "DEPLOYMENT_TEST_SENTINEL=\$(touch ${sentinel})" >>"${single_legacy_env}"
./deploy/deployment.sh config --stack single --env-file "${single_legacy_env}" >"${single_legacy_full}"
./deploy/deployment.sh config --stack cluster --env-file "${cluster_legacy_env}" >"${cluster_legacy_full}"
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
if PATH="${fake_bin}:${PATH}" ./deploy/deployment.sh config --stack single \
  --env-file deploy/stacks/single/.env.example -p community-single-smoke \
  >/dev/null 2>"${custom_project_err}"; then
  echo "expected a custom project with the default topology to fail" >&2
  exit 1
fi
test ! -e "${compose_invocation}"
grep -F 'custom project' "${custom_project_err}"
grep -F 'independent topology' "${custom_project_err}"

with_custom_single_topology deploy/stacks/single/.env.example >"${custom_single_env}"
cat >>"${custom_single_env}" <<'EOF'
COMMUNITY_VOLUME_NAMESPACE=community_single_last
COMMUNITY_NETWORK_SUBNET=172.42.0.0/24
COMMUNITY_NETWORK_DYNAMIC_RANGE=172.42.0.128/25
NGINX_STATIC_IP=172.42.0.10
COMMUNITY_GATEWAY_STATIC_IP=172.42.0.20
GATEWAY_TRUSTED_PROXY_CIDRS=172.42.0.10/32
COMMUNITY_APP_TRUSTED_PROXY_CIDRS=172.42.0.20/32
EOF
./deploy/deployment.sh config --stack single --env-file "${custom_single_env}" \
  -p community-single-smoke >"${custom_single_full}"
grep -F 'name: community-single-smoke' "${custom_single_full}"
test "$(rendered_network_value "${custom_single_full}" subnet)" = "172.42.0.0/24"
test "$(rendered_network_value "${custom_single_full}" ip_range)" = "172.42.0.128/25"
test "$(rendered_service_ipv4_address "${custom_single_full}" nginx)" = "172.42.0.10"
test "$(rendered_service_ipv4_address "${custom_single_full}" community-gateway)" = "172.42.0.20"
test "$(service_environment_value "${custom_single_full}" community-gateway GATEWAY_TRUSTED_PROXY_CIDRS)" = "172.42.0.10/32"
test "$(service_environment_value "${custom_single_full}" community-app COMMUNITY_APP_TRUSTED_PROXY_CIDRS)" = "172.42.0.20/32"
grep -F 'name: community_single_last_mysql_primary_data' "${custom_single_full}"

with_custom_cluster_topology deploy/stacks/cluster/.env.example >"${custom_cluster_env}"
./deploy/deployment.sh config --stack cluster --env-file "${custom_cluster_env}" \
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
NACOS_HOST_PORT=44848 \
MAILHOG_UI_HOST_PORT=44025 \
FRONTEND_HOST_PORT=41881 \
NGINX_API_PORT=41880 \
ELASTICSEARCH_PORT=41888 \
KIBANA_PORT=41889 \
  ./deploy/deployment.sh config --stack single \
    --env-file deploy/stacks/single/.env.example -p community-single-environment \
    >"${environment_override_full}"
test "$(rendered_network_value "${environment_override_full}" subnet)" = "172.41.0.0/24"
test "$(rendered_service_ipv4_address "${environment_override_full}" nginx)" = "172.41.0.10"
test "$(rendered_service_ipv4_address "${environment_override_full}" community-gateway)" = "172.41.0.20"
test "$(service_environment_value "${environment_override_full}" community-gateway GATEWAY_TRUSTED_PROXY_CIDRS)" = "172.41.0.10/32"
test "$(service_environment_value "${environment_override_full}" community-app COMMUNITY_APP_TRUSTED_PROXY_CIDRS)" = "172.41.0.20/32"
grep -F 'name: community_single_environment_mysql_primary_data' "${environment_override_full}"

grep -F 'name: community-single' "${single_full}"
grep -E '^  mysql:$' "${single_full}"
grep -E '^  nacos:$' "${single_full}"
grep -A40 -E '^  nacos:$' "${single_full}" | grep -F 'image: nacos/nacos-server:v3.1.2-slim'
grep -A40 -E '^  nacos:$' "${single_full}" | grep -F '/nacos/v3/admin/core/state/readiness'
grep -A40 -E '^  nacos:$' "${single_full}" | grep -E 'code.*0'
grep -A40 -E '^  nacos:$' "${single_full}" | grep -F 'bash -c'
grep -A40 -E '^  nacos:$' "${single_full}" | grep -F '/dev/tcp/127.0.0.1/9848'
grep -A40 -E '^  nacos:$' "${single_full}" | grep -F 'healthcheck:'
grep -E '^  nacos-config-bootstrap:$' "${single_full}"
grep -A36 -E '^  nacos-config-bootstrap:$' "${single_full}" | grep -F '/deploy/config/nacos'
grep -A36 -E '^  nacos-config-bootstrap:$' "${single_full}" | grep -F 'target: /nacos'
grep -A36 -E '^  nacos-config-bootstrap:$' "${single_full}" | grep -F 'read_only: true'
test "$(service_environment_value "${single_full}" nacos-config-bootstrap CONFIG_DIR)" = "/nacos"
for variable in BROWSER_ALLOWED_ORIGINS FRONTEND_PUBLIC_ORIGIN GATEWAY_PUBLIC_BASE_URL OSS_PUBLIC_BASE_URL IM_GATEWAY_PUBLIC_WS_URL; do
  grep -A30 -E '^  nacos-config-bootstrap:$' "${single_full}" | grep -F "${variable}:"
done
grep -E '^  community-gateway:$' "${single_full}"
grep -A4 -E '^      nacos:$' "${single_full}" | grep -F 'condition: service_healthy'
grep -A6 -E '^      nacos-config-bootstrap:$' "${single_full}" | grep -F 'condition: service_completed_successfully'
grep -A4 -E '^      community-gateway:$' "${single_full}" | grep -F 'condition: service_healthy'
grep -E 'KAFKA_TOPIC_REPLICATION_FACTOR: "?1"?' "${single_full}"
grep -A80 -E '^  im-realtime:$' "${single_full}" | grep -F 'SPRING_DATA_REDIS_HOST: redis'
grep -A80 -E '^  im-realtime:$' "${single_full}" | grep -F 'SPRING_DATA_REDIS_PORT: "6379"'
single_worker_slot="$(service_environment_value "${single_full}" im-realtime IM_ROOM_FANOUT_WORKER_INBOX_SLOT)"
test "${single_worker_slot}" = "0"

grep -F 'name: community-cluster' "${cluster_full}"
grep -E '^  mysql-primary:$' "${cluster_full}"
grep -E '^  nacos-1:$' "${cluster_full}"
for nacos_node in nacos-1 nacos-2 nacos-3; do
  grep -A40 -E "^  ${nacos_node}:$" "${cluster_full}" | grep -F 'image: nacos/nacos-server:v3.1.2-slim'
  grep -A40 -E "^  ${nacos_node}:$" "${cluster_full}" | grep -F '/nacos/v3/admin/core/state/readiness'
  grep -A40 -E "^  ${nacos_node}:$" "${cluster_full}" | grep -E 'code.*0'
  grep -A40 -E "^  ${nacos_node}:$" "${cluster_full}" | grep -F 'bash -c'
  grep -A40 -E "^  ${nacos_node}:$" "${cluster_full}" | grep -F '/dev/tcp/127.0.0.1/9848'
  grep -A40 -E "^  ${nacos_node}:$" "${cluster_full}" | grep -F 'healthcheck:'
done
grep -E '^  nacos-config-bootstrap:$' "${cluster_full}"
grep -A36 -E '^  nacos-config-bootstrap:$' "${cluster_full}" | grep -F '/deploy/config/nacos'
grep -A36 -E '^  nacos-config-bootstrap:$' "${cluster_full}" | grep -F 'target: /nacos'
grep -A36 -E '^  nacos-config-bootstrap:$' "${cluster_full}" | grep -F 'read_only: true'
test "$(service_environment_value "${cluster_full}" nacos-config-bootstrap CONFIG_DIR)" = "/nacos"
for variable in BROWSER_ALLOWED_ORIGINS FRONTEND_PUBLIC_ORIGIN GATEWAY_PUBLIC_BASE_URL OSS_PUBLIC_BASE_URL IM_GATEWAY_PUBLIC_WS_URL; do
  grep -A30 -E '^  nacos-config-bootstrap:$' "${cluster_full}" | grep -F "${variable}:"
done
grep -E '^  community-gateway-1:$' "${cluster_full}"
test "$(service_environment_value "${cluster_full}" community-app-1 MODULE)" = "community-app"
test "$(service_environment_value "${cluster_full}" community-app-1 mem_limit)" = "805306368"
grep -A4 -E '^      nacos-1:$' "${cluster_full}" | grep -F 'condition: service_healthy'
grep -A6 -E '^      nacos-config-bootstrap:$' "${cluster_full}" | grep -F 'condition: service_completed_successfully'
grep -A4 -E '^      community-gateway-1:$' "${cluster_full}" | grep -F 'condition: service_healthy'
grep -E 'KAFKA_TOPIC_REPLICATION_FACTOR: "?3"?' "${cluster_full}"
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
for legacy_option in --topology --scope --host-access; do
  if ./deploy/deployment.sh config --stack single "${legacy_option}" \
    --env-file deploy/stacks/single/.env.example >/dev/null 2>"${legacy_option_err}"; then
    echo "expected legacy option ${legacy_option} to fail" >&2
    exit 1
  fi
  grep -F "unsupported option: ${legacy_option}" "${legacy_option_err}" >/dev/null
done

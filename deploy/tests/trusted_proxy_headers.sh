#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"

fail() {
  echo "trusted proxy header contract: $*" >&2
  exit 1
}

assert_contains() {
  local expected="$1"
  local file="$2"

  grep -Fq -- "${expected}" "${file}" \
    || fail "${file#"${REPO_ROOT}/"} is missing: ${expected}"
}

assert_count() {
  local expected_count="$1"
  local expected="$2"
  local file="$3"
  local actual_count

  actual_count="$(grep -Fc -- "${expected}" "${file}" || true)"
  if [ "${actual_count}" -ne "${expected_count}" ]; then
    fail "${file#"${REPO_ROOT}/"} must contain ${expected_count} occurrence(s) of '${expected}', found ${actual_count}"
  fi
}

env_value() {
  local variable="$1"
  local file="$2"

  awk -F= -v variable="${variable}" '$1 == variable { print substr($0, length(variable) + 2); exit }' "${file}"
}

assert_env_value() {
  local variable="$1"
  local expected="$2"
  local file="$3"
  local actual

  actual="$(env_value "${variable}" "${file}")"
  if [ "${actual}" != "${expected}" ]; then
    fail "${file#"${REPO_ROOT}/"} must set ${variable}=${expected}, found '${actual}'"
  fi
}

assert_env_absent() {
  local variable="$1"
  local file="$2"

  if grep -q "^${variable}=" "${file}"; then
    fail "${file#"${REPO_ROOT}/"} must not advertise ${variable} as an overridable setting"
  fi
}

assert_ip_cidr_relation() {
  local ip="$1"
  local cidr="$2"
  local expected_relation="$3"
  local description="$4"

  awk -v ip="${ip}" -v cidr="${cidr}" -v expected="${expected_relation}" '
    function ipv4_number(value, octets, count, i, result) {
      count = split(value, octets, ".")
      if (count != 4) return -1
      result = 0
      for (i = 1; i <= 4; i++) {
        if (octets[i] !~ /^[0-9]+$/ || octets[i] < 0 || octets[i] > 255) return -1
        result = result * 256 + octets[i]
      }
      return result
    }

    BEGIN {
      if (split(cidr, parts, "/") != 2 || parts[2] !~ /^[0-9]+$/ || parts[2] < 0 || parts[2] > 32) exit 2
      address = ipv4_number(ip)
      network = ipv4_number(parts[1])
      if (address < 0 || network < 0) exit 2
      block_size = 2 ^ (32 - parts[2])
      first = int(network / block_size) * block_size
      inside = address >= first && address < first + block_size
      exit (expected == "inside" && inside) || (expected == "outside" && !inside) ? 0 : 1
    }
  ' || fail "${description}: ${ip} must be ${expected_relation} ${cidr}"
}

rendered_service_value() {
  local rendered_config="$1"
  local service="$2"
  local property="$3"

  awk -v service="${service}" -v property="${property}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service && $1 == property ":" {
      gsub(/"/, "", $2)
      print $2
      exit
    }
  ' "${rendered_config}"
}

assert_rendered_service_value() {
  local rendered_config="$1"
  local service="$2"
  local property="$3"
  local expected="$4"
  local actual

  actual="$(rendered_service_value "${rendered_config}" "${service}" "${property}")"
  if [ "${actual}" != "${expected}" ]; then
    fail "rendered ${service}.${property} must be '${expected}', found '${actual}'"
  fi
}

rendered_default_ipam_value() {
  local rendered_config="$1"
  local property="$2"

  awk -v property="${property}" '
    $0 == "networks:" { in_networks = 1; next }
    in_networks && /^[^ ]/ { exit }
    in_networks && $1 == "-" && $2 == property ":" { print $3; exit }
    in_networks && $1 == property ":" { print $2; exit }
  ' "${rendered_config}"
}

assert_proxy_locations_sanitize_headers() {
  local file="$1"

  if grep -Fq '$proxy_add_x_forwarded_for' "${file}"; then
    fail "${file#"${REPO_ROOT}/"} must not append untrusted X-Forwarded-For values"
  fi

  awk -v config="${file#"${REPO_ROOT}/"}" '
    function count_char(value, char, copy, count) {
      copy = value
      count = gsub(char, "", copy)
      return count
    }

    function reset_location() {
      in_location = 0
      depth = 0
      location_line = 0
      location_name = ""
      has_proxy_pass = 0
      has_forwarded = 0
      has_real_ip = 0
      has_forwarded_for = 0
      has_forwarded_host = 0
      has_forwarded_port = 0
      has_forwarded_prefix = 0
      has_forwarded_proto = 0
    }

    function finish_location(   missing) {
      if (!has_proxy_pass) {
        reset_location()
        return
      }

      proxy_locations++
      missing = ""
      if (!has_forwarded) missing = missing " Forwarded"
      if (!has_real_ip) missing = missing " X-Real-IP"
      if (!has_forwarded_for) missing = missing " X-Forwarded-For"
      if (!has_forwarded_host) missing = missing " X-Forwarded-Host"
      if (!has_forwarded_port) missing = missing " X-Forwarded-Port"
      if (!has_forwarded_prefix) missing = missing " X-Forwarded-Prefix"
      if (!has_forwarded_proto) missing = missing " X-Forwarded-Proto"
      if (missing != "") {
        printf "%s:%d proxying location %s is missing safe local header directives:%s\n", \
          config, location_line, location_name, missing > "/dev/stderr"
        failures++
      }
      reset_location()
    }

    BEGIN { reset_location() }

    /^[[:space:]]*location[[:space:]]/ {
      if (in_location) {
        printf "%s:%d nested location parsing is unsupported\n", config, NR > "/dev/stderr"
        failures++
        next
      }
      in_location = 1
      location_line = NR
      location_name = $0
    }

    in_location {
      depth += count_char($0, "{") - count_char($0, "}")
      if ($0 ~ /^[[:space:]]*proxy_pass[[:space:]]+/) has_proxy_pass = 1
      if ($0 ~ /^[[:space:]]*proxy_set_header[[:space:]]+Forwarded[[:space:]]+"";[[:space:]]*$/) has_forwarded = 1
      if ($0 ~ /^[[:space:]]*proxy_set_header[[:space:]]+X-Real-IP[[:space:]]+\$remote_addr;[[:space:]]*$/) has_real_ip = 1
      if ($0 ~ /^[[:space:]]*proxy_set_header[[:space:]]+X-Forwarded-For[[:space:]]+\$remote_addr;[[:space:]]*$/) has_forwarded_for = 1
      if ($0 ~ /^[[:space:]]*proxy_set_header[[:space:]]+X-Forwarded-Host[[:space:]]+"";[[:space:]]*$/) has_forwarded_host = 1
      if ($0 ~ /^[[:space:]]*proxy_set_header[[:space:]]+X-Forwarded-Port[[:space:]]+"";[[:space:]]*$/) has_forwarded_port = 1
      if ($0 ~ /^[[:space:]]*proxy_set_header[[:space:]]+X-Forwarded-Prefix[[:space:]]+"";[[:space:]]*$/) has_forwarded_prefix = 1
      if ($0 ~ /^[[:space:]]*proxy_set_header[[:space:]]+X-Forwarded-Proto[[:space:]]+\$scheme;[[:space:]]*$/) has_forwarded_proto = 1
      if (depth == 0) finish_location()
    }

    END {
      if (in_location) {
        printf "%s:%d unterminated location block\n", config, location_line > "/dev/stderr"
        failures++
      }
      if (proxy_locations == 0) {
        printf "%s contains no proxying locations\n", config > "/dev/stderr"
        failures++
      }
      exit failures == 0 ? 0 : 1
    }
  ' "${file}" || fail "${file#"${REPO_ROOT}/"} has an unsafe proxying location"
}

assert_gateway_header_filters_disabled() {
  local file="$1"

  awk '
    previous == "      forwarded:" && $0 == "        enabled: false" { forwarded_disabled = 1 }
    previous == "      x-forwarded:" && $0 == "        enabled: false" { x_forwarded_disabled = 1 }
    { previous = $0 }
    END { exit forwarded_disabled && x_forwarded_disabled ? 0 : 1 }
  ' "${file}" || fail "${file#"${REPO_ROOT}/"} must disable both Spring Cloud Gateway forwarded header filters"
}

assert_community_trusted_proxy_path() {
  local file="$1"

  awk '
    $0 == "community:" { in_community = 1; next }
    in_community && /^[^ ]/ { in_community = 0; in_web = 0 }
    in_community && $0 == "  web:" { in_web = 1; next }
    in_web && /^  [^ ]/ { in_web = 0 }
    in_web && $0 == "    trusted-proxy:" { found = 1 }
    END { exit found ? 0 : 1 }
  ' "${file}" || fail "${file#"${REPO_ROOT}/"} must own trusted proxy config at community.web.trusted-proxy"
}

assert_no_gateway_trusted_proxy_path() {
  local file="$1"

  awk '
    $0 == "gateway:" { in_gateway = 1; next }
    in_gateway && /^[^ ]/ { in_gateway = 0 }
    in_gateway && $0 == "  trusted-proxy:" { found = 1 }
    END { exit found ? 1 : 0 }
  ' "${file}" || fail "${file#"${REPO_ROOT}/"} must not consume the Gateway owner's trusted proxy path"
}

nginx_configs=(
  "${REPO_ROOT}/deploy/nginx/nginx.single.conf"
  "${REPO_ROOT}/deploy/nginx/nginx.cluster.conf"
)

for nginx_config in "${nginx_configs[@]}"; do
  assert_proxy_locations_sanitize_headers "${nginx_config}"
done

gateway_runtime_configs=(
  "${REPO_ROOT}/backend/community-gateway/src/main/resources/application.yml"
  "${REPO_ROOT}/deploy/nacos/config/community-gateway.yaml"
)

for gateway_runtime_config in "${gateway_runtime_configs[@]}"; do
  assert_gateway_header_filters_disabled "${gateway_runtime_config}"
  assert_contains 'enabled: ${GATEWAY_TRUSTED_PROXY_ENABLED:false}' "${gateway_runtime_config}"
  assert_contains 'cidrs: ${GATEWAY_TRUSTED_PROXY_CIDRS:}' "${gateway_runtime_config}"
done

community_runtime_configs=(
  "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"
  "${REPO_ROOT}/deploy/nacos/config/community-app.yaml"
)

for community_runtime_config in "${community_runtime_configs[@]}"; do
  assert_community_trusted_proxy_path "${community_runtime_config}"
  assert_no_gateway_trusted_proxy_path "${community_runtime_config}"
  assert_contains 'enabled: ${COMMUNITY_APP_TRUSTED_PROXY_ENABLED:false}' "${community_runtime_config}"
  assert_contains 'cidrs: ${COMMUNITY_APP_TRUSTED_PROXY_CIDRS:}' "${community_runtime_config}"
done

assert_contains 'source: application-default' "${REPO_ROOT}/backend/community-gateway/src/main/resources/application.yml"
assert_contains 'source: application-default' "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"
assert_contains 'source: compose-environment' "${REPO_ROOT}/deploy/nacos/config/community-gateway.yaml"
assert_contains 'source: compose-environment' "${REPO_ROOT}/deploy/nacos/config/community-app.yaml"

shared_config="${REPO_ROOT}/deploy/nacos/config/community-shared.yaml"
if grep -Fq 'trusted-proxy:' "${shared_config}"; then
  fail "deploy/nacos/config/community-shared.yaml must not share one owner domain's trusted proxy CIDRs with another"
fi

cidr_config_files=(
  "${shared_config}"
  "${gateway_runtime_configs[@]}"
  "${community_runtime_configs[@]}"
  "${REPO_ROOT}/deploy/.env.single.example"
  "${REPO_ROOT}/deploy/.env.cluster.example"
  "${REPO_ROOT}/deploy/compose.runtime.services.single.yml"
  "${REPO_ROOT}/deploy/compose.runtime.services.cluster.yml"
)

if grep -nE '0\.0\.0\.0/0|::/0' "${cidr_config_files[@]}"; then
  fail "trusted proxy configuration must not contain universal CIDRs"
fi

single_compose="${REPO_ROOT}/deploy/compose.runtime.services.single.yml"
cluster_compose="${REPO_ROOT}/deploy/compose.runtime.services.cluster.yml"
base_compose="${REPO_ROOT}/deploy/compose.yml"
single_frontend_compose="${REPO_ROOT}/deploy/compose.runtime.frontend-nginx.single.yml"
cluster_frontend_compose="${REPO_ROOT}/deploy/compose.runtime.frontend-nginx.cluster.yml"
single_env="${REPO_ROOT}/deploy/.env.single.example"
cluster_env="${REPO_ROOT}/deploy/.env.cluster.example"

assert_contains 'subnet: ${COMMUNITY_NETWORK_SUBNET:?COMMUNITY_NETWORK_SUBNET is required}' "${base_compose}"
assert_contains 'ip_range: ${COMMUNITY_NETWORK_DYNAMIC_RANGE:?COMMUNITY_NETWORK_DYNAMIC_RANGE is required}' "${base_compose}"
assert_contains 'ipv4_address: ${NGINX_STATIC_IP:?NGINX_STATIC_IP is required}' "${single_frontend_compose}"
assert_contains 'ipv4_address: ${NGINX_STATIC_IP:?NGINX_STATIC_IP is required}' "${cluster_frontend_compose}"
assert_contains 'ipv4_address: ${COMMUNITY_GATEWAY_STATIC_IP:?COMMUNITY_GATEWAY_STATIC_IP is required}' "${single_compose}"
assert_contains 'ipv4_address: ${COMMUNITY_GATEWAY_1_STATIC_IP:?COMMUNITY_GATEWAY_1_STATIC_IP is required}' "${cluster_compose}"
assert_contains 'ipv4_address: ${COMMUNITY_GATEWAY_2_STATIC_IP:?COMMUNITY_GATEWAY_2_STATIC_IP is required}' "${cluster_compose}"
assert_contains 'ipv4_address: ${COMMUNITY_GATEWAY_3_STATIC_IP:?COMMUNITY_GATEWAY_3_STATIC_IP is required}' "${cluster_compose}"

assert_count 1 'GATEWAY_TRUSTED_PROXY_ENABLED=true' "${single_compose}"
assert_count 1 'GATEWAY_TRUSTED_PROXY_CIDRS=${GATEWAY_TRUSTED_PROXY_CIDRS:?GATEWAY_TRUSTED_PROXY_CIDRS is required}' "${single_compose}"
assert_count 1 'COMMUNITY_APP_TRUSTED_PROXY_ENABLED=true' "${single_compose}"
assert_count 1 'COMMUNITY_APP_TRUSTED_PROXY_CIDRS=${COMMUNITY_APP_TRUSTED_PROXY_CIDRS:?COMMUNITY_APP_TRUSTED_PROXY_CIDRS is required}' "${single_compose}"
assert_count 3 'GATEWAY_TRUSTED_PROXY_ENABLED=true' "${cluster_compose}"
assert_count 3 'GATEWAY_TRUSTED_PROXY_CIDRS=${GATEWAY_TRUSTED_PROXY_CIDRS:?GATEWAY_TRUSTED_PROXY_CIDRS is required}' "${cluster_compose}"
assert_count 3 'COMMUNITY_APP_TRUSTED_PROXY_ENABLED=true' "${cluster_compose}"
assert_count 3 'COMMUNITY_APP_TRUSTED_PROXY_CIDRS=${COMMUNITY_APP_TRUSTED_PROXY_CIDRS:?COMMUNITY_APP_TRUSTED_PROXY_CIDRS is required}' "${cluster_compose}"

assert_env_value COMMUNITY_NETWORK_SUBNET 172.30.0.0/24 "${single_env}"
assert_env_value COMMUNITY_NETWORK_DYNAMIC_RANGE 172.30.0.128/25 "${single_env}"
assert_env_value NGINX_STATIC_IP 172.30.0.10 "${single_env}"
assert_env_value COMMUNITY_GATEWAY_STATIC_IP 172.30.0.20 "${single_env}"
assert_env_absent GATEWAY_TRUSTED_PROXY_ENABLED "${single_env}"
assert_env_value GATEWAY_TRUSTED_PROXY_CIDRS 172.30.0.10/32 "${single_env}"
assert_env_absent COMMUNITY_APP_TRUSTED_PROXY_ENABLED "${single_env}"
assert_env_value COMMUNITY_APP_TRUSTED_PROXY_CIDRS 172.30.0.20/32 "${single_env}"

assert_env_value COMMUNITY_NETWORK_SUBNET 172.31.0.0/24 "${cluster_env}"
assert_env_value COMMUNITY_NETWORK_DYNAMIC_RANGE 172.31.0.128/25 "${cluster_env}"
assert_env_value NGINX_STATIC_IP 172.31.0.10 "${cluster_env}"
assert_env_value COMMUNITY_GATEWAY_1_STATIC_IP 172.31.0.20 "${cluster_env}"
assert_env_value COMMUNITY_GATEWAY_2_STATIC_IP 172.31.0.21 "${cluster_env}"
assert_env_value COMMUNITY_GATEWAY_3_STATIC_IP 172.31.0.22 "${cluster_env}"
assert_env_absent GATEWAY_TRUSTED_PROXY_ENABLED "${cluster_env}"
assert_env_value GATEWAY_TRUSTED_PROXY_CIDRS 172.31.0.10/32 "${cluster_env}"
assert_env_absent COMMUNITY_APP_TRUSTED_PROXY_ENABLED "${cluster_env}"
assert_env_value COMMUNITY_APP_TRUSTED_PROXY_CIDRS 172.31.0.20/32,172.31.0.21/32,172.31.0.22/32 "${cluster_env}"

single_subnet="$(env_value COMMUNITY_NETWORK_SUBNET "${single_env}")"
single_dynamic_range="$(env_value COMMUNITY_NETWORK_DYNAMIC_RANGE "${single_env}")"
for variable in NGINX_STATIC_IP COMMUNITY_GATEWAY_STATIC_IP; do
  static_ip="$(env_value "${variable}" "${single_env}")"
  assert_ip_cidr_relation "${static_ip}" "${single_subnet}" inside "single ${variable}"
  assert_ip_cidr_relation "${static_ip}" "${single_dynamic_range}" outside "single ${variable}"
done

cluster_subnet="$(env_value COMMUNITY_NETWORK_SUBNET "${cluster_env}")"
cluster_dynamic_range="$(env_value COMMUNITY_NETWORK_DYNAMIC_RANGE "${cluster_env}")"
for variable in NGINX_STATIC_IP COMMUNITY_GATEWAY_1_STATIC_IP COMMUNITY_GATEWAY_2_STATIC_IP COMMUNITY_GATEWAY_3_STATIC_IP; do
  static_ip="$(env_value "${variable}" "${cluster_env}")"
  assert_ip_cidr_relation "${static_ip}" "${cluster_subnet}" inside "cluster ${variable}"
  assert_ip_cidr_relation "${static_ip}" "${cluster_dynamic_range}" outside "cluster ${variable}"
done

if [ "$(env_value COMMUNITY_NETWORK_SUBNET "${single_env}")" = "$(env_value COMMUNITY_NETWORK_SUBNET "${cluster_env}")" ]; then
  fail "single and cluster topologies must use distinct default-network subnets"
fi

single_rendered="$(mktemp)"
cluster_rendered="$(mktemp)"
single_false_override_rendered="$(mktemp)"
cluster_false_override_rendered="$(mktemp)"
trap 'rm -f "${single_rendered}" "${cluster_rendered}" "${single_false_override_rendered}" "${cluster_false_override_rendered}"' EXIT

"${REPO_ROOT}/deploy/deployment.sh" config --topology single --scope full \
  --env-file deploy/.env.single.example --no-observability >"${single_rendered}"
"${REPO_ROOT}/deploy/deployment.sh" config --topology cluster --scope full \
  --env-file deploy/.env.cluster.example --no-observability >"${cluster_rendered}"
env GATEWAY_TRUSTED_PROXY_ENABLED=false COMMUNITY_APP_TRUSTED_PROXY_ENABLED=false SPRING_PROFILES_ACTIVE=prod \
  "${REPO_ROOT}/deploy/deployment.sh" config --topology single --scope full \
  --env-file deploy/.env.single.example --no-observability >"${single_false_override_rendered}"
env GATEWAY_TRUSTED_PROXY_ENABLED=false COMMUNITY_APP_TRUSTED_PROXY_ENABLED=false SPRING_PROFILES_ACTIVE=prod \
  "${REPO_ROOT}/deploy/deployment.sh" config --topology cluster --scope full \
  --env-file deploy/.env.cluster.example --no-observability >"${cluster_false_override_rendered}"

if [ "$(rendered_default_ipam_value "${single_rendered}" subnet)" != "172.30.0.0/24" ]; then
  fail "rendered single default network must use subnet 172.30.0.0/24"
fi
if [ "$(rendered_default_ipam_value "${single_rendered}" ip_range)" != "172.30.0.128/25" ]; then
  fail "rendered single default network must reserve dynamic range 172.30.0.128/25"
fi
if [ "$(rendered_default_ipam_value "${cluster_rendered}" subnet)" != "172.31.0.0/24" ]; then
  fail "rendered cluster default network must use subnet 172.31.0.0/24"
fi
if [ "$(rendered_default_ipam_value "${cluster_rendered}" ip_range)" != "172.31.0.128/25" ]; then
  fail "rendered cluster default network must reserve dynamic range 172.31.0.128/25"
fi

assert_rendered_service_value "${single_rendered}" nginx ipv4_address 172.30.0.10
assert_rendered_service_value "${single_rendered}" community-gateway ipv4_address 172.30.0.20
assert_rendered_service_value "${single_rendered}" community-gateway GATEWAY_TRUSTED_PROXY_ENABLED true
assert_rendered_service_value "${single_rendered}" community-gateway GATEWAY_TRUSTED_PROXY_CIDRS 172.30.0.10/32
assert_rendered_service_value "${single_rendered}" community-app COMMUNITY_APP_TRUSTED_PROXY_ENABLED true
assert_rendered_service_value "${single_rendered}" community-app COMMUNITY_APP_TRUSTED_PROXY_CIDRS 172.30.0.20/32

for gateway_number in 1 2 3; do
  gateway_service="community-gateway-${gateway_number}"
  gateway_address="$(env_value "COMMUNITY_GATEWAY_${gateway_number}_STATIC_IP" "${cluster_env}")"
  assert_rendered_service_value "${cluster_rendered}" "${gateway_service}" ipv4_address "${gateway_address}"
  assert_rendered_service_value "${cluster_rendered}" "${gateway_service}" GATEWAY_TRUSTED_PROXY_ENABLED true
  assert_rendered_service_value "${cluster_rendered}" "${gateway_service}" GATEWAY_TRUSTED_PROXY_CIDRS 172.31.0.10/32
done

assert_rendered_service_value "${cluster_rendered}" nginx ipv4_address 172.31.0.10
for app_number in 1 2 3; do
  assert_rendered_service_value "${cluster_rendered}" "community-app-${app_number}" COMMUNITY_APP_TRUSTED_PROXY_ENABLED true
  assert_rendered_service_value "${cluster_rendered}" "community-app-${app_number}" COMMUNITY_APP_TRUSTED_PROXY_CIDRS \
    172.31.0.20/32,172.31.0.21/32,172.31.0.22/32
done

assert_rendered_service_value "${single_false_override_rendered}" community-gateway GATEWAY_TRUSTED_PROXY_ENABLED true
assert_rendered_service_value "${single_false_override_rendered}" community-app COMMUNITY_APP_TRUSTED_PROXY_ENABLED true
for gateway_number in 1 2 3; do
  assert_rendered_service_value "${cluster_false_override_rendered}" "community-gateway-${gateway_number}" GATEWAY_TRUSTED_PROXY_ENABLED true
done
for app_number in 1 2 3; do
  assert_rendered_service_value "${cluster_false_override_rendered}" "community-app-${app_number}" COMMUNITY_APP_TRUSTED_PROXY_ENABLED true
done

echo "trusted proxy header contract checks passed"

#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${repo_root}"

rendered_config="$(mktemp)"
duplicate_port_error="$(mktemp)"
custom_env="$(mktemp)"
custom_config="$(mktemp)"
generated_dir="$(mktemp -d)"
trap 'rm -f "${rendered_config}" "${duplicate_port_error}" "${custom_env}" "${custom_config}"; rm -rf "${generated_dir}"' EXIT

./deploy/deployment.sh config --stack infra \
  --env-file deploy/stacks/infra/.env.example >"${rendered_config}"

help_output="$(./deploy/deployment.sh --help 2>&1)"
grep -F -- '--stack <infra|single|cluster>' <<<"${help_output}" >/dev/null

if REDIS_HOST_PORT=23306 ./deploy/deployment.sh config --stack infra \
  --env-file deploy/stacks/infra/.env.example >/dev/null 2>"${duplicate_port_error}"; then
  echo 'infra stack unexpectedly accepted duplicate localhost ports' >&2
  exit 1
fi
grep -F -- 'REDIS_HOST_PORT and MYSQL_HOST_PORT must not use the same host port 23306' \
  "${duplicate_port_error}" >/dev/null

awk '
  /^COMMUNITY_VOLUME_NAMESPACE=/ { print "COMMUNITY_VOLUME_NAMESPACE=community_host_access_test"; next }
  /^COMMUNITY_NETWORK_SUBNET=/ { print "COMMUNITY_NETWORK_SUBNET=172.45.0.0/24"; next }
  /^COMMUNITY_NETWORK_DYNAMIC_RANGE=/ { print "COMMUNITY_NETWORK_DYNAMIC_RANGE=172.45.0.128/25"; next }
  /^NGINX_STATIC_IP=/ { print "NGINX_STATIC_IP=172.45.0.10"; next }
  /^COMMUNITY_GATEWAY_STATIC_IP=/ { print "COMMUNITY_GATEWAY_STATIC_IP=172.45.0.20"; next }
  /^GATEWAY_TRUSTED_PROXY_CIDRS=/ { print "GATEWAY_TRUSTED_PROXY_CIDRS=172.45.0.10/32"; next }
  /^COMMUNITY_APP_TRUSTED_PROXY_CIDRS=/ { print "COMMUNITY_APP_TRUSTED_PROXY_CIDRS=172.45.0.20/32"; next }
  { print }
' deploy/stacks/infra/.env.example >"${custom_env}"
MYSQL_HOST_PORT=33306 \
REDIS_HOST_PORT=36379 \
KAFKA_HOST_PORT=49092 \
ELASTICSEARCH_HOST_ACCESS_PORT=39200 \
NACOS_HOST_PORT=48848 \
NACOS_GRPC_HOST_PORT=49848 \
GARAGE_S3_HOST_PORT=33900 \
GARAGE_ADMIN_HOST_PORT=33903 \
MAILHOG_UI_HOST_PORT=38025 \
MAILHOG_SMTP_HOST_PORT=31025 \
  ./deploy/deployment.sh config --stack infra \
    --env-file "${custom_env}" -p community-host-access-test >"${custom_config}"
grep -F 'name: community-host-access-test' "${custom_config}" >/dev/null
grep -F 'published: "33306"' "${custom_config}" >/dev/null
grep -F 'KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,HOST://127.0.0.1:49092' \
  "${custom_config}" >/dev/null

assert_port() {
  local service="$1"
  local target="$2"
  local published="$3"

  awk -v service="${service}" -v target="${target}" -v published="${published}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service && $1 == "host_ip:" && $2 == "127.0.0.1" { loopback = 1 }
    in_service && $1 == "target:" && $2 == target { found_target = 1 }
    in_service && $1 == "published:" {
      value = $2
      gsub(/"/, "", value)
      if (value == published) found_published = 1
    }
    END { exit !(loopback && found_target && found_published) }
  ' "${rendered_config}" || {
    echo "${service} must bind ${published}:${target} on 127.0.0.1" >&2
    exit 1
  }
}

assert_port mysql 3306 23306
assert_port redis 6379 26379
assert_port kafka 29092 39092
assert_port elasticsearch 9200 29200
assert_port nacos 8848 28848
assert_port nacos 9848 29848
assert_port garage 3900 23900
assert_port garage 3903 23903
assert_port mailhog 1025 21025
assert_port mailhog 8025 28025

grep -F 'KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,HOST://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093' \
  "${rendered_config}" >/dev/null
grep -F 'KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,HOST://127.0.0.1:39092' \
  "${rendered_config}" >/dev/null
grep -F 'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,HOST:PLAINTEXT,CONTROLLER:PLAINTEXT' \
  "${rendered_config}" >/dev/null

env_file_value() {
  local file="$1"
  local variable="$2"
  awk -v variable="${variable}" '
    index($0, variable "=") == 1 {
      value = substr($0, length(variable) + 2)
      gsub(/^"|"$/, "", value)
      print value
      exit
    }
  ' "${file}"
}

./deploy/deployment.sh render-backend-env --stack infra \
  --env-file deploy/stacks/infra/.env.example --output-dir "${generated_dir}"

env_dir="${generated_dir}"
env_files=(
  community-app
  community-oss
  community-gateway
  community-im-gateway
  im-core
  im-realtime
)

declare -A expected_ports=(
  [community-app]=18080
  [community-oss]=18090
  [community-gateway]=12880
  [community-im-gateway]=18083
  [im-core]=18082
  [im-realtime]=18081
)

for service in "${env_files[@]}"; do
  file="${env_dir}/${service}.env"
  test -f "${file}"
  test "$(stat -c '%a' "${file}")" = '600'
  bash -n "${file}"
  test "$(env_file_value "${file}" SERVER_PORT)" = "${expected_ports[${service}]}"
  test "$(env_file_value "${file}" NACOS_SERVER_ADDR)" = '127.0.0.1:28848'
  test "$(env_file_value "${file}" SPRING_CLOUD_NACOS_DISCOVERY_IP)" = '127.0.0.1'
  test "$(env_file_value "${file}" OTEL_ENABLED)" = 'false'
done

app_env="${env_dir}/community-app.env"
oss_env="${env_dir}/community-oss.env"
gateway_env="${env_dir}/community-gateway.env"
im_gateway_env="${env_dir}/community-im-gateway.env"
im_core_env="${env_dir}/im-core.env"
realtime_env="${env_dir}/im-realtime.env"

test "$(env_file_value "${app_env}" KAFKA_BOOTSTRAP_SERVERS)" = '127.0.0.1:39092'
test "$(env_file_value "${app_env}" SPRING_DATA_REDIS_PORT)" = '26379'
test "$(env_file_value "${app_env}" ELASTICSEARCH_URIS)" = 'http://127.0.0.1:29200'
test "$(env_file_value "${app_env}" SPRING_MAIL_PORT)" = '21025'
test "$(env_file_value "${app_env}" OSS_CLIENT_BASE_URL)" = 'http://127.0.0.1:18090'
test "$(env_file_value "${oss_env}" OSS_OBJECT_STORE_ENDPOINT)" = 'http://127.0.0.1:23900'
test "$(env_file_value "${im_core_env}" KAFKA_BOOTSTRAP_SERVERS)" = '127.0.0.1:39092'
test "$(env_file_value "${realtime_env}" KAFKA_BOOTSTRAP_SERVERS)" = '127.0.0.1:39092'
test "$(env_file_value "${realtime_env}" SPRING_DATA_REDIS_PORT)" = '26379'

grep -F '127.0.0.1:23306/community?' "${app_env}" >/dev/null
grep -F '127.0.0.1:23306/community_oss?' "${oss_env}" >/dev/null
grep -F '127.0.0.1:23306/im_core?' "${im_core_env}" >/dev/null

grep -q '^JWT_ACCESS_PRIVATE_KEY=' "${app_env}"
for file in "${oss_env}" "${gateway_env}" "${im_gateway_env}" "${im_core_env}" "${realtime_env}"; do
  if grep -q '^JWT_ACCESS_PRIVATE_KEY=' "${file}"; then
    echo "${file} must not receive the access-token private key" >&2
    exit 1
  fi
done

for file in "${gateway_env}" "${im_gateway_env}"; do
  if grep -q '^JWT_SERVICE_HMAC_SECRET=' "${file}"; then
    echo "${file} must not receive the service JWT HMAC secret" >&2
    exit 1
  fi
done

for file in "${app_env}" "${oss_env}" "${gateway_env}" "${im_core_env}"; do
  if grep -q '^IM_SESSION_TICKET_HMAC_SECRET=' "${file}"; then
    echo "${file} must not receive the IM session ticket secret" >&2
    exit 1
  fi
done
grep -q '^IM_SESSION_TICKET_HMAC_SECRET=' "${im_gateway_env}"
grep -q '^IM_SESSION_TICKET_HMAC_SECRET=' "${realtime_env}"

special_password='space $dollar `backtick` "quote" slash\end'
MYSQL_PASSWORD="${special_password}" \
  ./deploy/deployment.sh render-backend-env --stack infra \
    --env-file deploy/stacks/infra/.env.example --output-dir "${generated_dir}" >/dev/null
loaded_password="$(bash -c '. "$1"; printf "%s" "$DB_PASSWORD"' _ "${app_env}")"
test "${loaded_password}" = "${special_password}"

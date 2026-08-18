#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
CONFIG_DIR="${REPO_ROOT}/deploy/config/nacos"
SEED_SCRIPT="${REPO_ROOT}/deploy/config/nacos/seed-configs.sh"
tmp_dir="$(mktemp -d)"
fake_bin="${tmp_dir}/bin"
curl_log="${tmp_dir}/curl.log"
readiness_count_file="${tmp_dir}/readiness-count"
mkdir -p "${fake_bin}"
printf '0\n' >"${readiness_count_file}"
trap 'rm -rf "${tmp_dir}"' EXIT

runtime_environment_key_exists() {
  local rendered_config="$1"
  local service="$2"
  local variable="$3"

  awk -v service="${service}" -v variable="${variable}" '
    $0 == "  " service ":" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit 1 }
    in_service && $0 == "    environment:" { in_environment = 1; next }
    in_environment && /^    [^ ]/ { in_environment = 0 }
    in_environment && $1 == variable ":" { found = 1; exit }
    END { exit found ? 0 : 1 }
  ' "${rendered_config}"
}

assert_runtime_environment_key() {
  local rendered_config="$1"
  local service="$2"
  local variable="$3"

  runtime_environment_key_exists "${rendered_config}" "${service}" "${variable}" || {
    echo "rendered ${service} must receive ${variable}" >&2
    return 1
  }
}

required_data_ids=(
  community-shared.yaml
  community-feature-flags.yaml
  community-frontend-runtime.yaml
  community-search-policy.yaml
  community-upload-policy.yaml
  community-notification-policy.yaml
  community-kafka-policy.yaml
  community-work-processing.yaml
  community-gateway.yaml
  community-app.yaml
  community-oss.yaml
  community-im-gateway.yaml
  im-core.yaml
  im-realtime.yaml
)

test -x "${SEED_SCRIPT}"
grep -F 'NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"' "${SEED_SCRIPT}"
grep -F -- '--data-urlencode "tenant=${NACOS_NAMESPACE}"' "${SEED_SCRIPT}"
grep -F 'publish_response="$(curl' "${SEED_SCRIPT}"
grep -F '[ "${publish_response}" != "true" ]' "${SEED_SCRIPT}"
grep -F 'failed to publish ${data_id}' "${SEED_SCRIPT}"
if grep -F 'seq ' "${SEED_SCRIPT}"; then
  echo "seed script must use a POSIX health retry loop without seq" >&2
  exit 1
fi

cat >"${fake_bin}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >>"${FAKE_NACOS_CURL_LOG}"
for arg in "$@"; do
  case "${arg}" in
    content@*)
      printf '%s\n' '--- published-content ---' >>"${FAKE_NACOS_CURL_LOG}"
      cat "${arg#content@}" >>"${FAKE_NACOS_CURL_LOG}"
      printf '%s\n' '--- end-published-content ---' >>"${FAKE_NACOS_CURL_LOG}"
      ;;
  esac
done
case "$*" in
  *"/nacos/v3/admin/core/state/readiness"*)
    readiness_count="$(cat "${FAKE_NACOS_READINESS_COUNT}")"
    readiness_count="$((readiness_count + 1))"
    printf '%s\n' "${readiness_count}" >"${FAKE_NACOS_READINESS_COUNT}"
    if [ "${readiness_count}" -eq 1 ]; then
      printf '%s\n' '{"code":1}'
    else
      printf '%s\n' '{"code":0}'
    fi
    ;;
  *"/nacos/v1/cs/configs"*)
    printf '%s\n' 'true'
    ;;
  *)
    printf '%s\n' 'true'
    ;;
esac
EOF
cat >"${fake_bin}/sleep" <<'EOF'
#!/usr/bin/env sh
exit 0
EOF
chmod +x "${fake_bin}/curl" "${fake_bin}/sleep"

PATH="${fake_bin}:${PATH}" \
  FAKE_NACOS_CURL_LOG="${curl_log}" \
  FAKE_NACOS_READINESS_COUNT="${readiness_count_file}" \
  BROWSER_ALLOWED_ORIGINS="http://localhost:13110,http://127.0.0.1:13110" \
  FRONTEND_PUBLIC_ORIGIN="http://localhost:13110" \
  AUTH_REFRESH_COOKIE_SECURE="false" \
  AUTH_REFRESH_COOKIE_SAME_SITE="Strict" \
  AUTH_MAIL_ENABLED="false" \
  AUTH_MAIL_FROM="auth-test@community.invalid" \
  AUTH_REGISTRATION_EXPOSE_CODE="true" \
  GATEWAY_PUBLIC_BASE_URL="http://localhost:13109" \
  OSS_PUBLIC_BASE_URL="http://localhost:13109" \
  IM_GATEWAY_PUBLIC_WS_URL="ws://localhost:13109/ws/im" \
  CONFIG_DIR="${CONFIG_DIR}" \
  NACOS_ADDR="http://nacos:8848" \
  "${SEED_SCRIPT}" >/dev/null

if [ "$(cat "${readiness_count_file}")" -ne 2 ]; then
  echo 'seed script must retry readiness until the response body contains code=0' >&2
  exit 1
fi
if [ "$(grep -Fc '/nacos/v3/admin/core/state/readiness' "${curl_log}")" -ne 2 ]; then
  echo 'seed script must call the Nacos v3 readiness endpoint until code=0' >&2
  exit 1
fi
if [ "$(grep -Fc '/nacos/v1/cs/configs' "${curl_log}")" -ne 14 ]; then
  echo 'seed script must publish every required Nacos configuration after readiness' >&2
  exit 1
fi
if grep -F '/nacos/actuator/health' "${curl_log}" >/dev/null; then
  echo 'seed script must use the Nacos v3 readiness endpoint' >&2
  exit 1
fi

browser_origin_seed_files=(
  community-gateway.yaml
  community-app.yaml
  community-im-gateway.yaml
  im-core.yaml
  im-realtime.yaml
)
for data_id in "${browser_origin_seed_files[@]}"; do
  grep -F 'allowed-origins: ${BROWSER_ALLOWED_ORIGINS}' "${CONFIG_DIR}/${data_id}"
done
grep -F 'http://localhost:13110,http://127.0.0.1:13110' "${curl_log}"
grep -F 'reset-base-url: ${FRONTEND_PUBLIC_ORIGIN}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'refresh-cookie-secure: ${AUTH_REFRESH_COOKIE_SECURE:true}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'refresh-cookie-same-site: ${AUTH_REFRESH_COOKIE_SAME_SITE:Lax}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'enabled: ${AUTH_MAIL_ENABLED:true}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'from: ${AUTH_MAIL_FROM:no-reply@community.local}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'expose-code: ${AUTH_REGISTRATION_EXPOSE_CODE:false}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'public-gateway-origin: ${GATEWAY_PUBLIC_BASE_URL}' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'websocket-url: ${IM_GATEWAY_PUBLIC_WS_URL}' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'public-base-url: ${OSS_PUBLIC_BASE_URL}' "${CONFIG_DIR}/community-oss.yaml"
grep -F 'public-ws-url: ${IM_GATEWAY_PUBLIC_WS_URL}' "${CONFIG_DIR}/community-im-gateway.yaml"
grep -F '"[/api/drive/shares/{shareToken}/verify]":' "${CONFIG_DIR}/community-gateway.yaml"
grep -F 'max-batches-per-root: ${CONTENT_COMMENT_THREAD_CLEANUP_MAX_BATCHES_PER_ROOT:10}' \
  "${CONFIG_DIR}/community-app.yaml"
grep -F 'reset-base-url: http://localhost:13110' "${curl_log}"
grep -F 'refresh-cookie-secure: false' "${curl_log}"
grep -F 'refresh-cookie-same-site: Strict' "${curl_log}"
grep -F 'enabled: false' "${curl_log}"
grep -F 'from: auth-test@community.invalid' "${curl_log}"
grep -F 'expose-code: true' "${curl_log}"
grep -F 'public-gateway-origin: http://localhost:13109' "${curl_log}"
grep -F 'websocket-url: ws://localhost:13109/ws/im' "${curl_log}"
grep -F 'public-base-url: http://localhost:13109' "${curl_log}"
grep -F 'public-ws-url: ws://localhost:13109/ws/im' "${curl_log}"
if grep -F '${BROWSER_ALLOWED_ORIGINS}' "${curl_log}" >/dev/null; then
  echo 'published Nacos config must not contain an unresolved browser origin placeholder' >&2
  exit 1
fi
for placeholder in FRONTEND_PUBLIC_ORIGIN AUTH_REFRESH_COOKIE_SECURE AUTH_REFRESH_COOKIE_SAME_SITE AUTH_MAIL_ENABLED AUTH_MAIL_FROM AUTH_REGISTRATION_EXPOSE_CODE GATEWAY_PUBLIC_BASE_URL OSS_PUBLIC_BASE_URL IM_GATEWAY_PUBLIC_WS_URL; do
  if grep -F "\${${placeholder}" "${curl_log}" >/dev/null; then
    echo "published Nacos config must not contain an unresolved ${placeholder} placeholder" >&2
    exit 1
  fi
done

for data_id in "${required_data_ids[@]}"; do
  test -s "${CONFIG_DIR}/${data_id}"
  grep -F "${data_id}" "${SEED_SCRIPT}"
done

grep -F 'issuer: community-auth' "${CONFIG_DIR}/community-shared.yaml"
grep -F 'access-token-audience: community-api' "${CONFIG_DIR}/community-shared.yaml"
if grep -RE 'access-(public|private)-key|service-hmac-secret|JWT_(ACCESS|SERVICE)' "${CONFIG_DIR}" >/dev/null; then
  echo 'Nacos seed configuration must not contain JWT key material or secret placeholders' >&2
  exit 1
fi

if PATH="${fake_bin}:${PATH}" \
  FAKE_NACOS_CURL_LOG="${curl_log}" \
  FAKE_NACOS_READINESS_COUNT="${readiness_count_file}" \
  BROWSER_ALLOWED_ORIGINS="https://community.invalid" \
  FRONTEND_PUBLIC_ORIGIN="https://community.invalid" \
  AUTH_REFRESH_COOKIE_SECURE="true" \
  AUTH_REFRESH_COOKIE_SAME_SITE="CrossSite" \
  GATEWAY_PUBLIC_BASE_URL="https://api.community.invalid" \
  OSS_PUBLIC_BASE_URL="https://api.community.invalid" \
  IM_GATEWAY_PUBLIC_WS_URL="wss://api.community.invalid/ws/im" \
  CONFIG_DIR="${CONFIG_DIR}" \
  NACOS_ADDR="http://nacos:8848" \
  "${SEED_SCRIPT}" >/dev/null 2>&1; then
  echo 'seed script must reject an invalid SameSite value before publishing' >&2
  exit 1
fi

if PATH="${fake_bin}:${PATH}" \
  FAKE_NACOS_CURL_LOG="${curl_log}" \
  FAKE_NACOS_READINESS_COUNT="${readiness_count_file}" \
  BROWSER_ALLOWED_ORIGINS="https://community.invalid" \
  FRONTEND_PUBLIC_ORIGIN="https://community.invalid" \
  AUTH_REFRESH_COOKIE_SECURE="false" \
  AUTH_REFRESH_COOKIE_SAME_SITE="None" \
  GATEWAY_PUBLIC_BASE_URL="https://api.community.invalid" \
  OSS_PUBLIC_BASE_URL="https://api.community.invalid" \
  IM_GATEWAY_PUBLIC_WS_URL="wss://api.community.invalid/ws/im" \
  CONFIG_DIR="${CONFIG_DIR}" \
  NACOS_ADDR="http://nacos:8848" \
  "${SEED_SCRIPT}" >/dev/null 2>&1; then
  echo 'seed script must reject SameSite=None when Secure is false' >&2
  exit 1
fi
grep -Fx '    identifier-hmac-secret: ${AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET}' \
  "${CONFIG_DIR}/community-app.yaml"
grep -Fx '    quota-hmac-secret: ${AUTH_PASSWORD_RESET_QUOTA_HMAC_SECRET}' \
  "${CONFIG_DIR}/community-app.yaml"
grep -Fx '    ttl-seconds: ${AUTH_PASSWORD_RESET_TTL_SECONDS:600}' \
  "${CONFIG_DIR}/community-app.yaml"
grep -Fx '    request-window-seconds: ${AUTH_PASSWORD_RESET_REQUEST_WINDOW_SECONDS:3600}' \
  "${CONFIG_DIR}/community-app.yaml"
grep -Fx '      ttl-seconds: ${AUTH_REGISTRATION_DRAFT_TTL_SECONDS:1800}' \
  "${CONFIG_DIR}/community-app.yaml"
grep -Fx '      window-seconds: ${AUTH_REGISTRATION_RESEND_WINDOW_SECONDS:3600}' \
  "${CONFIG_DIR}/community-app.yaml"
grep -Fx '    identifier-hmac-secret: ${AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET}' \
  "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"
if grep -F 'identifier-hmac-secret: ${AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET:' \
  "${CONFIG_DIR}/community-app.yaml" \
  "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml" >/dev/null; then
  echo 'password-reset identifier HMAC secret must not have a fallback' >&2
  exit 1
fi

backend_application_ymls=(
  "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-gateway/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-oss/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-im-gateway/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-im/im-core/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-im/im-realtime/src/main/resources/application.yml"
)

common_policy_imports=(
  community-feature-flags.yaml
  community-kafka-policy.yaml
)

for application_yml in "${backend_application_ymls[@]}"; do
  for data_id in "${common_policy_imports[@]}"; do
    grep -F "${data_id}" "${application_yml}"
  done
done

grep -F 'community-frontend-runtime.yaml' "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"
grep -F 'community-search-policy.yaml' "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"
grep -F 'community-notification-policy.yaml' "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"
grep -F 'community-upload-policy.yaml' "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"
grep -F 'community-upload-policy.yaml' "${REPO_ROOT}/backend/community-oss/src/main/resources/application.yml"
grep -F 'community-work-processing.yaml' "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"

kafka_producer_ymls=(
  "${REPO_ROOT}/backend/community-app/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-im/im-core/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-im/im-realtime/src/main/resources/application.yml"
)

for application_yml in "${kafka_producer_ymls[@]}"; do
  grep -F 'community.kafka-policy.producer.acks' "${application_yml}"
  grep -F 'community.kafka-policy.producer.enable-idempotence' "${application_yml}"
  grep -F 'community.kafka-policy.producer.max-in-flight-requests' "${application_yml}"
  grep -F 'community.kafka-policy.producer.metadata-max-age-ms' "${application_yml}"
  grep -F 'community.kafka-policy.producer.reconnect-backoff-ms' "${application_yml}"
  grep -F 'community.kafka-policy.producer.reconnect-backoff-max-ms' "${application_yml}"
  grep -F 'community.kafka-policy.producer.request-timeout-ms' "${application_yml}"
  grep -F 'community.kafka-policy.producer.delivery-timeout-ms' "${application_yml}"
  grep -F 'community.kafka-policy.producer.max-block-ms' "${application_yml}"
done

if grep -F 'trusted-proxy:' "${CONFIG_DIR}/community-shared.yaml"; then
  echo "trusted proxy CIDRs must be owned by each service config, not community-shared" >&2
  exit 1
fi
grep -F 'enabled: ${GATEWAY_TRUSTED_PROXY_ENABLED:false}' "${CONFIG_DIR}/community-gateway.yaml"
grep -F 'cidrs: ${GATEWAY_TRUSTED_PROXY_CIDRS:}' "${CONFIG_DIR}/community-gateway.yaml"
grep -Fx 'community:' "${CONFIG_DIR}/community-app.yaml"
grep -Fx '  web:' "${CONFIG_DIR}/community-app.yaml"
grep -Fx '    trusted-proxy:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'enabled: ${COMMUNITY_APP_TRUSTED_PROXY_ENABLED:false}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'cidrs: ${COMMUNITY_APP_TRUSTED_PROXY_CIDRS:}' "${CONFIG_DIR}/community-app.yaml"
if awk '
  $0 == "gateway:" { in_gateway = 1; next }
  in_gateway && /^[^ ]/ { in_gateway = 0 }
  in_gateway && $0 == "  trusted-proxy:" { found = 1 }
  END { exit found ? 0 : 1 }
' "${CONFIG_DIR}/community-app.yaml"; then
  echo "community-app must not consume the Gateway owner's trusted proxy path" >&2
  exit 1
fi
grep -F 'username: prometheus' "${CONFIG_DIR}/community-shared.yaml"
grep -F 'initialize: true' "${CONFIG_DIR}/community-search-policy.yaml"
grep -F -- '- /api/ops/**' "${CONFIG_DIR}/community-app.yaml"
grep -F 'max-file-size: 10GB' "${CONFIG_DIR}/community-app.yaml"
grep -F 'max-request-size: 10GB' "${CONFIG_DIR}/community-app.yaml"
grep -F 'base-url: ${OSS_CLIENT_BASE_URL:http://community-oss:18090}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'service-subject: ${OSS_CLIENT_SERVICE_SUBJECT:community-app}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'audience: ${OSS_CLIENT_AUDIENCE:community-oss}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'scope: ${OSS_CLIENT_SCOPE:oss.internal}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'token-ttl: ${OSS_CLIENT_TOKEN_TTL:PT5M}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'auto-confirm-batch-size: ${MARKET_ORDER_AUTO_CONFIRM_BATCH_SIZE:100}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'page-size: ${SEARCH_REINDEX_PAGE_SIZE:500}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'lock-ttl: ${SEARCH_REINDEX_LOCK_TTL:PT30M}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'keep-history: ${SEARCH_INDEX_KEEP_HISTORY:2}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'enabled: ${WALLET_TEST_CREDITS_ENABLED:false}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'grant-enabled: ${WALLET_TEST_CREDIT_GRANT_ENABLED:false}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'discard-enabled: ${WALLET_TEST_CREDIT_DISCARD_ENABLED:false}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'max-grant-per-request: ${WALLET_TEST_CREDIT_MAX_GRANT_PER_REQUEST:1000}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'max-discard-per-request: ${WALLET_TEST_CREDIT_MAX_DISCARD_PER_REQUEST:1000}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'grant-quota-per-user: ${WALLET_TEST_CREDIT_GRANT_QUOTA_PER_USER:5000}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'discard-quota-per-user: ${WALLET_TEST_CREDIT_DISCARD_QUOTA_PER_USER:5000}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'max-file-size: 10GB' "${CONFIG_DIR}/community-oss.yaml"
grep -F 'max-request-size: 10GB' "${CONFIG_DIR}/community-oss.yaml"
grep -F 'max-file-size: 10GB' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'max-request-size: 10GB' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'ticket-secret: ${DRIVE_SHARE_TICKET_SECRET:}' "${CONFIG_DIR}/community-app.yaml"
single_runtime_rendered="${tmp_dir}/single-runtime.yml"
cluster_runtime_rendered="${tmp_dir}/cluster-runtime.yml"
"${REPO_ROOT}/deploy/deployment.sh" config --stack single \
  --env-file deploy/stacks/single/.env.example --no-observability >"${single_runtime_rendered}"
"${REPO_ROOT}/deploy/deployment.sh" config --stack cluster \
  --env-file deploy/stacks/cluster/.env.example --no-observability >"${cluster_runtime_rendered}"
grep -F 'DRIVE_SHARE_TICKET_SECRET=' "${REPO_ROOT}/deploy/stacks/single/.env.example"
grep -F 'DRIVE_SHARE_TICKET_SECRET=' "${REPO_ROOT}/deploy/stacks/cluster/.env.example"
assert_runtime_environment_key "${single_runtime_rendered}" community-app DRIVE_SHARE_TICKET_SECRET
for app_number in 1 2 3; do
  assert_runtime_environment_key \
    "${cluster_runtime_rendered}" "community-app-${app_number}" DRIVE_SHARE_TICKET_SECRET
done
grep -F 'AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET=' "${REPO_ROOT}/deploy/stacks/single/.env.example"
grep -F 'AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET=' "${REPO_ROOT}/deploy/stacks/cluster/.env.example"
assert_runtime_environment_key \
  "${single_runtime_rendered}" community-app AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET
for app_number in 1 2 3; do
  assert_runtime_environment_key \
    "${cluster_runtime_rendered}" "community-app-${app_number}" AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET
done
grep -F 'allowed-mime-types:' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'image/jpeg' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'allowed-extensions:' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'jpg' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'avatar-upload-enabled: true' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'media-upload-enabled: true' "${CONFIG_DIR}/community-frontend-runtime.yaml"

grep -F '      enabled: ${AUTH_MAIL_ENABLED:true}' "${CONFIG_DIR}/community-app.yaml"

grep -F 'refresh:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'cleanup:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'interval-ms: 3600000' "${CONFIG_DIR}/community-app.yaml"
grep -F 'from: ${AUTH_MAIL_FROM:no-reply@community.local}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'subject: 注册验证码' "${CONFIG_DIR}/community-app.yaml"
grep -F 'username: ${SPRING_MAIL_USERNAME:}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'password: ${SPRING_MAIL_PASSWORD:}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'connectiontimeout: ${SPRING_MAIL_CONNECTION_TIMEOUT_MS:10000}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'timeout: ${SPRING_MAIL_READ_TIMEOUT_MS:10000}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'writetimeout: ${SPRING_MAIL_WRITE_TIMEOUT_MS:10000}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'required: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED:false}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'enable: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE:false}' "${CONFIG_DIR}/community-app.yaml"
grep -F 'http:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'idempotency:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'growth:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'business-zone-id: Asia/Shanghai' "${CONFIG_DIR}/community-app.yaml"
grep -F 'room-member-change:' "${CONFIG_DIR}/im-core.yaml"
grep -F 'publisher: kafka' "${CONFIG_DIR}/im-core.yaml"
grep -F 'max-members: 10000' "${CONFIG_DIR}/im-core.yaml"
grep -F 'max-chars: 10000' "${CONFIG_DIR}/im-core.yaml"
grep -F 'delay-ms: 30000' "${CONFIG_DIR}/community-work-processing.yaml"
grep -F 'process-batch-size: 50' "${CONFIG_DIR}/community-work-processing.yaml"
grep -F 'recovery-batch-size: 100' "${CONFIG_DIR}/community-work-processing.yaml"
grep -F 'processing-lease: 60s' "${CONFIG_DIR}/community-work-processing.yaml"
grep -F 'room-flush-interval-ms: 50' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'max-inbound-chars: 10000' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'max-inbound-buffer-frames: 64' "${CONFIG_DIR}/community-im-gateway.yaml"
grep -F 'snapshot-timeout-ms: 3000' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'event:' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'concurrency: 3' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'draining: ${im.realtime.worker.drain-enabled:false}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'maxConnections: ${im.realtime.worker.max-connections:10000}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'activeConnectionHint: ${im.realtime.worker.active-connection-hint:0}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'capacityWeight: ${im.realtime.worker.capacity-weight:100}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'acks: all' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'metadata-max-age-ms: 1000' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'reconnect-backoff-ms: 100' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'reconnect-backoff-max-ms: 1000' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'request-timeout-ms: 3000' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'delivery-timeout-ms: 5000' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'max-block-ms: 5000' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'command-private-text: im.command.private-text' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'event-user-block-relation-changed: im.event.user-block-relation-changed' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'outbox-topic: ${CONTENT_EVENTS_OUTBOX_TOPIC:eventbus.content}' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'kafka-topic: ${CONTENT_EVENTS_KAFKA_TOPIC:content.events}' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'outbox-topic: ${SOCIAL_EVENTS_OUTBOX_TOPIC:eventbus.social}' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'kafka-topic: ${SOCIAL_EVENTS_KAFKA_TOPIC:social.events}' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'outbox-topic: ${USER_EVENTS_OUTBOX_TOPIC:eventbus.user}' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'kafka-topic: ${USER_EVENTS_KAFKA_TOPIC:user.events}' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'topic: projection.im.policy' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'key-prefix: "im:"' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'ttl: PT30S' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'heartbeat-interval: PT10S' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'routed-command-topic: im.command.room-fanout-routed' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'worker-inbox-slot: ${IM_ROOM_FANOUT_WORKER_INBOX_SLOT}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'publish-timeout: PT1S' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'group-id: im-core' "${CONFIG_DIR}/im-core.yaml"
grep -F 'auto-offset-reset: earliest' "${CONFIG_DIR}/im-core.yaml"
grep -F 'group-id: im-realtime-${IM_REALTIME_WORKER_ID:${HOSTNAME:local}}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'auto-offset-reset: latest' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'service: im-realtime-worker' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'worker-id: ${IM_REALTIME_WORKER_ID:${HOSTNAME:local}}' "${CONFIG_DIR}/im-realtime.yaml"

nacos_bootstrap_env_vars=(
  AUTH_REFRESH_COOKIE_SECURE
  AUTH_REFRESH_COOKIE_SAME_SITE
  AUTH_MAIL_ENABLED
  AUTH_MAIL_FROM
  AUTH_REGISTRATION_EXPOSE_CODE
)
for compose_yml in \
  "${REPO_ROOT}/deploy/compose/infra/nacos/single.yml" \
  "${REPO_ROOT}/deploy/compose/infra/nacos/cluster.yml"
do
  for env_var in "${nacos_bootstrap_env_vars[@]}"; do
    test "$(grep -Fc -- "- ${env_var}=" "${compose_yml}")" -eq 1
  done
done

smtp_runtime_env_vars=(
  SPRING_MAIL_HOST
  SPRING_MAIL_PORT
  SPRING_MAIL_USERNAME
  SPRING_MAIL_PASSWORD
  SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH
  SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE
  SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED
  SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE
  SPRING_MAIL_CONNECTION_TIMEOUT_MS
  SPRING_MAIL_READ_TIMEOUT_MS
  SPRING_MAIL_WRITE_TIMEOUT_MS
)
for env_var in "${smtp_runtime_env_vars[@]}"; do
  assert_runtime_environment_key "${single_runtime_rendered}" community-app "${env_var}"
  for app_number in 1 2 3; do
    assert_runtime_environment_key \
      "${cluster_runtime_rendered}" "community-app-${app_number}" "${env_var}"
  done
  grep -F "${env_var}=" "${REPO_ROOT}/deploy/stacks/single/.env.example"
  grep -F "${env_var}=" "${REPO_ROOT}/deploy/stacks/cluster/.env.example"
done

community_auth_runtime_env_vars=(
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
)
for env_var in "${community_auth_runtime_env_vars[@]}"; do
  assert_runtime_environment_key "${single_runtime_rendered}" community-app "${env_var}"
  for app_number in 1 2 3; do
    assert_runtime_environment_key \
      "${cluster_runtime_rendered}" "community-app-${app_number}" "${env_var}"
  done
  grep -F "${env_var}=" "${REPO_ROOT}/deploy/stacks/single/.env.example"
  grep -F "${env_var}=" "${REPO_ROOT}/deploy/stacks/cluster/.env.example"
done

nacos_owned_env_vars=(
  OSS_CLIENT_BASE_URL
  OSS_CLIENT_SERVICE_SUBJECT
  OSS_CLIENT_AUDIENCE
  OSS_CLIENT_SCOPE
  OSS_CLIENT_TOKEN_TTL
  SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE
  SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE
  BROWSER_ALLOWED_ORIGINS
  FRONTEND_PUBLIC_ORIGIN
  GATEWAY_PUBLIC_BASE_URL
  AUTH_ORIGIN_GUARD_ALLOWED_ORIGINS
  AUTH_MAIL_ENABLED
  AUTH_MAIL_FROM
  AUTH_PASSWORD_RESET_BASE_URL
  AUTH_REGISTRATION_EXPOSE_CODE
  AUTH_REFRESH_COOKIE_SECURE
  AUTH_REFRESH_COOKIE_SAME_SITE
  OSS_OBJECT_STORE_MODE
  OSS_OBJECT_STORE_ENDPOINT
  OSS_OBJECT_STORE_REGION
  OSS_OBJECT_STORE_BUCKET
  OSS_OBJECT_STORE_PATH_STYLE
  OSS_PUBLIC_BASE_URL
  GATEWAY_CORS_ALLOWED_ORIGINS
  GATEWAY_IM_EDGE_SERVICE_ID
  IM_GATEWAY_CORS_ALLOWED_ORIGINS
  IM_GATEWAY_PUBLIC_WS_URL
  IM_REALTIME_WORKER_SERVICE_ID
  IM_WS_PATH
  IM_CORE_SERVICE_ID
  IM_COMMUNITY_SERVICE_ID
  IM_ROOM_FLUSH_INTERVAL_MS
  IM_WS_OUTBOUND_BUFFER_SIZE
  IM_CORS_ALLOWED_ORIGINS
  IM_REALTIME_CONSUMER_GROUP
)

for env_var in "${nacos_owned_env_vars[@]}"; do
  for service_family in \
    community-app community-oss community-gateway community-im-gateway im-core im-realtime
  do
    if runtime_environment_key_exists "${single_runtime_rendered}" "${service_family}" "${env_var}"; then
      echo "${env_var} must be supplied through Nacos Config, not runtime compose env" >&2
      exit 1
    fi
    for replica in 1 2 3; do
      if runtime_environment_key_exists \
        "${cluster_runtime_rendered}" "${service_family}-${replica}" "${env_var}"
      then
        echo "${env_var} must be supplied through Nacos Config, not runtime compose env" >&2
        exit 1
      fi
    done
  done
done

if rg -n -i '^[[:space:]]*(?:-[[:space:]]*)?[^:#\r\n]*(?:password|secret|access[_-]?key|hmac|token):[[:space:]]*[^$[:space:]]+' "${CONFIG_DIR}"; then
  echo "seed configs must not contain literal secret-like values" >&2
  exit 1
fi

if rg -n -i '(change-me|changeme|dummy|example-secret|example-password)' "${CONFIG_DIR}"; then
  echo "seed configs must not contain fake secret values" >&2
  exit 1
fi

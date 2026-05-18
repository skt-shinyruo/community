#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
CONFIG_DIR="${REPO_ROOT}/deploy/nacos/config"
SEED_SCRIPT="${REPO_ROOT}/deploy/nacos/seed-configs.sh"

required_data_ids=(
  community-shared.yaml
  community-feature-flags.yaml
  community-degradation.yaml
  community-canary-routing.yaml
  community-frontend-runtime.yaml
  community-cache-policy.yaml
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

for data_id in "${required_data_ids[@]}"; do
  test -s "${CONFIG_DIR}/${data_id}"
  grep -F "${data_id}" "${SEED_SCRIPT}"
done

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
  community-degradation.yaml
  community-cache-policy.yaml
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
grep -F 'community-canary-routing.yaml' "${REPO_ROOT}/backend/community-gateway/src/main/resources/application.yml"

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

grep -F 'trusted-proxy:' "${CONFIG_DIR}/community-shared.yaml"
grep -F 'username: prometheus' "${CONFIG_DIR}/community-shared.yaml"
grep -F 'initialize: true' "${CONFIG_DIR}/community-app.yaml"
grep -F -- '- /api/ops/**' "${CONFIG_DIR}/community-app.yaml"
grep -F 'max-file-size: 10GB' "${CONFIG_DIR}/community-app.yaml"
grep -F 'max-request-size: 10GB' "${CONFIG_DIR}/community-app.yaml"
grep -F 'max-file-size: 10GB' "${CONFIG_DIR}/community-oss.yaml"
grep -F 'max-request-size: 10GB' "${CONFIG_DIR}/community-oss.yaml"
grep -F 'max-file-size: 10GB' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'max-request-size: 10GB' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'allowed-mime-types:' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'image/jpeg' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'allowed-extensions:' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'jpg' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'avatar-upload-enabled: true' "${CONFIG_DIR}/community-frontend-runtime.yaml"
grep -F 'media-upload-enabled: true' "${CONFIG_DIR}/community-frontend-runtime.yaml"

awk '
  /^auth:/ { in_auth = 1 }
  in_auth && /^  registration:/ { in_registration = 1 }
  in_registration && /^    mail:/ { in_mail = 1 }
  in_mail && /^      enabled:/ {
    if ($2 == "true") found = 1
    else exit 1
  }
  END { exit found ? 0 : 1 }
' "${CONFIG_DIR}/community-app.yaml"

grep -F 'refresh:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'cleanup:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'interval-ms: 3600000' "${CONFIG_DIR}/community-app.yaml"
grep -F 'reset-base-url: http://localhost:12881' "${CONFIG_DIR}/community-app.yaml"
grep -F 'from: no-reply@community.local' "${CONFIG_DIR}/community-app.yaml"
grep -F 'subject: 注册验证码' "${CONFIG_DIR}/community-app.yaml"
grep -F 'http://localhost:5173' "${CONFIG_DIR}/community-app.yaml"
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
grep -F 'timeout-ms: 1500' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'event:' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'concurrency: 3' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'draining: ${im.realtime.worker.drain-enabled:false}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'maxConnections: ${im.realtime.worker.max-connections:10000}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'activeConnectionHint: ${im.realtime.worker.active-connection-hint:0}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'shardGroup: ${im.realtime.worker.shard-group:default}' "${CONFIG_DIR}/im-realtime.yaml"
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
grep -F 'post-topic: projection.search.post' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'topic: projection.im.policy' "${CONFIG_DIR}/community-kafka-policy.yaml"
grep -F 'group-id: im-core' "${CONFIG_DIR}/im-core.yaml"
grep -F 'auto-offset-reset: earliest' "${CONFIG_DIR}/im-core.yaml"
grep -F 'group-id: im-realtime-${IM_REALTIME_WORKER_ID:${HOSTNAME:local}}' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'auto-offset-reset: latest' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'service: im-realtime-worker' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'worker-id: ${IM_REALTIME_WORKER_ID:${HOSTNAME:local}}' "${CONFIG_DIR}/im-realtime.yaml"

nacos_owned_env_vars=(
  OSS_CLIENT_BASE_URL
  SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE
  SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE
  AUTH_ORIGIN_GUARD_ALLOWED_ORIGINS
  AUTH_MAIL_ENABLED
  AUTH_PASSWORD_RESET_BASE_URL
  AUTH_REGISTRATION_DRAFT_TTL_SECONDS
  AUTH_REGISTRATION_EXPOSE_CODE
  XXL_JOB_ENABLED
  XXL_JOB_ADMIN_INGRESS_URL
  XXL_JOB_ADMIN_ADDRESSES
  XXL_JOB_EXECUTOR_APPNAME
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

for compose_yml in \
  "${REPO_ROOT}/deploy/compose.runtime.services.single.yml" \
  "${REPO_ROOT}/deploy/compose.runtime.services.cluster.yml"
do
  for env_var in "${nacos_owned_env_vars[@]}"; do
    if grep -F -- "- ${env_var}=" "${compose_yml}"; then
      echo "${env_var} must be supplied through Nacos Config, not runtime compose env: ${compose_yml}" >&2
      exit 1
    fi
  done
done

if rg -n -i '(password|secret|access[_-]?key|hmac|token):[[:space:]]*[^$[:space:]]+' "${CONFIG_DIR}"; then
  echo "seed configs must not contain literal secret-like values" >&2
  exit 1
fi

if rg -n -i '(change-me|changeme|dummy|example-secret|example-password)' "${CONFIG_DIR}"; then
  echo "seed configs must not contain fake secret values" >&2
  exit 1
fi

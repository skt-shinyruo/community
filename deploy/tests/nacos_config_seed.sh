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

awk '
  /^auth:/ { in_auth = 1 }
  in_auth && /^  registration:/ { in_registration = 1 }
  in_registration && /^    mail:/ { in_mail = 1 }
  in_mail && /^      enabled:/ {
    if ($2 == "false") found = 1
    else exit 1
  }
  END { exit found ? 0 : 1 }
' "${CONFIG_DIR}/community-app.yaml"

grep -F 'refresh:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'cleanup:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'interval-ms: 3600000' "${CONFIG_DIR}/community-app.yaml"
grep -F 'reset-base-url: ""' "${CONFIG_DIR}/community-app.yaml"
grep -F 'from: no-reply@community.local' "${CONFIG_DIR}/community-app.yaml"
grep -F 'subject: 注册验证码' "${CONFIG_DIR}/community-app.yaml"
grep -F 'http:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'idempotency:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'growth:' "${CONFIG_DIR}/community-app.yaml"
grep -F 'business-zone-id: Asia/Shanghai' "${CONFIG_DIR}/community-app.yaml"
grep -F 'room-member-change:' "${CONFIG_DIR}/im-core.yaml"
grep -F 'publisher: kafka' "${CONFIG_DIR}/im-core.yaml"
grep -F 'max-members: 10000' "${CONFIG_DIR}/im-core.yaml"
grep -F 'max-chars: 10000' "${CONFIG_DIR}/im-core.yaml"
grep -F 'room-flush-interval-ms: 100' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'max-inbound-chars: 10000' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'timeout-ms: 1500' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'event:' "${CONFIG_DIR}/im-realtime.yaml"
grep -F 'concurrency: 3' "${CONFIG_DIR}/im-realtime.yaml"

if rg -n -i '(password|secret|access[_-]?key|hmac|token):[[:space:]]*[^$[:space:]]+' "${CONFIG_DIR}"; then
  echo "seed configs must not contain literal secret-like values" >&2
  exit 1
fi

if rg -n -i '(change-me|changeme|dummy|example-secret|example-password)' "${CONFIG_DIR}"; then
  echo "seed configs must not contain fake secret values" >&2
  exit 1
fi

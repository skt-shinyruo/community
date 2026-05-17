#!/usr/bin/env sh
set -eu

NACOS_ADDR="${NACOS_ADDR:-http://nacos:8848}"
NACOS_GROUP="${NACOS_CONFIG_GROUP:-COMMUNITY}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"
CONFIG_DIR="${CONFIG_DIR:-/nacos/config}"

data_ids="
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
"

echo "[nacos-config-bootstrap] waiting for ${NACOS_ADDR}"
health_attempt=1
while [ "${health_attempt}" -le 120 ]; do
  if curl -fsS "${NACOS_ADDR}/nacos/actuator/health" >/dev/null 2>&1; then
    break
  fi
  if [ "${health_attempt}" -eq 120 ]; then
    echo "[nacos-config-bootstrap] nacos did not become healthy" >&2
    exit 1
  fi
  health_attempt=$((health_attempt + 1))
  sleep 1
done

for data_id in ${data_ids}; do
  file="${CONFIG_DIR}/${data_id}"
  test -s "${file}"
  echo "[nacos-config-bootstrap] publishing ${data_id}"
  if [ -n "${NACOS_NAMESPACE}" ]; then
    publish_response="$(curl -fsS -X POST "${NACOS_ADDR}/nacos/v1/cs/configs" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "group=${NACOS_GROUP}" \
      --data-urlencode "tenant=${NACOS_NAMESPACE}" \
      --data-urlencode "type=yaml" \
      --data-urlencode "content@${file}")"
  else
    publish_response="$(curl -fsS -X POST "${NACOS_ADDR}/nacos/v1/cs/configs" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "group=${NACOS_GROUP}" \
      --data-urlencode "type=yaml" \
      --data-urlencode "content@${file}")"
  fi
  if [ "${publish_response}" != "true" ]; then
    echo "[nacos-config-bootstrap] failed to publish ${data_id}: ${publish_response}" >&2
    exit 1
  fi
done

echo "[nacos-config-bootstrap] done"

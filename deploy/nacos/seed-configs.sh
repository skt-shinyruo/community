#!/usr/bin/env sh
set -eu

NACOS_ADDR="${NACOS_ADDR:-http://nacos:8848}"
NACOS_GROUP="${NACOS_CONFIG_GROUP:-COMMUNITY}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"
CONFIG_DIR="${CONFIG_DIR:-/nacos/config}"
BROWSER_ALLOWED_ORIGINS="${BROWSER_ALLOWED_ORIGINS:?BROWSER_ALLOWED_ORIGINS is required}"
FRONTEND_PUBLIC_ORIGIN="${FRONTEND_PUBLIC_ORIGIN:?FRONTEND_PUBLIC_ORIGIN is required}"
GATEWAY_PUBLIC_BASE_URL="${GATEWAY_PUBLIC_BASE_URL:?GATEWAY_PUBLIC_BASE_URL is required}"
OSS_PUBLIC_BASE_URL="${OSS_PUBLIC_BASE_URL:?OSS_PUBLIC_BASE_URL is required}"
IM_GATEWAY_PUBLIC_WS_URL="${IM_GATEWAY_PUBLIC_WS_URL:?IM_GATEWAY_PUBLIC_WS_URL is required}"

validate_template_value() {
  name="$1"
  value="$2"
  if printf '%s' "${value}" | grep -q '[[:cntrl:]]'; then
    echo "[nacos-config-bootstrap] ${name} must not contain control characters" >&2
    exit 1
  fi
}

validate_template_value BROWSER_ALLOWED_ORIGINS "${BROWSER_ALLOWED_ORIGINS}"
validate_template_value FRONTEND_PUBLIC_ORIGIN "${FRONTEND_PUBLIC_ORIGIN}"
validate_template_value GATEWAY_PUBLIC_BASE_URL "${GATEWAY_PUBLIC_BASE_URL}"
validate_template_value OSS_PUBLIC_BASE_URL "${OSS_PUBLIC_BASE_URL}"
validate_template_value IM_GATEWAY_PUBLIC_WS_URL "${IM_GATEWAY_PUBLIC_WS_URL}"

rendered_config_dir="$(mktemp -d)"
cleanup_rendered_config() {
  rm -rf "${rendered_config_dir}"
}
trap cleanup_rendered_config EXIT

escape_sed_replacement() {
  printf '%s' "$1" | sed 's/[\\&|]/\\&/g'
}

escaped_browser_origins="$(escape_sed_replacement "${BROWSER_ALLOWED_ORIGINS}")"
escaped_frontend_origin="$(escape_sed_replacement "${FRONTEND_PUBLIC_ORIGIN}")"
escaped_gateway_base_url="$(escape_sed_replacement "${GATEWAY_PUBLIC_BASE_URL}")"
escaped_oss_base_url="$(escape_sed_replacement "${OSS_PUBLIC_BASE_URL}")"
escaped_im_ws_url="$(escape_sed_replacement "${IM_GATEWAY_PUBLIC_WS_URL}")"
render_config() {
  data_id="$1"
  source_file="${CONFIG_DIR}/${data_id}"
  rendered_file="${rendered_config_dir}/${data_id}"
  test -s "${source_file}"
  sed \
    -e "s|\${BROWSER_ALLOWED_ORIGINS}|${escaped_browser_origins}|g" \
    -e "s|\${FRONTEND_PUBLIC_ORIGIN}|${escaped_frontend_origin}|g" \
    -e "s|\${GATEWAY_PUBLIC_BASE_URL}|${escaped_gateway_base_url}|g" \
    -e "s|\${OSS_PUBLIC_BASE_URL}|${escaped_oss_base_url}|g" \
    -e "s|\${IM_GATEWAY_PUBLIC_WS_URL}|${escaped_im_ws_url}|g" \
    "${source_file}" >"${rendered_file}"
  for placeholder in BROWSER_ALLOWED_ORIGINS FRONTEND_PUBLIC_ORIGIN GATEWAY_PUBLIC_BASE_URL OSS_PUBLIC_BASE_URL IM_GATEWAY_PUBLIC_WS_URL; do
    if grep -F "\${${placeholder}}" "${rendered_file}" >/dev/null; then
      echo "[nacos-config-bootstrap] unresolved ${placeholder} placeholder in ${data_id}" >&2
      exit 1
    fi
  done
  printf '%s\n' "${rendered_file}"
}

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
is_ready() {
  readiness_response="$(curl -fsS "${NACOS_ADDR}/nacos/v3/admin/core/state/readiness" 2>/dev/null)" || return 1
  printf '%s' "${readiness_response}" \
    | grep -Eq '"code"[[:space:]]*:[[:space:]]*0([,}])'
}

health_attempt=1
while [ "${health_attempt}" -le 120 ]; do
  if is_ready; then
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
  file="$(render_config "${data_id}")"
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

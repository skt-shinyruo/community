#!/usr/bin/env sh
set -eu

NACOS_ADDR="${NACOS_ADDR:-http://nacos:8848}"
NACOS_GROUP="${NACOS_CONFIG_GROUP:-COMMUNITY}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"
CONFIG_DIR="${CONFIG_DIR:-/nacos/config}"
BROWSER_ALLOWED_ORIGINS="${BROWSER_ALLOWED_ORIGINS:?BROWSER_ALLOWED_ORIGINS is required}"
FRONTEND_PUBLIC_ORIGIN="${FRONTEND_PUBLIC_ORIGIN:?FRONTEND_PUBLIC_ORIGIN is required}"
AUTH_REFRESH_COOKIE_SECURE="${AUTH_REFRESH_COOKIE_SECURE:-true}"
AUTH_REFRESH_COOKIE_SAME_SITE="${AUTH_REFRESH_COOKIE_SAME_SITE:-Lax}"
AUTH_MAIL_ENABLED="${AUTH_MAIL_ENABLED:-true}"
AUTH_MAIL_FROM="${AUTH_MAIL_FROM:-no-reply@community.local}"
AUTH_REGISTRATION_EXPOSE_CODE="${AUTH_REGISTRATION_EXPOSE_CODE:-false}"
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
validate_template_value AUTH_MAIL_FROM "${AUTH_MAIL_FROM}"
validate_template_value GATEWAY_PUBLIC_BASE_URL "${GATEWAY_PUBLIC_BASE_URL}"
validate_template_value OSS_PUBLIC_BASE_URL "${OSS_PUBLIC_BASE_URL}"
validate_template_value IM_GATEWAY_PUBLIC_WS_URL "${IM_GATEWAY_PUBLIC_WS_URL}"
case "${AUTH_REFRESH_COOKIE_SECURE}" in
  true|false) ;;
  *)
    echo "[nacos-config-bootstrap] AUTH_REFRESH_COOKIE_SECURE must be true or false" >&2
    exit 1
    ;;
esac
case "${AUTH_REFRESH_COOKIE_SAME_SITE}" in
  Lax|Strict|None) ;;
  *)
    echo "[nacos-config-bootstrap] AUTH_REFRESH_COOKIE_SAME_SITE must be Lax, Strict, or None" >&2
    exit 1
    ;;
esac
if [ "${AUTH_REFRESH_COOKIE_SAME_SITE}" = "None" ] && [ "${AUTH_REFRESH_COOKIE_SECURE}" != "true" ]; then
  echo "[nacos-config-bootstrap] SameSite=None requires AUTH_REFRESH_COOKIE_SECURE=true" >&2
  exit 1
fi
validate_boolean() {
  boolean_name="$1"
  boolean_value="$2"
  case "${boolean_value}" in
    true|false) ;;
    *)
      echo "[nacos-config-bootstrap] ${boolean_name} must be true or false" >&2
      exit 1
      ;;
  esac
}
validate_boolean AUTH_MAIL_ENABLED "${AUTH_MAIL_ENABLED}"
validate_boolean AUTH_REGISTRATION_EXPOSE_CODE "${AUTH_REGISTRATION_EXPOSE_CODE}"

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
escaped_refresh_cookie_secure="$(escape_sed_replacement "${AUTH_REFRESH_COOKIE_SECURE}")"
escaped_refresh_cookie_same_site="$(escape_sed_replacement "${AUTH_REFRESH_COOKIE_SAME_SITE}")"
escaped_mail_enabled="$(escape_sed_replacement "${AUTH_MAIL_ENABLED}")"
escaped_mail_from="$(escape_sed_replacement "${AUTH_MAIL_FROM}")"
escaped_registration_expose_code="$(escape_sed_replacement "${AUTH_REGISTRATION_EXPOSE_CODE}")"
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
    -e "s|\${AUTH_REFRESH_COOKIE_SECURE:true}|${escaped_refresh_cookie_secure}|g" \
    -e "s|\${AUTH_REFRESH_COOKIE_SAME_SITE:Lax}|${escaped_refresh_cookie_same_site}|g" \
    -e "s|\${AUTH_MAIL_ENABLED:true}|${escaped_mail_enabled}|g" \
    -e "s|\${AUTH_MAIL_FROM:no-reply@community.local}|${escaped_mail_from}|g" \
    -e "s|\${AUTH_REGISTRATION_EXPOSE_CODE:false}|${escaped_registration_expose_code}|g" \
    -e "s|\${GATEWAY_PUBLIC_BASE_URL}|${escaped_gateway_base_url}|g" \
    -e "s|\${OSS_PUBLIC_BASE_URL}|${escaped_oss_base_url}|g" \
    -e "s|\${IM_GATEWAY_PUBLIC_WS_URL}|${escaped_im_ws_url}|g" \
    "${source_file}" >"${rendered_file}"
  for placeholder in BROWSER_ALLOWED_ORIGINS FRONTEND_PUBLIC_ORIGIN AUTH_REFRESH_COOKIE_SECURE AUTH_REFRESH_COOKIE_SAME_SITE AUTH_MAIL_ENABLED AUTH_MAIL_FROM AUTH_REGISTRATION_EXPOSE_CODE GATEWAY_PUBLIC_BASE_URL OSS_PUBLIC_BASE_URL IM_GATEWAY_PUBLIC_WS_URL; do
    if grep -F "\${${placeholder}" "${rendered_file}" >/dev/null; then
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

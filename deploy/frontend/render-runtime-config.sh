#!/bin/sh
set -eu

output_path="${1:?runtime config output path is required}"
output_dir="$(dirname "${output_path}")"
api_base_url="${FRONTEND_RUNTIME_API_BASE_URL-${GATEWAY_PUBLIC_BASE_URL-}}"
im_http_base_url="${FRONTEND_RUNTIME_IM_HTTP_BASE_URL-${GATEWAY_PUBLIC_BASE_URL-}}"

umask 077
mkdir -p "${output_dir}"
temporary_path="$(mktemp "${output_dir}/app-config.js.tmp.XXXXXX")"
trap 'rm -f "${temporary_path}"' EXIT HUP INT TERM

jq -nr \
  --arg apiBaseUrl "${api_base_url}" \
  --arg imHttpBaseUrl "${im_http_base_url}" \
  '
    def trimmed: gsub("^[[:space:]]+|[[:space:]]+$"; "");
    "globalThis.__COMMUNITY_RUNTIME_CONFIG__ = Object.freeze(" +
    ({
      apiBaseUrl: ($apiBaseUrl | trimmed),
      imHttpBaseUrl: ($imHttpBaseUrl | trimmed)
    } | tojson) +
    ");"
  ' >"${temporary_path}"

chmod 0444 "${temporary_path}"
mv -f "${temporary_path}" "${output_path}"
trap - EXIT HUP INT TERM

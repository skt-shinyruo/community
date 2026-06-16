#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${repo_root}"

es_url="${ELASTICSEARCH_URL:-http://localhost:12888}"
gateway_url="${COMMUNITY_GATEWAY_URL:-http://localhost:12880}"
timeout_seconds="${OBSERVABILITY_SMOKE_TIMEOUT_SECONDS:-90}"
sleep_seconds="${OBSERVABILITY_SMOKE_POLL_SECONDS:-5}"

fail() {
  echo "observability smoke failed: $*" >&2
  exit 1
}

case "${timeout_seconds}" in
  '' | *[!0-9]*)
    fail "OBSERVABILITY_SMOKE_TIMEOUT_SECONDS must be a positive integer"
    ;;
esac

case "${sleep_seconds}" in
  '' | *[!0-9]*)
    fail "OBSERVABILITY_SMOKE_POLL_SECONDS must be a positive integer"
    ;;
esac

if [ "${timeout_seconds}" -le 0 ]; then
  fail "OBSERVABILITY_SMOKE_TIMEOUT_SECONDS must be greater than 0"
fi

if [ "${sleep_seconds}" -le 0 ]; then
  fail "OBSERVABILITY_SMOKE_POLL_SECONDS must be greater than 0"
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

json_escape() {
  sed 's/\\/\\\\/g; s/"/\\"/g'
}

wait_for_elasticsearch() {
  local deadline=$((SECONDS + timeout_seconds))
  until curl -fsS "${es_url}/_cluster/health" >/dev/null 2>&1; do
    if [ "${SECONDS}" -ge "${deadline}" ]; then
      fail "Elasticsearch did not become available at ${es_url}"
    fi
    sleep "${sleep_seconds}"
  done
}

search_count() {
  local index="$1"
  local query_json="$2"

  curl -fsS -H 'Content-Type: application/json' \
    "${es_url}/${index}/_count" \
    -d "{\"query\":${query_json}}" |
    sed -n 's/.*"count"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p'
}

require_count() {
  local label="$1"
  local index="$2"
  local query_json="$3"
  local deadline=$((SECONDS + timeout_seconds))
  local count="0"

  until [ "${count:-0}" -gt 0 ]; do
    if ! count="$(search_count "${index}" "${query_json}")"; then
      count="0"
    fi
    count="${count:-0}"
    if [ "${count}" -gt 0 ]; then
      echo "${label}: found ${count}"
      return 0
    fi
    if [ "${SECONDS}" -ge "${deadline}" ]; then
      fail "${label} not found in ${index}; query=${query_json}"
    fi
    sleep "${sleep_seconds}"
  done
}

require_log_field() {
  local field="$1"

  require_count "log field ${field}" "logs-community-default" \
    "{\"bool\":{\"filter\":[{\"exists\":{\"field\":\"${field}\"}},{\"term\":{\"service.namespace\":\"community\"}}]}}"
}

require_event_category() {
  local category="$1"
  local escaped_category

  escaped_category="$(printf '%s' "${category}" | json_escape)"
  require_count "runtime event category ${category}" "logs-community-default" \
    "{\"term\":{\"event.category\":\"${escaped_category}\"}}"
}

request_trace_id() {
  local headers_file
  local body_file
  local deadline=$((SECONDS + timeout_seconds))
  local trace_id

  headers_file="${tmp_dir}/runtime-config.headers"
  body_file="${tmp_dir}/runtime-config.body"

  while :; do
    : >"${headers_file}"
    : >"${body_file}"

    if curl -fsS -D "${headers_file}" -o "${body_file}" "${gateway_url}/api/runtime-config" >/dev/null 2>&1; then
      trace_id="$(sed -n 's/.*"traceId"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F]\{32\}\)".*/\1/p' "${body_file}" | head -n 1)"
      if [ -z "${trace_id}" ]; then
        trace_id="$(sed -n 's/^[Tt][Rr][Aa][Cc][Ee][Pp][Aa][Rr][Ee][Nn][Tt][[:space:]]*:[[:space:]]*//p' "${headers_file}" |
          tr -d '\r' |
          sed -n 's/^00-\([0-9a-fA-F]\{32\}\)-[0-9a-fA-F]\{16\}-[0-9a-fA-F]\{2\}$/\1/p' |
          head -n 1)"
      fi

      if [ -n "${trace_id}" ]; then
        printf '%s\n' "${trace_id}"
        return 0
      fi
    fi

    if [ "${SECONDS}" -ge "${deadline}" ]; then
      echo "response body:" >&2
      cat "${body_file}" >&2
      echo "response headers:" >&2
      cat "${headers_file}" >&2
      fail "could not extract trace id from ${gateway_url}/api/runtime-config"
    fi
    sleep "${sleep_seconds}"
  done
}

wait_for_elasticsearch
trace_id="$(request_trace_id)"
escaped_trace_id="$(printf '%s' "${trace_id}" | json_escape)"
echo "trace.id=${trace_id}"

require_count "backend JSON logs" "logs-community-default" \
  '{"bool":{"filter":[{"exists":{"field":"service.name"}},{"term":{"service.namespace":"community"}}]}}'

for field in service.name service.version service.namespace deployment.environment; do
  require_log_field "${field}"
done

require_count "runtime stability events" "logs-community-default" \
  '{"bool":{"filter":[{"exists":{"field":"event.category"}},{"terms":{"event.category":["runtime","database","messaging","access","cache","http_client","job","security","logging"]}}]}}'

if [ -n "${OBSERVABILITY_EXPECT_EVENT_CATEGORIES:-}" ]; then
  old_ifs="${IFS}"
  IFS=','
  for category in ${OBSERVABILITY_EXPECT_EVENT_CATEGORIES}; do
    IFS="${old_ifs}"
    category="$(printf '%s' "${category}" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    if [ -n "${category}" ]; then
      require_event_category "${category}"
    fi
    IFS=','
  done
  IFS="${old_ifs}"
fi

require_count "request trace" "traces-*" \
  "{\"term\":{\"trace.id\":\"${escaped_trace_id}\"}}"

require_count "request-correlated logs" "logs-community-default" \
  "{\"term\":{\"trace.id\":\"${escaped_trace_id}\"}}"

if [ "${OBSERVABILITY_EXPECT_DIAGNOSTICS:-false}" = "true" ]; then
  require_count "runtime diagnostics events" "logs-community-default" \
    '{"term":{"event.category":"runtime_diagnostics"}}'
fi

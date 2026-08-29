#!/usr/bin/env bash
# Runs a k6 profile via docker. Usage: ./run.sh [profile] (default: smoke)
set -euo pipefail

suite_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
repo_root="$(CDPATH= cd -- "${suite_root}/../.." && pwd)"
results_dir="${repo_root}/temp/k6-results"

profile="${1:-smoke}"
if [ ! -f "${suite_root}/scenarios/${profile}.js" ]; then
  echo "[k6] unknown profile: ${profile}" >&2
  echo "[k6] supported profiles: smoke, api-mix, hot-path, write-paths, im-ws, soak, stress, spike" >&2
  exit 2
fi

mkdir -p "${results_dir}"
timestamp="$(date -u +%Y-%m-%dT%H-%M-%S-000Z)"
summary_path="/results/${profile}-${timestamp}.json"
image="${K6_DOCKER_IMAGE:-grafana/k6:0.51.0}"

env_args=()
while IFS= read -r name; do
  if [[ -n "${name}" ]] && [[ -v "${name}" ]]; then
    env_args+=(-e "${name}=${!name}")
  fi
done <<'EOF'
K6_BASE_URL
K6_WS_URL
K6_USERNAME
K6_PASSWORD
K6_LOGIN_EACH_ITERATION
K6_WRITE_RATIO
K6_READ_SIZE
K6_BOARD_ID
K6_POST_ID
K6_THINK_MIN_MS
K6_THINK_MAX_MS
K6_IM_HOLD_SECONDS
K6_IM_PING_INTERVAL_SECONDS
K6_IM_SEND_MESSAGES
K6_IM_ROOM_ID
K6_POST_TAG
K6_POST_CATEGORY_ID
K6_ALLOW_WRITES
K6_HTTP_FAILED_RATE
K6_HTTP_P95_MS
K6_HTTP_P99_MS
K6_CHECK_RATE
K6_WS_CONNECT_P95_MS
K6_WS_SESSION_P95_MIN_MS
K6_NO_CONNECTION_REUSE
K6_USER_AGENT
EOF

network_args=()
if [ "$(uname -s)" = "Linux" ]; then
  network_args=(--network host)
fi

echo "[k6] profile=${profile}"
echo "[k6] summary=${results_dir}/${profile}-${timestamp}.json"
echo "[k6] image=${image}"

cd "${repo_root}"
exec docker run --rm \
  "${network_args[@]}" \
  --user "$(id -u):$(id -g)" \
  -v "${suite_root}:/scripts:ro" \
  -v "${results_dir}:/results" \
  "${env_args[@]}" \
  "${image}" \
  run --summary-export "${summary_path}" \
  "/scripts/scenarios/${profile}.js"

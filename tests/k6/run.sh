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
image="${K6_DOCKER_IMAGE:-grafana/k6:0.54.0}"

env_args=()
for name in $(compgen -e K6_); do
  env_args+=(-e "${name}=${!name}")
done

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

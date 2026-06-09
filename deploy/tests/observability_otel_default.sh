#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${repo_root}"

if rg -n -- '--no-consistency' deploy/deployment.sh >/dev/null; then
  echo "deployment config must not rely on docker compose --no-consistency" >&2
  exit 1
fi

single_config="$(mktemp)"
cluster_config="$(mktemp)"
override_config="$(mktemp)"
disabled_config="$(mktemp)"
trap 'rm -f "${single_config}" "${cluster_config}" "${override_config}" "${disabled_config}"' EXIT

env -u OTEL_ENABLED ./deploy/deployment.sh config --topology single --env-file deploy/.env.single.example >"${single_config}"
env -u OTEL_ENABLED ./deploy/deployment.sh config --topology cluster --env-file deploy/.env.cluster.example >"${cluster_config}"

if ! rg -n 'OTEL_ENABLED[=: ]+"?true"?|OTEL_ENABLED=true' "${single_config}" >/dev/null; then
  echo "expected default single config to enable OTEL_ENABLED=true" >&2
  exit 1
fi

if ! rg -n 'OTEL_ENABLED[=: ]+"?true"?|OTEL_ENABLED=true' "${cluster_config}" >/dev/null; then
  echo "expected default cluster config to enable OTEL_ENABLED=true" >&2
  exit 1
fi

if ! rg -n '^  kibana:' "${single_config}" >/dev/null; then
  echo "expected default single config to include observability overlay" >&2
  exit 1
fi

if ! rg -n '^  kibana:' "${cluster_config}" >/dev/null; then
  echo "expected default cluster config to include observability overlay" >&2
  exit 1
fi

if rg -n '/var/log/community|COMMUNITY_LOGGING_DIR|COMMUNITY_LOGGING_FILE_NAME|volume-log-export|observability_logs' "${single_config}" >/dev/null; then
  echo "expected default single config to avoid file-volume application logs" >&2
  exit 1
fi

if rg -n '/var/log/community|COMMUNITY_LOGGING_DIR|COMMUNITY_LOGGING_FILE_NAME|volume-log-export|observability_logs' "${cluster_config}" >/dev/null; then
  echo "expected default cluster config to avoid file-volume application logs" >&2
  exit 1
fi

if ! rg -n 'OTEL_LOGS_COLLECTION[=: ]+"?stdout"?|OTEL_LOGS_COLLECTION=stdout' "${single_config}" >/dev/null; then
  echo "expected single config to mark stdout log collection" >&2
  exit 1
fi

if ! rg -n 'OTEL_LOGS_COLLECTION[=: ]+"?stdout"?|OTEL_LOGS_COLLECTION=stdout' "${cluster_config}" >/dev/null; then
  echo "expected cluster config to mark stdout log collection" >&2
  exit 1
fi

if ! rg -n 'RUNTIME_DIAGNOSTICS_ENABLED[=: ]+"?false"?|RUNTIME_DIAGNOSTICS_ENABLED=false' "${single_config}" >/dev/null; then
  echo "expected single config to keep runtime diagnostics disabled by default" >&2
  exit 1
fi

if ! rg -n 'RUNTIME_DIAGNOSTICS_ENABLED[=: ]+"?false"?|RUNTIME_DIAGNOSTICS_ENABLED=false' "${cluster_config}" >/dev/null; then
  echo "expected cluster config to keep runtime diagnostics disabled by default" >&2
  exit 1
fi

if ! rg -n 'RUNTIME_DIAGNOSTICS_INCLUDES[=: ]+"?com.nowcoder.community.\*"?|RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.\*' "${single_config}" >/dev/null; then
  echo "expected single config to use conservative community diagnostics includes" >&2
  exit 1
fi

if ! rg -n 'RUNTIME_DIAGNOSTICS_INCLUDES[=: ]+"?com.nowcoder.community.\*"?|RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.\*' "${cluster_config}" >/dev/null; then
  echo "expected cluster config to use conservative community diagnostics includes" >&2
  exit 1
fi

old_profiler_prefix='METHOD''_PROFILER_'
if rg -n "${old_profiler_prefix}" "${single_config}" "${cluster_config}" >/dev/null; then
  echo "expected rendered configs to remove old profiler settings" >&2
  exit 1
fi

OTEL_ENABLED=false ./deploy/deployment.sh config --topology single --env-file deploy/.env.single.example >"${override_config}"

if ! rg -n 'OTEL_ENABLED[=: ]+"?false"?|OTEL_ENABLED=false' "${override_config}" >/dev/null; then
  echo "expected explicit OTEL_ENABLED=false override to be preserved" >&2
  exit 1
fi

OTEL_ENABLED=true ./deploy/deployment.sh config --topology single --no-observability --env-file deploy/.env.single.example >"${disabled_config}"

if rg -n '^  kibana:' "${disabled_config}" >/dev/null; then
  echo "expected --no-observability config to omit observability overlay" >&2
  exit 1
fi

if ! rg -n 'OTEL_ENABLED[=: ]+"?false"?|OTEL_ENABLED=false' "${disabled_config}" >/dev/null; then
  echo "expected --no-observability config to disable OTEL_ENABLED" >&2
  exit 1
fi

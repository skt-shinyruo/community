#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${repo_root}"

if rg -n -- '--no-consistency' deploy/deployment.sh >/dev/null; then
  echo "deployment config must not rely on docker compose --no-consistency" >&2
  exit 1
fi

single_config="$(mktemp)"
single_default_config="$(mktemp)"
cluster_config="$(mktemp)"
cluster_enabled_config="$(mktemp)"
cluster_disabled_config="$(mktemp)"
infra_config="$(mktemp)"
infra_disabled_config="$(mktemp)"
override_config="$(mktemp)"
disabled_config="$(mktemp)"
trap 'rm -f "${single_config}" "${single_default_config}" "${cluster_config}" "${cluster_enabled_config}" "${cluster_disabled_config}" "${infra_config}" "${infra_disabled_config}" "${override_config}" "${disabled_config}"' EXIT

env -u OTEL_ENABLED ./deploy/deployment.sh config --stack infra --env-file deploy/stacks/infra/.env.example >"${infra_config}"
env -u OTEL_ENABLED ./deploy/deployment.sh config --stack infra --no-observability --env-file deploy/stacks/infra/.env.example >"${infra_disabled_config}"
env -u OTEL_ENABLED ./deploy/deployment.sh config --stack single --env-file deploy/stacks/single/.env.example >"${single_default_config}"
env -u OTEL_ENABLED ./deploy/deployment.sh config --stack single --observability --env-file deploy/stacks/single/.env.example >"${single_config}"
env -u OTEL_ENABLED ./deploy/deployment.sh config --stack cluster --env-file deploy/stacks/cluster/.env.example >"${cluster_config}"
env -u OTEL_ENABLED ./deploy/deployment.sh config --stack cluster --observability --env-file deploy/stacks/cluster/.env.example >"${cluster_enabled_config}"
env -u OTEL_ENABLED ./deploy/deployment.sh config --stack cluster --no-observability --env-file deploy/stacks/cluster/.env.example >"${cluster_disabled_config}"
env -u OTEL_ENABLED ./deploy/deployment.sh config --stack single --no-observability --env-file deploy/stacks/single/.env.example >"${disabled_config}"

require_overlay_enabled() {
  local config="$1"
  local label="$2"
  if ! rg -n '^  kibana:' "${config}" >/dev/null; then
    echo "expected ${label} config to include observability overlay" >&2
    exit 1
  fi
  if ! rg -n 'OTEL_ENABLED[=: ]+"?true"?|OTEL_ENABLED=true' "${config}" >/dev/null; then
    echo "expected ${label} config to enable OTEL_ENABLED=true" >&2
    exit 1
  fi
}

require_overlay_disabled() {
  local config="$1"
  local label="$2"
  if rg -n '^  kibana:' "${config}" >/dev/null; then
    echo "expected ${label} config to omit observability overlay" >&2
    exit 1
  fi
}

require_overlay_disabled "${infra_config}" "default infra"
require_overlay_disabled "${infra_disabled_config}" "explicitly disabled infra"
require_overlay_disabled "${single_default_config}" "default single"
require_overlay_enabled "${single_config}" "explicitly enabled single"
require_overlay_enabled "${cluster_config}" "default cluster"
require_overlay_enabled "${cluster_enabled_config}" "explicitly enabled cluster"
require_overlay_disabled "${cluster_disabled_config}" "explicitly disabled cluster"
require_overlay_disabled "${disabled_config}" "explicitly disabled single"

if ./deploy/deployment.sh config --stack infra --observability --env-file deploy/stacks/infra/.env.example >/dev/null 2>&1; then
  echo "expected infra to reject --observability" >&2
  exit 1
fi

if ./deploy/deployment.sh config --stack single --observability --no-observability --env-file deploy/stacks/single/.env.example >/dev/null 2>&1 ||
  ./deploy/deployment.sh config --stack single --no-observability --observability --env-file deploy/stacks/single/.env.example >/dev/null 2>&1; then
  echo "expected conflicting observability flags to be rejected in either order" >&2
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

require_env_count() {
  local key="$1"
  local expected="$2"
  local config="$3"
  local topology="$4"
  local actual

  actual="$(awk -v key="${key}" '
    $0 == "services:" { in_services = 1; next }
    in_services && /^[^[:space:]]/ { in_services = 0; in_service = 0 }
    in_services && /^  [A-Za-z0-9_.-]+:[[:space:]]*$/ { in_service = 1; found = 0; next }
    in_services && in_service && index($0, "      " key ":") == 1 && !found {
      count++
      found = 1
    }
    END { print count + 0 }
  ' "${config}")"
  if [ "${actual}" != "${expected}" ]; then
    echo "expected ${topology} config to expose ${key} for all ${expected} services, found ${actual}" >&2
    exit 1
  fi
}

for requirement in \
  'YIERLOOM_ENABLED[=: ]+"?false"?|YIERLOOM_ENABLED=false' \
  'YIERLOOM_PLUGIN__METHOD__INCLUDES[=: ]+"?com.nowcoder.community.\*"?|YIERLOOM_PLUGIN__METHOD__INCLUDES=com.nowcoder.community.\*' \
  'YIERLOOM_PLUGINS_DIR[=: ]+"?/opt/yierloom/plugins"?|YIERLOOM_PLUGINS_DIR=/opt/yierloom/plugins'; do
  if ! rg -n "${requirement}" "${single_config}" >/dev/null; then
    echo "expected single config to expose the YierLoom default: ${requirement}" >&2
    exit 1
  fi
  if ! rg -n "${requirement}" "${cluster_config}" >/dev/null; then
    echo "expected cluster config to expose the YierLoom default: ${requirement}" >&2
    exit 1
  fi
done

for key in YIERLOOM_ENABLED YIERLOOM_PLUGIN__METHOD__INCLUDES YIERLOOM_PLUGINS_DIR; do
  require_env_count "${key}" 6 "${single_config}" single
  require_env_count "${key}" 18 "${cluster_config}" cluster
done

old_profiler_prefix='METHOD''_PROFILER_'
if rg -n "${old_profiler_prefix}" "${single_config}" "${cluster_config}" >/dev/null; then
  echo "expected rendered configs to remove old profiler settings" >&2
  exit 1
fi

bash deploy/tests/contracts/config/observability_contracts.sh

collector_config="deploy/observability/edot-collector.yml"

require_pipeline_receiver() {
  local pipeline="$1"
  local receiver="$2"
  local config="$3"

  awk -v pipeline="${pipeline}" -v receiver="${receiver}" '
    function indent_of(line) {
      return match(line, /[^[:space:]]/) - 1
    }
    function trim(value) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      return value
    }
    function inline_receivers_contains(line, receiver, values, count, i) {
      sub(/^[^[]*\[/, "", line)
      sub(/\].*$/, "", line)
      count = split(line, values, ",")
      for (i = 1; i <= count; i++) {
        if (trim(values[i]) == receiver) {
          return 1
        }
      }
      return 0
    }
    function leave_sections(indent) {
      if (in_receivers && indent <= receivers_indent) {
        in_receivers = 0
      }
      if (in_pipeline && indent <= pipeline_indent) {
        in_pipeline = 0
        in_receivers = 0
      }
      if (in_pipelines && indent <= pipelines_indent) {
        in_pipelines = 0
        in_pipeline = 0
        in_receivers = 0
      }
      if (in_service && indent <= service_indent) {
        in_service = 0
        in_pipelines = 0
        in_pipeline = 0
        in_receivers = 0
      }
    }
    /^[[:space:]]*[A-Za-z0-9_\/.-]+:/ {
      indent = indent_of($0)
      leave_sections(indent)

      if ($0 ~ /^[[:space:]]*service:[[:space:]]*($|#)/) {
        in_service = 1
        service_indent = indent
      } else if (in_service && $0 ~ /^[[:space:]]*pipelines:[[:space:]]*($|#)/) {
        in_pipelines = 1
        pipelines_indent = indent
      } else if (in_pipelines && $0 ~ "^[[:space:]]*" pipeline ":[[:space:]]*($|#)") {
        in_pipeline = 1
        pipeline_indent = indent
      } else if (in_pipeline && $0 ~ /^[[:space:]]*receivers:[[:space:]]*\[/) {
        if (inline_receivers_contains($0, receiver)) {
          found = 1
        }
      } else if (in_pipeline && $0 ~ /^[[:space:]]*receivers:[[:space:]]*($|#)/) {
        in_receivers = 1
        receivers_indent = indent
      }
    }
    in_receivers && $0 ~ "^[[:space:]]*-[[:space:]]*" receiver "([[:space:]]*(#.*)?)?$" {
      found = 1
    }
    END {
      exit found ? 0 : 1
    }
  ' "${config}"
}

if ! rg -n '^[[:space:]]*filelog/docker_stdout:[[:space:]]*$' "${collector_config}" >/dev/null; then
  echo "expected collector to read Docker stdout logs through filelog/docker_stdout" >&2
  exit 1
fi

if ! require_pipeline_receiver "logs" "filelog/docker_stdout" "${collector_config}"; then
  echo "expected collector logs pipeline to receive Docker stdout logs" >&2
  exit 1
fi

if ! require_pipeline_receiver "traces" "otlp" "${collector_config}" ||
  ! require_pipeline_receiver "metrics" "otlp" "${collector_config}"; then
  echo "expected collector traces and metrics pipelines to receive OTLP" >&2
  exit 1
fi

if ! rg -n 'logs_index:[[:space:]]*logs-community-default' "${collector_config}" >/dev/null; then
  echo "expected collector logs exporter to write logs-community-default" >&2
  exit 1
fi

if rg -n 'mode:[[:space:]]*otel' "${collector_config}" >/dev/null; then
  echo "expected local collector to avoid Elasticsearch OTel mapping mode against the bundled ES 8.12 runtime" >&2
  exit 1
fi

if ! rg -n 'mode:[[:space:]]*ecs' "${collector_config}" >/dev/null; then
  echo "expected local collector traces and metrics to use Elasticsearch ECS mapping mode" >&2
  exit 1
fi

if ! rg -U -n '(?s)^    logs/otlp:.*?receivers:[[:space:]]*\[otlp\].*?exporters:[[:space:]]*\[elasticsearch/logs\]' "${collector_config}" >/dev/null; then
  echo "expected collector to receive OTLP logs in a dedicated logs/otlp pipeline" >&2
  exit 1
fi

if ! rg -n '^[[:space:]]*cumulativetodelta:[[:space:]]*$' "${collector_config}" >/dev/null ||
  ! awk '
    /metrics:/ {
      in_metrics = 1
    }
    in_metrics && /processors:[[:space:]]*\[/ && /cumulativetodelta/ {
      found = 1
    }
    in_metrics && /^[[:space:]]*logs:/ {
      in_metrics = 0
    }
    END {
      exit found ? 0 : 1
    }
  ' "${collector_config}"; then
  echo "expected collector metrics pipeline to convert cumulative metrics before Elasticsearch export" >&2
  exit 1
fi

if ! awk '
  /key:[[:space:]]*service\.namespace/ {
    in_service_namespace = 1
  }
  in_service_namespace && /action:[[:space:]]*upsert/ {
    found = 1
  }
  in_service_namespace && /^[[:space:]]*-[[:space:]]*key:/ && $0 !~ /service\.namespace/ {
    in_service_namespace = 0
  }
  END {
    exit found ? 0 : 1
  }
' "${collector_config}"; then
  echo "expected collector service.namespace processor to use upsert action" >&2
  exit 1
fi

OTEL_ENABLED=false ./deploy/deployment.sh config --stack single --observability --env-file deploy/stacks/single/.env.example >"${override_config}"

if ! rg -n 'OTEL_ENABLED[=: ]+"?false"?|OTEL_ENABLED=false' "${override_config}" >/dev/null; then
  echo "expected explicit OTEL_ENABLED=false override to be preserved" >&2
  exit 1
fi

OTEL_ENABLED=true ./deploy/deployment.sh config --stack single --no-observability --env-file deploy/stacks/single/.env.example >"${disabled_config}"

if rg -n '^  kibana:' "${disabled_config}" >/dev/null; then
  echo "expected --no-observability config to omit observability overlay" >&2
  exit 1
fi

if ! rg -n 'OTEL_ENABLED[=: ]+"?false"?|OTEL_ENABLED=false' "${disabled_config}" >/dev/null; then
  echo "expected --no-observability config to disable OTEL_ENABLED" >&2
  exit 1
fi

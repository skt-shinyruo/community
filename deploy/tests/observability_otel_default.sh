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

collector_config="deploy/observability/edot-collector.yml"
logback_config="backend/community-common/common-observability/src/main/resources/logback/community-observability.xml"

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

require_console_json_content() {
  local pattern="$1"
  local config="$2"

  awk -v pattern="${pattern}" '
    /<appender[[:space:]][^>]*name="CONSOLE_JSON"/ {
      in_console_json = 1
      depth = 1
    }
    in_console_json {
      if ($0 ~ pattern) {
        found = 1
      }
      if ($0 ~ /<appender[[:space:]][^>]*name="CONSOLE_JSON"/) {
        next
      }
      if ($0 ~ /<appender([[:space:]>])/) {
        depth++
      }
      if ($0 ~ /<\/appender>/) {
        depth--
        if (depth == 0) {
          in_console_json = 0
        }
      }
    }
    END {
      exit found ? 0 : 1
    }
  ' "${config}"
}

reject_console_json_content() {
  local pattern="$1"
  local config="$2"

  if require_console_json_content "${pattern}" "${config}"; then
    return 1
  fi
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

if ! rg -n 'appender[[:space:]][^>]*name="CONSOLE_JSON"' "${logback_config}" >/dev/null; then
  echo "expected shared logback config to define CONSOLE_JSON" >&2
  exit 1
fi

if ! require_console_json_content 'service[.]name' "${logback_config}"; then
  echo "expected shared logback JSON to include service.name" >&2
  exit 1
fi

if ! require_console_json_content '<mdc>' "${logback_config}" ||
  ! require_console_json_content '<excludeMdcKeyName>traceId</excludeMdcKeyName>' "${logback_config}"; then
  echo "expected shared logback CONSOLE_JSON appender to preserve trace/span MDC correlation" >&2
  exit 1
fi

if ! reject_console_json_content '<excludeMdcKeyName>trace[.]id</excludeMdcKeyName>' "${logback_config}" ||
  ! reject_console_json_content '<excludeMdcKeyName>trace_id</excludeMdcKeyName>' "${logback_config}" ||
  ! reject_console_json_content '<excludeMdcKeyName>span[.]id</excludeMdcKeyName>' "${logback_config}" ||
  ! reject_console_json_content '<excludeMdcKeyName>span_id</excludeMdcKeyName>' "${logback_config}"; then
  echo "expected shared logback CONSOLE_JSON appender not to exclude trace/span correlation MDC keys" >&2
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

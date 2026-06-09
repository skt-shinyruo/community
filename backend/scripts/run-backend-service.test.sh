#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
script="${script_dir}/run-backend-service.sh"

grep -F 'runtime_diagnostics_enabled="${RUNTIME_DIAGNOSTICS_ENABLED:-false}"' "${script}"
grep -F 'RUNTIME_DIAGNOSTICS_ENABLED=true' "${script}"
grep -F '/otel/runtime-diagnostics-agent.jar' "${script}"
grep -F 'java_opts="${java_opts:+${java_opts} }-javaagent:/otel/runtime-diagnostics-agent.jar"' "${script}"
grep -F 'missing runtime diagnostics agent at /otel/runtime-diagnostics-agent.jar' "${script}"

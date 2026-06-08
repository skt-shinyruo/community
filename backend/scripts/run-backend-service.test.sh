#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
script="${script_dir}/run-backend-service.sh"

grep -F 'method_profiler_enabled="${METHOD_PROFILER_ENABLED:-false}"' "${script}"
grep -F 'METHOD_PROFILER_ENABLED=true' "${script}"
grep -F '/otel/method-profiler-agent.jar' "${script}"
grep -F 'java_opts="${java_opts:+${java_opts} }-javaagent:/otel/method-profiler-agent.jar"' "${script}"
grep -F 'missing method profiler agent at /otel/method-profiler-agent.jar' "${script}"

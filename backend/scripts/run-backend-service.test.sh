#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
script="${script_dir}/run-backend-service.sh"

grep -F 'yierloom_enabled="${YIERLOOM_ENABLED:-false}"' "${script}"
grep -F 'YIERLOOM_ENABLED=true' "${script}"
grep -F '/otel/yierloom-agent.jar' "${script}"
grep -F 'java_opts="${java_opts:+${java_opts} }-javaagent:/otel/yierloom-agent.jar"' "${script}"
grep -F 'missing YierLoom agent at /otel/yierloom-agent.jar' "${script}"

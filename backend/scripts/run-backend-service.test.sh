#!/usr/bin/env bash
set -euo pipefail

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
script="${script_dir}/run-backend-service.sh"

grep -F 'runtime_dir="/tmp/community-runtime"' "${script}"
grep -F 'umask 077' "${script}"
grep -F 'mkdir -p "${runtime_home}" "${runtime_tmp}"' "${script}"
grep -F 'java_opts="-Duser.home=${runtime_home} -Djava.io.tmpdir=${runtime_tmp}"' "${script}"
grep -F 'otel_enabled="${OTEL_ENABLED:-false}"' "${script}"
grep -F '/otel/opentelemetry-javaagent.jar' "${script}"
grep -F 'java_opts="${java_opts:+${java_opts} }-javaagent:/otel/opentelemetry-javaagent.jar"' "${script}"

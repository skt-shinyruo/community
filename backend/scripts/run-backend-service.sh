#!/bin/sh
set -eu

# Operator note:
# - JAVA_OPTS supports standard space-separated JVM flags only.
# - Shell quoting inside JAVA_OPTS is not parsed by this script.
# - METHOD_PROFILER_ENABLED=true enables the optional method profiler Java agent.
java_opts="${JAVA_OPTS:-}"
service_version="${SERVICE_VERSION:-unknown}"
otel_enabled="${OTEL_ENABLED:-false}"
otel_service_name="${OTEL_SERVICE_NAME:-}"
otel_resource_attributes="${OTEL_RESOURCE_ATTRIBUTES:-}"
method_profiler_enabled="${METHOD_PROFILER_ENABLED:-false}"

export SERVICE_VERSION="${service_version}"

case ",${otel_resource_attributes}," in
  *,service.version=*,)
    ;;
  *,)
    otel_resource_attributes="${otel_resource_attributes:+${otel_resource_attributes},}service.version=${service_version}"
    ;;
esac
export OTEL_RESOURCE_ATTRIBUTES="${otel_resource_attributes}"

if [ "${otel_enabled}" = "true" ]; then
  if [ -z "${otel_service_name}" ]; then
    echo "[backend-runtime] OTEL_SERVICE_NAME must be set when OTEL_ENABLED=true" >&2
    exit 1
  fi
  if [ ! -f /otel/opentelemetry-javaagent.jar ]; then
    echo "[backend-runtime] missing OTel Java agent at /otel/opentelemetry-javaagent.jar" >&2
    exit 1
  fi
  java_opts="${java_opts:+${java_opts} }-javaagent:/otel/opentelemetry-javaagent.jar"
fi

if [ "${method_profiler_enabled}" = "true" ]; then
  if [ ! -f /otel/method-profiler-agent.jar ]; then
    echo "[backend-runtime] missing method profiler agent at /otel/method-profiler-agent.jar" >&2
    exit 1
  fi
  java_opts="${java_opts:+${java_opts} }-javaagent:/otel/method-profiler-agent.jar"
fi

export OTEL_SERVICE_NAME="${otel_service_name}"
export JAVA_OPTS="${java_opts}"

# Intentionally allow JAVA_OPTS word splitting so standard JVM flags behave as expected.
exec java ${JAVA_OPTS:-} -jar /app/app.jar

#!/bin/sh
set -eu

# Operator note:
# - JAVA_OPTS supports standard space-separated JVM flags only.
# - Shell quoting inside JAVA_OPTS is not parsed by this script.
# - RUNTIME_DIAGNOSTICS_ENABLED=true enables the optional runtime diagnostics Java agent.
java_opts="${JAVA_OPTS:-}"
service_version="${SERVICE_VERSION:-unknown}"
otel_enabled="${OTEL_ENABLED:-false}"
otel_service_name="${OTEL_SERVICE_NAME:-}"
otel_resource_attributes="${OTEL_RESOURCE_ATTRIBUTES:-}"
runtime_diagnostics_enabled="${RUNTIME_DIAGNOSTICS_ENABLED:-false}"

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

if [ "${runtime_diagnostics_enabled}" = "true" ]; then
  if [ ! -f /otel/runtime-diagnostics-agent.jar ]; then
    echo "[backend-runtime] missing runtime diagnostics agent at /otel/runtime-diagnostics-agent.jar" >&2
    exit 1
  fi
  java_opts="${java_opts:+${java_opts} }-javaagent:/otel/runtime-diagnostics-agent.jar"
fi

export OTEL_SERVICE_NAME="${otel_service_name}"
export JAVA_OPTS="${java_opts}"

# Intentionally allow JAVA_OPTS word splitting so standard JVM flags behave as expected.
exec java ${JAVA_OPTS:-} -jar /app/app.jar

#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./deploy/deployment.sh <command> [options] [compose-args...]

Commands:
  up        Start the stack with `up -d --build`
  down      Stop the stack
  ps        Show compose status
  logs      Show logs with `logs -f --tail=200`
  config    Render the merged compose config

Options:
  --topology <single|cluster>  Choose topology (default: cluster)
  --scope <full|infra>         Choose compose scope (default: full)
  --no-observability  Disable deploy/compose.observability.yml
  --env-file <path>   Override env file path (default: deploy/.env.single or deploy/.env.cluster)
  -p, --project-name  Override compose project name (default: community-single or community-cluster)
  Custom project names require an independent network topology and volume namespace.
  Topology values use shell environment, then env file, then the built-in topology defaults.
  -h, --help          Show this help

Examples:
  ./deploy/deployment.sh up
  ./deploy/deployment.sh up --no-observability
  ./deploy/deployment.sh up --topology single
  ./deploy/deployment.sh up --topology single --scope infra
  ./deploy/deployment.sh config --topology single -p community-single-smoke --env-file deploy/.env.single.smoke
  ./deploy/deployment.sh logs --no-observability community-app-1
  ./deploy/deployment.sh down --no-observability
  ./deploy/deployment.sh config --topology single
EOF
}

print_command() {
  local arg
  for arg in "$@"; do
    printf '%q ' "${arg}"
  done
  printf '\n'
}

resolve_path() {
  local path="$1"
  case "${path}" in
    /*) printf '%s\n' "${path}" ;;
    *) printf '%s/%s\n' "${CALLER_PWD}" "${path}" ;;
  esac
}

resolve_default_env_file() {
  local topology="$1"
  case "${topology}" in
    single)
      printf '%s/deploy/.env.single\n' "${REPO_ROOT}"
      ;;
    cluster)
      printf '%s/deploy/.env.cluster\n' "${REPO_ROOT}"
      ;;
    *)
      echo "[deployment.sh] unsupported topology: ${topology}" >&2
      exit 1
      ;;
  esac
}

resolve_default_project_name() {
  case "${TOPOLOGY}" in
    single) printf 'community-single\n' ;;
    cluster) printf 'community-cluster\n' ;;
    *)
      echo "[deployment.sh] unsupported topology: ${TOPOLOGY}" >&2
      exit 1
      ;;
  esac
}

read_env_file_value() {
  local variable="$1"
  local file="$2"

  awk -v variable="${variable}" '
    /^[[:space:]]*(#|$)/ { next }
    {
      line = $0
      sub(/\r$/, "", line)
      prefix = "^[[:space:]]*(export[[:space:]]+)?" variable "[[:space:]]*="
      if (line ~ prefix) {
        sub(prefix, "", line)
        sub(/^[[:space:]]+/, "", line)
        sub(/[[:space:]]+$/, "", line)
        if (length(line) >= 2) {
          first = substr(line, 1, 1)
          last = substr(line, length(line), 1)
          if ((first == "\"" && last == "\"") || (first == "\047" && last == "\047")) {
            line = substr(line, 2, length(line) - 2)
          }
        }
        value = line
        found = 1
      }
    }
    END {
      if (!found) exit 1
      print value
    }
  ' "${file}"
}

initialize_topology_defaults() {
  declare -gA TOPOLOGY_DEFAULTS=()
  declare -ga TOPOLOGY_VARIABLES=()

  case "${TOPOLOGY}" in
    single)
      TOPOLOGY_VARIABLES=(
        COMMUNITY_VOLUME_NAMESPACE
        COMMUNITY_NETWORK_SUBNET
        COMMUNITY_NETWORK_DYNAMIC_RANGE
        NGINX_STATIC_IP
        COMMUNITY_GATEWAY_STATIC_IP
        GATEWAY_TRUSTED_PROXY_CIDRS
        COMMUNITY_APP_TRUSTED_PROXY_CIDRS
      )
      TOPOLOGY_DEFAULTS[COMMUNITY_VOLUME_NAMESPACE]=community_single
      TOPOLOGY_DEFAULTS[COMMUNITY_NETWORK_SUBNET]=172.30.0.0/24
      TOPOLOGY_DEFAULTS[COMMUNITY_NETWORK_DYNAMIC_RANGE]=172.30.0.128/25
      TOPOLOGY_DEFAULTS[NGINX_STATIC_IP]=172.30.0.10
      TOPOLOGY_DEFAULTS[COMMUNITY_GATEWAY_STATIC_IP]=172.30.0.20
      TOPOLOGY_DEFAULTS[GATEWAY_TRUSTED_PROXY_CIDRS]=172.30.0.10/32
      TOPOLOGY_DEFAULTS[COMMUNITY_APP_TRUSTED_PROXY_CIDRS]=172.30.0.20/32
      ;;
    cluster)
      TOPOLOGY_VARIABLES=(
        COMMUNITY_VOLUME_NAMESPACE
        COMMUNITY_NETWORK_SUBNET
        COMMUNITY_NETWORK_DYNAMIC_RANGE
        NGINX_STATIC_IP
        COMMUNITY_GATEWAY_1_STATIC_IP
        COMMUNITY_GATEWAY_2_STATIC_IP
        COMMUNITY_GATEWAY_3_STATIC_IP
        GATEWAY_TRUSTED_PROXY_CIDRS
        COMMUNITY_APP_TRUSTED_PROXY_CIDRS
      )
      TOPOLOGY_DEFAULTS[COMMUNITY_VOLUME_NAMESPACE]=community_cluster
      TOPOLOGY_DEFAULTS[COMMUNITY_NETWORK_SUBNET]=172.31.0.0/24
      TOPOLOGY_DEFAULTS[COMMUNITY_NETWORK_DYNAMIC_RANGE]=172.31.0.128/25
      TOPOLOGY_DEFAULTS[NGINX_STATIC_IP]=172.31.0.10
      TOPOLOGY_DEFAULTS[COMMUNITY_GATEWAY_1_STATIC_IP]=172.31.0.20
      TOPOLOGY_DEFAULTS[COMMUNITY_GATEWAY_2_STATIC_IP]=172.31.0.21
      TOPOLOGY_DEFAULTS[COMMUNITY_GATEWAY_3_STATIC_IP]=172.31.0.22
      TOPOLOGY_DEFAULTS[GATEWAY_TRUSTED_PROXY_CIDRS]=172.31.0.10/32
      TOPOLOGY_DEFAULTS[COMMUNITY_APP_TRUSTED_PROXY_CIDRS]=172.31.0.20/32,172.31.0.21/32,172.31.0.22/32
      ;;
    *)
      echo "[deployment.sh] unsupported topology: ${TOPOLOGY}" >&2
      exit 1
      ;;
  esac
}

validate_topology_value() {
  local variable="$1"
  local value="$2"

  if [ -z "${value}" ]; then
    echo "[deployment.sh] ${variable} must not be empty" >&2
    exit 1
  fi

  case "${variable}" in
    COMMUNITY_VOLUME_NAMESPACE)
      if [[ ! "${value}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
        echo "[deployment.sh] ${variable} contains unsupported characters" >&2
        exit 1
      fi
      ;;
    *)
      if [[ ! "${value}" =~ ^[0-9A-Fa-f.,:/]+$ ]]; then
        echo "[deployment.sh] ${variable} must contain literal IP/CIDR values only" >&2
        exit 1
      fi
      ;;
  esac
}

resolve_topology_values() {
  local variable
  local value

  declare -gA TOPOLOGY_VALUES=()
  for variable in "${TOPOLOGY_VARIABLES[@]}"; do
    if [[ -v "${variable}" ]]; then
      value="${!variable}"
    elif value="$(read_env_file_value "${variable}" "${ENV_FILE}")"; then
      :
    else
      value="${TOPOLOGY_DEFAULTS[${variable}]}"
    fi

    validate_topology_value "${variable}" "${value}"
    TOPOLOGY_VALUES["${variable}"]="${value}"
    printf -v "${variable}" '%s' "${value}"
    export "${variable}"
  done
}

validate_project_topology() {
  local default_project_name
  local variable
  local reused_variables=()

  default_project_name="$(resolve_default_project_name)"
  if [ "${PROJECT_NAME}" = "${default_project_name}" ]; then
    return
  fi

  for variable in "${TOPOLOGY_VARIABLES[@]}"; do
    if [ "${TOPOLOGY_VALUES[${variable}]}" = "${TOPOLOGY_DEFAULTS[${variable}]}" ]; then
      reused_variables+=("${variable}")
    fi
  done

  if [ "${#reused_variables[@]}" -gt 0 ]; then
    echo "[deployment.sh] custom project '${PROJECT_NAME}' requires an independent topology; override every default network, static peer, trusted CIDR, and volume namespace value" >&2
    echo "[deployment.sh] values still using ${TOPOLOGY} defaults: ${reused_variables[*]}" >&2
    exit 1
  fi
}

append_topology_files() {
  case "${TOPOLOGY}" in
    single)
      COMPOSE_FILES+=(
        deploy/compose.infra.mysql.single.yml
        deploy/compose.infra.redis.single.yml
        deploy/compose.infra.kafka.single.yml
        deploy/compose.infra.elasticsearch.single.yml
        deploy/compose.infra.garage.single.yml
        deploy/compose.infra.nacos.single.yml
        deploy/compose.infra.xxl-job.single.yml
        deploy/compose.infra.mailhog.yml
        deploy/compose.infra.mock-data-studio-bootstrap.single.yml
      )
      if [ "${SCOPE}" = "full" ]; then
        COMPOSE_FILES+=(
          deploy/compose.runtime.services.single.yml
          deploy/compose.runtime.frontend-nginx.single.yml
          deploy/compose.runtime.mock-data-studio.single.yml
        )
      fi
      ;;
    cluster)
      COMPOSE_FILES+=(
        deploy/compose.infra.mysql.cluster.yml
        deploy/compose.infra.redis.cluster.yml
        deploy/compose.infra.kafka.cluster.yml
        deploy/compose.infra.elasticsearch.cluster.yml
        deploy/compose.infra.garage.cluster.yml
        deploy/compose.infra.nacos.cluster.yml
        deploy/compose.infra.xxl-job.cluster.yml
        deploy/compose.infra.mailhog.yml
        deploy/compose.infra.mock-data-studio-bootstrap.cluster.yml
      )
      if [ "${SCOPE}" = "full" ]; then
        COMPOSE_FILES+=(
          deploy/compose.runtime.services.cluster.yml
          deploy/compose.runtime.frontend-nginx.cluster.yml
          deploy/compose.runtime.mock-data-studio.cluster.yml
        )
      fi
      ;;
    *)
      echo "[deployment.sh] unsupported topology: ${TOPOLOGY}" >&2
      exit 1
      ;;
  esac
}

CALLER_PWD="$(pwd)"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

if [ "$#" -eq 0 ]; then
  usage
  exit 1
fi

COMMAND="$1"
shift

OBSERVABILITY=1
TOPOLOGY="cluster"
SCOPE="full"
ENV_FILE=""
PROJECT_NAME=""
EXTRA_ARGS=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-observability)
      OBSERVABILITY=0
      ;;
    --topology)
      if [ "$#" -lt 2 ]; then
        echo "[deployment.sh] missing value for --topology" >&2
        exit 1
      fi
      TOPOLOGY="$2"
      shift
      ;;
    --scope)
      if [ "$#" -lt 2 ]; then
        echo "[deployment.sh] missing value for --scope" >&2
        exit 1
      fi
      SCOPE="$2"
      shift
      ;;
    --env-file)
      if [ "$#" -lt 2 ]; then
        echo "[deployment.sh] missing value for --env-file" >&2
        exit 1
      fi
      ENV_FILE="$(resolve_path "$2")"
      shift
      ;;
    -p|--project-name)
      if [ "$#" -lt 2 ]; then
        echo "[deployment.sh] missing value for $1" >&2
        exit 1
      fi
      PROJECT_NAME="$2"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      EXTRA_ARGS+=("$@")
      break
      ;;
    -*)
      echo "[deployment.sh] unsupported option: $1" >&2
      exit 1
      ;;
    *)
      EXTRA_ARGS+=("$1")
      ;;
  esac
  shift
done

case "${COMMAND}" in
  up)
    SUBCOMMAND=(up -d --build)
    ;;
  down)
    SUBCOMMAND=(down)
    ;;
  ps)
    SUBCOMMAND=(ps)
    ;;
  logs)
    SUBCOMMAND=(logs -f --tail=200)
    ;;
  config)
    SUBCOMMAND=(config)
    ;;
  -h|--help|help)
    usage
    exit 0
    ;;
  *)
    echo "[deployment.sh] unsupported command: ${COMMAND}" >&2
    usage
    exit 1
    ;;
esac

case "${TOPOLOGY}" in
  single|cluster)
    ;;
  *)
    echo "[deployment.sh] unsupported topology: ${TOPOLOGY}" >&2
    exit 1
    ;;
esac

case "${SCOPE}" in
  full|infra)
    ;;
  *)
    echo "[deployment.sh] unsupported scope: ${SCOPE}" >&2
    exit 1
    ;;
esac

if [ -z "${ENV_FILE}" ]; then
  ENV_FILE="$(resolve_default_env_file "${TOPOLOGY}")"
fi

if [ ! -f "${ENV_FILE}" ]; then
  echo "[deployment.sh] env file not found: ${ENV_FILE}" >&2
  exit 1
fi

if [ -z "${PROJECT_NAME}" ]; then
  PROJECT_NAME="$(resolve_default_project_name)"
fi

initialize_topology_defaults
resolve_topology_values
validate_project_topology

COMPOSE_FILES=(deploy/compose.yml)
append_topology_files

if [ "${OBSERVABILITY}" -eq 1 ]; then
  COMPOSE_FILES+=(deploy/compose.observability.yml)
fi

if [ "${OBSERVABILITY}" -eq 1 ] && [ -z "${OTEL_ENABLED+x}" ]; then
  export OTEL_ENABLED=true
fi

if [ "${OBSERVABILITY}" -eq 0 ]; then
  export OTEL_ENABLED=false
fi

COMPOSE_CMD=(docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}")
for compose_file in "${COMPOSE_FILES[@]}"; do
  COMPOSE_CMD+=(-f "${compose_file}")
done

COMPOSE_CMD+=("${SUBCOMMAND[@]}")
COMPOSE_CMD+=("${EXTRA_ARGS[@]}")

cd "${REPO_ROOT}"

exec "${COMPOSE_CMD[@]}"

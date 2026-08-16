#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./deploy/deployment.sh <command> [options] [compose-args...]

Commands:
  up        Start the stack with `up -d --build`
  down      Stop the stack
  reset-mysql  Stop the stack and delete only its MySQL data volumes
  ps        Show compose status
  logs      Show logs with `logs -f --tail=200`
  config    Render the merged compose config
  mock-data Run the synchronous mock-data CLI in the selected full stack
  render-backend-env  Generate host-run backend env files from the infra stack env

Options:
  --stack <infra|single|cluster> Select the required independent Stack
  --observability     Enable deploy/compose/overlays/observability.yml
  --no-observability  Disable the observability overlay
  --env-file <path>   Override env file path (default: the selected Stack's .env)
  --output-dir <path>  Override render-backend-env output (default: backend/env/generated)
  -p, --project-name  Override compose project name (default: community-infra/single/cluster)
  Custom project names require an independent network topology and volume namespace.
  Custom infra projects also require independent localhost ports.
  Topology values use shell environment, then env file, then the built-in topology defaults.
  -h, --help          Show this help

Examples:
  ./deploy/deployment.sh up --stack infra
  ./deploy/deployment.sh up --stack single --observability
  ./deploy/deployment.sh up --stack cluster
  ./deploy/deployment.sh render-backend-env --stack infra
  ./deploy/deployment.sh up --stack single --no-observability
  ./deploy/deployment.sh config --stack single -p community-single-smoke --env-file deploy/.env.single.smoke
  ./deploy/deployment.sh logs --stack single --observability community-app
  ./deploy/deployment.sh down --stack single --observability
  ./deploy/deployment.sh reset-mysql --stack single
  ./deploy/deployment.sh config --stack cluster
  ./deploy/deployment.sh mock-data --stack single -- generate --seed demo
  ./deploy/deployment.sh mock-data --stack single -- delete <batch-id>
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
  case "${STACK}" in
    infra|single|cluster)
      printf '%s/deploy/stacks/%s/.env\n' "${REPO_ROOT}" "${STACK}"
      ;;
    *)
      echo "[deployment.sh] unsupported stack: ${STACK}" >&2
      exit 1
      ;;
  esac
}

resolve_default_project_name() {
  case "${STACK}" in
    infra) printf 'community-infra\n' ;;
    single) printf 'community-single\n' ;;
    cluster) printf 'community-cluster\n' ;;
    *)
      echo "[deployment.sh] unsupported stack: ${STACK}" >&2
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

  if [ "${STACK:-}" = "infra" ]; then
    TOPOLOGY_VARIABLES=(
      COMMUNITY_VOLUME_NAMESPACE
      COMMUNITY_NETWORK_SUBNET
      COMMUNITY_NETWORK_DYNAMIC_RANGE
      NGINX_STATIC_IP
      COMMUNITY_GATEWAY_STATIC_IP
      GATEWAY_TRUSTED_PROXY_CIDRS
      COMMUNITY_APP_TRUSTED_PROXY_CIDRS
    )
    TOPOLOGY_DEFAULTS[COMMUNITY_VOLUME_NAMESPACE]=community_infra
    TOPOLOGY_DEFAULTS[COMMUNITY_NETWORK_SUBNET]=172.32.0.0/24
    TOPOLOGY_DEFAULTS[COMMUNITY_NETWORK_DYNAMIC_RANGE]=172.32.0.128/25
    TOPOLOGY_DEFAULTS[NGINX_STATIC_IP]=172.32.0.10
    TOPOLOGY_DEFAULTS[COMMUNITY_GATEWAY_STATIC_IP]=172.32.0.20
    TOPOLOGY_DEFAULTS[GATEWAY_TRUSTED_PROXY_CIDRS]=172.32.0.10/32
    TOPOLOGY_DEFAULTS[COMMUNITY_APP_TRUSTED_PROXY_CIDRS]=172.32.0.20/32
    return
  fi

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

initialize_host_access_defaults() {
  declare -gA HOST_ACCESS_DEFAULTS=()
  declare -ga HOST_ACCESS_VARIABLES=(
    MYSQL_HOST_PORT
    REDIS_HOST_PORT
    KAFKA_HOST_PORT
    ELASTICSEARCH_HOST_ACCESS_PORT
    NACOS_HOST_PORT
    NACOS_GRPC_HOST_PORT
    GARAGE_S3_HOST_PORT
    GARAGE_ADMIN_HOST_PORT
    MAILHOG_UI_HOST_PORT
    MAILHOG_SMTP_HOST_PORT
  )

  HOST_ACCESS_DEFAULTS[MYSQL_HOST_PORT]=13306
  HOST_ACCESS_DEFAULTS[REDIS_HOST_PORT]=16379
  HOST_ACCESS_DEFAULTS[KAFKA_HOST_PORT]=29092
  HOST_ACCESS_DEFAULTS[ELASTICSEARCH_HOST_ACCESS_PORT]=19200
  HOST_ACCESS_DEFAULTS[NACOS_HOST_PORT]=18848
  HOST_ACCESS_DEFAULTS[NACOS_GRPC_HOST_PORT]=19848
  HOST_ACCESS_DEFAULTS[GARAGE_S3_HOST_PORT]=13900
  HOST_ACCESS_DEFAULTS[GARAGE_ADMIN_HOST_PORT]=13903
  HOST_ACCESS_DEFAULTS[MAILHOG_UI_HOST_PORT]=8025
  HOST_ACCESS_DEFAULTS[MAILHOG_SMTP_HOST_PORT]=11025

  if [ "${STACK:-}" = "infra" ]; then
    HOST_ACCESS_DEFAULTS[MYSQL_HOST_PORT]=23306
    HOST_ACCESS_DEFAULTS[REDIS_HOST_PORT]=26379
    HOST_ACCESS_DEFAULTS[KAFKA_HOST_PORT]=39092
    HOST_ACCESS_DEFAULTS[ELASTICSEARCH_HOST_ACCESS_PORT]=29200
    HOST_ACCESS_DEFAULTS[NACOS_HOST_PORT]=28848
    HOST_ACCESS_DEFAULTS[NACOS_GRPC_HOST_PORT]=29848
    HOST_ACCESS_DEFAULTS[GARAGE_S3_HOST_PORT]=23900
    HOST_ACCESS_DEFAULTS[GARAGE_ADMIN_HOST_PORT]=23903
    HOST_ACCESS_DEFAULTS[MAILHOG_UI_HOST_PORT]=28025
    HOST_ACCESS_DEFAULTS[MAILHOG_SMTP_HOST_PORT]=21025
  fi
}

resolve_host_access_values() {
  local variable
  local value
  local existing_variable
  declare -A used_ports=()
  declare -gA HOST_ACCESS_VALUES=()

  for variable in "${HOST_ACCESS_VARIABLES[@]}"; do
    if [[ -v "${variable}" ]]; then
      value="${!variable}"
    elif value="$(read_env_file_value "${variable}" "${ENV_FILE}")"; then
      :
    else
      value="${HOST_ACCESS_DEFAULTS[${variable}]}"
    fi

    if [[ ! "${value}" =~ ^[0-9]+$ ]] || (( 10#${value} < 1 || 10#${value} > 65535 )); then
      echo "[deployment.sh] ${variable} must be a port between 1 and 65535" >&2
      exit 1
    fi
    existing_variable="${used_ports[${value}]:-}"
    if [ -n "${existing_variable}" ]; then
      echo "[deployment.sh] ${variable} and ${existing_variable} must not use the same host port ${value}" >&2
      exit 1
    fi

    used_ports["${value}"]="${variable}"
    HOST_ACCESS_VALUES["${variable}"]="${value}"
    printf -v "${variable}" '%s' "${value}"
    export "${variable}"
  done
}

validate_custom_project_host_access() {
  local default_project_name
  local variable
  local reused_variables=()

  default_project_name="$(resolve_default_project_name)"
  if [ "${PROJECT_NAME}" = "${default_project_name}" ]; then
    return
  fi

  for variable in "${HOST_ACCESS_VARIABLES[@]}"; do
    if [ "${HOST_ACCESS_VALUES[${variable}]}" = "${HOST_ACCESS_DEFAULTS[${variable}]}" ]; then
      reused_variables+=("${variable}")
    fi
  done

  if [ "${#reused_variables[@]}" -gt 0 ]; then
    echo "[deployment.sh] custom infra project '${PROJECT_NAME}' requires independent localhost ports" >&2
    echo "[deployment.sh] values still using host-access defaults: ${reused_variables[*]}" >&2
    exit 1
  fi
}

initialize_stack_port_defaults() {
  declare -gA STACK_PORT_DEFAULTS=()
  declare -ga STACK_PORT_VARIABLES=()

  case "${STACK}" in
    single)
      STACK_PORT_VARIABLES=(
        NACOS_HOST_PORT
        MAILHOG_UI_HOST_PORT
        FRONTEND_HOST_PORT
        NGINX_API_PORT
        ELASTICSEARCH_PORT
        KIBANA_PORT
      )
      STACK_PORT_DEFAULTS[NACOS_HOST_PORT]=18848
      STACK_PORT_DEFAULTS[MAILHOG_UI_HOST_PORT]=8025
      STACK_PORT_DEFAULTS[FRONTEND_HOST_PORT]=12881
      STACK_PORT_DEFAULTS[NGINX_API_PORT]=12880
      STACK_PORT_DEFAULTS[ELASTICSEARCH_PORT]=12888
      STACK_PORT_DEFAULTS[KIBANA_PORT]=12889
      ;;
    cluster)
      STACK_PORT_VARIABLES=(
        NACOS_HOST_PORT
        MAILHOG_UI_HOST_PORT
        FRONTEND_HOST_PORT
        NGINX_API_PORT
        GARAGE_S3_HOST_PORT
        GARAGE_ADMIN_HOST_PORT
        ELASTICSEARCH_PORT
        KIBANA_PORT
      )
      STACK_PORT_DEFAULTS[NACOS_HOST_PORT]=38848
      STACK_PORT_DEFAULTS[MAILHOG_UI_HOST_PORT]=38025
      STACK_PORT_DEFAULTS[FRONTEND_HOST_PORT]=13881
      STACK_PORT_DEFAULTS[NGINX_API_PORT]=13880
      STACK_PORT_DEFAULTS[GARAGE_S3_HOST_PORT]=33900
      STACK_PORT_DEFAULTS[GARAGE_ADMIN_HOST_PORT]=33903
      STACK_PORT_DEFAULTS[ELASTICSEARCH_PORT]=13888
      STACK_PORT_DEFAULTS[KIBANA_PORT]=13889
      ;;
    *)
      return
      ;;
  esac
}

resolve_stack_port_values() {
  local variable
  local value
  local existing_variable
  declare -A used_ports=()
  declare -gA STACK_PORT_VALUES=()

  for variable in "${STACK_PORT_VARIABLES[@]}"; do
    if [[ -v "${variable}" ]]; then
      value="${!variable}"
    elif value="$(read_env_file_value "${variable}" "${ENV_FILE}")"; then
      :
    else
      value="${STACK_PORT_DEFAULTS[${variable}]}"
    fi

    if [[ ! "${value}" =~ ^[0-9]+$ ]] || (( 10#${value} < 1 || 10#${value} > 65535 )); then
      echo "[deployment.sh] ${variable} must be a port between 1 and 65535" >&2
      exit 1
    fi
    existing_variable="${used_ports[${value}]:-}"
    if [ -n "${existing_variable}" ]; then
      echo "[deployment.sh] ${variable} and ${existing_variable} must not use the same host port ${value}" >&2
      exit 1
    fi

    used_ports["${value}"]="${variable}"
    STACK_PORT_VALUES["${variable}"]="${value}"
    printf -v "${variable}" '%s' "${value}"
    export "${variable}"
  done
}

validate_custom_project_stack_ports() {
  local default_project_name
  local variable
  local reused_variables=()

  default_project_name="$(resolve_default_project_name)"
  if [ "${PROJECT_NAME}" = "${default_project_name}" ]; then
    return
  fi

  for variable in "${STACK_PORT_VARIABLES[@]}"; do
    if [ "${STACK_PORT_VALUES[${variable}]}" = "${STACK_PORT_DEFAULTS[${variable}]}" ]; then
      reused_variables+=("${variable}")
    fi
  done

  if [ "${#reused_variables[@]}" -gt 0 ]; then
    echo "[deployment.sh] custom project '${PROJECT_NAME}' requires independent localhost ports" >&2
    echo "[deployment.sh] values still using ${STACK} port defaults: ${reused_variables[*]}" >&2
    exit 1
  fi
}

CALLER_PWD="$(pwd)"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"
export COMMUNITY_DEPLOY_ROOT="${REPO_ROOT}/deploy"

if [ "$#" -eq 0 ]; then
  usage
  exit 1
fi

COMMAND="$1"
shift

OBSERVABILITY_MODE="default"
HOST_ACCESS=0
STACK=""
ENV_FILE=""
PROJECT_NAME=""
EXTRA_ARGS=()
OUTPUT_DIR="${REPO_ROOT}/backend/env/generated"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --stack)
      if [ "$#" -lt 2 ]; then
        echo "[deployment.sh] missing value for --stack" >&2
        exit 1
      fi
      STACK="$2"
      shift
      ;;
    --observability)
      if [ "${OBSERVABILITY_MODE}" = "disabled" ]; then
        echo "[deployment.sh] --observability and --no-observability are mutually exclusive" >&2
        exit 1
      fi
      OBSERVABILITY_MODE="enabled"
      ;;
    --no-observability)
      if [ "${OBSERVABILITY_MODE}" = "enabled" ]; then
        echo "[deployment.sh] --observability and --no-observability are mutually exclusive" >&2
        exit 1
      fi
      OBSERVABILITY_MODE="disabled"
      ;;
    --env-file)
      if [ "$#" -lt 2 ]; then
        echo "[deployment.sh] missing value for --env-file" >&2
        exit 1
      fi
      ENV_FILE="$(resolve_path "$2")"
      shift
      ;;
    --output-dir)
      if [ "$#" -lt 2 ]; then
        echo "[deployment.sh] missing value for --output-dir" >&2
        exit 1
      fi
      OUTPUT_DIR="$(resolve_path "$2")"
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
  reset-mysql)
    SUBCOMMAND=()
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
  mock-data)
    SUBCOMMAND=(--profile tools run --rm mock-data-studio)
    ;;
  render-backend-env)
    SUBCOMMAND=()
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

if [ -z "${STACK}" ]; then
  echo "[deployment.sh] --stack is required" >&2
  exit 1
fi

case "${STACK}" in
  infra)
    TOPOLOGY="single"
    HOST_ACCESS=1
    if [ "${OBSERVABILITY_MODE}" = "enabled" ]; then
      echo "[deployment.sh] --stack infra does not support --observability" >&2
      exit 1
    fi
    OBSERVABILITY=0
    ;;
  single)
    TOPOLOGY="single"
    HOST_ACCESS=0
    if [ "${OBSERVABILITY_MODE}" = "enabled" ]; then
      OBSERVABILITY=1
    else
      OBSERVABILITY=0
    fi
    ;;
  cluster)
    TOPOLOGY="cluster"
    HOST_ACCESS=0
    if [ "${OBSERVABILITY_MODE}" = "disabled" ]; then
      OBSERVABILITY=0
    else
      OBSERVABILITY=1
    fi
    ;;
  *)
    echo "[deployment.sh] unsupported stack: ${STACK}" >&2
    exit 1
    ;;
esac

if [ -z "${ENV_FILE}" ]; then
  ENV_FILE="$(resolve_default_env_file)"
fi

if [ "${COMMAND}" = "mock-data" ] && [ "${STACK}" = "infra" ]; then
  echo "[deployment.sh] mock-data requires --stack single or --stack cluster" >&2
  exit 1
fi

if [ ! -f "${ENV_FILE}" ]; then
  echo "[deployment.sh] env file not found: ${ENV_FILE}" >&2
  exit 1
fi

if [ "${COMMAND}" = "render-backend-env" ]; then
  if [ "${STACK}" != "infra" ]; then
    echo "[deployment.sh] render-backend-env requires --stack infra" >&2
    exit 1
  fi
  if [ "${#EXTRA_ARGS[@]}" -ne 0 ]; then
    echo "[deployment.sh] render-backend-env does not accept compose arguments" >&2
    exit 1
  fi
  exec "${REPO_ROOT}/deploy/scripts/render-backend-env.sh" "${ENV_FILE}" "${OUTPUT_DIR}"
fi

if [ -z "${PROJECT_NAME}" ]; then
  PROJECT_NAME="$(resolve_default_project_name)"
fi

STACK_FILE="${REPO_ROOT}/deploy/stacks/${STACK}/compose.yml"
if [ ! -f "${STACK_FILE}" ]; then
  echo "[deployment.sh] stack manifest not found: ${STACK_FILE}" >&2
  exit 1
fi

initialize_topology_defaults
resolve_topology_values
validate_project_topology

if [ "${HOST_ACCESS}" -eq 1 ]; then
  initialize_host_access_defaults
  resolve_host_access_values
  validate_custom_project_host_access
fi

if [ "${STACK}" = "single" ] || [ "${STACK}" = "cluster" ]; then
  initialize_stack_port_defaults
  resolve_stack_port_values
  validate_custom_project_stack_ports
fi

COMPOSE_FILES=("${STACK_FILE}")

if [ "${OBSERVABILITY}" -eq 1 ]; then
  COMPOSE_FILES+=(deploy/compose/overlays/observability.yml)
fi

if [ "${OBSERVABILITY}" -eq 1 ] && [ -z "${OTEL_ENABLED+x}" ]; then
  export OTEL_ENABLED=true
fi

if [ "${OBSERVABILITY}" -eq 0 ]; then
  export OTEL_ENABLED=false
fi

COMPOSE_CMD=(docker compose --project-directory "${REPO_ROOT}/deploy" --env-file "${ENV_FILE}" -p "${PROJECT_NAME}")
for compose_file in "${COMPOSE_FILES[@]}"; do
  COMPOSE_CMD+=(-f "${compose_file}")
done

if [ "${COMMAND}" = "reset-mysql" ]; then
  if [ "${#EXTRA_ARGS[@]}" -ne 0 ]; then
    echo "[deployment.sh] reset-mysql does not accept compose arguments" >&2
    exit 1
  fi
  cd "${REPO_ROOT}"
  "${COMPOSE_CMD[@]}" down

  MYSQL_VOLUMES=("${COMMUNITY_VOLUME_NAMESPACE}_mysql_primary_data")
  if [ "${TOPOLOGY}" = "cluster" ]; then
    MYSQL_VOLUMES+=(
      "${COMMUNITY_VOLUME_NAMESPACE}_mysql_replica_1_data"
      "${COMMUNITY_VOLUME_NAMESPACE}_mysql_replica_2_data"
    )
  fi

  for mysql_volume in "${MYSQL_VOLUMES[@]}"; do
    if docker volume inspect "${mysql_volume}" >/dev/null 2>&1; then
      echo "[deployment.sh] deleting MySQL volume ${mysql_volume}"
      docker volume rm "${mysql_volume}"
    else
      echo "[deployment.sh] MySQL volume ${mysql_volume} does not exist"
    fi
  done
  exit 0
fi

COMPOSE_CMD+=("${SUBCOMMAND[@]}")
COMPOSE_CMD+=("${EXTRA_ARGS[@]}")

cd "${REPO_ROOT}"

exec "${COMPOSE_CMD[@]}"

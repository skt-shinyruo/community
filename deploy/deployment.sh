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

initialize_topology_defaults() {
  local volume_namespace
  local subnet_prefix

  case "${STACK}" in
    infra)
      volume_namespace=community_infra
      subnet_prefix=172.32
      ;;
    single)
      volume_namespace=community_single
      subnet_prefix=172.30
      ;;
    cluster)
      volume_namespace=community_cluster
      subnet_prefix=172.31
      ;;
    *)
      echo "[deployment.sh] unsupported stack: ${STACK}" >&2
      exit 1
      ;;
  esac

  declare -gA TOPOLOGY_DEFAULTS=(
    [COMMUNITY_VOLUME_NAMESPACE]="${volume_namespace}"
    [COMMUNITY_NETWORK_SUBNET]="${subnet_prefix}.0.0/24"
    [COMMUNITY_NETWORK_DYNAMIC_RANGE]="${subnet_prefix}.0.128/25"
    [NGINX_STATIC_IP]="${subnet_prefix}.0.10"
    [GATEWAY_TRUSTED_PROXY_CIDRS]="${subnet_prefix}.0.10/32"
  )
  declare -ga TOPOLOGY_VARIABLES=(
    COMMUNITY_VOLUME_NAMESPACE
    COMMUNITY_NETWORK_SUBNET
    COMMUNITY_NETWORK_DYNAMIC_RANGE
    NGINX_STATIC_IP
  )

  if [ "${STACK}" = "cluster" ]; then
    TOPOLOGY_VARIABLES+=(
      COMMUNITY_GATEWAY_1_STATIC_IP
      COMMUNITY_GATEWAY_2_STATIC_IP
      COMMUNITY_GATEWAY_3_STATIC_IP
    )
    TOPOLOGY_DEFAULTS[COMMUNITY_GATEWAY_1_STATIC_IP]="${subnet_prefix}.0.20"
    TOPOLOGY_DEFAULTS[COMMUNITY_GATEWAY_2_STATIC_IP]="${subnet_prefix}.0.21"
    TOPOLOGY_DEFAULTS[COMMUNITY_GATEWAY_3_STATIC_IP]="${subnet_prefix}.0.22"
    TOPOLOGY_DEFAULTS[COMMUNITY_APP_TRUSTED_PROXY_CIDRS]="${subnet_prefix}.0.20/32,${subnet_prefix}.0.21/32,${subnet_prefix}.0.22/32"
  else
    TOPOLOGY_VARIABLES+=(COMMUNITY_GATEWAY_STATIC_IP)
    TOPOLOGY_DEFAULTS[COMMUNITY_GATEWAY_STATIC_IP]="${subnet_prefix}.0.20"
    TOPOLOGY_DEFAULTS[COMMUNITY_APP_TRUSTED_PROXY_CIDRS]="${subnet_prefix}.0.20/32"
  fi
  TOPOLOGY_VARIABLES+=(GATEWAY_TRUSTED_PROXY_CIDRS COMMUNITY_APP_TRUSTED_PROXY_CIDRS)
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
    value="$(resolve_process_env_then_dotenv_then_fallback \
      "${variable}" "${TOPOLOGY_DEFAULTS[${variable}]}")"

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
    echo "[deployment.sh] values still using ${STACK} defaults: ${reused_variables[*]}" >&2
    exit 1
  fi
}

declare -a PORT_SPECS=()
declare -A RESOLVED_PORTS=()

initialize_port_specs() {
  PORT_SPECS=()
  case "${STACK}" in
    infra)
      PORT_SPECS=(
        MYSQL_HOST_PORT=23306
        REDIS_HOST_PORT=26379
        KAFKA_HOST_PORT=39092
        ELASTICSEARCH_HOST_ACCESS_PORT=29200
        NACOS_HOST_PORT=28848
        NACOS_GRPC_HOST_PORT=29848
        GARAGE_S3_HOST_PORT=23900
        GARAGE_ADMIN_HOST_PORT=23903
        MAILHOG_UI_HOST_PORT=28025
        MAILHOG_SMTP_HOST_PORT=21025
      )
      ;;
    single)
      PORT_SPECS=(
        NACOS_HOST_PORT=18848
        MAILHOG_UI_HOST_PORT=8025
        FRONTEND_HOST_PORT=12881
        NGINX_API_PORT=12880
        ELASTICSEARCH_PORT=12888
        KIBANA_PORT=12889
      )
      ;;
    cluster)
      PORT_SPECS=(
        NACOS_HOST_PORT=38848
        MAILHOG_UI_HOST_PORT=38025
        FRONTEND_HOST_PORT=13881
        NGINX_API_PORT=13880
        GARAGE_S3_HOST_PORT=33900
        GARAGE_ADMIN_HOST_PORT=33903
        ELASTICSEARCH_PORT=13888
        KIBANA_PORT=13889
      )
      ;;
  esac
}

resolve_ports() {
  local spec
  local variable
  local fallback
  local value
  local occupied_by
  declare -A occupied_port_owners=()
  RESOLVED_PORTS=()

  for spec in "${PORT_SPECS[@]}"; do
    variable="${spec%%=*}"
    fallback="${spec#*=}"
    value="$(resolve_process_env_then_dotenv_then_fallback "${variable}" "${fallback}")"

    if [[ ! "${value}" =~ ^[0-9]+$ ]] || (( 10#${value} < 1 || 10#${value} > 65535 )); then
      echo "[deployment.sh] ${variable} must be a port between 1 and 65535" >&2
      exit 1
    fi
    occupied_by="${occupied_port_owners[${value}]:-}"
    if [ -n "${occupied_by}" ]; then
      echo "[deployment.sh] project '${PROJECT_NAME}': ${variable} and ${occupied_by} must not use the same host port ${value}" >&2
      exit 1
    fi

    occupied_port_owners["${value}"]="${variable}"
    RESOLVED_PORTS["${variable}"]="${value}"
    export "${variable}=${value}"
  done
}

validate_custom_project_ports() {
  local default_project_name
  local spec
  local variable
  local fallback
  local reused_specs=()

  default_project_name="$(resolve_default_project_name)"
  if [ "${PROJECT_NAME}" = "${default_project_name}" ]; then
    return
  fi

  for spec in "${PORT_SPECS[@]}"; do
    variable="${spec%%=*}"
    fallback="${spec#*=}"
    if [ "${RESOLVED_PORTS[${variable}]}" = "${fallback}" ]; then
      reused_specs+=("${spec}")
    fi
  done

  if [ "${#reused_specs[@]}" -gt 0 ]; then
    if [ "${STACK}" = "infra" ]; then
      echo "[deployment.sh] custom infra project '${PROJECT_NAME}' requires independent localhost ports" >&2
    else
      echo "[deployment.sh] custom project '${PROJECT_NAME}' requires independent localhost ports" >&2
    fi
    echo "[deployment.sh] values still using port defaults: ${reused_specs[*]}" >&2
    exit 1
  fi
}

CALLER_PWD="$(pwd)"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"
export COMMUNITY_DEPLOY_ROOT="${REPO_ROOT}/deploy"

. "${SCRIPT_DIR}/scripts/lib/dotenv.sh"

if [ "$#" -eq 0 ]; then
  usage
  exit 1
fi

COMMAND="$1"
shift

OBSERVABILITY_MODE="default"
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
    if [ "${OBSERVABILITY_MODE}" = "enabled" ]; then
      echo "[deployment.sh] --stack infra does not support --observability" >&2
      exit 1
    fi
    OBSERVABILITY=0
    ;;
  single)
    if [ "${OBSERVABILITY_MODE}" = "enabled" ]; then
      OBSERVABILITY=1
    else
      OBSERVABILITY=0
    fi
    ;;
  cluster)
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

initialize_port_specs
resolve_ports
validate_custom_project_ports

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
  if [ "${STACK}" = "cluster" ]; then
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

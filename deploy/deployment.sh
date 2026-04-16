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
  --topology <dev|ha>  Choose topology (default: ha)
  --scope <full|infra> Choose compose scope (default: full)
  --observability     Add deploy/compose.observability.yml
  --env-file <path>   Override env file path (default: deploy/.env.dev or deploy/.env.ha, fallback deploy/.env for ha)
  -p, --project-name  Override compose project name (default: community-dev or community-ha)
  -h, --help          Show this help

Examples:
  ./deploy/deployment.sh up
  ./deploy/deployment.sh up --topology dev
  ./deploy/deployment.sh up --topology dev --scope infra
  ./deploy/deployment.sh up --observability
  ./deploy/deployment.sh up --topology ha -p community-ha-smoke
  ./deploy/deployment.sh logs --observability community-app-1
  ./deploy/deployment.sh down --observability
  ./deploy/deployment.sh config --topology dev
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
    dev)
      printf '%s/deploy/.env.dev\n' "${REPO_ROOT}"
      ;;
    ha)
      if [ -f "${REPO_ROOT}/deploy/.env.ha" ]; then
        printf '%s/deploy/.env.ha\n' "${REPO_ROOT}"
      else
        printf '%s/deploy/.env\n' "${REPO_ROOT}"
      fi
      ;;
    *)
      echo "[deployment.sh] unsupported topology: ${topology}" >&2
      exit 1
      ;;
  esac
}

resolve_default_project_name() {
  case "${TOPOLOGY}" in
    dev) printf 'community-dev\n' ;;
    ha) printf 'community-ha\n' ;;
    *)
      echo "[deployment.sh] unsupported topology: ${TOPOLOGY}" >&2
      exit 1
      ;;
  esac
}

append_topology_files() {
  case "${TOPOLOGY}" in
    dev)
      COMPOSE_FILES+=(
        deploy/compose.infra.mysql.dev.yml
        deploy/compose.infra.redis.dev.yml
        deploy/compose.infra.kafka.dev.yml
        deploy/compose.infra.elasticsearch.dev.yml
        deploy/compose.infra.nacos.dev.yml
        deploy/compose.infra.xxl-job.dev.yml
        deploy/compose.infra.mailhog.yml
        deploy/compose.infra.mock-data-studio-bootstrap.dev.yml
      )
      if [ "${SCOPE}" = "full" ]; then
        COMPOSE_FILES+=(
          deploy/compose.runtime.services.dev.yml
          deploy/compose.runtime.frontend-nginx.dev.yml
          deploy/compose.runtime.mock-data-studio.dev.yml
        )
      fi
      ;;
    ha)
      COMPOSE_FILES+=(
        deploy/compose.infra.mysql.ha.yml
        deploy/compose.infra.redis.ha.yml
        deploy/compose.infra.kafka.ha.yml
        deploy/compose.infra.elasticsearch.ha.yml
        deploy/compose.infra.nacos.ha.yml
        deploy/compose.infra.xxl-job.ha.yml
        deploy/compose.infra.mailhog.yml
        deploy/compose.infra.mock-data-studio-bootstrap.ha.yml
      )
      if [ "${SCOPE}" = "full" ]; then
        COMPOSE_FILES+=(
          deploy/compose.runtime.services.ha.yml
          deploy/compose.runtime.frontend-nginx.ha.yml
          deploy/compose.runtime.mock-data-studio.ha.yml
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

ELASTIC=0
TOPOLOGY="ha"
SCOPE="full"
ENV_FILE=""
PROJECT_NAME=""
EXTRA_ARGS=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    --obs)
      echo "[deployment.sh] --obs has been removed; use --observability for the supported observability path" >&2
      exit 1
      ;;
    --debug)
      echo "[deployment.sh] --debug has been removed; use logs/exec or the default ingress path for troubleshooting" >&2
      exit 1
      ;;
    --observability)
      ELASTIC=1
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
  dev|ha)
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

COMPOSE_FILES=(deploy/compose.yml)
append_topology_files

if [ "${ELASTIC}" -eq 1 ]; then
  COMPOSE_FILES+=(deploy/compose.observability.yml)
fi

COMPOSE_CMD=(docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}")
for compose_file in "${COMPOSE_FILES[@]}"; do
  COMPOSE_CMD+=(-f "${compose_file}")
done

COMPOSE_CMD+=("${SUBCOMMAND[@]}")
COMPOSE_CMD+=("${EXTRA_ARGS[@]}")

cd "${REPO_ROOT}"

exec "${COMPOSE_CMD[@]}"

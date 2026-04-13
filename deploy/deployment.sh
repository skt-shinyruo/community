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
  --observability     Add deploy/compose.observability.yml
  --env-file <path>   Override env file path (default: deploy/.env)
  -p, --project-name  Override compose project name
  -h, --help          Show this help

Examples:
  ./deploy/deployment.sh up
  ./deploy/deployment.sh up --observability
  ./deploy/deployment.sh up -p community-dev
  ./deploy/deployment.sh logs --observability community-app-1
  ./deploy/deployment.sh down --observability
  ./deploy/deployment.sh config
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
ENV_FILE="${REPO_ROOT}/deploy/.env"
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

if [ ! -f "${ENV_FILE}" ]; then
  echo "[deployment.sh] env file not found: ${ENV_FILE}" >&2
  exit 1
fi

COMPOSE_FILES=(
  deploy/compose.yml
  deploy/compose.infra.mysql.yml
  deploy/compose.infra.redis.yml
  deploy/compose.infra.kafka.yml
  deploy/compose.infra.elasticsearch.yml
  deploy/compose.infra.nacos.yml
  deploy/compose.infra.xxl-job.yml
  deploy/compose.infra.mailhog.yml
  deploy/compose.infra.mock-data-studio-bootstrap.yml
  deploy/compose.runtime.community-app.yml
  deploy/compose.runtime.im-core.yml
  deploy/compose.runtime.im-realtime.yml
  deploy/compose.runtime.community-gateway.yml
  deploy/compose.runtime.frontend-nginx.yml
  deploy/compose.runtime.mock-data-studio.yml
)

if [ "${ELASTIC}" -eq 1 ]; then
  COMPOSE_FILES+=(deploy/compose.observability.yml)
fi

COMPOSE_CMD=(docker compose --env-file "${ENV_FILE}")
if [ -n "${PROJECT_NAME}" ]; then
  COMPOSE_CMD+=(-p "${PROJECT_NAME}")
fi
for compose_file in "${COMPOSE_FILES[@]}"; do
  COMPOSE_CMD+=(-f "${compose_file}")
done

COMPOSE_CMD+=("${SUBCOMMAND[@]}")
COMPOSE_CMD+=("${EXTRA_ARGS[@]}")

cd "${REPO_ROOT}"

exec "${COMPOSE_CMD[@]}"

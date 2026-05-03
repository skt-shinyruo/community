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
  --observability     Add deploy/compose.observability.yml
  --env-file <path>   Override env file path (default: deploy/.env.single or deploy/.env.cluster)
  -p, --project-name  Override compose project name (default: community-single or community-cluster)
  -h, --help          Show this help

Examples:
  ./deploy/deployment.sh up
  ./deploy/deployment.sh up --topology single
  ./deploy/deployment.sh up --topology single --scope infra
  ./deploy/deployment.sh up --observability
  ./deploy/deployment.sh up --topology cluster -p community-cluster-smoke
  ./deploy/deployment.sh logs --observability community-app-1
  ./deploy/deployment.sh down --observability
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

append_topology_files() {
  case "${TOPOLOGY}" in
    single)
      COMPOSE_FILES+=(
        deploy/compose.infra.mysql.single.yml
        deploy/compose.infra.redis.single.yml
        deploy/compose.infra.kafka.single.yml
        deploy/compose.infra.elasticsearch.single.yml
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

ELASTIC=0
TOPOLOGY="cluster"
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

COMPOSE_FILES=(deploy/compose.yml)
append_topology_files

if [ "${ELASTIC}" -eq 1 ]; then
  COMPOSE_FILES+=(deploy/compose.observability.yml)
fi

if [ "${ELASTIC}" -eq 1 ] && [ -z "${OTEL_ENABLED+x}" ]; then
  export OTEL_ENABLED=true
fi

COMPOSE_CMD=(docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}")
for compose_file in "${COMPOSE_FILES[@]}"; do
  COMPOSE_CMD+=(-f "${compose_file}")
done

COMPOSE_CMD+=("${SUBCOMMAND[@]}")
COMPOSE_CMD+=("${EXTRA_ARGS[@]}")

cd "${REPO_ROOT}"

exec "${COMPOSE_CMD[@]}"

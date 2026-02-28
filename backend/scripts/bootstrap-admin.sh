#!/usr/bin/env bash
set -euo pipefail

# Bootstrap / downgrade user roles by directly updating the user-service DB (self-host friendly).
#
# Usage examples:
#   ./scripts/bootstrap-admin.sh --username aaa --set admin --reason "initial bootstrap"
#   ./scripts/bootstrap-admin.sh --email admin@example.com --set moderator --reason "ops staffing"
#   ./scripts/bootstrap-admin.sh --user-id 1 --set user --reason "rollback"
#
# Notes:
# - This script prefers docker (container name: community-mysql). If docker is not available,
#   it falls back to local mysql client using MYSQL_HOST/MYSQL_PORT.
# - It will source ENV_FILE (default: deploy/.env) if present.

ENV_FILE="${ENV_FILE:-deploy/.env}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-community-mysql}"

TARGET_USER_ID=""
TARGET_USERNAME=""
TARGET_EMAIL=""
SET_ROLE=""
REASON=""
DRY_RUN="false"

usage() {
  cat <<'EOF'
Usage: bootstrap-admin.sh [options]

Target (choose one):
  --user-id <id>
  --username <name>
  --email <email>

Role:
  --set <admin|moderator|user|1|2|0>

Audit:
  --reason <text>    (required)

Optional:
  --dry-run

Env:
  ENV_FILE=deploy/.env          (default)
  MYSQL_CONTAINER=community-mysql
  MYSQL_HOST=127.0.0.1
  MYSQL_PORT=3306
  MYSQL_ROOT_PASSWORD=...
  MYSQL_DATABASE=community
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --user-id) TARGET_USER_ID="${2:-}"; shift 2 ;;
    --username) TARGET_USERNAME="${2:-}"; shift 2 ;;
    --email) TARGET_EMAIL="${2:-}"; shift 2 ;;
    --set) SET_ROLE="${2:-}"; shift 2 ;;
    --reason) REASON="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN="true"; shift 1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "[bootstrap-admin] unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  set -a && source "${ENV_FILE}" && set +a
fi

if [[ -z "${SET_ROLE}" ]]; then
  echo "[bootstrap-admin] missing --set" >&2
  usage
  exit 2
fi
if [[ -z "${REASON}" ]]; then
  echo "[bootstrap-admin] missing --reason" >&2
  usage
  exit 2
fi

ROLE_TYPE=""
case "${SET_ROLE}" in
  admin|ADMIN|1) ROLE_TYPE="1" ;;
  moderator|MODERATOR|2) ROLE_TYPE="2" ;;
  user|USER|0) ROLE_TYPE="0" ;;
  *) echo "[bootstrap-admin] invalid --set: ${SET_ROLE}" >&2; usage; exit 2 ;;
esac

target_count=0
[[ -n "${TARGET_USER_ID}" ]] && target_count=$((target_count + 1))
[[ -n "${TARGET_USERNAME}" ]] && target_count=$((target_count + 1))
[[ -n "${TARGET_EMAIL}" ]] && target_count=$((target_count + 1))
if [[ ${target_count} -ne 1 ]]; then
  echo "[bootstrap-admin] choose exactly one target: --user-id/--username/--email" >&2
  usage
  exit 2
fi

MYSQL_DATABASE="${MYSQL_DATABASE:-community}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[bootstrap-admin] missing MYSQL_ROOT_PASSWORD (set it or provide deploy/.env)" >&2
  exit 1
fi

where_clause=""
if [[ -n "${TARGET_USER_ID}" ]]; then
  where_clause="id = ${TARGET_USER_ID}"
elif [[ -n "${TARGET_USERNAME}" ]]; then
  where_clause="username = '$(printf "%s" "${TARGET_USERNAME}" | sed "s/'/''/g")'"
elif [[ -n "${TARGET_EMAIL}" ]]; then
  where_clause="email = '$(printf "%s" "${TARGET_EMAIL}" | sed "s/'/''/g")'"
fi

select_sql="select id, username, email, type from user where ${where_clause} limit 1;"
update_sql="update user set type = ${ROLE_TYPE} where ${where_clause} limit 1;"

run_mysql() {
  local sql="$1"
  if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}\$"; then
    docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" -e "${sql}"
  else
    if ! command -v mysql >/dev/null 2>&1; then
      echo "[bootstrap-admin] mysql client not found and docker container not running (${MYSQL_CONTAINER})" >&2
      exit 1
    fi
    mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" -e "${sql}"
  fi
}

echo "[bootstrap-admin] target=${where_clause}"
echo "[bootstrap-admin] roleType=${ROLE_TYPE} dryRun=${DRY_RUN}"
echo "[bootstrap-admin] reason=${REASON}"

echo "[bootstrap-admin] BEFORE:"
run_mysql "${select_sql}"

if [[ "${DRY_RUN}" == "true" ]]; then
  echo "[bootstrap-admin] DRY RUN: skip update"
  exit 0
fi

run_mysql "${update_sql}"

echo "[bootstrap-admin] AFTER:"
run_mysql "${select_sql}"

echo "[audit] action=bootstrap_user_role_update actor=script target=${where_clause} toType=${ROLE_TYPE} reason=\"${REASON}\""


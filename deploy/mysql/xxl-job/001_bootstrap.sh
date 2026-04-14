#!/usr/bin/env bash
set -euo pipefail

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
XXL_JOB_MYSQL_HOST="${XXL_JOB_MYSQL_HOST:-mysql-primary}"
XXL_JOB_MYSQL_PORT="${XXL_JOB_MYSQL_PORT:-3306}"
XXL_JOB_MYSQL_DATABASE="${XXL_JOB_MYSQL_DATABASE:-xxl_job}"
XXL_JOB_MYSQL_USER="${XXL_JOB_MYSQL_USER:-xxl_job}"
XXL_JOB_MYSQL_PASSWORD="${XXL_JOB_MYSQL_PASSWORD:-xxljobpass}"
BOOTSTRAP_DIR="${BOOTSTRAP_DIR:-/bootstrap}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[xxl-job-db-bootstrap] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

db_escaped="$(sql_escape "${XXL_JOB_MYSQL_DATABASE}")"
user_escaped="$(sql_escape "${XXL_JOB_MYSQL_USER}")"
password_escaped="$(sql_escape "${XXL_JOB_MYSQL_PASSWORD}")"

mysql_base_args=(
  --default-character-set=utf8mb4
  "-h${XXL_JOB_MYSQL_HOST}"
  "-P${XXL_JOB_MYSQL_PORT}"
  -uroot
  "-p${MYSQL_ROOT_PASSWORD}"
)

echo "[xxl-job-db-bootstrap] ensuring database and runtime grants..."
mysql "${mysql_base_args[@]}" <<SQL
create database if not exists \`${db_escaped}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;
create user if not exists '${user_escaped}'@'%' identified by '${password_escaped}';
grant select, insert, update, delete on \`${db_escaped}\`.* to '${user_escaped}'@'%';
flush privileges;
SQL

table_exists="$(
  mysql "${mysql_base_args[@]}" -N -B <<SQL
select count(*)
from information_schema.tables
where table_schema = '${db_escaped}'
  and table_name = 'xxl_job_info';
SQL
)"

if [[ "${table_exists}" == "0" ]]; then
  echo "[xxl-job-db-bootstrap] importing XXL-JOB schema..."
  mysql "${mysql_base_args[@]}" "${XXL_JOB_MYSQL_DATABASE}" < "${BOOTSTRAP_DIR}/010_schema.sql"
else
  echo "[xxl-job-db-bootstrap] schema already initialized; skipping import."
fi

echo "[xxl-job-db-bootstrap] applying local XXL-JOB seed..."
MYSQL_HOST="${XXL_JOB_MYSQL_HOST}" \
MYSQL_PORT="${XXL_JOB_MYSQL_PORT}" \
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD}" \
XXL_JOB_MYSQL_DATABASE="${XXL_JOB_MYSQL_DATABASE}" \
"${BOOTSTRAP_DIR}/020_seed_local.sh"

echo "[xxl-job-db-bootstrap] done."

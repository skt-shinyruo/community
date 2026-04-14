#!/usr/bin/env bash
set -euo pipefail

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
NACOS_MYSQL_HOST="${NACOS_MYSQL_HOST:-mysql-primary}"
NACOS_MYSQL_PORT="${NACOS_MYSQL_PORT:-3306}"
NACOS_MYSQL_DATABASE="${NACOS_MYSQL_DATABASE:-nacos}"
NACOS_MYSQL_USER="${NACOS_MYSQL_USER:-nacos}"
NACOS_MYSQL_PASSWORD="${NACOS_MYSQL_PASSWORD:-nacospass}"
BOOTSTRAP_DIR="${BOOTSTRAP_DIR:-/bootstrap}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[nacos-db-bootstrap] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

db_escaped="$(sql_escape "${NACOS_MYSQL_DATABASE}")"
user_escaped="$(sql_escape "${NACOS_MYSQL_USER}")"
password_escaped="$(sql_escape "${NACOS_MYSQL_PASSWORD}")"

mysql_base_args=(
  --default-character-set=utf8mb4
  "-h${NACOS_MYSQL_HOST}"
  "-P${NACOS_MYSQL_PORT}"
  -uroot
  "-p${MYSQL_ROOT_PASSWORD}"
)

echo "[nacos-db-bootstrap] ensuring database and runtime grants..."
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
  and table_name = 'config_info';
SQL
)"

if [[ "${table_exists}" == "0" ]]; then
  echo "[nacos-db-bootstrap] importing Nacos schema..."
  mysql "${mysql_base_args[@]}" "${NACOS_MYSQL_DATABASE}" < "${BOOTSTRAP_DIR}/010_schema.sql"
else
  echo "[nacos-db-bootstrap] schema already initialized; skipping import."
fi

echo "[nacos-db-bootstrap] done."

#!/usr/bin/env bash
set -euo pipefail

# mysql-primary first-boot database/user bootstrap for community + im_core only.
# The MySQL image entrypoint executes this file from /docker-entrypoint-initdb.d
# only when the data directory is empty.

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_DATABASE="${MYSQL_DATABASE:-community}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MOCK_DATA_STUDIO_DB_USER="${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}"
MOCK_DATA_STUDIO_DB_PASSWORD="${MOCK_DATA_STUDIO_DB_PASSWORD:-mockdatastudiopass}"

IM_MYSQL_DATABASE="${IM_MYSQL_DATABASE:-im_core}"
IM_MYSQL_USER="${IM_MYSQL_USER:-im_core}"
IM_MYSQL_PASSWORD="${IM_MYSQL_PASSWORD:-imcorepass}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[mysql-primary-init] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

echo "[mysql-primary-init] creating community/im_core databases and users..."

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

MYSQL_USER_ESCAPED="$(sql_escape "${MYSQL_USER:-community}")"
MYSQL_PASSWORD_ESCAPED="$(sql_escape "${MYSQL_PASSWORD:-communitypass}")"
MYSQL_DATABASE_ESCAPED="$(sql_escape "${MYSQL_DATABASE}")"
MOCK_DATA_STUDIO_DB_USER_ESCAPED="$(sql_escape "${MOCK_DATA_STUDIO_DB_USER}")"
MOCK_DATA_STUDIO_DB_PASSWORD_ESCAPED="$(sql_escape "${MOCK_DATA_STUDIO_DB_PASSWORD}")"
IM_MYSQL_DATABASE_ESCAPED="$(sql_escape "${IM_MYSQL_DATABASE}")"
IM_MYSQL_USER_ESCAPED="$(sql_escape "${IM_MYSQL_USER}")"
IM_MYSQL_PASSWORD_ESCAPED="$(sql_escape "${IM_MYSQL_PASSWORD}")"

mysql --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
create database if not exists \`${MYSQL_DATABASE_ESCAPED}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${MYSQL_USER_ESCAPED}'@'%' identified by '${MYSQL_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`${MYSQL_DATABASE_ESCAPED}\`.* to '${MYSQL_USER_ESCAPED}'@'%';

create user if not exists '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%' identified by '${MOCK_DATA_STUDIO_DB_PASSWORD_ESCAPED}';
grant select, insert, update, delete, create, alter on \`${MYSQL_DATABASE_ESCAPED}\`.* to '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%';

create database if not exists \`${IM_MYSQL_DATABASE_ESCAPED}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${IM_MYSQL_USER_ESCAPED}'@'%' identified by '${IM_MYSQL_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`${IM_MYSQL_DATABASE_ESCAPED}\`.* to '${IM_MYSQL_USER_ESCAPED}'@'%';
grant select, insert, update, delete on \`${IM_MYSQL_DATABASE_ESCAPED}\`.* to '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%';

flush privileges;
SQL

echo "[mysql-primary-init] done."

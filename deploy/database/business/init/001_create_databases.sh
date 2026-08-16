#!/usr/bin/env bash
set -euo pipefail

# mysql-primary first-boot database/user bootstrap for Community-owned databases.
# The MySQL image entrypoint executes this file from /docker-entrypoint-initdb.d
# only when the data directory is empty.

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_HOST="${MYSQL_HOST:-}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="community"
COMMUNITY_MYSQL_USER="${COMMUNITY_MYSQL_USER:-community}"
COMMUNITY_MYSQL_PASSWORD="${COMMUNITY_MYSQL_PASSWORD:-communitypass}"
COMMUNITY_MIGRATION_USERNAME="${COMMUNITY_MIGRATION_USERNAME:-community_migrator}"
COMMUNITY_MIGRATION_PASSWORD="${COMMUNITY_MIGRATION_PASSWORD:-}"
MOCK_DATA_STUDIO_DB_USER="${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}"
MOCK_DATA_STUDIO_DB_PASSWORD="${MOCK_DATA_STUDIO_DB_PASSWORD:-mockdatastudiopass}"

IM_MYSQL_DATABASE="im_core"
IM_MYSQL_USER="${IM_MYSQL_USER:-im_core}"
IM_MYSQL_PASSWORD="${IM_MYSQL_PASSWORD:-imcorepass}"

OSS_MYSQL_DATABASE="community_oss"
OSS_MYSQL_USER="${OSS_MYSQL_USER:-community_oss}"
OSS_MYSQL_PASSWORD="${OSS_MYSQL_PASSWORD:-communityosspass}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[mysql-primary-init] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi
if [[ -z "${COMMUNITY_MIGRATION_PASSWORD}" ]]; then
  echo "[mysql-primary-init] missing env: COMMUNITY_MIGRATION_PASSWORD" >&2
  exit 1
fi
if [[ "${COMMUNITY_MIGRATION_USERNAME}" == "${COMMUNITY_MYSQL_USER}" ]]; then
  echo "[mysql-primary-init] migration and runtime usernames must differ" >&2
  exit 1
fi

echo "[mysql-primary-init] creating community, im_core, and community_oss databases and users..."

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

MYSQL_USER_ESCAPED="$(sql_escape "${COMMUNITY_MYSQL_USER}")"
MYSQL_PASSWORD_ESCAPED="$(sql_escape "${COMMUNITY_MYSQL_PASSWORD}")"
MYSQL_DATABASE_ESCAPED="$(sql_escape "${MYSQL_DATABASE}")"
COMMUNITY_MIGRATION_USERNAME_ESCAPED="$(sql_escape "${COMMUNITY_MIGRATION_USERNAME}")"
COMMUNITY_MIGRATION_PASSWORD_ESCAPED="$(sql_escape "${COMMUNITY_MIGRATION_PASSWORD}")"
MOCK_DATA_STUDIO_DB_USER_ESCAPED="$(sql_escape "${MOCK_DATA_STUDIO_DB_USER}")"
MOCK_DATA_STUDIO_DB_PASSWORD_ESCAPED="$(sql_escape "${MOCK_DATA_STUDIO_DB_PASSWORD}")"
IM_MYSQL_DATABASE_ESCAPED="$(sql_escape "${IM_MYSQL_DATABASE}")"
IM_MYSQL_USER_ESCAPED="$(sql_escape "${IM_MYSQL_USER}")"
IM_MYSQL_PASSWORD_ESCAPED="$(sql_escape "${IM_MYSQL_PASSWORD}")"
OSS_MYSQL_DATABASE_ESCAPED="$(sql_escape "${OSS_MYSQL_DATABASE}")"
OSS_MYSQL_USER_ESCAPED="$(sql_escape "${OSS_MYSQL_USER}")"
OSS_MYSQL_PASSWORD_ESCAPED="$(sql_escape "${OSS_MYSQL_PASSWORD}")"

mysql_args=(
  --default-character-set=utf8mb4
  -uroot
  "-p${MYSQL_ROOT_PASSWORD}"
)
if [[ -n "${MYSQL_HOST}" ]]; then
  mysql_args+=("-h${MYSQL_HOST}" "-P${MYSQL_PORT}")
fi

mysql "${mysql_args[@]}" <<SQL
create database if not exists \`${MYSQL_DATABASE_ESCAPED}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${MYSQL_USER_ESCAPED}'@'%' identified by '${MYSQL_PASSWORD_ESCAPED}';
revoke all privileges, grant option from '${MYSQL_USER_ESCAPED}'@'%';
grant select, insert, update, delete on \`${MYSQL_DATABASE_ESCAPED}\`.* to '${MYSQL_USER_ESCAPED}'@'%';

create user if not exists '${COMMUNITY_MIGRATION_USERNAME_ESCAPED}'@'%'
  identified by '${COMMUNITY_MIGRATION_PASSWORD_ESCAPED}';
revoke all privileges, grant option from '${COMMUNITY_MIGRATION_USERNAME_ESCAPED}'@'%';
grant select, insert, update, delete, create, alter, index, drop
  on \`${MYSQL_DATABASE_ESCAPED}\`.* to '${COMMUNITY_MIGRATION_USERNAME_ESCAPED}'@'%';

create user if not exists '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%' identified by '${MOCK_DATA_STUDIO_DB_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`${MYSQL_DATABASE_ESCAPED}\`.* to '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%';

create database if not exists \`${IM_MYSQL_DATABASE_ESCAPED}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${IM_MYSQL_USER_ESCAPED}'@'%' identified by '${IM_MYSQL_PASSWORD_ESCAPED}';
revoke all privileges, grant option from '${IM_MYSQL_USER_ESCAPED}'@'%';
grant select, insert, update, delete on \`${IM_MYSQL_DATABASE_ESCAPED}\`.* to '${IM_MYSQL_USER_ESCAPED}'@'%';

grant select, insert, update, delete on \`${IM_MYSQL_DATABASE_ESCAPED}\`.* to '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%';

create database if not exists \`${OSS_MYSQL_DATABASE_ESCAPED}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${OSS_MYSQL_USER_ESCAPED}'@'%' identified by '${OSS_MYSQL_PASSWORD_ESCAPED}';
revoke all privileges, grant option from '${OSS_MYSQL_USER_ESCAPED}'@'%';
grant select, insert, update, delete on \`${OSS_MYSQL_DATABASE_ESCAPED}\`.* to '${OSS_MYSQL_USER_ESCAPED}'@'%';

grant select, insert, update, delete on \`${OSS_MYSQL_DATABASE_ESCAPED}\`.* to '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%';

flush privileges;
SQL

echo "[mysql-primary-init] done."

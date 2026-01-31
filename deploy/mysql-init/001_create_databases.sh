#!/usr/bin/env bash
set -euo pipefail

# 在 MySQL 容器初始化阶段创建多 schema 与最小权限账号。
# 说明：该脚本由 mysql 镜像 entrypoint 在首次初始化数据卷时自动执行（位于 /docker-entrypoint-initdb.d）。

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_DATABASE="${MYSQL_DATABASE:-community}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"

CONTENT_DB_NAME="${CONTENT_DB_NAME:-community_content}"
CONTENT_DB_USER="${CONTENT_DB_USER:-community_content}"
CONTENT_DB_PASSWORD="${CONTENT_DB_PASSWORD:-community_contentpass}"

MESSAGE_DB_NAME="${MESSAGE_DB_NAME:-community_message}"
MESSAGE_DB_USER="${MESSAGE_DB_USER:-community_message}"
MESSAGE_DB_PASSWORD="${MESSAGE_DB_PASSWORD:-community_messagepass}"

SEARCH_DB_NAME="${SEARCH_DB_NAME:-community_search}"
SEARCH_DB_USER="${SEARCH_DB_USER:-community_search}"
SEARCH_DB_PASSWORD="${SEARCH_DB_PASSWORD:-community_searchpass}"

SOCIAL_DB_NAME="${SOCIAL_DB_NAME:-community_social}"
SOCIAL_DB_USER="${SOCIAL_DB_USER:-community_social}"
SOCIAL_DB_PASSWORD="${SOCIAL_DB_PASSWORD:-community_socialpass}"

USER_DB_NAME="${USER_DB_NAME:-${MYSQL_DATABASE}}"
USER_DB_USERNAME="${USER_DB_USERNAME:-${MYSQL_USER:-community}}"
USER_DB_PASSWORD="${USER_DB_PASSWORD:-${MYSQL_PASSWORD:-communitypass}}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[mysql-init] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

echo "[mysql-init] creating databases/users..."

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
create database if not exists \`${MYSQL_DATABASE}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create database if not exists \`${CONTENT_DB_NAME}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create database if not exists \`${MESSAGE_DB_NAME}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create database if not exists \`${SEARCH_DB_NAME}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create database if not exists \`${SOCIAL_DB_NAME}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${CONTENT_DB_USER}'@'%' identified by '${CONTENT_DB_PASSWORD}';
grant select, insert, update, delete on \`${CONTENT_DB_NAME}\`.* to '${CONTENT_DB_USER}'@'%';

create user if not exists '${MESSAGE_DB_USER}'@'%' identified by '${MESSAGE_DB_PASSWORD}';
grant select, insert, update, delete on \`${MESSAGE_DB_NAME}\`.* to '${MESSAGE_DB_USER}'@'%';

create user if not exists '${SEARCH_DB_USER}'@'%' identified by '${SEARCH_DB_PASSWORD}';
grant select, insert, update, delete on \`${SEARCH_DB_NAME}\`.* to '${SEARCH_DB_USER}'@'%';

create user if not exists '${SOCIAL_DB_USER}'@'%' identified by '${SOCIAL_DB_PASSWORD}';
grant select, insert, update, delete on \`${SOCIAL_DB_NAME}\`.* to '${SOCIAL_DB_USER}'@'%';

create user if not exists '${USER_DB_USERNAME}'@'%' identified by '${USER_DB_PASSWORD}';
grant select, insert, update, delete on \`${USER_DB_NAME}\`.* to '${USER_DB_USERNAME}'@'%';

flush privileges;
SQL

echo "[mysql-init] done."

#!/usr/bin/env bash
set -euo pipefail

# 在 MySQL 容器初始化阶段创建 schema 与最小权限账号。
# 说明：该脚本由 mysql 镜像 entrypoint 在首次初始化数据卷时自动执行（位于 /docker-entrypoint-initdb.d）。

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_DATABASE="${MYSQL_DATABASE:-community}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MOCK_DATA_STUDIO_DB_USER="${MOCK_DATA_STUDIO_DB_USER:-mock_data_studio}"
MOCK_DATA_STUDIO_DB_PASSWORD="${MOCK_DATA_STUDIO_DB_PASSWORD:-mockdatastudiopass}"

IM_MYSQL_DATABASE="${IM_MYSQL_DATABASE:-im_core}"
IM_MYSQL_USER="${IM_MYSQL_USER:-im_core}"
IM_MYSQL_PASSWORD="${IM_MYSQL_PASSWORD:-imcorepass}"

NACOS_MYSQL_DATABASE="${NACOS_MYSQL_DATABASE:-nacos}"
NACOS_MYSQL_USER="${NACOS_MYSQL_USER:-nacos}"
NACOS_MYSQL_PASSWORD="${NACOS_MYSQL_PASSWORD:-nacospass}"

XXL_JOB_MYSQL_USER="${XXL_JOB_MYSQL_USER:-xxl_job}"
XXL_JOB_MYSQL_PASSWORD="${XXL_JOB_MYSQL_PASSWORD:-xxljobpass}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[mysql-init] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

echo "[mysql-init] creating databases/users..."

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

MYSQL_USER_ESCAPED="$(sql_escape "${MYSQL_USER:-community}")"
MYSQL_PASSWORD_ESCAPED="$(sql_escape "${MYSQL_PASSWORD:-communitypass}")"
MOCK_DATA_STUDIO_DB_USER_ESCAPED="$(sql_escape "${MOCK_DATA_STUDIO_DB_USER}")"
MOCK_DATA_STUDIO_DB_PASSWORD_ESCAPED="$(sql_escape "${MOCK_DATA_STUDIO_DB_PASSWORD}")"
IM_MYSQL_USER_ESCAPED="$(sql_escape "${IM_MYSQL_USER}")"
IM_MYSQL_PASSWORD_ESCAPED="$(sql_escape "${IM_MYSQL_PASSWORD}")"
NACOS_MYSQL_DATABASE_ESCAPED="$(sql_escape "${NACOS_MYSQL_DATABASE}")"
NACOS_MYSQL_USER_ESCAPED="$(sql_escape "${NACOS_MYSQL_USER}")"
NACOS_MYSQL_PASSWORD_ESCAPED="$(sql_escape "${NACOS_MYSQL_PASSWORD}")"
XXL_JOB_MYSQL_USER_ESCAPED="$(sql_escape "${XXL_JOB_MYSQL_USER}")"
XXL_JOB_MYSQL_PASSWORD_ESCAPED="$(sql_escape "${XXL_JOB_MYSQL_PASSWORD}")"

mysql --default-character-set=utf8mb4 -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
create database if not exists \`${MYSQL_DATABASE}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${MYSQL_USER_ESCAPED}'@'%' identified by '${MYSQL_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`${MYSQL_DATABASE}\`.* to '${MYSQL_USER_ESCAPED}'@'%';

create user if not exists '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%' identified by '${MOCK_DATA_STUDIO_DB_PASSWORD_ESCAPED}';
grant select, insert, update, delete, create on \`${MYSQL_DATABASE}\`.* to '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%';

create database if not exists \`${IM_MYSQL_DATABASE}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

grant select, insert, update, delete on \`${IM_MYSQL_DATABASE}\`.* to '${MOCK_DATA_STUDIO_DB_USER_ESCAPED}'@'%';

create user if not exists '${IM_MYSQL_USER_ESCAPED}'@'%' identified by '${IM_MYSQL_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`${IM_MYSQL_DATABASE}\`.* to '${IM_MYSQL_USER_ESCAPED}'@'%';

create database if not exists \`${NACOS_MYSQL_DATABASE_ESCAPED}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${NACOS_MYSQL_USER_ESCAPED}'@'%' identified by '${NACOS_MYSQL_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`${NACOS_MYSQL_DATABASE_ESCAPED}\`.* to '${NACOS_MYSQL_USER_ESCAPED}'@'%';

create database if not exists \`xxl_job\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${XXL_JOB_MYSQL_USER_ESCAPED}'@'%' identified by '${XXL_JOB_MYSQL_PASSWORD_ESCAPED}';
grant select, insert, update, delete on \`xxl_job\`.* to '${XXL_JOB_MYSQL_USER_ESCAPED}'@'%';

flush privileges;
SQL

echo "[mysql-init] done."

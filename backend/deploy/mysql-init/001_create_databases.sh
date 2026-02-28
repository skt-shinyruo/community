#!/usr/bin/env bash
set -euo pipefail

# 在 MySQL 容器初始化阶段创建 schema 与最小权限账号。
# 说明：该脚本由 mysql 镜像 entrypoint 在首次初始化数据卷时自动执行（位于 /docker-entrypoint-initdb.d）。

MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_DATABASE="${MYSQL_DATABASE:-community}"
MYSQL_USER="${MYSQL_USER:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[mysql-init] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

echo "[mysql-init] creating databases/users..."

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
create database if not exists \`${MYSQL_DATABASE}\`
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

create user if not exists '${MYSQL_USER:-community}'@'%' identified by '${MYSQL_PASSWORD:-communitypass}';
grant select, insert, update, delete on \`${MYSQL_DATABASE}\`.* to '${MYSQL_USER:-community}'@'%';

flush privileges;
SQL

echo "[mysql-init] done."

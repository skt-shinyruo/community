#!/usr/bin/env bash
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-mysql-primary}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
BOOTSTRAP_DIR="${BOOTSTRAP_DIR:-/bootstrap/community_oss}"
SCHEMA_FILES=(
  010_schema.sql
)

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[community-oss-bootstrap] missing env: MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

mysql_base_args=(
  --default-character-set=utf8mb4
  "-h${MYSQL_HOST}"
  "-P${MYSQL_PORT}"
  -uroot
  "-p${MYSQL_ROOT_PASSWORD}"
)

for schema_file in "${SCHEMA_FILES[@]}"; do
  echo "[community-oss-bootstrap] applying ${schema_file}..."
  mysql "${mysql_base_args[@]}" < "${BOOTSTRAP_DIR}/${schema_file}"
done

echo "[community-oss-bootstrap] done."

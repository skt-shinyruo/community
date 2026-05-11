#!/usr/bin/env bash
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-mysql-primary}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
BOOTSTRAP_DIR="${BOOTSTRAP_DIR:-/bootstrap/community}"
SCHEMA_FILES=(
  010_schema_shared.sql
  011_schema_demo_metadata.sql
  020_schema_identity.sql
  030_schema_growth_reward.sql
  031_schema_growth_wallet.sql
  032_schema_growth_market.sql
  033_schema_growth_task.sql
  040_schema_content_core.sql
  050_schema_social.sql
  060_schema_message.sql
  070_schema_im_core.sql
  080_schema_search.sql
  090_seed_identity.sql
)

if [[ -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[community-bootstrap] missing env: MYSQL_ROOT_PASSWORD" >&2
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
  echo "[community-bootstrap] applying ${schema_file}..."
  mysql "${mysql_base_args[@]}" < "${BOOTSTRAP_DIR}/${schema_file}"
done

echo "[community-bootstrap] done."

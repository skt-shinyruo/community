#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.yml}"
OUT_DIR="${OUT_DIR:-deploy/backups}"
TS="$(date +%Y%m%d%H%M%S)"

MYSQL_DATABASE="${MYSQL_DATABASE:-}"
MYSQL_DATABASES="${MYSQL_DATABASES:-${MYSQL_DATABASE}}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"

if [[ -z "${MYSQL_DATABASES}" || -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[mysql-backup] missing env: MYSQL_DATABASES / MYSQL_ROOT_PASSWORD" >&2
  echo "[mysql-backup] tip: copy deploy/.env.example -> deploy/.env and export envs (or run with env file)" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"
DB_LIST="${MYSQL_DATABASES//,/ }"
OUT_FILE="${OUT_DIR}/mysql-${TS}.sql"

echo "[mysql-backup] dumping ${DB_LIST} -> ${OUT_FILE}"
docker compose -f "${COMPOSE_FILE}" exec -T mysql \
  mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --skip-lock-tables --databases ${DB_LIST} \
  > "${OUT_FILE}"

echo "[mysql-backup] OK"

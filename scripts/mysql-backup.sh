#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.yml}"
OUT_DIR="${OUT_DIR:-deploy/backups}"
TS="$(date +%Y%m%d%H%M%S)"

MYSQL_DATABASE="${MYSQL_DATABASE:-}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"

if [[ -z "${MYSQL_DATABASE}" || -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[mysql-backup] missing env: MYSQL_DATABASE / MYSQL_ROOT_PASSWORD" >&2
  echo "[mysql-backup] tip: copy deploy/.env.example -> deploy/.env and export envs (or run with env file)" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"
OUT_FILE="${OUT_DIR}/mysql-${MYSQL_DATABASE}-${TS}.sql"

echo "[mysql-backup] dumping ${MYSQL_DATABASE} -> ${OUT_FILE}"
docker compose -f "${COMPOSE_FILE}" exec -T mysql \
  mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" --databases "${MYSQL_DATABASE}" \
  > "${OUT_FILE}"

echo "[mysql-backup] OK"


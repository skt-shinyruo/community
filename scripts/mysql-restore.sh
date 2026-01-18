#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.yml}"

MYSQL_DATABASE="${MYSQL_DATABASE:-}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"

SQL_FILE="${1:-}"
if [[ -z "${SQL_FILE}" || ! -f "${SQL_FILE}" ]]; then
  echo "[mysql-restore] usage: $0 <backup.sql>" >&2
  exit 1
fi

if [[ -z "${MYSQL_DATABASE}" || -z "${MYSQL_ROOT_PASSWORD}" ]]; then
  echo "[mysql-restore] missing env: MYSQL_DATABASE / MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

echo "[mysql-restore] restoring ${SQL_FILE} -> ${MYSQL_DATABASE}"
docker compose -f "${COMPOSE_FILE}" exec -T mysql \
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${SQL_FILE}"

echo "[mysql-restore] OK"


#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.yml}"
OUT_DIR="${OUT_DIR:-deploy/backups}"
TS="$(date +%Y%m%d%H%M%S)"

mkdir -p "${OUT_DIR}"
OUT_FILE="${OUT_DIR}/redis-dump-${TS}.rdb"

echo "[redis-backup] triggering SAVE"
docker compose -f "${COMPOSE_FILE}" exec -T redis redis-cli save >/dev/null

CONTAINER_ID="$(docker compose -f "${COMPOSE_FILE}" ps -q redis)"
if [[ -z "${CONTAINER_ID}" ]]; then
  echo "[redis-backup] cannot find redis container id" >&2
  exit 1
fi

echo "[redis-backup] copying /data/dump.rdb -> ${OUT_FILE}"
docker cp "${CONTAINER_ID}:/data/dump.rdb" "${OUT_FILE}"

echo "[redis-backup] OK"


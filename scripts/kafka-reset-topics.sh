#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-deploy/docker-compose.yml}"
BROKER="${BROKER:-kafka:9092}"

TOPICS=(
  "community.event.post.v1"
  "community.event.comment.v1"
  "community.event.social.v1"
  "community.event.post.v1.dlq"
  "community.event.comment.v1.dlq"
  "community.event.social.v1.dlq"
)

echo "[kafka-reset] deleting topics (ignore not-exists)..."
for t in "${TOPICS[@]}"; do
  docker compose -f "${COMPOSE_FILE}" exec -T kafka \
    bash -lc "command -v kafka-topics >/dev/null 2>&1 && kafka-topics --bootstrap-server \"${BROKER}\" --delete --topic \"${t}\" >/dev/null 2>&1 || kafka-topics.sh --bootstrap-server \"${BROKER}\" --delete --topic \"${t}\" >/dev/null 2>&1" || true
done

echo "[kafka-reset] creating topics..."
for t in "${TOPICS[@]}"; do
  docker compose -f "${COMPOSE_FILE}" exec -T kafka \
    bash -lc "command -v kafka-topics >/dev/null 2>&1 && kafka-topics --bootstrap-server \"${BROKER}\" --create --if-not-exists --topic \"${t}\" --replication-factor 1 --partitions 1 || kafka-topics.sh --bootstrap-server \"${BROKER}\" --create --if-not-exists --topic \"${t}\" --replication-factor 1 --partitions 1"
done

echo "[kafka-reset] OK"

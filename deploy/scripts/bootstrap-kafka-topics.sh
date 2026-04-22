#!/usr/bin/env bash
set -euo pipefail

KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka-1:9092,kafka-2:9092,kafka-3:9092}"

echo "[kafka-init] waiting for brokers..."
for _ in $(seq 1 90); do
  if kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --list >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[kafka-init] creating topics (if not exists)..."
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.command.private_text.v1 --replication-factor 3 --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.command.room_text.v1 --replication-factor 3 --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.private_persisted.v1 --replication-factor 3 --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.room_persisted.v1 --replication-factor 3 --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.private_rejected.v1 --replication-factor 3 --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.room_rejected.v1 --replication-factor 3 --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.room_member_changed.v1 --replication-factor 3 --partitions 3
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.command.private_text.v1.dlq --replication-factor 3 --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.command.room_text.v1.dlq --replication-factor 3 --partitions 12
echo "[kafka-init] done."

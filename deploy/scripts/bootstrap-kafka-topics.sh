#!/usr/bin/env bash
set -euo pipefail

KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka-1:9092,kafka-2:9092,kafka-3:9092}"
KAFKA_TOPIC_REPLICATION_FACTOR="${KAFKA_TOPIC_REPLICATION_FACTOR:-3}"

echo "[kafka-init] waiting for brokers..."
for _ in $(seq 1 90); do
  if kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --list >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "[kafka-init] creating topics (if not exists)..."
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.command.private-text --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.command.room-text --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.private-persisted --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.room-persisted --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.private-committed --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.room-committed --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.private-rejected --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.room-rejected --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.room-member-changed --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 3
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.user-messaging-policy-changed --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.event.user-block-relation-changed --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.command.private-text.dlq --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --create --if-not-exists --topic im.command.room-text.dlq --replication-factor "${KAFKA_TOPIC_REPLICATION_FACTOR}" --partitions 12
echo "[kafka-init] done."

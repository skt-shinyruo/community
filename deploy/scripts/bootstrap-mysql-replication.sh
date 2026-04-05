#!/usr/bin/env bash
set -euo pipefail

MYSQL_PRIMARY_HOST="${MYSQL_PRIMARY_HOST:-mysql-primary}"
MYSQL_PRIMARY_PORT="${MYSQL_PRIMARY_PORT:-3306}"
MYSQL_REPLICA_HOSTS="${MYSQL_REPLICA_HOSTS:-mysql-replica-1,mysql-replica-2}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
MYSQL_REPLICATION_USER="${MYSQL_REPLICATION_USER:-replicator}"
MYSQL_REPLICATION_PASSWORD="${MYSQL_REPLICATION_PASSWORD:-replicatorpass}"

wait_for_mysql() {
  local host="$1"
  echo "[mysql-replication-bootstrap] waiting for ${host}..."
  for _ in $(seq 1 90); do
    if mysqladmin ping -h"${host}" -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "[mysql-replication-bootstrap] mysql ${host} did not become ready in time" >&2
  return 1
}

sql_escape() {
  local value="${1//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf "%s" "${value}"
}

wait_for_mysql "${MYSQL_PRIMARY_HOST}"

IFS=',' read -r -a replica_hosts <<<"${MYSQL_REPLICA_HOSTS}"
for replica in "${replica_hosts[@]}"; do
  wait_for_mysql "${replica}"
done

replication_user_escaped="$(sql_escape "${MYSQL_REPLICATION_USER}")"
replication_password_escaped="$(sql_escape "${MYSQL_REPLICATION_PASSWORD}")"
primary_host_escaped="$(sql_escape "${MYSQL_PRIMARY_HOST}")"

echo "[mysql-replication-bootstrap] ensuring replication user exists on ${MYSQL_PRIMARY_HOST}..."
mysql --default-character-set=utf8mb4 -h"${MYSQL_PRIMARY_HOST}" -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
create user if not exists '${replication_user_escaped}'@'%' identified by '${replication_password_escaped}';
grant replication slave, replication client on *.* to '${replication_user_escaped}'@'%';
flush privileges;
SQL

for replica in "${replica_hosts[@]}"; do
  replica_escaped="$(sql_escape "${replica}")"
  echo "[mysql-replication-bootstrap] wiring ${replica} to ${MYSQL_PRIMARY_HOST}..."
  mysql --default-character-set=utf8mb4 -h"${replica}" -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
stop replica;
reset replica all;
change replication source to
  source_host='${primary_host_escaped}',
  source_port=${MYSQL_PRIMARY_PORT},
  source_user='${replication_user_escaped}',
  source_password='${replication_password_escaped}',
  source_auto_position=1,
  get_source_public_key=1;
start replica;
set global super_read_only = on;
SQL
  mysql --default-character-set=utf8mb4 -h"${replica}" -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "show replica status\\G" \
    | sed "s/^/[mysql-replication-bootstrap] ${replica_escaped}: /"
done

echo "[mysql-replication-bootstrap] done."

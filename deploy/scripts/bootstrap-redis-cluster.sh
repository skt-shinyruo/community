#!/bin/sh
set -eu

REDIS_CLUSTER_NODES="${REDIS_CLUSTER_NODES:-redis-1:6379,redis-2:6379,redis-3:6379,redis-4:6379,redis-5:6379,redis-6:6379}"

IFS=','
set -- ${REDIS_CLUSTER_NODES}
unset IFS

first_node="$1"
first_host="${first_node%:*}"
first_port="${first_node##*:}"

wait_for_redis() {
  host="$1"
  port="$2"
  echo "[redis-cluster-bootstrap] waiting for ${host}:${port}..."
  i=0
  while [ "$i" -lt 90 ]; do
    if redis-cli -h "${host}" -p "${port}" ping >/dev/null 2>&1; then
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  echo "[redis-cluster-bootstrap] ${host}:${port} did not become ready in time" >&2
  exit 1
}

for node in "$@"; do
  host="${node%:*}"
  port="${node##*:}"
  wait_for_redis "${host}" "${port}"
done

if redis-cli -h "${first_host}" -p "${first_port}" cluster info 2>/dev/null | grep -q "cluster_state:ok"; then
  echo "[redis-cluster-bootstrap] cluster already initialized."
  exit 0
fi

echo "[redis-cluster-bootstrap] creating cluster..."
redis-cli --cluster create "$@" --cluster-replicas 1 --cluster-yes
echo "[redis-cluster-bootstrap] done."

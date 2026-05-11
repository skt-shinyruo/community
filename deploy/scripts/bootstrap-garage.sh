#!/bin/sh
set -eu

GARAGE_BIN="${GARAGE_BIN:-/garage}"
BUCKET="${GARAGE_DEFAULT_BUCKET:-community-oss}"
ACCESS_KEY="${GARAGE_DEFAULT_ACCESS_KEY:-GK000000000000000000000001}"
SECRET_KEY="${GARAGE_DEFAULT_SECRET_KEY:-0000000000000000000000000000000000000000000000000000000000000001}"
REPLICATION_FACTOR="${GARAGE_REPLICATION_FACTOR:-1}"
CAPACITY="${GARAGE_NODE_CAPACITY:-1G}"
MAX_ATTEMPTS="${GARAGE_INIT_MAX_ATTEMPTS:-60}"

wait_for_node_ids() {
  attempt=1
  while [ "${attempt}" -le "${MAX_ATTEMPTS}" ]; do
    status_json="$("${GARAGE_BIN}" json-api GetClusterStatus 2>/dev/null || true)"
    node_ids="$(printf '%s\n' "${status_json}" | awk -F'"' '/"id":/ { print $4 }')"
    node_count="$(printf '%s\n' "${node_ids}" | sed '/^$/d' | wc -l | tr -d ' ')"
    if [ "${node_count}" -ge "${REPLICATION_FACTOR}" ]; then
      printf '%s\n' "${node_ids}"
      return 0
    fi
    attempt="$((attempt + 1))"
    sleep 2
  done

  echo "[garage-init] Garage nodes did not become ready; expected ${REPLICATION_FACTOR}" >&2
  exit 1
}

node_ids="$(wait_for_node_ids)"

layout_json="$("${GARAGE_BIN}" json-api GetClusterLayout 2>/dev/null)"
current_version="$(printf '%s\n' "${layout_json}" | awk -F'[: ,]+' '/"version":/ { print $3; exit }')"
next_version="$((current_version + 1))"

assigned=0
for node_id in ${node_ids}; do
  if ! printf '%s\n' "${layout_json}" | grep -F "\"id\": \"${node_id}\"" >/dev/null 2>&1; then
    "${GARAGE_BIN}" layout assign -z local -c "${CAPACITY}" "${node_id}"
    assigned=1
  fi
done

if [ "${assigned}" -eq 1 ]; then
  "${GARAGE_BIN}" layout apply --version "${next_version}"
fi

if ! "${GARAGE_BIN}" bucket info "${BUCKET}" >/dev/null 2>&1; then
  "${GARAGE_BIN}" bucket create "${BUCKET}"
fi

if ! "${GARAGE_BIN}" key info "${ACCESS_KEY}" >/dev/null 2>&1; then
  "${GARAGE_BIN}" key import --yes -n community-oss "${ACCESS_KEY}" "${SECRET_KEY}"
fi

"${GARAGE_BIN}" bucket allow --read --write --owner --key "${ACCESS_KEY}" "${BUCKET}"

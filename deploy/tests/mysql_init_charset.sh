#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${repo_root}"

charset_config="deploy/mysql/conf/mysql-client.cnf"

if [ ! -f "${charset_config}" ]; then
  echo "expected ${charset_config} to configure mysql client charset for initdb SQL imports" >&2
  exit 1
fi

if ! rg -n '^\[mysql\]$' "${charset_config}" >/dev/null; then
  echo "expected ${charset_config} to contain a [mysql] client section" >&2
  exit 1
fi

if ! rg -n '^default-character-set=utf8mb4$' "${charset_config}" >/dev/null; then
  echo "expected ${charset_config} to force mysql client UTF-8 when reading initdb SQL files" >&2
  exit 1
fi

single_config="$(mktemp)"
cluster_config="$(mktemp)"
trap 'rm -f "${single_config}" "${cluster_config}"' EXIT

./deploy/deployment.sh config --topology single --scope infra --env-file deploy/.env.single.example >"${single_config}"
./deploy/deployment.sh config --topology cluster --scope infra --env-file deploy/.env.cluster.example >"${cluster_config}"

grep -F 'deploy/mysql/conf/mysql-client.cnf' "${single_config}" >/dev/null
grep -F 'target: /etc/mysql/conf.d/mysql-client.cnf' "${single_config}" >/dev/null

grep -F 'deploy/mysql/conf/mysql-client.cnf' "${cluster_config}" >/dev/null
grep -F 'target: /etc/mysql/conf.d/mysql-client.cnf' "${cluster_config}" >/dev/null

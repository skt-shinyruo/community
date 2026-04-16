#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

help_output="$(./deploy/deployment.sh --help 2>&1)"
printf '%s\n' "${help_output}" | grep -F -- '--topology <single|cluster>'
if printf '%s\n' "${help_output}" | grep -F -- '--topology <dev|ha>' >/dev/null 2>&1; then
  echo "old topology help text is still visible" >&2
  exit 1
fi

single_infra="$(mktemp)"
single_full="$(mktemp)"
cluster_infra="$(mktemp)"
cluster_full="$(mktemp)"
dev_err="$(mktemp)"
ha_err="$(mktemp)"
trap 'rm -f "${single_infra}" "${single_full}" "${cluster_infra}" "${cluster_full}" "${dev_err}" "${ha_err}"' EXIT

./deploy/deployment.sh config --topology single --scope infra --env-file deploy/.env.single.example >"${single_infra}"
./deploy/deployment.sh config --topology single --scope full --env-file deploy/.env.single.example >"${single_full}"
./deploy/deployment.sh config --topology cluster --scope infra --env-file deploy/.env.cluster.example >"${cluster_infra}"
./deploy/deployment.sh config --topology cluster --scope full --env-file deploy/.env.cluster.example >"${cluster_full}"

grep -F 'name: community-single' "${single_infra}"
grep -E '^  mysql:$' "${single_infra}"
grep -E '^  community-gateway:$' "${single_full}"

grep -F 'name: community-cluster' "${cluster_infra}"
grep -E '^  mysql-primary:$' "${cluster_infra}"
grep -E '^  community-gateway-1:$' "${cluster_full}"

if ./deploy/deployment.sh config --topology dev --scope infra --env-file deploy/.env.single.example >/dev/null 2>"${dev_err}"; then
  echo "expected old topology dev to fail" >&2
  exit 1
fi
grep -F 'unsupported topology: dev' "${dev_err}"

if ./deploy/deployment.sh config --topology ha --scope infra --env-file deploy/.env.cluster.example >/dev/null 2>"${ha_err}"; then
  echo "expected old topology ha to fail" >&2
  exit 1
fi
grep -F 'unsupported topology: ha' "${ha_err}"

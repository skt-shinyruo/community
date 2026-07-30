#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

tmp_dir="$(mktemp -d)"
fake_bin="${tmp_dir}/bin"
mkdir -p "${fake_bin}"
trap 'rm -rf "${tmp_dir}"' EXIT

cat >"${fake_bin}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${FAKE_DOCKER_LOG}"
exit 0
EOF
chmod +x "${fake_bin}/docker"

run_reset() {
  local topology="$1"
  local log_file="$2"
  PATH="${fake_bin}:${PATH}" FAKE_DOCKER_LOG="${log_file}" \
    ./deploy/deployment.sh reset-mysql --topology "${topology}" --scope full \
      --no-observability --env-file "deploy/.env.${topology}.example"
}

single_log="${tmp_dir}/single.log"
run_reset single "${single_log}"
grep -Fq 'compose --env-file' "${single_log}"
grep -Fq -- '-p community-single' "${single_log}"
grep -Fq ' down' "${single_log}"
grep -Fxq 'volume inspect community_single_mysql_primary_data' "${single_log}"
grep -Fxq 'volume rm community_single_mysql_primary_data' "${single_log}"
test "$(grep -Fc 'volume rm ' "${single_log}")" -eq 1

cluster_log="${tmp_dir}/cluster.log"
run_reset cluster "${cluster_log}"
grep -Fq -- '-p community-cluster' "${cluster_log}"
for volume in \
  community_cluster_mysql_primary_data \
  community_cluster_mysql_replica_1_data \
  community_cluster_mysql_replica_2_data; do
  grep -Fxq "volume inspect ${volume}" "${cluster_log}"
  grep -Fxq "volume rm ${volume}" "${cluster_log}"
done
test "$(grep -Fc 'volume rm ' "${cluster_log}")" -eq 3

if rg -n 'volume rm .*_(redis|kafka|garage|elasticsearch)_' \
    "${single_log}" "${cluster_log}"; then
  echo 'reset-mysql removed a non-MySQL volume' >&2
  exit 1
fi

scope_log="${tmp_dir}/scope.log"
scope_error="${tmp_dir}/scope.error"
if PATH="${fake_bin}:${PATH}" FAKE_DOCKER_LOG="${scope_log}" \
  ./deploy/deployment.sh reset-mysql --topology single --scope infra \
    --no-observability --env-file deploy/.env.single.example \
    >/dev/null 2>"${scope_error}"; then
  echo 'reset-mysql unexpectedly accepted --scope infra' >&2
  exit 1
fi
grep -Fq 'requires --scope full' "${scope_error}"
test ! -e "${scope_log}"

#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
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
  local stack="$1"
  local log_file="$2"
  PATH="${fake_bin}:${PATH}" FAKE_DOCKER_LOG="${log_file}" \
    ./deploy/deployment.sh reset-mysql --stack "${stack}" \
      --no-observability --env-file "deploy/stacks/${stack}/.env.example"
}

single_log="${tmp_dir}/single.log"
run_reset single "${single_log}"
grep -Fq 'compose --project-directory' "${single_log}"
grep -Fq -- '--env-file' "${single_log}"
grep -Fq -- "-f ${REPO_ROOT}/deploy/stacks/single/compose.yml" "${single_log}"
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

infra_log="${tmp_dir}/infra.log"
PATH="${fake_bin}:${PATH}" FAKE_DOCKER_LOG="${infra_log}" \
  ./deploy/deployment.sh reset-mysql --stack infra \
    --env-file deploy/stacks/infra/.env.example
grep -Fq -- '--project-directory' "${infra_log}"
grep -Fq -- '-p community-infra' "${infra_log}"
grep -Fxq 'volume inspect community_infra_mysql_primary_data' "${infra_log}"
grep -Fxq 'volume rm community_infra_mysql_primary_data' "${infra_log}"
test "$(grep -Fc 'volume rm ' "${infra_log}")" -eq 1

if rg -n 'volume rm .*_(redis|kafka|garage|elasticsearch)_' \
    "${infra_log}" "${single_log}" "${cluster_log}"; then
  echo 'reset-mysql removed a non-MySQL volume' >&2
  exit 1
fi

#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${repo_root}"

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

for stack in infra single cluster; do
  test -f "deploy/stacks/${stack}/compose.yml"
  test -f "deploy/stacks/${stack}/.env.example"
  ./deploy/deployment.sh config --stack "${stack}" \
    --env-file "deploy/stacks/${stack}/.env.example" >"${work_dir}/${stack}.yml"
done

grep -F 'name: community-infra' "${work_dir}/infra.yml" >/dev/null
grep -F 'name: community-single' "${work_dir}/single.yml" >/dev/null
grep -F 'name: community-cluster' "${work_dir}/cluster.yml" >/dev/null

grep -F 'name: community-infra_default' "${work_dir}/infra.yml" >/dev/null
grep -F 'name: community-single_default' "${work_dir}/single.yml" >/dev/null
grep -F 'name: community-cluster_default' "${work_dir}/cluster.yml" >/dev/null

grep -F 'name: community_infra_mysql_primary_data' "${work_dir}/infra.yml" >/dev/null
grep -F 'name: community_single_mysql_primary_data' "${work_dir}/single.yml" >/dev/null
grep -F 'name: community_cluster_mysql_primary_data' "${work_dir}/cluster.yml" >/dev/null

grep -F "source: ${repo_root}/deploy/database/business/current-state/010_current_schema.sql" \
  "${work_dir}/infra.yml" >/dev/null
grep -F "source: ${repo_root}/deploy/config/nacos" "${work_dir}/infra.yml" >/dev/null
grep -F "context: ${repo_root}/backend" "${work_dir}/single.yml" >/dev/null
grep -F "dockerfile: ../deploy/images/backend/Dockerfile" "${work_dir}/single.yml" >/dev/null
grep -F "context: ${repo_root}" "${work_dir}/single.yml" >/dev/null
grep -F "dockerfile: deploy/images/frontend/Dockerfile" "${work_dir}/single.yml" >/dev/null

if rg -n "source: ${repo_root}/(database|config|scripts|observability)" "${work_dir}"/*.yml \
    || rg -n "source: ${repo_root}/deploy/compose/(database|config|scripts|observability)" \
      "${work_dir}"/*.yml; then
  echo 'compose include resolved a deployment asset outside its owning deploy directory' >&2
  exit 1
fi

for runtime in community-app community-gateway community-im-gateway im-core im-realtime frontend-nginx; do
  if grep -Eq "^  ${runtime}:$" "${work_dir}/infra.yml"; then
    echo "infra stack must not contain runtime service ${runtime}" >&2
    exit 1
  fi
done

grep -Eq '^  community-app:$' "${work_dir}/single.yml"
grep -Eq '^  community-app-1:$' "${work_dir}/cluster.yml"

host_ports() {
  awk '$1 == "published:" { gsub(/"/, "", $2); print $2 }' "$1" | sort -u
}

host_ports "${work_dir}/infra.yml" >"${work_dir}/infra.ports"
host_ports "${work_dir}/single.yml" >"${work_dir}/single.ports"
host_ports "${work_dir}/cluster.yml" >"${work_dir}/cluster.ports"

if comm -12 "${work_dir}/infra.ports" "${work_dir}/single.ports" | grep -q .; then
  echo 'infra and single stacks must not share default host ports' >&2
  exit 1
fi
if comm -12 "${work_dir}/infra.ports" "${work_dir}/cluster.ports" | grep -q .; then
  echo 'infra and cluster stacks must not share default host ports' >&2
  exit 1
fi
if comm -12 "${work_dir}/single.ports" "${work_dir}/cluster.ports" | grep -q .; then
  echo 'single and cluster stacks must not share default host ports' >&2
  exit 1
fi

if rg -n 'container_name:' deploy/compose --glob '*.yml' >/dev/null; then
  echo 'shared compose fragments must not use global container_name values' >&2
  exit 1
fi

missing_stack_error="${work_dir}/missing-stack.error"
if ./deploy/deployment.sh config \
  --env-file deploy/stacks/infra/.env.example >/dev/null 2>"${missing_stack_error}"; then
  echo 'deployment unexpectedly accepted a command without --stack' >&2
  exit 1
fi
grep -F -- '--stack is required' "${missing_stack_error}" >/dev/null

custom_topology_error="${work_dir}/custom-topology.error"
if ./deploy/deployment.sh config --stack infra -p community-infra-copy \
  --env-file deploy/stacks/infra/.env.example >/dev/null 2>"${custom_topology_error}"; then
  echo 'custom infra project unexpectedly reused the default topology' >&2
  exit 1
fi
grep -F 'requires an independent topology' "${custom_topology_error}" >/dev/null

custom_env="${work_dir}/custom-infra.env"
awk '
  /^COMMUNITY_VOLUME_NAMESPACE=/ { print "COMMUNITY_VOLUME_NAMESPACE=community_infra_copy"; next }
  /^COMMUNITY_NETWORK_SUBNET=/ { print "COMMUNITY_NETWORK_SUBNET=172.42.0.0/24"; next }
  /^COMMUNITY_NETWORK_DYNAMIC_RANGE=/ { print "COMMUNITY_NETWORK_DYNAMIC_RANGE=172.42.0.128/25"; next }
  /^NGINX_STATIC_IP=/ { print "NGINX_STATIC_IP=172.42.0.10"; next }
  /^COMMUNITY_GATEWAY_STATIC_IP=/ { print "COMMUNITY_GATEWAY_STATIC_IP=172.42.0.20"; next }
  /^GATEWAY_TRUSTED_PROXY_CIDRS=/ { print "GATEWAY_TRUSTED_PROXY_CIDRS=172.42.0.10/32"; next }
  /^COMMUNITY_APP_TRUSTED_PROXY_CIDRS=/ { print "COMMUNITY_APP_TRUSTED_PROXY_CIDRS=172.42.0.20/32"; next }
  { print }
' deploy/stacks/infra/.env.example >"${custom_env}"

custom_ports_error="${work_dir}/custom-ports.error"
if ./deploy/deployment.sh config --stack infra -p community-infra-copy \
  --env-file "${custom_env}" >/dev/null 2>"${custom_ports_error}"; then
  echo 'custom infra project unexpectedly reused the default localhost ports' >&2
  exit 1
fi
grep -F 'requires independent localhost ports' "${custom_ports_error}" >/dev/null

custom_single_env="${work_dir}/custom-single.env"
awk '
  /^COMMUNITY_VOLUME_NAMESPACE=/ { print "COMMUNITY_VOLUME_NAMESPACE=community_single_copy"; next }
  /^COMMUNITY_NETWORK_SUBNET=/ { print "COMMUNITY_NETWORK_SUBNET=172.43.0.0/24"; next }
  /^COMMUNITY_NETWORK_DYNAMIC_RANGE=/ { print "COMMUNITY_NETWORK_DYNAMIC_RANGE=172.43.0.128/25"; next }
  /^NGINX_STATIC_IP=/ { print "NGINX_STATIC_IP=172.43.0.10"; next }
  /^COMMUNITY_GATEWAY_STATIC_IP=/ { print "COMMUNITY_GATEWAY_STATIC_IP=172.43.0.20"; next }
  /^GATEWAY_TRUSTED_PROXY_CIDRS=/ { print "GATEWAY_TRUSTED_PROXY_CIDRS=172.43.0.10/32"; next }
  /^COMMUNITY_APP_TRUSTED_PROXY_CIDRS=/ { print "COMMUNITY_APP_TRUSTED_PROXY_CIDRS=172.43.0.20/32"; next }
  { print }
' deploy/stacks/single/.env.example >"${custom_single_env}"

custom_single_ports_error="${work_dir}/custom-single-ports.error"
if ./deploy/deployment.sh config --stack single -p community-single-copy \
  --env-file "${custom_single_env}" >/dev/null 2>"${custom_single_ports_error}"; then
  echo 'custom single project unexpectedly reused the default localhost ports' >&2
  exit 1
fi
grep -F 'requires independent localhost ports' "${custom_single_ports_error}" >/dev/null

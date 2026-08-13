#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${REPO_ROOT}"

single_rendered="$(mktemp)"
cluster_rendered="$(mktemp)"
trap 'rm -f "${single_rendered}" "${cluster_rendered}"' EXIT

env_value() {
  local env_file="$1"
  local variable="$2"
  awk -v variable="${variable}" '
    index($0, variable "=") == 1 {
      print substr($0, length(variable) + 2)
      exit
    }
  ' "${env_file}"
}

service_image() {
  local rendered_config="$1"
  local service="$2"
  awk -v service="${service}" '
    $0 == "  " service ":" {
      in_service = 1
      next
    }
    in_service && /^  [^ ]/ {
      exit
    }
    in_service && $1 == "image:" {
      print $2
      exit
    }
  ' "${rendered_config}"
}

client_version="$(cd backend && mvn -q help:evaluate \
  -Dexpression=elasticsearch-client.version -DforceStdout)"
single_version="$(env_value deploy/stacks/single/.env.example ELASTIC_STACK_VERSION)"
cluster_version="$(env_value deploy/stacks/cluster/.env.example ELASTIC_STACK_VERSION)"

if [[ -z "${client_version}" || -z "${single_version}" || -z "${cluster_version}" ]]; then
  echo "Elasticsearch client, single stack, and cluster stack versions must all be defined" >&2
  exit 1
fi
if [[ "${single_version}" != "${client_version}" || "${cluster_version}" != "${client_version}" ]]; then
  echo "Elasticsearch version mismatch: client=${client_version}, single=${single_version}, cluster=${cluster_version}" >&2
  exit 1
fi

./deploy/deployment.sh config --stack single \
  --observability \
  --env-file deploy/stacks/single/.env.example >"${single_rendered}"
./deploy/deployment.sh config --stack cluster \
  --env-file deploy/stacks/cluster/.env.example >"${cluster_rendered}"

for service in elasticsearch; do
  expected="docker.elastic.co/elasticsearch/elasticsearch:${client_version}"
  actual="$(service_image "${single_rendered}" "${service}")"
  test "${actual}" = "${expected}" || {
    echo "${service} must use ${expected}, got ${actual:-<missing>}" >&2
    exit 1
  }
done
for service in elasticsearch-1 elasticsearch-2 elasticsearch-3; do
  expected="docker.elastic.co/elasticsearch/elasticsearch:${client_version}"
  actual="$(service_image "${cluster_rendered}" "${service}")"
  test "${actual}" = "${expected}" || {
    echo "${service} must use ${expected}, got ${actual:-<missing>}" >&2
    exit 1
  }
done
for rendered_config in "${single_rendered}" "${cluster_rendered}"; do
  expected="docker.elastic.co/kibana/kibana:${client_version}"
  actual="$(service_image "${rendered_config}" kibana)"
  test "${actual}" = "${expected}" || {
    echo "kibana must use ${expected}, got ${actual:-<missing>}" >&2
    exit 1
  }
done

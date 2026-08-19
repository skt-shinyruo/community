#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${repo_root}"

fail() {
  echo "observability contract check failed: $*" >&2
  exit 1
}

contract_dir="deploy/observability/contracts"
handbook="docs/handbook/observability.md"
scanner_source="deploy/tests/contracts/config/MetricTagScanner.java"
metric_scan="$(mktemp)"
trap 'rm -f "${metric_scan}"' EXIT

required_files=(
  "${handbook}"
  "backend/community-app/src/main/resources/application.yml"
  "backend/community-gateway/src/main/resources/application.yml"
  "backend/community-im-gateway/src/main/resources/application.yml"
  "backend/community-im/im-core/src/main/resources/application.yml"
  "backend/community-im/im-realtime/src/main/resources/application.yml"
  "backend/community-oss/src/main/resources/application.yml"
  "${contract_dir}/README.md"
  "${contract_dir}/required-resource-fields.txt"
  "${contract_dir}/forbidden-observability-fields.txt"
  "${scanner_source}"
)

for file in "${required_files[@]}"; do
  if [ ! -s "${file}" ]; then
    fail "required file missing or empty: ${file}"
  fi
done

backend_configs=(
  backend/community-app/src/main/resources/application.yml
  backend/community-gateway/src/main/resources/application.yml
  backend/community-im-gateway/src/main/resources/application.yml
  backend/community-im/im-core/src/main/resources/application.yml
  backend/community-im/im-realtime/src/main/resources/application.yml
  backend/community-oss/src/main/resources/application.yml
)

for config in "${backend_configs[@]}"; do
  if ! rg -n -F 'console: logstash' "${config}" >/dev/null; then
    fail "structured logstash console formatter missing from ${config}"
  fi
  for field in service.name service.version service.namespace deployment.environment; do
    if ! rg -n -F '"['"${field}"']":' "${config}" >/dev/null; then
      fail "structured log field ${field} missing from ${config}"
    fi
  done
done

if rg -n 'logstash-logback-encoder|community-common-observability' \
  backend/*/pom.xml backend/community-im/*/pom.xml >/dev/null; then
  fail "retired custom logging dependencies remain in backend deployables"
fi

unfinished_pattern='TB''D|TO''DO|FIX''ME|place''holder|to be ''decided'
if rg -n "${unfinished_pattern}" "${handbook}" "${contract_dir}" >/dev/null; then
  rg -n "${unfinished_pattern}" "${handbook}" "${contract_dir}" >&2
  fail "observability docs or contracts contain unfinished marker text"
fi

for heading in \
  '## SLO/SLI Catalog' \
  '## Shared Resource Fields' \
  '## Metrics Contract' \
  '## Trace Contract' \
  '## Instrumentation Boundaries' \
  '## Alert Priority' \
  '## Governance'
do
  if ! rg -n "^${heading}$" "${handbook}" >/dev/null; then
    fail "missing handbook heading: ${heading}"
  fi
done

metric_sources=()
while IFS= read -r file; do
  metric_sources+=("${file}")
done < <(rg -l 'io\.micrometer\.core\.instrument|Counter\.builder|Timer\.builder|Gauge\.builder|DistributionSummary\.builder|MeterRegistry' backend || true)

if [ "${#metric_sources[@]}" -gt 0 ]; then
  scanner_dir="$(mktemp -d)"
  trap 'rm -f "${metric_scan}"; rm -rf "${scanner_dir}"' EXIT
  javac --release 17 -d "${scanner_dir}" "${scanner_source}"
  java -cp "${scanner_dir}" MetricTagScanner "${metric_sources[@]}" >"${metric_scan}"
fi

while IFS= read -r forbidden; do
  case "${forbidden}" in
    '' | '#'*)
      continue
      ;;
  esac
  if awk -F '\t' -v forbidden="${forbidden}" '
    $3 == forbidden {
      printf "%s:%s: forbidden metric tag key %s in %s\n", $1, $2, $3, $4 > "/dev/stderr"
      found = 1
    }
    END {
      exit found ? 0 : 1
    }
  ' "${metric_scan}"; then
    fail "forbidden observability field appears as a metric tag key: ${forbidden}"
  fi
done <"${contract_dir}/forbidden-observability-fields.txt"

for required_field in service.name service.version service.namespace deployment.environment; do
  if ! rg -n "^${required_field}$" "${contract_dir}/required-resource-fields.txt" >/dev/null; then
    fail "required resource field missing from contract: ${required_field}"
  fi
done

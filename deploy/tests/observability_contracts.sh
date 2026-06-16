#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${repo_root}"

fail() {
  echo "observability contract check failed: $*" >&2
  exit 1
}

contract_dir="deploy/observability/contracts"
handbook="docs/handbook/observability.md"
metric_scan="$(mktemp)"
trap 'rm -f "${metric_scan}"' EXIT

required_files=(
  "${handbook}"
  "${contract_dir}/README.md"
  "${contract_dir}/required-resource-fields.txt"
  "${contract_dir}/runtime-event-fields.txt"
  "${contract_dir}/stable-event-categories.txt"
  "${contract_dir}/metric-families.txt"
  "${contract_dir}/allowed-metric-dimensions.txt"
  "${contract_dir}/forbidden-observability-fields.txt"
  "${contract_dir}/manual-span-names.txt"
)

for file in "${required_files[@]}"; do
  if [ ! -s "${file}" ]; then
    fail "required file missing or empty: ${file}"
  fi
done

unfinished_pattern='TB''D|TO''DO|FIX''ME|place''holder|to be ''decided'
if rg -n "${unfinished_pattern}" "${handbook}" "${contract_dir}" >/dev/null; then
  rg -n "${unfinished_pattern}" "${handbook}" "${contract_dir}" >&2
  fail "observability docs or contracts contain unfinished marker text"
fi

for heading in \
  '## SLO/SLI Catalog' \
  '## Shared Resource Fields' \
  '## Runtime Event Contract' \
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

while IFS= read -r category; do
  case "${category}" in
    '' | '#'*)
      continue
      ;;
  esac
  if ! rg -n "^${category}$" "${contract_dir}/stable-event-categories.txt" >/dev/null; then
    fail "stable category lookup failed for ${category}"
  fi
  if ! rg -n "${category}" "${handbook}" >/dev/null; then
    fail "handbook does not mention stable event category: ${category}"
  fi
done <"${contract_dir}/stable-event-categories.txt"

if rg -n 'Counter\.builder|Timer\.builder|Gauge\.builder|DistributionSummary\.builder|Tags\.of|\.tag\(' backend >"${metric_scan}"; then
  while IFS= read -r forbidden; do
    case "${forbidden}" in
      '' | '#'*)
        continue
        ;;
    esac
    if grep -F "\"${forbidden}\"" "${metric_scan}" >/dev/null || grep -F "'${forbidden}'" "${metric_scan}" >/dev/null; then
      grep -n -F "\"${forbidden}\"" "${metric_scan}" >&2 || true
      grep -n -F "'${forbidden}'" "${metric_scan}" >&2 || true
      fail "forbidden metric dimension appears in metric builder or tag call: ${forbidden}"
    fi
  done <"${contract_dir}/forbidden-observability-fields.txt"
fi

for required_field in service.name service.version service.namespace deployment.environment; do
  if ! rg -n "^${required_field}$" "${contract_dir}/required-resource-fields.txt" >/dev/null; then
    fail "required resource field missing from contract: ${required_field}"
  fi
  if ! rg -n "${required_field}" backend/community-common/common-observability/src/main/resources/logback/community-observability.xml >/dev/null; then
    fail "shared logback config does not mention required resource field: ${required_field}"
  fi
done

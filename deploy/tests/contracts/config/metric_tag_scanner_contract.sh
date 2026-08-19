#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${repo_root}"

fail() {
  echo "metric tag scanner contract check failed: $*" >&2
  exit 1
}

scanner_source="deploy/tests/contracts/config/MetricTagScanner.java"
fixture="deploy/tests/contracts/config/fixtures/MetricTagScannerFixture.java"
scanner_dir="$(mktemp -d)"
scan_output="${scanner_dir}/scan.tsv"
actual_keys="${scanner_dir}/actual-keys.txt"
expected_keys="${scanner_dir}/expected-keys.txt"
trap 'rm -rf "${scanner_dir}"' EXIT

javac --release 17 -d "${scanner_dir}" "${scanner_source}"
java -cp "${scanner_dir}" MetricTagScanner "${fixture}" >"${scan_output}"

if ! awk -F '\t' 'NF != 4 || $2 !~ /^[0-9]+$/ { exit 1 }' "${scan_output}"; then
  fail "scanner output is not file, line, tag key, invocation TSV"
fi

cut -f 3 "${scan_output}" | sort >"${actual_keys}"
printf '%s\n' \
  cache \
  client.ip \
  event.type \
  job.name \
  objectKey \
  orderId \
  pool.name \
  redisKey \
  result \
  result \
  scope \
  trace.id \
  url.full \
  userId | sort >"${expected_keys}"

if ! diff -u "${expected_keys}" "${actual_keys}"; then
  fail "supported Micrometer forms did not expose exactly their tag-key arguments"
fi

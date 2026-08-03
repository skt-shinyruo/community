#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
SOURCE_ROOT="${REPO_ROOT}/backend/community-app/src/main/java"
SEED_SCRIPT="${REPO_ROOT}/deploy/mysql/xxl-job/020_seed_local.sh"
SCHEMA="${REPO_ROOT}/deploy/mysql/xxl-job/010_schema.sql"

grep -Fq '`020_seed_local.sh`' "${SCHEMA}"

source_handlers="$({
  while IFS= read -r source; do
    constant_handler="$(sed -nE 's/^[[:space:]]*(public |protected |private )?static final String JOB_NAME[[:space:]]*=[[:space:]]*"([^"]+)".*/\2/p' "${source}")"
    literal_handler="$(sed -nE 's/.*@XxlJob\("([^"]+)"\).*/\1/p' "${source}")"

    if [[ -n "${constant_handler}" && -z "${literal_handler}" ]]; then
      test "$(grep -Ec '@XxlJob\(JOB_NAME\)' "${source}")" -eq 1
      printf '%s\n' "${constant_handler}"
    elif [[ -n "${literal_handler}" && -z "${constant_handler}" ]]; then
      printf '%s\n' "${literal_handler}"
    else
      echo "XXL handler must declare exactly one literal @XxlJob or one JOB_NAME constant: ${source}" >&2
      exit 1
    fi
  done < <(rg -l '@XxlJob\(' "${SOURCE_ROOT}" --glob '*.java' | sort)
} | sort)"

seed_handlers="$(sed -nE \
  '/XXL_JOB_HANDLER_SEED_BEGIN/,/XXL_JOB_HANDLER_SEED_END/ s/^[[:space:]]*\('\''([^'\'']+)'\'',.*/\1/p' \
  "${SEED_SCRIPT}" | sort)"

test -n "${source_handlers}"
test "$(printf '%s\n' "${source_handlers}" | uniq -d | wc -l)" -eq 0
test "$(printf '%s\n' "${seed_handlers}" | uniq -d | wc -l)" -eq 0

if ! diff -u \
    <(printf '%s\n' "${source_handlers}") \
    <(printf '%s\n' "${seed_handlers}"); then
  echo "@XxlJob handlers and deploy seed entries have drifted" >&2
  exit 1
fi

expected_rows=(
  "('searchReindex', 'Search Reindex', 'NONE', '', 0, 'FIRST', 'SERIAL_EXECUTION')"
  "('marketWalletActionProcessor', 'Market Wallet Action Processor', 'CRON', '0/5 * * * * ?', 1, 'FIRST', 'SERIAL_EXECUTION')"
  "('marketWalletActionRecovery', 'Market Wallet Action Recovery', 'CRON', '15 0/1 * * * ?', 1, 'FIRST', 'SERIAL_EXECUTION')"
  "('marketOrderAutoConfirm', 'Market Order Auto Confirm', 'CRON', '30 0/1 * * * ?', 1, 'FIRST', 'SERIAL_EXECUTION')"
)

test "$(printf '%s\n' "${seed_handlers}" | wc -l)" -eq "${#expected_rows[@]}"
for row in "${expected_rows[@]}"; do
  test "$(grep -Fc "${row}" "${SEED_SCRIPT}")" -eq 1
done

required_columns=(
  executor_handler
  schedule_type
  schedule_conf
  misfire_strategy
  executor_route_strategy
  executor_block_strategy
  trigger_status
  trigger_last_time
  trigger_next_time
)

for column in "${required_columns[@]}"; do
  grep -Fq "\`${column}\`" "${SCHEMA}"
done

bash -n "${SEED_SCRIPT}"

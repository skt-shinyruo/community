#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
forbidden='runtime-diagnostics-agent|runtime\.diagnostics|RUNTIME_DIAGNOSTICS_|runtime_diagnostics'

if rg -n "${forbidden}" \
  --glob '!**/src/test/**' \
  --glob '!**/target/**' \
  --glob '!**/yierloom_naming_contract.sh' \
  "${repo_root}/backend/pom.xml" \
  "${repo_root}/backend/yierloom" \
  "${repo_root}/backend/scripts" \
  "${repo_root}/deploy" \
  "${repo_root}/docs/handbook"; then
  echo "legacy runtime diagnostics naming remains in production surfaces" >&2
  exit 1
fi

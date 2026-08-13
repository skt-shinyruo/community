#!/usr/bin/env bash
set -euo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${repo_root}"

groups=(compose database config images)
if [ "$#" -gt 0 ]; then
  groups=("$@")
fi

for group in "${groups[@]}"; do
  case "${group}" in
    compose|database|config|images) ;;
    *)
      echo "unknown deployment contract group: ${group}" >&2
      echo "expected one or more of: compose database config images" >&2
      exit 2
      ;;
  esac

  for contract in "deploy/tests/contracts/${group}"/*.sh; do
    printf '[deploy-contracts] %s\n' "${contract}"
    "./${contract}"
  done
done

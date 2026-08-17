#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
schema="${REPO_ROOT}/deploy/database/business/001_schema.sql"
demo="${REPO_ROOT}/deploy/database/business/seed/090_seed_identity.sql"

retired_projection_seeds='@(user_policy|social_block|im_membership)_seed_version|unix_timestamp|current_timestamp\(3\)[[:space:]]*\*[[:space:]]*1000'
if rg -n "${retired_projection_seeds}" "${schema}"; then
  echo "timestamp-derived projection seed remains" >&2
  exit 1
fi

grep -F 'INSERT INTO `user_policy_version_counter` VALUES (1,0)' "${schema}"
grep -F 'INSERT INTO `social_block_version_counter` VALUES (1,0)' "${schema}"
grep -F 'INSERT INTO `im_membership_version_counter` VALUES (1,0)' "${schema}"
grep -E 'INSERT INTO `user_security_version_counter` VALUES \(1,[1-9][0-9]+\)' "${schema}"
grep -F 'policy_version' "${demo}"
grep -F 'values (1, 3)' "${demo}"

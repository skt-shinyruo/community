#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
identity="${REPO_ROOT}/deploy/mysql/community/020_schema_identity.sql"
social="${REPO_ROOT}/deploy/mysql/community/050_schema_social.sql"
membership="${REPO_ROOT}/deploy/mysql/community/070_schema_im_core.sql"
demo="${REPO_ROOT}/deploy/mysql/community/090_seed_identity.sql"

retired_projection_seeds='@(user_policy|social_block|im_membership)_seed_version'
if rg -n "${retired_projection_seeds}" "${identity}" "${social}" "${membership}"; then
  echo "timestamp-derived projection seed remains" >&2
  exit 1
fi
if rg -n '\*\s*4096' "${social}" "${membership}"; then
  echo "timestamp-domain social or membership version remains" >&2
  exit 1
fi

# Identity security_version intentionally retains its independent timestamp domain.
grep -F '@user_security_seed_version' "${identity}"
grep -F 'policy_version' "${demo}"
grep -F 'values (1, 3)' "${demo}"

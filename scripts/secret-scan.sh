#!/usr/bin/env bash
set -euo pipefail

fail=0

echo "[secret-scan] checking forbidden files in git..."
if git ls-files --error-unmatch "deploy/.env" >/dev/null 2>&1; then
  echo "[secret-scan] ERROR: deploy/.env is tracked by git (must not be committed)" >&2
  fail=1
elif [[ -f "deploy/.env" ]]; then
  echo "[secret-scan] WARN: deploy/.env exists locally (ignored: untracked + gitignored)" >&2
fi

echo "[secret-scan] scanning tracked files for hardcoded secrets (best-effort)..."

# Heuristics: forbid obvious secrets in repo (allow ${...} placeholders)
# NOTE: Anchor patterns to the beginning of the line to avoid false positives
# from placeholders like ${VAR:-} / ${VAR:} inside compose/YAML files.
PATTERNS=(
  '^\\s*(?:-\\s*)?(?:export\\s+)?JWT_HMAC_SECRET\\s*[:=]\\s*[^\\s\\$\\{]'
  '^\\s*(?:-\\s*)?(?:export\\s+)?AUTH_JWT_HMAC_SECRET\\s*[:=]\\s*[^\\s\\$\\{]'
  '^\\s*(?:-\\s*)?(?:export\\s+)?QINIU_(ACCESS_KEY|SECRET_KEY)\\s*[:=]\\s*[^\\s\\$\\{]'
  '^\\s*(?:-\\s*)?(?:export\\s+)?MYSQL_(ROOT_PASSWORD|PASSWORD)\\s*[:=]\\s*[^\\s\\$\\{]'
)

for p in "${PATTERNS[@]}"; do
  if git grep -n -P "${p}" -- \
    . \
    ':(exclude)deploy/.env.example' \
    ':(exclude)deploy/nacos-config/*' \
    >/dev/null; then
    echo "[secret-scan] ERROR: matched forbidden pattern: ${p}" >&2
    git grep -n -P "${p}" -- \
      . \
      ':(exclude)deploy/.env.example' \
      ':(exclude)deploy/nacos-config/*' \
      || true
    fail=1
  fi
done

if [[ "${fail}" -ne 0 ]]; then
  echo "[secret-scan] FAIL" >&2
  exit 1
fi

echo "[secret-scan] OK"

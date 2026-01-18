#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:12882}"
USERNAME="${USERNAME:-aaa}"
PASSWORD="${PASSWORD:-aaa}"
COOKIE_JAR="${COOKIE_JAR:-/tmp/community-cookie.jar}"

echo "[I0] gateway=${GATEWAY_URL}"
echo "[I0] user=${USERNAME}"
echo "[I0] cookieJar=${COOKIE_JAR}"

login_payload=$(python3 - <<PY
import json, os
print(json.dumps({"username": os.environ["USERNAME"], "password": os.environ["PASSWORD"]}))
PY
)

echo "[I0] 1) login"
login_resp="$(curl -fsS -c "${COOKIE_JAR}" \
  -H "Content-Type: application/json" \
  -X POST "${GATEWAY_URL}/api/auth/login" \
  -d "${login_payload}")"

access_token="$(python3 - <<PY
import json, sys
data=json.loads(sys.stdin.read())
print((data.get("data") or {}).get("accessToken") or "")
PY
<<<"${login_resp}")"

if [[ -z "${access_token}" ]]; then
  echo "[I0] login failed response:"
  echo "${login_resp}"
  exit 1
fi

echo "[I0] 2) me (protected)"
curl -fsS -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${access_token}" \
  "${GATEWAY_URL}/api/auth/me" >/dev/null

echo "[I0] 3) refresh"
refresh_resp="$(curl -fsS -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${GATEWAY_URL}/api/auth/refresh")"

new_access_token="$(python3 - <<PY
import json, sys
data=json.loads(sys.stdin.read())
print((data.get("data") or {}).get("accessToken") or "")
PY
<<<"${refresh_resp}")"

if [[ -z "${new_access_token}" ]]; then
  echo "[I0] refresh failed response:"
  echo "${refresh_resp}"
  exit 1
fi

echo "[I0] 4) logout"
curl -fsS -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${new_access_token}" \
  -X POST "${GATEWAY_URL}/api/auth/logout" >/dev/null

echo "[I0] OK"

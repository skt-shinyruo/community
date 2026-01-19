#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:12882}"
USERNAME="${SMOKE_USERNAME:-aaa}"
PASSWORD="${SMOKE_PASSWORD:-aaa}"
COOKIE_JAR="${COOKIE_JAR:-/tmp/community-cookie.jar}"

export USERNAME PASSWORD

echo "[I0] gateway=${GATEWAY_URL}"
echo "[I0] user=${USERNAME}"
echo "[I0] cookieJar=${COOKIE_JAR}"

login_payload="$(python3 -c 'import json,os; print(json.dumps({"username": os.environ.get("USERNAME",""), "password": os.environ.get("PASSWORD","")}))')"

echo "[I0] 1) login"
login_resp="$(curl -fsS -c "${COOKIE_JAR}" \
  -H "Content-Type: application/json" \
  -X POST "${GATEWAY_URL}/api/auth/login" \
  -d "${login_payload}")"

access_token="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print((data.get("data") or {}).get("accessToken") or "")' <<<"${login_resp}")"

if [[ -z "${access_token}" ]]; then
  echo "[I0] login failed: accessToken missing" >&2
  echo "[I0] response: ${login_resp}" >&2
  exit 1
fi

echo "[I0] 2) me (protected)"
curl -fsS -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${access_token}" \
  "${GATEWAY_URL}/api/auth/me" >/dev/null

echo "[I0] 3) refresh"
refresh_resp="$(curl -fsS -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${GATEWAY_URL}/api/auth/refresh")"

new_access_token="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print((data.get("data") or {}).get("accessToken") or "")' <<<"${refresh_resp}")"

if [[ -z "${new_access_token}" ]]; then
  echo "[I0] refresh failed: accessToken missing" >&2
  echo "[I0] response: ${refresh_resp}" >&2
  exit 1
fi

echo "[I0] 4) logout"
curl -fsS -b "${COOKIE_JAR}" \
  -H "Authorization: Bearer ${new_access_token}" \
  -X POST "${GATEWAY_URL}/api/auth/logout" >/dev/null

echo "[I0] OK"

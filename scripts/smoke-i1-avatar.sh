#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:12882}"
USERNAME="${SMOKE_USERNAME:-aaa}"
PASSWORD="${SMOKE_PASSWORD:-aaa}"
COOKIE_JAR="${COOKIE_JAR:-/tmp/community-cookie.jar}"

export USERNAME PASSWORD

echo "[I1] gateway=${GATEWAY_URL}"
echo "[I1] user=${USERNAME}"
echo "[I1] cookieJar=${COOKIE_JAR}"

login_payload="$(python3 -c 'import json,os; print(json.dumps({"username": os.environ.get("USERNAME",""), "password": os.environ.get("PASSWORD","")}))')"

echo "[I1] 1) login"
login_resp="$(curl -fsS -c "${COOKIE_JAR}" \
  -H "Content-Type: application/json" \
  -X POST "${GATEWAY_URL}/api/auth/login" \
  -d "${login_payload}")"

access_token="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print((data.get("data") or {}).get("accessToken") or "")' <<<"${login_resp}")"
if [[ -z "${access_token}" ]]; then
  echo "[I1] login failed: accessToken missing" >&2
  echo "[I1] response: ${login_resp}" >&2
  exit 1
fi

echo "[I1] 2) me (get userId)"
me_resp="$(curl -fsS -b "${COOKIE_JAR}" -H "Authorization: Bearer ${access_token}" "${GATEWAY_URL}/api/auth/me")"
user_id="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print(((data.get("data") or {}).get("userId") or 0))' <<<"${me_resp}")"
if [[ -z "${user_id}" || "${user_id}" == "0" ]]; then
  echo "[I1] me failed: userId missing" >&2
  echo "[I1] response: ${me_resp}" >&2
  exit 1
fi

echo "[I1] 3) get avatar upload token"
token_resp="$(curl -fsS -H "Authorization: Bearer ${access_token}" "${GATEWAY_URL}/api/users/${user_id}/avatar/upload-token")"
provider="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print(((data.get("data") or {}).get("provider") or "").strip())' <<<"${token_resp}")"
file_name="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print(((data.get("data") or {}).get("fileName") or "").strip())' <<<"${token_resp}")"
upload_url="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print(((data.get("data") or {}).get("uploadUrl") or "").strip())' <<<"${token_resp}")"

if [[ -z "${provider}" || -z "${file_name}" ]]; then
  echo "[I1] upload token missing provider/fileName" >&2
  echo "[I1] response: ${token_resp}" >&2
  exit 1
fi

echo "[I1] provider=${provider} fileName=${file_name}"
if [[ "${provider}" != "local" ]]; then
  echo "[I1] skip: provider != local (set USER_AVATAR_STORAGE=local for local provider)" >&2
  exit 0
fi
if [[ -z "${upload_url}" ]]; then
  echo "[I1] missing uploadUrl for local provider" >&2
  echo "[I1] response: ${token_resp}" >&2
  exit 1
fi

tmp_png="$(mktemp /tmp/community-avatar-XXXXXX.png)"
cleanup() { rm -f "${tmp_png}"; }
trap cleanup EXIT

# 1x1 PNG
base64 -d >"${tmp_png}" <<'EOF'
iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/6XnQpUAAAAASUVORK5CYII=
EOF

echo "[I1] 4) upload avatar file (multipart)"
curl -fsS \
  -H "Authorization: Bearer ${access_token}" \
  -F "file=@${tmp_png};type=image/png" \
  -F "fileName=${file_name}" \
  "${GATEWAY_URL}${upload_url}" >/dev/null

echo "[I1] 5) update avatar headerUrl"
update_payload="$(python3 -c 'import json,os; print(json.dumps({"fileName": os.environ.get("FILE_NAME","")}))' FILE_NAME="${file_name}")"
curl -fsS \
  -H "Authorization: Bearer ${access_token}" \
  -H "Content-Type: application/json" \
  -X PUT "${GATEWAY_URL}/api/users/${user_id}/avatar" \
  -d "${update_payload}" >/dev/null

echo "[I1] 6) GET /files/* (public read)"
curl -fsS -o /dev/null "${GATEWAY_URL}/files/${file_name}"

echo "[I1] OK"


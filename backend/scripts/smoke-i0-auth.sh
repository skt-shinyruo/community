#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:12882}"
USERNAME="${SMOKE_USERNAME:-aaa}"
PASSWORD="${SMOKE_PASSWORD:-aaa}"
COOKIE_JAR="${COOKIE_JAR:-/tmp/community-cookie.jar}"
SMOKE_ONBOARDING="${SMOKE_ONBOARDING:-false}"

export USERNAME PASSWORD

echo "[I0] gateway=${GATEWAY_URL}"
echo "[I0] user=${USERNAME}"
echo "[I0] cookieJar=${COOKIE_JAR}"

issue_captcha_id() {
  local resp
  resp="$(curl -fsS "${GATEWAY_URL}/api/auth/captcha")"
  python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print((data.get("data") or {}).get("captchaId") or "")' <<<"${resp}"
}

if [[ "${SMOKE_ONBOARDING}" == "true" ]]; then
  echo "[I0] onboarding=true (register -> activation -> password reset -> login)"
  captcha_id="$(issue_captcha_id)"
  if [[ -z "${captcha_id}" ]]; then
    echo "[I0] captchaId missing (ensure auth-service dev profile + captcha enabled)" >&2
    exit 1
  fi

  # dev-only: auth.captcha.fixed-code=0000（见 auth-service application-dev.yml）
  captcha_code="0000"

  new_username="u$(date +%s)$(python3 -c 'import random; print(random.randint(1000,9999))')"
  new_password="p$(date +%s)A!"
  new_email="${SMOKE_EMAIL:-${new_username}@example.com}"

  export USERNAME="${new_username}" PASSWORD="${new_password}"

  register_payload="$(EMAIL="${new_email}" CAPTCHA_ID="${captcha_id}" CAPTCHA_CODE="${captcha_code}" \
    python3 -c 'import json,os; print(json.dumps({"username": os.environ.get("USERNAME",""), "password": os.environ.get("PASSWORD",""), "email": os.environ.get("EMAIL",""), "captchaId": os.environ.get("CAPTCHA_ID",""), "captchaCode": os.environ.get("CAPTCHA_CODE","")}))')"

  echo "[I0] 0) register (username=${new_username} email=${new_email})"
  register_resp="$(curl -fsS -H "Content-Type: application/json" -X POST "${GATEWAY_URL}/api/auth/register" -d "${register_payload}")"

  activation_link="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print(((data.get("data") or {}).get("activationLink") or "").strip())' <<<"${register_resp}")"
  user_id="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print(((data.get("data") or {}).get("userId") or 0))' <<<"${register_resp}")"

  if [[ -z "${activation_link}" ]]; then
    echo "[I0] register ok but activationLink missing. Ensure:" >&2
    echo "  - auth.registration.expose-activation-link=true (dev default) OR AUTH_EXPOSE_ACTIVATION_LINK=true" >&2
    exit 1
  fi
  if [[ -z "${user_id}" || "${user_id}" == "0" ]]; then
    echo "[I0] register failed: userId missing" >&2
    echo "[I0] response: ${register_resp}" >&2
    exit 1
  fi

  # activationLink is an API URL; parse userId/code and call via gateway (more stable than trusting host/port).
  act_path="$(python3 -c 'import sys,urllib.parse; u=urllib.parse.urlparse(sys.stdin.read().strip()); print(u.path)' <<<"${activation_link}")"
  echo "[I0] 0.1) activation via ${act_path}"
  curl -fsS "${GATEWAY_URL}${act_path}" >/dev/null

  # password reset: request -> confirm
  captcha_id2="$(issue_captcha_id)"
  if [[ -z "${captcha_id2}" ]]; then
    echo "[I0] captchaId missing for password reset" >&2
    exit 1
  fi
  reset_req_payload="$(EMAIL="${new_email}" CAPTCHA_ID="${captcha_id2}" CAPTCHA_CODE="${captcha_code}" \
    python3 -c 'import json,os; print(json.dumps({"email": os.environ.get("EMAIL",""), "captchaId": os.environ.get("CAPTCHA_ID",""), "captchaCode": os.environ.get("CAPTCHA_CODE","")}))')"
  echo "[I0] 0.2) password reset request"
  reset_req_resp="$(curl -fsS -H "Content-Type: application/json" -X POST "${GATEWAY_URL}/api/auth/password/reset/request" -d "${reset_req_payload}")"
  reset_link="$(python3 -c 'import json,sys; data=json.loads(sys.stdin.read() or "{}"); print(((data.get("data") or {}).get("resetLink") or "").strip())' <<<"${reset_req_resp}")"
  if [[ -z "${reset_link}" ]]; then
    echo "[I0] resetLink missing. Ensure:" >&2
    echo "  - auth.password-reset.expose-reset-link=true (dev default) OR AUTH_EXPOSE_RESET_LINK=true" >&2
    exit 1
  fi
  reset_token="$(python3 -c 'import sys,urllib.parse; u=urllib.parse.urlparse(sys.stdin.read().strip()); q=urllib.parse.parse_qs(u.query); print((q.get("token") or [""])[0])' <<<"${reset_link}")"
  if [[ -z "${reset_token}" ]]; then
    echo "[I0] resetToken missing in resetLink: ${reset_link}" >&2
    exit 1
  fi

  captcha_id3="$(issue_captcha_id)"
  if [[ -z "${captcha_id3}" ]]; then
    echo "[I0] captchaId missing for confirm reset" >&2
    exit 1
  fi

  # set a new password and verify we can login after reset
  new_password2="p$(date +%s)B!"
  reset_confirm_payload="$(RESET_TOKEN="${reset_token}" NEW_PASSWORD="${new_password2}" CAPTCHA_ID="${captcha_id3}" CAPTCHA_CODE="${captcha_code}" \
    python3 -c 'import json,os; print(json.dumps({"resetToken": os.environ.get("RESET_TOKEN",""), "newPassword": os.environ.get("NEW_PASSWORD",""), "captchaId": os.environ.get("CAPTCHA_ID",""), "captchaCode": os.environ.get("CAPTCHA_CODE","")}))')"

  echo "[I0] 0.3) password reset confirm"
  curl -fsS -H "Content-Type: application/json" -X POST "${GATEWAY_URL}/api/auth/password/reset/confirm" -d "${reset_confirm_payload}" >/dev/null

  export USERNAME="${new_username}" PASSWORD="${new_password2}"
  echo "[I0] onboarding done: will login with new credentials"
fi

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

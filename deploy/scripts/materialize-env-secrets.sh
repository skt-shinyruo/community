#!/usr/bin/env bash
# Expands `<generate:...>` placeholder values in an env file, in place.
# Recipes:
#   openssl-rsa-2048-x509-der-base64  / openssl-rsa-2048-pkcs8-der-base64
#     One RSA keypair is generated per file; both recipes derive from it.
#   openssl-rand-hex-32-*
#     openssl rand -hex 32 (suffix only documents the consumer).
# Used by CI, which copies deploy/stacks/*/.env.example verbatim; local dev
# keeps real secrets in gitignored deploy/.env* files instead.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <env-file>" >&2
  exit 1
fi
ENV_FILE="$1"
if [ ! -f "${ENV_FILE}" ]; then
  echo "[materialize-env-secrets] env file not found: ${ENV_FILE}" >&2
  exit 1
fi

if ! grep -q '<generate:' "${ENV_FILE}"; then
  exit 0
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

rsa_needed="$(grep -c '<generate:openssl-rsa-2048-' "${ENV_FILE}" || true)"
if [ "${rsa_needed}" -gt 0 ]; then
  openssl genrsa -out "${work_dir}/jwt.pem" 2048 2>/dev/null
  openssl pkcs8 -topk8 -nocrypt -in "${work_dir}/jwt.pem" -outform DER 2>/dev/null | base64 -w0 >"${work_dir}/rsa-private.b64"
  openssl rsa -in "${work_dir}/jwt.pem" -pubout -outform DER 2>/dev/null | base64 -w0 >"${work_dir}/rsa-public.b64"
fi

result="$(cat "${ENV_FILE}")"
while IFS= read -r line; do
  case "${line}" in
    *'=<generate:openssl-rsa-2048-x509-der-base64>')
      result="${result//"${line}"/${line%%=*}=$(cat "${work_dir}/rsa-public.b64")}"
      ;;
    *'=<generate:openssl-rsa-2048-pkcs8-der-base64>')
      result="${result//"${line}"/${line%%=*}=$(cat "${work_dir}/rsa-private.b64")}"
      ;;
    *'=<generate:openssl-rand-hex-32-'*'>'*)
      result="${result//"${line}"/${line%%=*}=$(openssl rand -hex 32)}"
      ;;
    *'<generate:'*)
      echo "[materialize-env-secrets] unknown recipe in line: ${line}" >&2
      exit 1
      ;;
  esac
done < <(grep '<generate:' "${ENV_FILE}")

printf '%s\n' "${result}" >"${ENV_FILE}"

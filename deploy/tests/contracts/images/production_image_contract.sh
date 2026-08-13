#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${REPO_ROOT}"

test_tmp="$(mktemp -d)"
single_config="${test_tmp}/single.yml"
cluster_config="${test_tmp}/cluster.yml"
runtime_config="${test_tmp}/app-config.js"
trap 'rm -rf "${test_tmp}"' EXIT

grep -Fq 'FROM nginx:1.27-alpine AS frontend-runtime' deploy/images/frontend/Dockerfile
grep -Fq 'COPY --from=frontend-build /workspace/frontend/dist /usr/share/nginx/html' deploy/images/frontend/Dockerfile
grep -Fq 'USER 101:101' deploy/images/frontend/Dockerfile
grep -Fq 'USER 10001:10001' deploy/images/backend/Dockerfile
grep -Fq 'ENV HOME=/tmp/community-runtime/home' deploy/images/backend/Dockerfile
grep -Fq -- '-pl :community-app,:community-gateway,:community-im-gateway,:community-oss,:im-core,:im-realtime,:yierloom-agent' deploy/images/backend/Dockerfile
if rg -n 'mvn[^\n]*\$\{MODULE|rm -rf /root/\.m2' deploy/images/backend/Dockerfile; then
  echo 'backend image builds must share one module-independent reactor layer and preserve the Maven cache on failures' >&2
  exit 1
fi
grep -Fq 'runtime_dir="/tmp/community-runtime"' backend/scripts/run-backend-service.sh
if rg -n 'vite preview|npm run preview|node scripts/renderRuntimeConfig' deploy/images/frontend/Dockerfile; then
  echo 'frontend runtime image must not use the Vite development preview server' >&2
  exit 1
fi

grep -Fq 'pid /tmp/nginx.pid;' deploy/images/frontend/nginx.conf
grep -Fq 'alias /tmp/community-frontend/app-config.js;' deploy/images/frontend/nginx.conf
grep -Fq '/app-config.js "no-store, max-age=0";' deploy/images/frontend/nginx.conf
grep -Fq '~^/assets/ "public, max-age=31536000, immutable";' deploy/images/frontend/nginx.conf
grep -Fq 'server_tokens off;' deploy/images/frontend/nginx.conf
grep -Fq 'script-src '\''self'\'';' deploy/images/frontend/nginx.conf
grep -Fq 'X-Content-Type-Options "nosniff"' deploy/images/frontend/nginx.conf
grep -Fq 'X-Frame-Options "DENY"' deploy/images/frontend/nginx.conf
grep -Fq 'location ^~ /api/' deploy/images/frontend/nginx.conf
grep -Fq 'location ^~ /files/' deploy/images/frontend/nginx.conf
grep -Fq 'location ^~ /ws/im' deploy/images/frontend/nginx.conf
if rg -n '^user[[:space:]]+' deploy/images/frontend/nginx.conf; then
  echo 'nginx user directive is invalid when the container already starts as a non-root user' >&2
  exit 1
fi
if rg -n "script-src[^;]*'unsafe-inline'" deploy/images/frontend/nginx.conf; then
  echo 'inline scripts must remain disabled by CSP' >&2
  exit 1
fi

api_value="$(printf '  https://api.example.test/root?value=\"</script>\";globalThis.RUNTIME_CONFIG_INJECTED=true;//\\\nnext  ')"
im_value="$(printf '\thttps://im.example.test/path?quote=\"&slash=\\\\  ')"
FRONTEND_RUNTIME_API_BASE_URL="${api_value}" \
FRONTEND_RUNTIME_IM_HTTP_BASE_URL="${im_value}" \
  ./deploy/images/frontend/render-runtime-config.sh "${runtime_config}"

EXPECTED_API="${api_value}" EXPECTED_IM="${im_value}" RUNTIME_CONFIG_PATH="${runtime_config}" \
  node --input-type=module <<'NODE'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const source = fs.readFileSync(process.env.RUNTIME_CONFIG_PATH, 'utf8')
const context = vm.createContext({})
vm.runInContext(source, context)

assert.equal(context.RUNTIME_CONFIG_INJECTED, undefined)
assert.equal(context.__COMMUNITY_RUNTIME_CONFIG__.apiBaseUrl, process.env.EXPECTED_API.trim())
assert.equal(context.__COMMUNITY_RUNTIME_CONFIG__.imHttpBaseUrl, process.env.EXPECTED_IM.trim())
assert.equal(vm.runInContext('Object.isFrozen(globalThis.__COMMUNITY_RUNTIME_CONFIG__)', context), true)
NODE
test "$(stat -c '%a' "${runtime_config}")" = '444'
test -z "$(find "${test_tmp}" -name 'app-config.js.tmp.*' -print -quit)"

GATEWAY_PUBLIC_BASE_URL='https://gateway.example.test' \
  ./deploy/images/frontend/render-runtime-config.sh "${runtime_config}"
EXPECTED_API='https://gateway.example.test' EXPECTED_IM='https://gateway.example.test' \
RUNTIME_CONFIG_PATH="${runtime_config}" node --input-type=module <<'NODE'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const context = vm.createContext({})
vm.runInContext(fs.readFileSync(process.env.RUNTIME_CONFIG_PATH, 'utf8'), context)
assert.equal(context.__COMMUNITY_RUNTIME_CONFIG__.apiBaseUrl, process.env.EXPECTED_API)
assert.equal(context.__COMMUNITY_RUNTIME_CONFIG__.imHttpBaseUrl, process.env.EXPECTED_IM)
NODE

FRONTEND_RUNTIME_API_BASE_URL='' \
FRONTEND_RUNTIME_IM_HTTP_BASE_URL='' \
GATEWAY_PUBLIC_BASE_URL='https://gateway.example.test' \
  ./deploy/images/frontend/render-runtime-config.sh "${runtime_config}"
RUNTIME_CONFIG_PATH="${runtime_config}" node --input-type=module <<'NODE'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const context = vm.createContext({})
vm.runInContext(fs.readFileSync(process.env.RUNTIME_CONFIG_PATH, 'utf8'), context)
assert.equal(context.__COMMUNITY_RUNTIME_CONFIG__.apiBaseUrl, '')
assert.equal(context.__COMMUNITY_RUNTIME_CONFIG__.imHttpBaseUrl, '')
NODE

./deploy/deployment.sh config --stack single \
  --env-file deploy/stacks/single/.env.example --no-observability >"${single_config}"
./deploy/deployment.sh config --stack cluster \
  --env-file deploy/stacks/cluster/.env.example --no-observability >"${cluster_config}"

extract_frontend_service() {
  local rendered_config="$1"
  awk '
    $0 == "  frontend:" { in_service = 1; next }
    in_service && /^  [^ ]/ { exit }
    in_service { print }
  ' "${rendered_config}"
}

for rendered_config in "${single_config}" "${cluster_config}"; do
  frontend_service="$(extract_frontend_service "${rendered_config}")"
  printf '%s\n' "${frontend_service}" | grep -Fq 'read_only: true'
  printf '%s\n' "${frontend_service}" | grep -Fq -- '- ALL'
  printf '%s\n' "${frontend_service}" | grep -Fq -- '- no-new-privileges:true'
  printf '%s\n' "${frontend_service}" | grep -Fq -- '- /tmp:size=32m,mode=1777,noexec,nosuid,nodev'
  if printf '%s\n' "${frontend_service}" | rg -n 'VITE_PREVIEW_PROXY_TARGET'; then
    echo 'production frontend service must not receive Vite preview settings' >&2
    exit 1
  fi
done

extract_frontend_service "${single_config}" \
  | grep -Fq 'GATEWAY_PUBLIC_BASE_URL: http://localhost:12880'
extract_frontend_service "${cluster_config}" \
  | grep -Fq 'GATEWAY_PUBLIC_BASE_URL: http://localhost:13880'

grep -Fq '<script src="/app-config.js"></script>' frontend/index.html
grep -Fq '<script src="/theme-bootstrap.js"></script>' frontend/index.html
if rg -n '<script>[[:space:]]*$' frontend/index.html; then
  echo 'index.html must not require CSP unsafe-inline for bootstrap code' >&2
  exit 1
fi

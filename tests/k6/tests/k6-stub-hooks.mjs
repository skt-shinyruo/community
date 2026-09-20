// Resolve hook for node --test: maps the k6 runtime modules to in-memory
// stubs so tests/auth-wiring.test.mjs can import the real lib/auth.js +
// lib/http.js and exercise the 401 recovery wiring end to end. Runtime
// counterpart of the ambient type stubs in tests/k6/types.
const stubs = {
  k6: `
export function check(response, checks) {
  Object.values(checks || {}).forEach((fn) => fn(response))
  return true
}
export function sleep() {}
`,
  'k6/http': `
const state = globalThis.__k6StubHttp
function nextResponse() {
  const next = state.responses.shift()
  return next === undefined ? { status: 200 } : next
}
export default {
  get: () => nextResponse(),
  post: (url) => {
    if (String(url).endsWith('/api/auth/login')) {
      state.loginCount += 1
      return {
        status: 200,
        // Long enough for lib/auth.js's token length>20 guard.
        json: () => ({ data: { accessToken: 'stubbed-access-token-' + state.loginCount } })
      }
    }
    return nextResponse()
  },
  put: (url, body, request) => {
    state.putHeaders.push(request && request.headers)
    return nextResponse()
  }
}
`,
  'k6/metrics': `
export class Counter { add() {} }
export class Rate { add() {} }
export class Trend { add() {} }
`
}

export async function resolve(specifier, context, nextResolve) {
  const stub = stubs[specifier]
  if (stub) {
    return { url: `data:text/javascript,${encodeURIComponent(stub)}`, shortCircuit: true }
  }
  return nextResolve(specifier, context)
}

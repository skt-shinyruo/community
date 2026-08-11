export const IDEMPOTENCY_HEADER = 'Idempotency-Key'

function defaultKeyFactory() {
  try {
    const cryptoObject = globalThis?.crypto
    if (cryptoObject?.randomUUID) return cryptoObject.randomUUID()
  } catch {}

  return `idem_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`
}

/** Owns one high-risk write across its initial request and any manual retries. */
export function createWriteAttempt({ keyFactory = defaultKeyFactory } = {}) {
  let key = ''
  let state = 'idle'

  const ensureKey = () => {
    if (!key) {
      key = String(keyFactory() || '').trim()
      if (!key) throw new Error('Idempotency-Key factory returned an empty key')
      state = 'active'
    }
    return key
  }

  return {
    get key() { return key },
    get state() { return state },
    get active() { return state === 'active' },
    begin() { return ensureKey() },
    headers() { return { [IDEMPOTENCY_HEADER]: ensureKey() } },
    succeed() {
      key = ''
      state = 'succeeded'
    },
    cancel() {
      key = ''
      state = 'cancelled'
    },
    changeIntent() {
      key = ''
      state = 'changed'
    }
  }
}

export function writeAttemptConfig(attempt) {
  if (!attempt || typeof attempt.headers !== 'function') {
    throw new TypeError('writeAttempt is required for a high-risk write')
  }
  return { headers: attempt.headers() }
}

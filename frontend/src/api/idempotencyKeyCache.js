function defaultGenerateKey() {
  try {
    const cryptoObj = globalThis?.crypto
    if (cryptoObj?.randomUUID) return cryptoObj.randomUUID()
  } catch { }

  const rand = Math.random().toString(36).slice(2)
  const now = Date.now().toString(36)
  return `idem_${now}_${rand}`
}

export function createIdempotencyKeyCache({
  windowMs = 10_000,
  maxSize = 5_000,
  generateKey = defaultGenerateKey
} = {}) {
  const ttlMs = Math.max(0, Number(windowMs) || 0)
  const maxEntries = Math.max(1, Number(maxSize) || 1)
  const keyFn = typeof generateKey === 'function' ? generateKey : defaultGenerateKey

  const cache = new Map()
  let lastNowMs = 0

  function nowMs() {
    const t = Date.now()
    // Date.now() is wall-clock and can move backwards; clamp to keep eviction ordering stable.
    lastNowMs = Math.max(lastNowMs, t)
    return lastNowMs
  }

  function isValidEntry(entry) {
    return entry && typeof entry.expiresAt === 'number' && typeof entry.key === 'string' && entry.key
  }

  function evictExpired(now) {
    // `cache` iteration order matches insertion order.
    // We keep insertion order aligned with expiry order by doing delete+set on renewals.
    for (const [fingerprint, entry] of cache) {
      if (isValidEntry(entry) && entry.expiresAt > now) break
      cache.delete(fingerprint)
    }
  }

  function evictOverflow() {
    while (cache.size > maxEntries) {
      const oldest = cache.keys().next().value
      if (oldest == null) break
      cache.delete(oldest)
    }
  }

  function getOrReuse(fingerprint) {
    const fp = String(fingerprint || '')
    const now = nowMs()

    evictExpired(now)

    const existing = cache.get(fp)
    if (isValidEntry(existing) && existing.expiresAt > now) {
      return existing.key
    }

    const key = keyFn()
    // Renew as a new insertion so the eviction loop can stop early.
    cache.delete(fp)
    cache.set(fp, { key, expiresAt: now + ttlMs })

    evictOverflow()
    return key
  }

  function size() {
    return cache.size
  }

  function clear() {
    cache.clear()
  }

  return {
    getOrReuse,
    size,
    clear
  }
}


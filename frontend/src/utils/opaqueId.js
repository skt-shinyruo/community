export function normalizeOpaqueId(value) {
  if (value == null) return ''

  const next = String(value).trim()
  if (!next || next === '0') return ''

  const lower = next.toLowerCase()
  if (lower === 'null' || lower === 'undefined' || lower === 'nan') return ''

  return next
}

export function hasOpaqueId(value) {
  return normalizeOpaqueId(value) !== ''
}

export function sameOpaqueId(left, right) {
  const a = normalizeOpaqueId(left)
  const b = normalizeOpaqueId(right)
  return !!a && a === b
}

export function normalizeOpaqueIds(values, { max = 200 } = {}) {
  const raw = Array.isArray(values) ? values : []
  const out = []
  const seen = new Set()

  for (const value of raw) {
    const id = normalizeOpaqueId(value)
    if (!id || seen.has(id)) continue
    seen.add(id)
    out.push(id)
    if (out.length >= max) break
  }

  return out
}

export function isUuid(value) {
  const id = normalizeOpaqueId(value)
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)
}

export function requireOpaqueId(value, label = 'id') {
  const id = normalizeOpaqueId(value)
  if (!id) {
    throw new Error(`${label} 非法`)
  }
  return id
}

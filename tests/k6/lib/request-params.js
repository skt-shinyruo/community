export function compactK6Params(params = {}) {
  return Object.fromEntries(Object.entries(params || {}).filter(([, value]) => value != null))
}

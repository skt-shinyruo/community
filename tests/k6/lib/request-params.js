export function compactK6Params(params = {}) {
  const compacted = {}
  Object.keys(params || {}).forEach((key) => {
    if (params[key] !== undefined && params[key] !== null) {
      compacted[key] = params[key]
    }
  })
  return compacted
}

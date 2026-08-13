/** @param {{ getScope?: () => unknown }} [options] */
export function createLatestRequestTracker({ getScope } = {}) {
  let currentToken = 0
  const scopeProvider = typeof getScope === 'function' ? getScope : null

  return {
    begin() {
      currentToken += 1
      return scopeProvider
        ? { token: currentToken, scope: scopeProvider() }
        : currentToken
    },

    /** @param {number | { token: number, scope: unknown }} handle */
    isCurrent(handle) {
      if (!scopeProvider) return Number(handle) === currentToken
      const scopedHandle = typeof handle === 'object' && handle !== null ? handle : null
      return Number(scopedHandle?.token) === currentToken && scopedHandle?.scope === scopeProvider()
    },

    invalidate() {
      currentToken += 1
      return currentToken
    }
  }
}

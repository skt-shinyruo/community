export function createLatestRequestTracker() {
  let currentToken = 0

  return {
    begin() {
      currentToken += 1
      return currentToken
    },

    isCurrent(token) {
      return Number(token) === currentToken
    },

    invalidate() {
      currentToken += 1
      return currentToken
    }
  }
}

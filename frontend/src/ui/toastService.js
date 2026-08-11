let toastHandler = null
const handledErrors = new WeakSet()

export function setToastHandler(handler) {
  toastHandler = typeof handler === 'function' ? handler : null
}

export function showToast(payload = {}) {
  if (!toastHandler) return false
  toastHandler(payload || {})
  return true
}

export function showErrorToast(error, payload = {}, handler = showToast) {
  const trackable = error != null && (typeof error === 'object' || typeof error === 'function')
  if (trackable && handledErrors.has(error)) return false
  if (trackable) handledErrors.add(error)

  const shown = typeof handler === 'function' ? handler(payload || {}) : false
  if (shown === false && trackable) handledErrors.delete(error)
  return shown !== false
}

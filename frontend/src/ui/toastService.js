let toastHandler = null

export function setToastHandler(handler) {
  toastHandler = typeof handler === 'function' ? handler : null
}

export function showToast(payload = {}) {
  if (!toastHandler) return false
  toastHandler(payload || {})
  return true
}

const STORAGE_KEY = 'community.session.hint'

function getStorage() {
  try {
    return globalThis?.localStorage || null
  } catch {
    return null
  }
}

export function hasSessionHint(storage = getStorage()) {
  try {
    return storage?.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

export function setSessionHint(storage = getStorage()) {
  try {
    storage?.setItem(STORAGE_KEY, '1')
  } catch {
    // Best-effort only.
  }
}

export function clearSessionHint(storage = getStorage()) {
  try {
    storage?.removeItem(STORAGE_KEY)
  } catch {
    // Best-effort only.
  }
}

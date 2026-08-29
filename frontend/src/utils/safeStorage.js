// 安全 localStorage 访问：隐私/受限模式下存储访问可能抛错，读取、写入、删除统一兜底。

export function safeStorageGet(key) {
  try {
    return window.localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

export function safeStorageSet(key, value) {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // ignore
  }
}

export function safeStorageRemove(key) {
  try {
    window.localStorage.removeItem(key)
  } catch {
    // ignore
  }
}

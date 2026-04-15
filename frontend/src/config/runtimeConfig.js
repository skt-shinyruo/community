function readRuntimeConfigObject() {
  try {
    const config = globalThis?.__COMMUNITY_RUNTIME_CONFIG__
    return config && typeof config === 'object' ? config : {}
  } catch {
    return {}
  }
}

export function readRuntimeConfigString(key) {
  if (typeof key !== 'string' || key.trim() === '') return ''
  const value = readRuntimeConfigObject()[key]
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

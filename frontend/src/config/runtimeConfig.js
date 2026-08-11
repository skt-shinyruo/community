function readRuntimeConfigObject() {
  try {
    const config = globalThis?.__COMMUNITY_RUNTIME_CONFIG__
    return config && typeof config === 'object' ? config : {}
  } catch {
    return {}
  }
}

export function readRuntimeConfigString(key) {
  if (typeof key !== 'string' || key.trim() === '') return undefined
  const config = readRuntimeConfigObject()
  if (!Object.prototype.hasOwnProperty.call(config, key)) return undefined
  const value = config[key]
  return typeof value === 'string' ? value.trim() : undefined
}

import { readRuntimeConfigString } from './runtimeConfig'

function readViteString(name) {
  const value = import.meta.env?.[name]
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

export function resolveApiBaseUrl() {
  return readRuntimeConfigString('apiBaseUrl') || readViteString('VITE_API_BASE_URL') || ''
}

export function resolveImHttpBaseUrl() {
  return readRuntimeConfigString('imHttpBaseUrl') || readViteString('VITE_IM_CORE_BASE_URL') || ''
}

export function resolveImWsUrl() {
  const configured = readRuntimeConfigString('imWsUrl') || readViteString('VITE_IM_WS_URL')
  if (configured) return configured

  try {
    const loc = globalThis?.location
    if (!loc) return ''
    const scheme = loc.protocol === 'https:' ? 'wss' : 'ws'
    return `${scheme}://${loc.host}/ws/im`
  } catch {
    return ''
  }
}

import { readRuntimeConfigString } from './runtimeConfig'

const LOCAL_GATEWAY_SOURCE_PORTS = new Set(['5173', '12881', '12890', '12888'])

function readViteString(name) {
  const value = import.meta.env?.[name]
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

function inferLocalGatewayOrigin() {
  try {
    const loc = globalThis?.location
    if (!loc) return ''

    const hostname = String(loc.hostname || '').trim()
    const port = String(loc.port || '').trim()
    if (!hostname || !LOCAL_GATEWAY_SOURCE_PORTS.has(port)) return ''
    if (hostname !== 'localhost' && hostname !== '127.0.0.1') return ''

    return `${loc.protocol}//${hostname}:12880`
  } catch {
    return ''
  }
}

export function resolveApiBaseUrl() {
  return readRuntimeConfigString('apiBaseUrl') || readViteString('VITE_API_BASE_URL') || inferLocalGatewayOrigin() || ''
}

export function resolveImHttpBaseUrl() {
  return readRuntimeConfigString('imHttpBaseUrl') || readViteString('VITE_IM_CORE_BASE_URL') || inferLocalGatewayOrigin() || ''
}

export function resolveImWsUrl() {
  const configured = readRuntimeConfigString('imWsUrl') || readViteString('VITE_IM_WS_URL')
  if (configured) return configured

  const gatewayOrigin = inferLocalGatewayOrigin()
  if (gatewayOrigin) {
    return gatewayOrigin.replace(/^http/, 'ws') + '/ws/im'
  }

  try {
    const loc = globalThis?.location
    if (!loc) return ''
    const scheme = loc.protocol === 'https:' ? 'wss' : 'ws'
    return `${scheme}://${loc.host}/ws/im`
  } catch {
    return ''
  }
}

import { readRuntimeConfigString } from './runtimeConfig'

function readViteString(name) {
  const value = import.meta.env?.[name]
  return typeof value === 'string' && value.trim() ? value.trim() : ''
}

export function resolveApiBaseUrl() {
  const runtimeValue = readRuntimeConfigString('apiBaseUrl')
  return runtimeValue !== undefined ? runtimeValue : readViteString('VITE_API_BASE_URL')
}

export function resolveImHttpBaseUrl() {
  const runtimeValue = readRuntimeConfigString('imHttpBaseUrl')
  return runtimeValue !== undefined ? runtimeValue : readViteString('VITE_IM_CORE_BASE_URL')
}

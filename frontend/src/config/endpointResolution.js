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

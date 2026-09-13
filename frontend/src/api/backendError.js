export function backendErrorCode(error) {
  const code = Number(error?.response?.data?.code ?? error?.code ?? 0)
  return Number.isFinite(code) ? code : 0
}

export function backendErrorMessage(error, fallback = '') {
  return error?.response?.data?.message || error?.message || fallback
}

export function isCaptchaRejected(error) {
  const code = backendErrorCode(error)
  return code === 10005 || code === 10006
}

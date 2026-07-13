const STORAGE_KEY = 'community.register.pending'

function safeString(value) {
  return typeof value === 'string' ? value : ''
}

function getStorage() {
  if (typeof window === 'undefined' || !window.localStorage) {
    return null
  }
  return window.localStorage
}

export function buildRegisterFlowState(registerData = null) {
  const registrationToken = safeString(registerData?.registrationToken)
  const emailCodeIssued = registerData?.emailCodeIssued === true
  const maskedEmail = safeString(registerData?.maskedEmail)
  const debugEmailCode = safeString(registerData?.debugEmailCode)
  const step = emailCodeIssued && registrationToken ? 'verify' : 'form'

  return {
    step,
    registrationToken,
    emailCodeIssued,
    maskedEmail,
    debugEmailCode,
    successMessage: step === 'verify'
      ? `注册成功：验证码已发送至 ${maskedEmail || '你的邮箱'}。`
      : ''
  }
}

export function persistRegisterFlowState(flowState, storage = getStorage()) {
  const normalized = buildRegisterFlowState(flowState)
  if (!storage) {
    return normalized
  }
  if (normalized.step !== 'verify') {
    storage.removeItem(STORAGE_KEY)
    return normalized
  }
  storage.setItem(STORAGE_KEY, JSON.stringify({
    registrationToken: normalized.registrationToken,
    emailCodeIssued: normalized.emailCodeIssued,
    maskedEmail: normalized.maskedEmail,
    debugEmailCode: normalized.debugEmailCode
  }))
  return normalized
}

export function restoreRegisterFlowState(storage = getStorage()) {
  if (!storage) {
    return buildRegisterFlowState()
  }
  const raw = storage.getItem(STORAGE_KEY)
  if (!raw) {
    return buildRegisterFlowState()
  }
  try {
    return buildRegisterFlowState(JSON.parse(raw))
  } catch {
    storage.removeItem(STORAGE_KEY)
    return buildRegisterFlowState()
  }
}

export function clearRegisterFlowState(storage = getStorage()) {
  storage?.removeItem(STORAGE_KEY)
}

export function resolveRegisterFlowError(error) {
  const code = Number(error?.code || 0)
  if (code === 10013) {
    return {
      resetFlow: true,
      message: '注册上下文已失效，请重新注册'
    }
  }
  if (code === 10014) {
    return {
      resetFlow: true,
      message: error?.message || '注册已完成，请直接登录'
    }
  }
  return {
    resetFlow: false,
    message: error?.message || ''
  }
}

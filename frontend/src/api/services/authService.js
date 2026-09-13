// 认证相关 API：登录、注册、注册验证码、图形验证码、查询当前用户信息。
// 登录 / 注册 / 找回密码表单在页面行内展示错误，提交类调用统一跳过全局错误 toast，避免双重提示。

import http from '../http'
import { unwrapResultBody } from '../result'

const inlineErrorConfig = /** @type {import('axios').AxiosRequestConfig} */ ({ skipGlobalErrorToast: true })

export async function login(username, password, { captchaId = '', captchaCode = '' } = {}) {
  const payload = { username, password }
  if (captchaId && captchaCode) {
    payload.captchaId = captchaId
    payload.captchaCode = captchaCode
  }
  const resp = await http.post('/api/auth/login', payload, inlineErrorConfig)
  return unwrapResultBody(resp.data, '登录')
}

export async function me() {
  const resp = await http.get('/api/auth/me')
  return unwrapResultBody(resp.data, '获取用户信息')
}

export async function register({ username, password, email, captchaId = '', captchaCode = '' }) {
  const resp = await http.post('/api/auth/register', { username, password, email, captchaId, captchaCode }, inlineErrorConfig)
  return unwrapResultBody(resp.data, '注册')
}

export async function resendRegisterCode(registrationToken, { captchaId = '', captchaCode = '' } = {}) {
  const resp = await http.post('/api/auth/register/code/resend', { registrationToken, captchaId, captchaCode }, inlineErrorConfig)
  return unwrapResultBody(resp.data, '重发注册验证码')
}

export async function verifyRegisterCode(registrationToken, code) {
  const resp = await http.post('/api/auth/register/code/verify', { registrationToken, code }, inlineErrorConfig)
  return unwrapResultBody(resp.data, '验证注册验证码')
}

export async function issueCaptcha() {
  const resp = await http.get('/api/auth/captcha')
  return unwrapResultBody(resp.data, '获取验证码')
}

export async function requestPasswordReset(email, { captchaId = '', captchaCode = '' } = {}) {
  const resp = await http.post('/api/auth/password/reset/request', { email, captchaId, captchaCode }, inlineErrorConfig)
  return unwrapResultBody(resp.data, '找回密码')
}

export async function confirmPasswordReset(resetToken, newPassword, { captchaId = '', captchaCode = '' } = {}) {
  const resp = await http.post('/api/auth/password/reset/confirm', { resetToken, newPassword, captchaId, captchaCode }, inlineErrorConfig)
  const { data, traceId } = unwrapResultBody(resp.data, '重置密码')
  return { data: !!data, traceId }
}

// 认证相关 API：登录、注册、注册验证码、图形验证码、查询当前用户信息。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function login(username, password, { captchaId = '', captchaCode = '' } = {}) {
  const payload = { username, password }
  if (captchaId && captchaCode) {
    payload.captchaId = captchaId
    payload.captchaCode = captchaCode
  }
  const resp = await http.post('/api/auth/login', payload)
  return unwrapResultBody(resp.data, '登录')
}

export async function me() {
  const resp = await http.get('/api/auth/me')
  return unwrapResultBody(resp.data, '获取用户信息')
}

export async function register({ username, password, email, captchaId = '', captchaCode = '' }) {
  const resp = await http.post('/api/auth/register', { username, password, email, captchaId, captchaCode })
  return unwrapResultBody(resp.data, '注册')
}

export async function resendRegisterCode(registrationToken, { captchaId = '', captchaCode = '' } = {}) {
  const resp = await http.post('/api/auth/register/code/resend', { registrationToken, captchaId, captchaCode })
  return unwrapResultBody(resp.data, '重发注册验证码')
}

export async function verifyRegisterCode(registrationToken, code) {
  const resp = await http.post('/api/auth/register/code/verify', { registrationToken, code })
  return unwrapResultBody(resp.data, '验证注册验证码')
}

export async function issueCaptcha() {
  const resp = await http.get('/api/auth/captcha')
  return unwrapResultBody(resp.data, '获取验证码')
}

export async function requestPasswordReset(email, { captchaId = '', captchaCode = '' } = {}) {
  const resp = await http.post('/api/auth/password/reset/request', { email, captchaId, captchaCode })
  return unwrapResultBody(resp.data, '找回密码')
}

export async function confirmPasswordReset(resetToken, newPassword, { captchaId = '', captchaCode = '' } = {}) {
  const resp = await http.post('/api/auth/password/reset/confirm', { resetToken, newPassword, captchaId, captchaCode })
  const { data, traceId } = unwrapResultBody(resp.data, '重置密码')
  return { data: !!data, traceId }
}

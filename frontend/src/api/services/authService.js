// 认证相关 API：登录、注册、激活、验证码、查询当前用户信息。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function login(username, password, { captchaId = '', captchaCode = '' } = {}) {
  const payload = { username, password }
  if (captchaId && captchaCode) {
    payload.captchaId = captchaId
    payload.captchaCode = captchaCode
  }
  const resp = await http.post('/api/auth/login', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '登录')
  return { data, traceId }
}

export async function me() {
  const resp = await http.get('/api/auth/me')
  const { data, traceId } = unwrapResultBody(resp.data, '获取用户信息')
  return { data, traceId }
}

export async function logout() {
  const resp = await http.post('/api/auth/logout')
  const { traceId } = unwrapResultBody(resp.data, '登出')
  return { traceId }
}

export async function register({ username, password, email, captchaId = '', captchaCode = '' }) {
  const resp = await http.post('/api/auth/register', { username, password, email, captchaId, captchaCode })
  const { data, traceId } = unwrapResultBody(resp.data, '注册')
  return { data, traceId }
}

export async function activation(userId, code) {
  const resp = await http.get(`/api/auth/activation/${encodeURIComponent(userId)}/${encodeURIComponent(code)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '激活')
  return { data, traceId }
}

export async function issueCaptcha() {
  const resp = await http.get('/api/auth/captcha')
  const { data, traceId } = unwrapResultBody(resp.data, '获取验证码')
  return { data, traceId }
}

export async function verifyCaptcha(captchaId, code) {
  const resp = await http.post('/api/auth/captcha/verify', { captchaId, code })
  const { data, traceId } = unwrapResultBody(resp.data, '验证码校验')
  return { data: !!data, traceId }
}

export async function requestPasswordReset(email, { captchaId = '', captchaCode = '' } = {}) {
  const resp = await http.post('/api/auth/password/reset/request', { email, captchaId, captchaCode })
  const { data, traceId } = unwrapResultBody(resp.data, '找回密码')
  return { data, traceId }
}

export async function confirmPasswordReset(resetToken, newPassword, { captchaId = '', captchaCode = '' } = {}) {
  const resp = await http.post('/api/auth/password/reset/confirm', { resetToken, newPassword, captchaId, captchaCode })
  const { data, traceId } = unwrapResultBody(resp.data, '重置密码')
  return { data: !!data, traceId }
}

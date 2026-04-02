import http from '../http'
import { unwrapResultBody } from '../result'

export async function searchAdminGrowthUser({ userId, username, email } = {}) {
  const params = {}
  if (Number.isFinite(Number(userId)) && Number(userId) > 0) params.userId = Number(userId)
  if (String(username || '').trim()) params.username = String(username).trim()
  if (String(email || '').trim()) params.email = String(email).trim()
  const resp = await http.get('/api/growth/admin/users/search', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询成长账户')
  return { data: data || null, traceId }
}

export async function adjustAdminGrowth(payload) {
  const resp = await http.post('/api/growth/admin/adjustments', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '执行成长调账')
  return { data: data || null, traceId }
}

export async function listAdminGrowthLedgers(userId, { limit = 10 } = {}) {
  const resp = await http.get(`/api/growth/admin/users/${encodeURIComponent(userId)}/ledgers`, {
    params: { limit }
  })
  const { data, traceId } = unwrapResultBody(resp.data, '查询奖励流水')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listAdminGrowthAdjustments(userId, { limit = 10 } = {}) {
  const resp = await http.get(`/api/growth/admin/users/${encodeURIComponent(userId)}/adjustments`, {
    params: { limit }
  })
  const { data, traceId } = unwrapResultBody(resp.data, '查询调账记录')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function getUserLevelConfig() {
  const resp = await http.get('/api/growth/admin/user-level/config')
  const { data, traceId } = unwrapResultBody(resp.data, '查询用户等级配置')
  return { data: data || {}, traceId }
}

export async function updateUserLevelConfig(payload) {
  const resp = await http.put('/api/growth/admin/user-level/config', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '更新用户等级配置')
  return { data: data || {}, traceId }
}

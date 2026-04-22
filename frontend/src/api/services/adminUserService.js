// 管理员用户管理相关 API：搜索用户与修改角色。

import http from '../http'
import { unwrapResultBody } from '../result'
import { normalizeOpaqueId, requireOpaqueId } from '../../utils/opaqueId'

export async function adminSearchUser({ userId, username, email } = {}) {
  const params = {}
  const uid = normalizeOpaqueId(userId)
  if (uid) params.userId = uid
  if (typeof username === 'string' && username.trim()) params.username = username.trim()
  if (typeof email === 'string' && email.trim()) params.email = email.trim()

  const resp = await http.get('/api/users/admin/search', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '搜索用户')
  return { data: data || null, traceId }
}

export async function adminUpdateUserRole({ targetUserId, type, reason, confirm } = {}) {
  const payload = {
    targetUserId: requireOpaqueId(targetUserId, 'targetUserId'),
    type: Number(type || 0),
    reason: String(reason || ''),
    confirm: !!confirm
  }
  const resp = await http.post('/api/users/admin/role', payload)
  const { traceId } = unwrapResultBody(resp.data, '修改用户角色')
  return { traceId }
}

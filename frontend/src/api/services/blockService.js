// 拉黑/屏蔽相关 API：拉黑/解除拉黑/列表/状态。

import http from '../http'
import { unwrapResultBody } from '../result'
import { requireOpaqueId } from '../../utils/opaqueId'

export async function blockUser(userId) {
  const uid = requireOpaqueId(userId, 'userId')
  const resp = await http.post('/api/blocks', { userId: uid })
  const { traceId } = unwrapResultBody(resp.data, '屏蔽用户')
  return { traceId }
}

export async function unblockUser(userId) {
  const uid = requireOpaqueId(userId, 'userId')
  const resp = await http.delete('/api/blocks', { params: { userId: uid } })
  const { traceId } = unwrapResultBody(resp.data, '解除屏蔽')
  return { traceId }
}

export async function listBlockedUsers() {
  const resp = await http.get('/api/blocks')
  const { data, traceId } = unwrapResultBody(resp.data, '查询屏蔽列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function getBlockStatus(userId) {
  const uid = requireOpaqueId(userId, 'userId')
  const resp = await http.get('/api/blocks/status', { params: { userId: uid } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询屏蔽状态')
  return { data: !!data, traceId }
}

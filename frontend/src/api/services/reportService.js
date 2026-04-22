// 举报相关 API：提交举报（帖子/评论/用户）。

import http from '../http'
import { unwrapResultBody } from '../result'
import { requireOpaqueId } from '../../utils/opaqueId'

export async function createReport({ targetType, targetId, reason, detail } = {}) {
  const payload = {
    targetType: String(targetType || '').trim(),
    targetId: requireOpaqueId(targetId, 'targetId'),
    reason: String(reason || '').trim()
  }
  if (detail != null && String(detail).trim()) payload.detail = String(detail).trim()

  const resp = await http.post('/api/reports', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '提交举报')
  return { data, traceId }
}

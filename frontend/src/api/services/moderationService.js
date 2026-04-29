// 治理后台 API：举报队列 + 处置动作 + 审计查询。

import http from '../http'
import { unwrapResultBody } from '../result'
import { normalizeOpaqueId, requireOpaqueId } from '../../utils/opaqueId'

export async function listReports({ status, targetType, reporterId, page = 0, size = 20 } = {}) {
  const params = { page, size }
  if (status != null && String(status).trim()) params.status = Number(status)
  if (targetType != null && String(targetType).trim()) params.targetType = Number(targetType)
  const rid = normalizeOpaqueId(reporterId)
  if (rid) params.reporterId = rid
  const resp = await http.get('/api/moderation/reports', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询举报队列')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function takeAction({ reportId, action, reason, durationSeconds } = {}) {
  const payload = {
    reportId: requireOpaqueId(reportId, 'reportId'),
    action: String(action || '').trim(),
    reason: String(reason || '').trim()
  }
  if (durationSeconds != null && Number(durationSeconds) > 0) payload.durationSeconds = Number(durationSeconds)

  const resp = await http.post('/api/moderation/actions', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '执行处置动作')
  return { data, traceId }
}

export async function listActions({ actorId, page = 0, size = 20 } = {}) {
  const params = { page, size }
  const aid = normalizeOpaqueId(actorId)
  if (aid) params.actorId = aid
  const resp = await http.get('/api/moderation/actions', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询审计记录')
  return { data: Array.isArray(data) ? data : [], traceId }
}

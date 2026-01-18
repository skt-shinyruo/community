// 通知相关 API：汇总、列表、未读、标记已读。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function topicSummary() {
  const resp = await http.get('/api/notices/summary')
  const { data, traceId } = unwrapResultBody(resp.data, '查询通知汇总')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listNotices(topic, { page = 0, size = 10 } = {}) {
  const resp = await http.get('/api/notices', { params: { topic, page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询通知')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function unreadCount(topic) {
  const resp = await http.get('/api/notices/unread-count', { params: { topic } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询通知未读数')
  return { data: Number(data || 0), traceId }
}

export async function markRead(ids) {
  const resp = await http.put('/api/notices/read', { ids })
  const { traceId } = unwrapResultBody(resp.data, '通知标记已读')
  return { traceId }
}


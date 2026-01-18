// 私信相关 API：会话列表、会话详情、发送消息、未读、标记已读。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function listConversationItems({ page = 0, size = 10 } = {}) {
  const resp = await http.get('/api/messages/conversations/detail', { params: { page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询会话列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listLetters(conversationId, { page = 0, size = 20 } = {}) {
  const resp = await http.get(`/api/messages/conversations/${encodeURIComponent(conversationId)}`, { params: { page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询私信')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function sendMessage({ toId, toName, content }) {
  const resp = await http.post('/api/messages', { toId, toName, content })
  const { traceId } = unwrapResultBody(resp.data, '发送私信')
  return { traceId }
}

export async function unreadCount({ conversationId } = {}) {
  const resp = await http.get('/api/messages/unread-count', { params: { conversationId } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询未读数')
  return { data: Number(data || 0), traceId }
}

export async function markRead(ids) {
  const resp = await http.put('/api/messages/read', { ids })
  const { traceId } = unwrapResultBody(resp.data, '标记已读')
  return { traceId }
}


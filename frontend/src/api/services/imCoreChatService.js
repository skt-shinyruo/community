import imCoreHttp from '../imCoreHttp'
import { unwrapResultBody } from '../result'

export async function listImConversations({ page = 0, size = 20 } = {}) {
  const resp = await imCoreHttp.get('/api/im/conversations', { params: { page, size } })
  const { data } = unwrapResultBody(resp?.data, '加载会话列表')
  return Array.isArray(data) ? data : []
}

export async function listImConversationPage({ cursor = '', size = 20 } = {}) {
  const resp = await imCoreHttp.get('/api/im/conversations/page', { params: { cursor, size } })
  const { data } = unwrapResultBody(resp?.data, '加载会话列表')
  return data || { items: [] }
}

export async function listImConversationMessages(conversationId, { afterSeq = 0, limit = 50 } = {}) {
  const cid = encodeURIComponent(String(conversationId || ''))
  const resp = await imCoreHttp.get(`/api/im/conversations/${cid}/messages`, { params: { afterSeq, limit } })
  const { data } = unwrapResultBody(resp?.data, '加载会话消息')
  return data || { items: [] }
}

export async function listImConversationHistory(conversationId, { beforeSeq, limit = 50 } = {}) {
  const cid = encodeURIComponent(String(conversationId || ''))
  const resp = await imCoreHttp.get(`/api/im/conversations/${cid}/messages/history`, { params: { beforeSeq, limit } })
  const { data } = unwrapResultBody(resp?.data, '加载会话消息')
  return data || { items: [] }
}

export async function markImConversationRead(conversationId, lastReadSeq) {
  const cid = encodeURIComponent(String(conversationId || ''))
  const resp = await imCoreHttp.post(`/api/im/conversations/${cid}/read`, { lastReadSeq: Number(lastReadSeq || 0) })
  unwrapResultBody(resp?.data, '标记已读')
}

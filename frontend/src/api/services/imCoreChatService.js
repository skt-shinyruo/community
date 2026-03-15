import imCoreHttp from '../imCoreHttp'
import { unwrapResultBody } from '../result'

function isResultBody(body) {
  const code = body?.code
  const message = body?.message
  return typeof code === 'number' && typeof message === 'string'
}

export async function listImConversations({ page = 0, size = 20 } = {}) {
  const resp = await imCoreHttp.get('/api/im/conversations', { params: { page, size } })
  const body = resp?.data
  if (isResultBody(body)) {
    const { data } = unwrapResultBody(body, '加载会话列表')
    return Array.isArray(data) ? data : []
  }
  return Array.isArray(body) ? body : []
}

export async function listImConversationMessages(conversationId, { afterSeq = 0, limit = 50 } = {}) {
  const cid = encodeURIComponent(String(conversationId || ''))
  const resp = await imCoreHttp.get(`/api/im/conversations/${cid}/messages`, { params: { afterSeq, limit } })
  const body = resp?.data
  if (isResultBody(body)) {
    const { data } = unwrapResultBody(body, '加载会话消息')
    return data || { items: [] }
  }
  return body || { items: [] }
}

export async function markImConversationRead(conversationId, lastReadSeq) {
  const cid = encodeURIComponent(String(conversationId || ''))
  const resp = await imCoreHttp.post(`/api/im/conversations/${cid}/read`, { lastReadSeq: Number(lastReadSeq || 0) })
  const body = resp?.data
  if (isResultBody(body)) {
    unwrapResultBody(body, '标记已读')
  }
}

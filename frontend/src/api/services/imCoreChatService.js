import imCoreHttp from '../imCoreHttp'

export async function listImConversations({ page = 0, size = 20 } = {}) {
  const resp = await imCoreHttp.get('/api/im/conversations', { params: { page, size } })
  const data = resp?.data
  return Array.isArray(data) ? data : []
}

export async function listImConversationMessages(conversationId, { afterSeq = 0, limit = 50 } = {}) {
  const cid = encodeURIComponent(String(conversationId || ''))
  const resp = await imCoreHttp.get(`/api/im/conversations/${cid}/messages`, { params: { afterSeq, limit } })
  return resp?.data || { items: [] }
}

export async function markImConversationRead(conversationId, lastReadSeq) {
  const cid = encodeURIComponent(String(conversationId || ''))
  await imCoreHttp.post(`/api/im/conversations/${cid}/read`, { lastReadSeq: Number(lastReadSeq || 0) })
}


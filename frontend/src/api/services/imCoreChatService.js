import imCoreHttp from '../imCoreHttp'
import { unwrapResultBody } from '../result'

/** @typedef {Record<string, any> & { items?: Record<string, any>[], nextBeforeSeq?: number | null, hasMore?: boolean }} ImConversationMessagePage */

export async function listImConversationPage({ cursor = '', size = 20 } = {}) {
  const resp = await imCoreHttp.get('/api/im/conversations/page', { params: { cursor, size } })
  const { data } = unwrapResultBody(resp?.data, '加载会话列表')
  return data || { items: [] }
}

/**
 * @param {unknown} conversationId
 * @param {{ afterSeq?: number, limit?: number }} [options]
 * @returns {Promise<ImConversationMessagePage>}
 */
export async function listImConversationMessages(conversationId, { afterSeq = 0, limit = 50 } = {}) {
  const cid = encodeURIComponent(String(conversationId || ''))
  const resp = await imCoreHttp.get(`/api/im/conversations/${cid}/messages`, { params: { afterSeq, limit } })
  const { data } = unwrapResultBody(resp?.data, '加载会话消息')
  return /** @type {ImConversationMessagePage} */ (data || { items: [] })
}

/**
 * @param {unknown} conversationId
 * @param {{ beforeSeq?: number, limit?: number }} [options]
 * @returns {Promise<ImConversationMessagePage>}
 */
export async function listImConversationHistory(conversationId, { beforeSeq, limit = 50 } = {}) {
  const cid = encodeURIComponent(String(conversationId || ''))
  const resp = await imCoreHttp.get(`/api/im/conversations/${cid}/messages/history`, { params: { beforeSeq, limit } })
  const { data } = unwrapResultBody(resp?.data, '加载会话消息')
  return /** @type {ImConversationMessagePage} */ (data || { items: [] })
}

export async function markImConversationRead(conversationId, lastReadSeq) {
  const cid = encodeURIComponent(String(conversationId || ''))
  const resp = await imCoreHttp.post(`/api/im/conversations/${cid}/read`, { lastReadSeq: Number(lastReadSeq || 0) })
  unwrapResultBody(resp?.data, '标记已读')
}

/**
 * 私信/群聊未读摘要（壳层角标用）。后台刷新语义：失败静默，不弹全局错误 toast。
 * @param {{ limit?: number }} [options]
 * @returns {Promise<{ rooms?: Record<string, any>[], conversations?: Record<string, any>[] }>}
 */
export async function getImUnreadSummary({ limit = 500 } = {}) {
  const config = { params: { limit }, skipGlobalErrorToast: true }
  const resp = await imCoreHttp.get('/api/im/unread/summary', config)
  const { data } = unwrapResultBody(resp?.data, '加载未读摘要')
  return data || { rooms: [], conversations: [] }
}

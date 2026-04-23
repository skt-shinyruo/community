import { normalizeOpaqueId, sameOpaqueId } from '../utils/opaqueId'

export function parseConversationTargetId(conversationId, meUserId) {
  const cid = String(conversationId || '').trim()
  const me = normalizeOpaqueId(meUserId)
  if (!cid || !me) return ''

  const parts = cid.split('_').map((value) => normalizeOpaqueId(value)).filter(Boolean)
  if (parts.length !== 2) return ''

  const [a, b] = parts
  if (sameOpaqueId(a, me)) return b
  if (sameOpaqueId(b, me)) return a
  return ''
}

export function mapConversationMessage(raw) {
  const seq = Number(raw?.seq || 0)
  return {
    id: normalizeOpaqueId(raw?.messageId) || (seq > 0 ? String(seq) : ''),
    seq,
    fromId: normalizeOpaqueId(raw?.fromUserId),
    toId: normalizeOpaqueId(raw?.toUserId),
    content: String(raw?.content || ''),
    createTime: Number(raw?.createdAtEpochMs || 0)
  }
}

function compareConversationMessages(a, b) {
  const aSeq = Number(a?.seq || 0)
  const bSeq = Number(b?.seq || 0)
  if (aSeq > 0 && bSeq > 0 && aSeq !== bSeq) {
    return aSeq - bSeq
  }

  const aTime = Number(a?.createTime || 0)
  const bTime = Number(b?.createTime || 0)
  if (aTime !== bTime) {
    return aTime - bTime
  }

  return String(a?.id || '').localeCompare(String(b?.id || ''))
}

function conversationMessageKey(message) {
  const seq = Number(message?.seq || 0)
  if (seq > 0) return `seq:${seq}`

  const id = normalizeOpaqueId(message?.id)
  if (id) return `id:${id}`

  const fromId = normalizeOpaqueId(message?.fromId)
  const toId = normalizeOpaqueId(message?.toId)
  const createTime = Number(message?.createTime || 0)
  const content = String(message?.content || '')
  return `fallback:${fromId}:${toId}:${createTime}:${content}`
}

export function mergeConversationMessages(currentItems, incomingItems) {
  const merged = new Map()

  for (const message of Array.isArray(currentItems) ? currentItems : []) {
    merged.set(conversationMessageKey(message), message)
  }

  for (const message of Array.isArray(incomingItems) ? incomingItems : []) {
    merged.set(conversationMessageKey(message), message)
  }

  return Array.from(merged.values()).sort(compareConversationMessages)
}

export function findLatestConversationSeq(items) {
  return (Array.isArray(items) ? items : []).reduce((maxSeq, item) => {
    return Math.max(maxSeq, Number(item?.seq || 0))
  }, 0)
}

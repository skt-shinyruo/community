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

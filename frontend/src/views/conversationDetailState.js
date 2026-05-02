import { normalizeOpaqueId, sameOpaqueId } from '../utils/opaqueId'

const SIGNED_64_MINIMUM = 1n << 63n
const UNSIGNED_64_MODULUS = 1n << 64n

function toSigned64(value) {
  return value >= SIGNED_64_MINIMUM ? value - UNSIGNED_64_MODULUS : value
}

function uuidSignedParts(value) {
  const hex = normalizeOpaqueId(value).replaceAll('-', '')
  if (!/^[0-9a-f]{32}$/i.test(hex)) return null

  return {
    mostSignificantBits: toSigned64(BigInt(`0x${hex.slice(0, 16)}`)),
    leastSignificantBits: toSigned64(BigInt(`0x${hex.slice(16)}`))
  }
}

function compareJavaUuid(leftValue, rightValue) {
  const left = uuidSignedParts(leftValue)
  const right = uuidSignedParts(rightValue)
  if (!left || !right) return String(leftValue || '').localeCompare(String(rightValue || ''))

  if (left.mostSignificantBits < right.mostSignificantBits) return -1
  if (left.mostSignificantBits > right.mostSignificantBits) return 1
  if (left.leastSignificantBits < right.leastSignificantBits) return -1
  if (left.leastSignificantBits > right.leastSignificantBits) return 1
  return 0
}

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

export function buildCanonicalConversationId(leftUserId, rightUserId) {
  const left = normalizeOpaqueId(leftUserId)
  const right = normalizeOpaqueId(rightUserId)
  if (!left || !right || sameOpaqueId(left, right)) return ''

  return [left, right].sort(compareJavaUuid).join('_')
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

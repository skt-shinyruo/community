import { normalizeOpaqueId, requireApiOpaqueId, sameOpaqueId } from '../utils/opaqueId'

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
  const seq = Number(raw?.seq)
  if (!Number.isSafeInteger(seq) || seq <= 0) {
    throw new Error('seq 非法')
  }
  const createTime = Number(raw?.createdAtEpochMs)
  if (!Number.isFinite(createTime) || createTime <= 0) {
    throw new Error('createdAtEpochMs 非法')
  }
  return {
    id: requireApiOpaqueId(raw?.messageId, 'messageId'),
    seq,
    fromId: requireApiOpaqueId(raw?.fromUserId, 'fromUserId'),
    toId: requireApiOpaqueId(raw?.toUserId, 'toUserId'),
    content: String(raw?.content || ''),
    clientMsgId: String(raw?.clientMsgId ?? ''),
    createTime
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

function conversationMessageKeys(message) {
  const keys = []

  const seq = Number(message?.seq)
  if (Number.isSafeInteger(seq) && seq > 0) {
    keys.push(`seq:${seq}`)
  }

  const id = normalizeOpaqueId(message?.id)
  if (id) {
    keys.push(`id:${id}`)
  }

  const clientMsgId = String(message?.clientMsgId ?? '').trim()
  if (clientMsgId) {
    keys.push(`client:${clientMsgId}`)
  }

  if (keys.length === 0) {
    throw new Error('message identity 缺失')
  }
  return keys
}

export function mergeConversationMessages(currentItems, incomingItems) {
  const merged = []
  const active = []
  const identityIndexes = new Map()
  const identitiesByIndex = []

  const add = (message) => {
    const keys = conversationMessageKeys(message)
    const matches = new Set()
    for (const key of keys) {
      const index = identityIndexes.get(key)
      if (index !== undefined && active[index]) {
        matches.add(index)
      }
    }

    const index = matches.size > 0 ? Math.min(...matches) : merged.length
    if (index === merged.length) {
      merged.push(message)
      active.push(true)
      identitiesByIndex.push(new Set())
    } else {
      merged[index] = message
    }

    for (const matchedIndex of matches) {
      if (matchedIndex === index) continue
      active[matchedIndex] = false
      for (const key of identitiesByIndex[matchedIndex]) {
        identityIndexes.set(key, index)
        identitiesByIndex[index].add(key)
      }
    }

    for (const key of keys) {
      identityIndexes.set(key, index)
      identitiesByIndex[index].add(key)
    }
  }

  for (const message of Array.isArray(currentItems) ? currentItems : []) add(message)
  for (const message of Array.isArray(incomingItems) ? incomingItems : []) add(message)

  return merged.filter((_, index) => active[index]).sort(compareConversationMessages)
}

export function mergeConversations(currentItems, incomingItems) {
  const merged = []
  const indexes = new Map()

  const add = (conversation) => {
    const conversationId = normalizeOpaqueId(conversation?.conversationId)
    const index = indexes.get(conversationId)
    if (index === undefined) {
      indexes.set(conversationId, merged.length)
      merged.push(conversation)
    } else {
      merged[index] = conversation
    }
  }

  for (const conversation of Array.isArray(currentItems) ? currentItems : []) add(conversation)
  for (const conversation of Array.isArray(incomingItems) ? incomingItems : []) add(conversation)

  return merged
}

export function findLatestConversationSeq(items) {
  return (Array.isArray(items) ? items : []).reduce((maxSeq, item) => {
    return Math.max(maxSeq, Number(item?.seq || 0))
  }, 0)
}

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
  return withConversationMessageIdentity({
    id: requireApiOpaqueId(raw?.messageId, 'messageId'),
    seq,
    fromId: requireApiOpaqueId(raw?.fromUserId, 'fromUserId'),
    toId: requireApiOpaqueId(raw?.toUserId, 'toUserId'),
    content: String(raw?.content || ''),
    clientMsgId: String(raw?.clientMsgId ?? ''),
    createTime
  })
}

/**
 * @param {{ clientMsgId?: unknown, fromId?: unknown, toId?: unknown, content?: unknown, createTime?: number }} [message]
 */
export function createPendingConversationMessage({ clientMsgId, fromId, toId, content, createTime = Date.now() } = {}) {
  const clientId = String(clientMsgId || '').trim()
  if (!clientId) throw new Error('clientMsgId 缺失')
  return withConversationMessageIdentity({
    id: `pending:${clientId}`,
    seq: 0,
    fromId: requireApiOpaqueId(fromId, 'fromUserId'),
    toId: requireApiOpaqueId(toId, 'toUserId'),
    content: String(content || ''),
    clientMsgId: clientId,
    createTime: Number(createTime || Date.now()),
    deliveryState: 'pending'
  })
}

export function commitPendingConversationMessage(message, frame = {}) {
  const seq = Number(frame?.seq)
  if (!Number.isSafeInteger(seq) || seq <= 0) throw new Error('seq 非法')
  return withConversationMessageIdentity({
    ...(message || {}),
    id: requireApiOpaqueId(frame?.messageId, 'messageId'),
    seq,
    clientMsgId: String(frame?.clientMsgId || message?.clientMsgId || '').trim(),
    requestId: String(frame?.requestId || message?.requestId || '').trim(),
    deliveryState: 'committed'
  })
}

export function failPendingConversationMessage(message) {
  return { ...(message || {}), deliveryState: 'failed' }
}

export function advanceConversationSeqWaterline(currentWaterline, messages) {
  let waterline = Number(currentWaterline)
  if (!Number.isSafeInteger(waterline) || waterline < 0) waterline = 0

  const availableSeqs = new Set()
  for (const message of Array.isArray(messages) ? messages : []) {
    const seq = Number(message?.seq)
    if (Number.isSafeInteger(seq) && seq > waterline) availableSeqs.add(seq)
  }
  while (availableSeqs.has(waterline + 1)) waterline += 1
  return waterline
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

function collectConversationMessageIdentity(message) {
  const aliases = new Map()
  const source = message?.messageIdentity || {}

  const addSequence = (value) => {
    const sequence = Number(value)
    if (Number.isSafeInteger(sequence) && sequence > 0) {
      aliases.set(`sequence:${sequence}`, { field: 'sequences', value: sequence })
    }
  }
  const addServerMessageId = (value) => {
    const messageId = normalizeOpaqueId(value)
    if (messageId && !messageId.startsWith('pending:')) {
      aliases.set(`server:${messageId}`, { field: 'serverMessageIds', value: messageId })
    }
  }
  const addClientMessageId = (value) => {
    const clientMsgId = String(value?.clientMsgId ?? '').trim()
    const fromId = normalizeOpaqueId(value?.fromId)
    if (clientMsgId && fromId) {
      aliases.set(`client:${fromId}:${clientMsgId}`, {
        field: 'clientMessageIds',
        value: { fromId, clientMsgId }
      })
    }
  }
  const addRequestId = (value) => {
    const requestId = String(value ?? '').trim()
    if (requestId) aliases.set(`request:${requestId}`, { field: 'requestIds', value: requestId })
  }

  for (const value of Array.isArray(source.sequences) ? source.sequences : []) addSequence(value)
  for (const value of Array.isArray(source.serverMessageIds) ? source.serverMessageIds : []) addServerMessageId(value)
  for (const value of Array.isArray(source.clientMessageIds) ? source.clientMessageIds : []) addClientMessageId(value)
  for (const value of Array.isArray(source.requestIds) ? source.requestIds : []) addRequestId(value)

  addSequence(message?.seq)
  addServerMessageId(message?.id)
  addClientMessageId(message)
  addRequestId(message?.requestId)

  if (aliases.size === 0) throw new Error('message identity 缺失')
  return aliases
}

function toVisibleMessageIdentity(aliases) {
  const identity = /** @type {Record<string, any[]>} */ ({
    serverMessageIds: [],
    clientMessageIds: [],
    requestIds: [],
    sequences: []
  })
  for (const alias of aliases.values()) identity[alias.field].push(alias.value)
  return identity
}

function withConversationMessageIdentity(message) {
  return {
    ...message,
    messageIdentity: toVisibleMessageIdentity(collectConversationMessageIdentity(message))
  }
}

export function mergeConversationMessages(currentItems, incomingItems) {
  const records = new Set()
  const recordsByAlias = new Map()

  const add = (message) => {
    const aliases = collectConversationMessageIdentity(message)
    const matches = new Set()
    for (const key of aliases.keys()) {
      const record = recordsByAlias.get(key)
      if (record) matches.add(record)
    }

    const record = matches.values().next().value || { message, aliases: new Map() }
    if (matches.size === 0) records.add(record)

    for (const matchedRecord of matches) {
      if (matchedRecord === record) continue
      records.delete(matchedRecord)
      for (const [key, alias] of matchedRecord.aliases) record.aliases.set(key, alias)
    }

    for (const [key, alias] of aliases) record.aliases.set(key, alias)
    for (const key of record.aliases.keys()) recordsByAlias.set(key, record)

    const persistedMatch = Number(message?.seq) <= 0
      ? Array.from(matches).find((matchedRecord) => Number(matchedRecord.message?.seq) > 0)
      : null
    record.message = {
      ...(persistedMatch?.message || message),
      messageIdentity: toVisibleMessageIdentity(record.aliases)
    }
  }

  for (const message of Array.isArray(currentItems) ? currentItems : []) add(message)
  for (const message of Array.isArray(incomingItems) ? incomingItems : []) add(message)

  return Array.from(records, (record) => record.message).sort(compareConversationMessages)
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

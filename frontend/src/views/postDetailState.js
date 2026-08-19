import { normalizeOpaqueId } from '../utils/opaqueId'

export function collectThreadHydrationIds(items, { includeReplyToUserId = false } = {}) {
  const userIds = []
  const entityIds = []
  const seenUsers = new Set()
  const seenEntities = new Set()

  for (const item of Array.isArray(items) ? items : []) {
    const userId = normalizeOpaqueId(item?.userId)
    const entityId = normalizeOpaqueId(item?.id)
    const replyToUserId = includeReplyToUserId ? normalizeOpaqueId(item?.replyToUserId) : ''

    if (userId && !seenUsers.has(userId)) {
      seenUsers.add(userId)
      userIds.push(userId)
    }
    if (replyToUserId && !seenUsers.has(replyToUserId)) {
      seenUsers.add(replyToUserId)
      userIds.push(replyToUserId)
    }
    if (entityId && !seenEntities.has(entityId)) {
      seenEntities.add(entityId)
      entityIds.push(entityId)
    }
    if (userIds.length >= 200 && entityIds.length >= 200) break
  }

  return { userIds, entityIds }
}

function createLikeState(entityId, counts, statuses) {
  const count = counts?.[entityId]
  return {
    liked: !!statuses?.[entityId],
    count: typeof count === 'number' ? count : 0,
    loading: false,
    error: ''
  }
}

export function hydrateCommentItem(raw, { users = {}, counts = {}, statuses = {} } = {}) {
  const commentId = normalizeOpaqueId(raw?.id)
  const userId = normalizeOpaqueId(raw?.userId)

  return {
    ...raw,
    user: users?.[userId] || null,
    ui: {
      replyEditor: {
        open: false,
        draft: '',
        error: '',
        submitting: false,
        parentCommentId: '',
        quote: null
      },
      replyList: {
        expanded: false,
        items: [],
        page: 0,
        size: 5,
        nextCursor: '',
        cursorHistory: [''],
        loading: false,
        error: ''
      },
      like: createLikeState(commentId, counts, statuses)
    }
  }
}

export function hydrateReplyItem(raw, { users = {}, counts = {}, statuses = {} } = {}) {
  const replyId = normalizeOpaqueId(raw?.id)
  const userId = normalizeOpaqueId(raw?.userId)
  const replyToUserId = normalizeOpaqueId(raw?.replyToUserId)

  return {
    ...raw,
    user: users?.[userId] || null,
    replyToUserId,
    targetUserId: replyToUserId,
    targetUser: replyToUserId ? (users?.[replyToUserId] || null) : null,
    ui: {
      like: createLikeState(replyId, counts, statuses)
    }
  }
}

export function buildQuotePreview(text) {
  const normalized = String(text || '').replace(/\s+/g, ' ').trim()
  if (!normalized) return ''
  return normalized.length > 120 ? `${normalized.slice(0, 120)}…` : normalized
}

export function buildQuoteMarkdown(quote) {
  const raw = String(quote?.raw || '').trim()
  if (!raw) return ''

  const username = String(quote?.username || '').trim()
  const userId = normalizeOpaqueId(quote?.userId)
  const who = username ? `@${username}` : userId ? `成员 ${userId}` : '用户'
  const lines = raw
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(0, 6)

  const header = `> 引用 ${who}`
  const body = lines.map((line) => `> ${line}`).join('\n')
  return body ? `${header}\n${body}` : header
}

export function composeReplyContent(draft, quote) {
  const draftContent = String(draft || '').trim()
  const quoteMarkdown = quote ? buildQuoteMarkdown(quote) : ''
  if (!quoteMarkdown) return draftContent
  if (!draftContent) return quoteMarkdown
  return `${quoteMarkdown}\n\n${draftContent}`
}

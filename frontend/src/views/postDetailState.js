import { normalizeOpaqueId } from '../utils/opaqueId'

function collectHydrationIds(list, { includeTargetUserId = false } = {}) {
  const userIds = []
  const entityIds = []
  const seenUsers = new Set()
  const seenEntities = new Set()

  for (const item of Array.isArray(list) ? list : []) {
    const userId = normalizeOpaqueId(item?.userId)
    const entityId = normalizeOpaqueId(item?.id)
    const targetUserId = includeTargetUserId ? normalizeOpaqueId(item?.targetId) : ''

    if (userId && !seenUsers.has(userId)) {
      seenUsers.add(userId)
      userIds.push(userId)
    }
    if (targetUserId && !seenUsers.has(targetUserId)) {
      seenUsers.add(targetUserId)
      userIds.push(targetUserId)
    }
    if (entityId && !seenEntities.has(entityId)) {
      seenEntities.add(entityId)
      entityIds.push(entityId)
    }

    if (userIds.length >= 200 && entityIds.length >= 200) break
  }

  return { userIds, entityIds }
}

export function collectCommentHydrationIds(items) {
  return collectHydrationIds(items)
}

export function collectReplyHydrationIds(items) {
  return collectHydrationIds(items, { includeTargetUserId: true })
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

export function hydratePostComment(raw, { users = {}, counts = {}, statuses = {} } = {}) {
  const commentId = normalizeOpaqueId(raw?.id)
  const userId = normalizeOpaqueId(raw?.userId)
  const likeCount = counts?.[commentId]
  const liked = statuses?.[commentId]

  return {
    ...raw,
    user: users?.[userId] || null,
    likeCount: typeof likeCount === 'number' ? likeCount : 0,
    liked: !!liked,
    _likeLoading: false,

    _replying: false,
    _replyDraft: '',
    _replyError: '',
    _replySubmitting: false,
    _replyTargetId: '',
    _replyTargetUser: null,
    _replyQuote: null,

    _repliesExpanded: false,
    _replies: [],
    _repliesPage: 0,
    _repliesSize: 5,
    _repliesLoading: false,
    _repliesError: ''
  }
}

export function hydratePostReply(raw, { users = {}, counts = {}, statuses = {} } = {}) {
  const replyId = normalizeOpaqueId(raw?.id)
  const userId = normalizeOpaqueId(raw?.userId)
  const targetUserId = normalizeOpaqueId(raw?.targetId)
  const likeCount = counts?.[replyId]
  const liked = statuses?.[replyId]

  return {
    ...raw,
    user: users?.[userId] || null,
    targetUserId,
    targetUser: targetUserId ? (users?.[targetUserId] || null) : null,
    likeCount: typeof likeCount === 'number' ? likeCount : 0,
    liked: !!liked,
    _likeLoading: false
  }
}

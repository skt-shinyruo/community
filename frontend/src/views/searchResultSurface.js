import { hasOpaqueId, normalizeOpaqueId } from '../utils/opaqueId'

const SEARCH_ACTIVITY_PREVIEW_LIMIT = 72

function trimActivityCopy(value, limit = SEARCH_ACTIVITY_PREVIEW_LIMIT) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return ''
  if (text.length <= limit) return text
  return `${text.slice(0, Math.max(0, limit - 1)).trimEnd()}…`
}

export function collectSearchHydrationIds(items) {
  const list = Array.isArray(items) ? items : []
  const userIds = []
  const postIds = []
  const seenUsers = new Set()
  const seenPosts = new Set()

  for (const item of list) {
    const userId = normalizeOpaqueId(item?.userId)
    const lastReplyUserId = normalizeOpaqueId(item?.lastReplyUserId)
    const postId = normalizeOpaqueId(item?.postId)

    if (userId && !seenUsers.has(userId)) {
      seenUsers.add(userId)
      userIds.push(userId)
    }
    if (lastReplyUserId && !seenUsers.has(lastReplyUserId)) {
      seenUsers.add(lastReplyUserId)
      userIds.push(lastReplyUserId)
    }
    if (postId && !seenPosts.has(postId)) {
      seenPosts.add(postId)
      postIds.push(postId)
    }
  }

  return { userIds, postIds }
}

export function applySearchHydration(items, { users = {}, likeCounts = {} } = {}) {
  const list = Array.isArray(items) ? items : []
  return list.map((item) => {
    const userId = normalizeOpaqueId(item?.userId)
    const lastReplyUserId = normalizeOpaqueId(item?.lastReplyUserId)
    const postId = normalizeOpaqueId(item?.postId)
    return {
      ...item,
      author: userId ? (users?.[userId] || null) : null,
      lastReplyUser: lastReplyUserId ? (users?.[lastReplyUserId] || null) : null,
      likeCount: typeof likeCounts?.[postId] === 'number' ? likeCounts[postId] : 0
    }
  })
}

export function applySearchSummaries(items, summaries) {
  const list = Array.isArray(items) ? items : []
  const rows = Array.isArray(summaries) ? summaries : []
  const byId = new Map()
  for (const row of rows) {
    const id = normalizeOpaqueId(row?.id)
    if (id) byId.set(id, row)
  }

  return list.map((item) => {
    const summary = byId.get(normalizeOpaqueId(item?.postId))
    if (!summary) return { ...item }
    return {
      ...item,
      commentCount: Number(summary.commentCount || 0),
      lastActivityTime: summary.lastActivityTime || null,
      lastReplyUserId: hasOpaqueId(summary.lastReplyUserId) ? normalizeOpaqueId(summary.lastReplyUserId) : '',
      lastReplyPreview: summary.lastReplyPreview || ''
    }
  })
}

export function describeSearchActivity(item) {
  const preview = trimActivityCopy(item?.lastReplyPreview)
  const commentCount = Number(item?.commentCount || 0)
  const username = String(item?.lastReplyUser?.username || '').trim()

  if (preview) {
    return {
      label: username ? `最近回复 · ${username}` : '最近回复',
      copy: preview
    }
  }

  if (commentCount > 0) {
    return {
      label: username ? `最近回复 · ${username}` : '最近回复',
      copy: `这条讨论目前已有 ${commentCount} 条回复，打开后可继续沿着最新上下文阅读。`
    }
  }

  return null
}

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
    const userId = Number(item?.userId || 0)
    const lastReplyUserId = Number(item?.lastReplyUserId || 0)
    const postId = Number(item?.postId || 0)

    if (userId > 0 && !seenUsers.has(userId)) {
      seenUsers.add(userId)
      userIds.push(userId)
    }
    if (lastReplyUserId > 0 && !seenUsers.has(lastReplyUserId)) {
      seenUsers.add(lastReplyUserId)
      userIds.push(lastReplyUserId)
    }
    if (postId > 0 && !seenPosts.has(postId)) {
      seenPosts.add(postId)
      postIds.push(postId)
    }
  }

  return { userIds, postIds }
}

export function applySearchHydration(items, { users = {}, likeCounts = {} } = {}) {
  const list = Array.isArray(items) ? items : []
  return list.map((item) => {
    const userId = Number(item?.userId || 0)
    const lastReplyUserId = Number(item?.lastReplyUserId || 0)
    const postId = Number(item?.postId || 0)
    return {
      ...item,
      author: userId > 0 ? (users?.[userId] || null) : null,
      lastReplyUser: lastReplyUserId > 0 ? (users?.[lastReplyUserId] || null) : null,
      likeCount: typeof likeCounts?.[postId] === 'number' ? likeCounts[postId] : 0
    }
  })
}

export function applySearchSummaries(items, summaries) {
  const list = Array.isArray(items) ? items : []
  const rows = Array.isArray(summaries) ? summaries : []
  const byId = new Map()
  for (const row of rows) {
    const id = Number(row?.id || 0)
    if (id > 0) byId.set(id, row)
  }

  return list.map((item) => {
    const summary = byId.get(Number(item?.postId || 0))
    if (!summary) return { ...item }
    return {
      ...item,
      commentCount: Number(summary.commentCount || 0),
      lastActivityTime: summary.lastActivityTime || null,
      lastReplyUserId: Number(summary.lastReplyUserId || 0) || 0,
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

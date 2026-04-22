import { normalizeOpaqueId } from '../utils/opaqueId'

function toMs(value) {
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : 0
}

function usernameOf(usersById, userId) {
  const uid = normalizeOpaqueId(userId)
  if (!uid) return ''
  return usersById?.[uid]?.username || ''
}

function buildPostEntry(post, usersById) {
  const postId = normalizeOpaqueId(post?.id)
  if (!postId) return null
  const lastReplyName = usernameOf(usersById, post?.lastReplyUserId)
  const replyPreview = String(post?.lastReplyPreview || '').replace(/\s+/g, ' ').trim()
  const replyText = lastReplyName
    ? (replyPreview || `${lastReplyName} 最近回复 · ${Number(post?.commentCount || 0)} 条回复`)
    : `当前有 ${Number(post?.commentCount || 0)} 条回复，适合继续顺着线程往下读。`

  return {
    key: `post:${postId}`,
    kind: 'post',
    title: '发起了讨论',
    headline: post?.title || `帖子 ${postId}`,
    body: replyText,
    contextLabel: lastReplyName ? '最近回复' : '',
    contextUser: lastReplyName ? (usersById?.[normalizeOpaqueId(post?.lastReplyUserId)] || null) : null,
    timestamp: post?.lastActivityTime || post?.createTime || null,
    route: { name: 'postDetail', params: { postId: String(postId) } }
  }
}

function buildCommentEntry(comment, usersById) {
  const commentId = normalizeOpaqueId(comment?.id)
  const postId = normalizeOpaqueId(comment?.postId)
  if (!commentId || !postId) return null

  const isReply = Number(comment?.entityType || 0) === 2
  const query = isReply
    ? { commentId: String(comment?.entityId || ''), replyId: String(commentId) }
    : { commentId: String(commentId) }
  const targetName = usernameOf(usersById, comment?.targetId)

  return {
    key: `comment:${commentId}`,
    kind: 'comment',
    title: isReply ? `回复了 ${targetName || '一条评论'}` : '参与了讨论',
    headline: comment?.postTitle || `帖子 ${postId}`,
    body: comment?.content || '',
    contextLabel: isReply && targetName ? '回复对象' : '',
    contextUser: isReply && targetName ? (usersById?.[normalizeOpaqueId(comment?.targetId)] || null) : null,
    timestamp: comment?.createTime || null,
    route: { name: 'postDetail', params: { postId: String(postId) }, query }
  }
}

export function collectTimelineUserIds({ posts = [], comments = [] } = {}) {
  const ids = []
  const seen = new Set()

  for (const post of Array.isArray(posts) ? posts : []) {
    const userId = normalizeOpaqueId(post?.lastReplyUserId)
    if (userId && !seen.has(userId)) {
      seen.add(userId)
      ids.push(userId)
    }
  }

  for (const comment of Array.isArray(comments) ? comments : []) {
    const userId = normalizeOpaqueId(comment?.targetId)
    if (userId && !seen.has(userId)) {
      seen.add(userId)
      ids.push(userId)
    }
  }

  return ids
}

export function buildProfileTimeline({ posts = [], comments = [], usersById = {}, limit = 6 } = {}) {
  const items = [
    ...(Array.isArray(posts) ? posts.map((post) => buildPostEntry(post, usersById)).filter(Boolean) : []),
    ...(Array.isArray(comments) ? comments.map((comment) => buildCommentEntry(comment, usersById)).filter(Boolean) : [])
  ]

  return items
    .sort((a, b) => toMs(b?.timestamp) - toMs(a?.timestamp))
    .slice(0, Math.max(1, Number(limit || 0)))
}

import { normalizeOpaqueId } from '../utils/opaqueId'

/** @typedef {'latest' | 'hot'} PostsOrder */

export const POSTS_ORDER_LATEST = 'latest'
export const POSTS_ORDER_HOT = 'hot'
const POSTS_ORDERS = [POSTS_ORDER_LATEST, POSTS_ORDER_HOT]

/**
 * @param {unknown} value
 * @returns {PostsOrder}
 */
export function normalizePostsOrder(value) {
  const order = String(value || '').trim().toLowerCase()
  return /** @type {PostsOrder} */ (POSTS_ORDERS.includes(order) ? order : POSTS_ORDER_LATEST)
}

/**
 * @param {unknown} value
 * @returns {string}
 */
export function normalizePostsTag(value) {
  let tag = String(value || '').trim()
  if (tag.startsWith('#')) tag = tag.slice(1).trim()
  return tag
}

// 帖子流路由 query 是唯一事实源：categoryId / tag / order=latest|hot。
// boardId 已退役，旧链接的 boardId 在读取时归一为 categoryId。
/**
 * @param {Record<string, any>} [query]
 * @returns {{ categoryId: string, tag: string, order: PostsOrder }}
 */
export function parsePostsRouteQuery(query = {}) {
  return {
    categoryId: normalizeOpaqueId(query?.categoryId) || normalizeOpaqueId(query?.boardId),
    tag: normalizePostsTag(query?.tag),
    order: normalizePostsOrder(query?.order)
  }
}

/**
 * @param {Record<string, any>} [currentQuery]
 * @param {{ categoryId?: unknown, tag?: unknown, order?: unknown }} [changes]
 * @returns {Record<string, any>}
 */
export function serializePostsRouteQuery(currentQuery = {}, changes = {}) {
  const next = /** @type {Record<string, any>} */ ({ ...(currentQuery || {}) })
  const legacyBoardId = normalizeOpaqueId(next.boardId)
  delete next.boardId

  if (Object.prototype.hasOwnProperty.call(changes, 'categoryId')) {
    const categoryId = normalizeOpaqueId(changes.categoryId)
    if (categoryId) next.categoryId = String(categoryId)
    else delete next.categoryId
  } else if (legacyBoardId && next.categoryId === undefined) {
    next.categoryId = legacyBoardId
  }
  if (Object.prototype.hasOwnProperty.call(changes, 'tag')) {
    const tag = normalizePostsTag(changes.tag)
    if (tag) next.tag = tag
    else delete next.tag
  }
  if (Object.prototype.hasOwnProperty.call(changes, 'order')) {
    const order = normalizePostsOrder(changes.order)
    if (order === POSTS_ORDER_LATEST) delete next.order
    else next.order = order
  }

  return next
}

// 数据源映射：tag 过滤只有搜索栈支持（最终一致）；其余视图走游标 feed。
// 后端当前只暴露单一热度 rank feed，order=latest|hot 共用同一端点（见 handbook）。
/**
 * @param {{ categoryId?: unknown, tag?: unknown }} [query]
 * @returns {{ source: 'feed', scope: 'global' | 'category' } | { source: 'search' }}
 */
export function resolvePostsFeedPlan({ categoryId = '', tag = '' } = {}) {
  if (normalizePostsTag(tag)) return { source: 'search' }
  return {
    source: 'feed',
    scope: normalizeOpaqueId(categoryId) ? 'category' : 'global'
  }
}

const TAG_MAX = 5
const TAG_MAX_LEN = 20
const TAG_PATTERN = /^[\p{L}\p{N}_-]{1,20}$/u

function appendComposerTag(currentTags, raw) {
  let token = String(raw || '').trim()
  if (token.startsWith('#')) token = token.slice(1).trim()
  const nextToken = token
  if (!nextToken) return Array.isArray(currentTags) ? [...currentTags] : []

  if (nextToken.length > TAG_MAX_LEN) {
    throw new Error(`标签过长（单个标签最长 ${TAG_MAX_LEN}）`)
  }
  if (!TAG_PATTERN.test(nextToken)) {
    throw new Error('标签格式非法（仅允许中英文、数字、_、-）')
  }

  const list = Array.isArray(currentTags) ? [...currentTags] : []
  const key = nextToken.toLowerCase()
  const exists = list.some((item) => String(item || '').toLowerCase() === key)
  if (exists) return list

  if (list.length >= TAG_MAX) {
    throw new Error(`标签最多 ${TAG_MAX} 个`)
  }

  list.push(nextToken)
  return list
}

export function commitComposerTagDraft(currentTags, draft) {
  const rawDraft = String(draft || '').trim()
  if (!rawDraft) {
    return {
      tags: Array.isArray(currentTags) ? [...currentTags] : [],
      error: '',
      draft: ''
    }
  }

  try {
    let tags = Array.isArray(currentTags) ? [...currentTags] : []
    const parts = rawDraft
      .split(/[\s,，]+/g)
      .map((value) => String(value || '').trim())
      .filter(Boolean)

    for (const part of parts) {
      tags = appendComposerTag(tags, part)
    }

    return {
      tags,
      error: '',
      draft: ''
    }
  } catch (error) {
    return {
      tags: Array.isArray(currentTags) ? [...currentTags] : [],
      error: error?.message || '标签不合法',
      draft: rawDraft
    }
  }
}

export function collectPostsHydrationIds(list) {
  const userIds = []
  const postIds = []
  const seenUsers = new Set()
  const seenPosts = new Set()

  for (const post of Array.isArray(list) ? list : []) {
    const userId = normalizeOpaqueId(post?.userId)
    const lastReplyUserId = normalizeOpaqueId(post?.lastReplyUserId)
    const postId = normalizeOpaqueId(post?.id)

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

    if (userIds.length >= 200 && postIds.length >= 200) break
  }

  return { userIds, postIds }
}

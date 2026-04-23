import { normalizeOpaqueId } from '../utils/opaqueId'

const TAG_MAX = 5
const TAG_MAX_LEN = 20
const TAG_PATTERN = /^[\p{L}\p{N}_-]{1,20}$/u

export function normalizeComposerTagToken(raw) {
  let token = String(raw || '').trim()
  if (token.startsWith('#')) token = token.slice(1).trim()
  token = token.replaceAll(/\s+/g, '-').trim()
  return token
}

function appendComposerTag(currentTags, token) {
  const nextToken = normalizeComposerTagToken(token)
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

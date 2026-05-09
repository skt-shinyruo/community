// 帖子与评论相关 API：列表、详情、评论、回复。

import http from '../http'
import { unwrapResultBody } from '../result'
import { normalizeOpaqueId, normalizeOpaqueIds, requireOpaqueId } from '../../utils/opaqueId'

export async function listPosts({ order = 'latest', page = 0, size = 10, categoryId, tag, subscribed = false } = {}) {
  const params = { order, page, size }
  {
    const cid = normalizeOpaqueId(categoryId)
    if (cid) params.categoryId = cid
  }
  if (tag != null && String(tag).trim()) params.tag = String(tag).trim()
  if (subscribed === true) params.subscribed = true
  const resp = await http.get('/api/posts', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询帖子列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function createPost({ title, blocks, categoryId, tags } = {}) {
  const payload = { title, blocks: normalizeBlocks(blocks) }
  {
    const cid = normalizeOpaqueId(categoryId)
    if (cid) payload.categoryId = cid
  }
  if (Array.isArray(tags) && tags.length > 0) payload.tags = tags
  const resp = await http.post('/api/posts', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '发帖')
  return { data, traceId }
}

export async function batchPostSummaries(postIds) {
  const ids = normalizeOpaqueIds(postIds)
  const resp = await http.post('/api/posts/batch-summary', { postIds: ids })
  const { data, traceId } = unwrapResultBody(resp.data, '批量获取帖子摘要')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function getPostDetail(postId) {
  const resp = await http.get(`/api/posts/${postId}`)
  const { data, traceId } = unwrapResultBody(resp.data, '获取帖子详情')
  return { data, traceId }
}

export async function updatePost(postId, { title, blocks, categoryId, tags } = {}) {
  const pid = requireOpaqueId(postId, 'postId')
  const payload = { title, blocks: normalizeBlocks(blocks) }
  {
    const cid = normalizeOpaqueId(categoryId)
    if (cid) payload.categoryId = cid
  }
  if (Array.isArray(tags) && tags.length > 0) payload.tags = tags
  const resp = await http.put(`/api/posts/${pid}`, payload)
  const { traceId } = unwrapResultBody(resp.data, '编辑帖子')
  return { traceId }
}

export async function deletePostByAuthor(postId) {
  const pid = requireOpaqueId(postId, 'postId')
  const resp = await http.delete(`/api/posts/${pid}`)
  const { traceId } = unwrapResultBody(resp.data, '删除帖子')
  return { traceId }
}

export async function listComments(postId, { page = 0, size = 10 } = {}) {
  const resp = await http.get(`/api/posts/${postId}/comments`, { params: { page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询评论')
  const list = Array.isArray(data) ? data : []
  return { data: list.map(pickCommentFields), traceId }
}

export async function listReplies(postId, commentId, { page = 0, size = 10 } = {}) {
  const resp = await http.get(`/api/posts/${postId}/comments/${commentId}/replies`, { params: { page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询回复')
  const list = Array.isArray(data) ? data : []
  return { data: list.map(pickCommentFields), traceId }
}

function pickCommentFields(raw) {
  const r = raw || {}
  return {
    id: normalizeOpaqueId(r.id),
    userId: normalizeOpaqueId(r.userId),
    entityType: Number(r.entityType || 0),
    entityId: normalizeOpaqueId(r.entityId),
    targetId: normalizeOpaqueId(r.targetId),
    content: r.content == null ? '' : String(r.content),
    createTime: r.createTime,
    updateTime: r.updateTime,
    editCount: Number(r.editCount || 0)
  }
}

function normalizeBlocks(blocks) {
  return (Array.isArray(blocks) ? blocks : []).map((b) => {
    const raw = b || {}
    const block = {
      type: String(raw.type || '').trim(),
      text: raw.text == null ? '' : String(raw.text)
    }
    const assetId = normalizeOpaqueId(raw.assetId)
    if (assetId) block.assetId = assetId
    if (raw.language != null) block.language = String(raw.language)
    if (raw.caption != null) block.caption = String(raw.caption)
    if (raw.displayName != null) block.displayName = String(raw.displayName)
    if (raw.metadata && typeof raw.metadata === 'object') block.metadata = raw.metadata
    return block
  })
}

export async function addComment(postId, { content, entityType, entityId, targetId }) {
  const pid = requireOpaqueId(postId, 'postId')
  const payload = { content }
  if (entityType != null) payload.entityType = entityType
  {
    const commentId = normalizeOpaqueId(entityId)
    if (commentId) payload.entityId = commentId
  }
  {
    const replyToUserId = normalizeOpaqueId(targetId)
    if (replyToUserId) payload.targetId = replyToUserId
  }
  const resp = await http.post(`/api/posts/${pid}/comments`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '发表评论')
  return { data, traceId }
}

export async function updateComment(postId, commentId, { content } = {}) {
  const pid = requireOpaqueId(postId, 'postId')
  const cid = requireOpaqueId(commentId, 'commentId')
  const payload = { content }
  const resp = await http.put(`/api/posts/${pid}/comments/${cid}`, payload)
  const { traceId } = unwrapResultBody(resp.data, '编辑评论')
  return { traceId }
}

export async function moderationTop(postId) {
  const resp = await http.post(`/api/posts/${postId}/top`)
  const { traceId } = unwrapResultBody(resp.data, '置顶')
  return { traceId }
}

export async function moderationWonderful(postId) {
  const resp = await http.post(`/api/posts/${postId}/wonderful`)
  const { traceId } = unwrapResultBody(resp.data, '加精')
  return { traceId }
}

export async function moderationDelete(postId) {
  const resp = await http.post(`/api/posts/${postId}/delete`)
  const { traceId } = unwrapResultBody(resp.data, '删除')
  return { traceId }
}

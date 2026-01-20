// 帖子与评论相关 API：列表、详情、评论、回复。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function listPosts({ order = 'latest', page = 0, size = 10, categoryId, tag } = {}) {
  const params = { order, page, size }
  if (categoryId != null && Number(categoryId) > 0) params.categoryId = Number(categoryId)
  if (tag != null && String(tag).trim()) params.tag = String(tag).trim()
  const resp = await http.get('/api/posts', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询帖子列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function createPost({ title, content, categoryId, tags } = {}) {
  const payload = { title, content }
  if (categoryId != null && Number(categoryId) > 0) payload.categoryId = Number(categoryId)
  if (Array.isArray(tags) && tags.length > 0) payload.tags = tags
  const resp = await http.post('/api/posts', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '发帖')
  return { data, traceId }
}

export async function getPostDetail(postId) {
  const resp = await http.get(`/api/posts/${postId}`)
  const { data, traceId } = unwrapResultBody(resp.data, '获取帖子详情')
  return { data, traceId }
}

export async function listComments(postId, { page = 0, size = 10 } = {}) {
  const resp = await http.get(`/api/posts/${postId}/comments`, { params: { page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询评论')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listReplies(postId, commentId, { page = 0, size = 10 } = {}) {
  const resp = await http.get(`/api/posts/${postId}/comments/${commentId}/replies`, { params: { page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询回复')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function addComment(postId, { content, entityType, entityId, targetId }) {
  const payload = { content }
  if (entityType != null) payload.entityType = entityType
  if (entityId != null) payload.entityId = entityId
  if (targetId != null) payload.targetId = targetId
  const resp = await http.post(`/api/posts/${postId}/comments`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '发表评论')
  return { data, traceId }
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

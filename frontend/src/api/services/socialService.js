// 社交相关 API：点赞与关注。包含轻量缓存以降低 N+1 请求压力。

import http from '../http'
import { unwrapResultBody } from '../result'
import { normalizeOpaqueIds } from '../../utils/opaqueId'

const likeCountCache = new Map()
const likeStatusCache = new Map()
const followStatusCache = new Map()

const likeCountInflight = new Map()
const likeStatusInflight = new Map()
const followStatusInflight = new Map()

function likeKey(entityType, entityId) {
  return `${entityType}:${entityId}`
}

export async function setLike({ entityType, entityId, entityUserId, postId, liked }) {
  const resp = await http.post('/api/likes', { entityType, entityId, entityUserId, postId, liked })
  const { data, traceId } = unwrapResultBody(resp.data, '点赞')
  if (typeof data?.likeCount === 'number') {
    likeCountCache.set(likeKey(entityType, entityId), data.likeCount)
  }
  if (typeof data?.liked === 'boolean') {
    likeStatusCache.set(likeKey(entityType, entityId), data.liked)
  }
  return { data, traceId }
}

export async function getLikeCount(entityType, entityId, { force = false } = {}) {
  const k = likeKey(entityType, entityId)
  if (!force && likeCountCache.has(k)) {
    return { data: Number(likeCountCache.get(k) || 0), traceId: '' }
  }

  if (likeCountInflight.has(k)) {
    return likeCountInflight.get(k)
  }

  const p = (async () => {
    const resp = await http.get('/api/likes/count', { params: { entityType, entityId } })
    const { data, traceId } = unwrapResultBody(resp.data, '查询点赞数')
    likeCountCache.set(k, Number(data || 0))
    return { data: Number(data || 0), traceId }
  })()

  likeCountInflight.set(k, p)
  try {
    return await p
  } finally {
    if (likeCountInflight.get(k) === p) likeCountInflight.delete(k)
  }
}

export async function getLikeStatus(entityType, entityId, { force = false } = {}) {
  const k = likeKey(entityType, entityId)
  if (!force && likeStatusCache.has(k)) {
    return { data: !!likeStatusCache.get(k), traceId: '' }
  }

  if (likeStatusInflight.has(k)) {
    return likeStatusInflight.get(k)
  }

  const p = (async () => {
    const resp = await http.get('/api/likes/status', { params: { entityType, entityId } })
    const { data, traceId } = unwrapResultBody(resp.data, '查询点赞状态')
    likeStatusCache.set(k, !!data)
    return { data: !!data, traceId }
  })()

  likeStatusInflight.set(k, p)
  try {
    return await p
  } finally {
    if (likeStatusInflight.get(k) === p) likeStatusInflight.delete(k)
  }
}

export async function followUser(entityType, entityId, entityUserId) {
  const resp = await http.post('/api/follows', { entityType, entityId, entityUserId })
  const { traceId } = unwrapResultBody(resp.data, '关注')
  followStatusCache.set(likeKey(entityType, entityId), true)
  return { traceId }
}

export async function unfollowUser(entityType, entityId) {
  const resp = await http.delete('/api/follows', { params: { entityType, entityId } })
  const { traceId } = unwrapResultBody(resp.data, '取关')
  followStatusCache.set(likeKey(entityType, entityId), false)
  return { traceId }
}

export async function getFollowStatus(entityType, entityId, { force = false } = {}) {
  const k = likeKey(entityType, entityId)
  if (!force && followStatusCache.has(k)) {
    return { data: !!followStatusCache.get(k), traceId: '' }
  }

  if (followStatusInflight.has(k)) {
    return followStatusInflight.get(k)
  }

  const p = (async () => {
    const resp = await http.get('/api/follows/status', { params: { entityType, entityId } })
    const { data, traceId } = unwrapResultBody(resp.data, '查询关注状态')
    followStatusCache.set(k, !!data)
    return { data: !!data, traceId }
  })()

  followStatusInflight.set(k, p)
  try {
    return await p
  } finally {
    if (followStatusInflight.get(k) === p) followStatusInflight.delete(k)
  }
}

export async function listFollowees(userId, { page = 0, size = 10, entityType = 3 } = {}) {
  const resp = await http.get(`/api/follows/${userId}/followees`, { params: { page, size, entityType } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询关注列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listFollowers(userId, { page = 0, size = 10, entityType = 3 } = {}) {
  const resp = await http.get(`/api/follows/${userId}/followers`, { params: { page, size, entityType } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询粉丝列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

function normalizeEntityIds(entityIds, { max = 200 } = {}) {
  return normalizeOpaqueIds(entityIds, { max })
}

export async function getLikeCounts(entityType, entityIds) {
  const ids = normalizeEntityIds(entityIds)
  if (ids.length === 0) return { data: {}, traceId: '' }
  const resp = await http.get('/api/likes/counts', { params: { entityType, entityIds: ids.join(',') } })
  const { data, traceId } = unwrapResultBody(resp.data, '批量查询点赞数')
  return { data: data && typeof data === 'object' ? data : {}, traceId }
}

export async function getLikeStatuses(entityType, entityIds) {
  const ids = normalizeEntityIds(entityIds)
  if (ids.length === 0) return { data: {}, traceId: '' }
  const resp = await http.get('/api/likes/statuses', { params: { entityType, entityIds: ids.join(',') } })
  const { data, traceId } = unwrapResultBody(resp.data, '批量查询点赞状态')
  return { data: data && typeof data === 'object' ? data : {}, traceId }
}

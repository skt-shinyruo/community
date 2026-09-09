// 社交相关 API：点赞与关注。包含轻量缓存以降低 N+1 请求压力。

import http from '../http'
import { unwrapResultBody } from '../result'
import { normalizeOpaqueIds } from '../../utils/opaqueId'
import { useAuthStore } from '../../stores/auth'
import { identityScope } from '../../stores/identityScope'

const FOLLOW_CACHE_TTL_MS = 5 * 60 * 1000
const FOLLOW_CACHE_MAX_ENTRIES = 500

const followStatusCache = new Map()

const followStatusInflight = new Map()
let followCacheAuthStore = null
let followCacheScope = ''

function likeKey(entityType, entityId) {
  return `${entityType}:${entityId}`
}

function syncFollowCacheScope() {
  const auth = useAuthStore()
  const scope = identityScope(auth)
  if (followCacheAuthStore !== auth || followCacheScope !== scope) {
    followStatusCache.clear()
    followStatusInflight.clear()
    followCacheAuthStore = auth
    followCacheScope = scope
  }
  return scope
}

function scopedFollowKey(scope, entityType, entityId) {
  return `${scope}:${likeKey(entityType, entityId)}`
}

function touchFollowEntry(key, entry) {
  followStatusCache.delete(key)
  followStatusCache.set(key, entry)
}

function readCachedFollowStatus(key) {
  const entry = followStatusCache.get(key)
  if (!entry) return null
  if (entry.expiresAt <= Date.now()) {
    followStatusCache.delete(key)
    return null
  }
  touchFollowEntry(key, entry)
  return entry.value
}

function writeCachedFollowStatus(key, value) {
  const now = Date.now()
  touchFollowEntry(key, { value, updatedAt: now, expiresAt: now + FOLLOW_CACHE_TTL_MS })
  while (followStatusCache.size > FOLLOW_CACHE_MAX_ENTRIES) {
    followStatusCache.delete(followStatusCache.keys().next().value)
  }
}

// 后写获胜：晚于查询发起时间写入的缓存项（即变更结果）不得被该查询的响应覆盖。
function followQueryMayWrite(key, startedAt) {
  const entry = followStatusCache.get(key)
  if (!entry || entry.expiresAt <= Date.now()) return true
  return entry.updatedAt < startedAt
}

export async function setLike({ entityType, entityId, liked }) {
  const resp = await http.post('/api/likes', { entityType, entityId, liked })
  return unwrapResultBody(resp.data, '点赞')
}

export async function followUser(entityType, entityId) {
  const cacheScope = syncFollowCacheScope()
  const cacheKey = scopedFollowKey(cacheScope, entityType, entityId)
  const resp = await http.post('/api/follows', { entityType, entityId })
  const { traceId } = unwrapResultBody(resp.data, '关注')
  if (syncFollowCacheScope() === cacheScope) {
    writeCachedFollowStatus(cacheKey, true)
  }
  return { traceId }
}

export async function unfollowUser(entityType, entityId) {
  const cacheScope = syncFollowCacheScope()
  const cacheKey = scopedFollowKey(cacheScope, entityType, entityId)
  const resp = await http.delete('/api/follows', { params: { entityType, entityId } })
  const { traceId } = unwrapResultBody(resp.data, '取关')
  if (syncFollowCacheScope() === cacheScope) {
    writeCachedFollowStatus(cacheKey, false)
  }
  return { traceId }
}

export async function getFollowStatus(entityType, entityId, { force = false } = {}) {
  const cacheScope = syncFollowCacheScope()
  const k = scopedFollowKey(cacheScope, entityType, entityId)
  if (!force) {
    const cached = readCachedFollowStatus(k)
    if (cached !== null) return { data: cached, traceId: '' }
  }

  if (followStatusInflight.has(k)) {
    return followStatusInflight.get(k)
  }

  const startedAt = Date.now()
  const p = (async () => {
    const resp = await http.get('/api/follows/status', { params: { entityType, entityId } })
    const { data, traceId } = unwrapResultBody(resp.data, '查询关注状态')
    if (syncFollowCacheScope() !== cacheScope) {
      return getFollowStatus(entityType, entityId, { force: true })
    }
    if (!followQueryMayWrite(k, startedAt)) {
      return { data: readCachedFollowStatus(k) ?? !!data, traceId }
    }
    writeCachedFollowStatus(k, !!data)
    return { data: !!data, traceId }
  })()

  followStatusInflight.set(k, p)
  try {
    return await p
  } finally {
    if (followStatusInflight.get(k) === p) followStatusInflight.delete(k)
  }
}

export async function getFollowStatuses(entityType, entityIds, { force = false } = {}) {
  const ids = normalizeOpaqueIds(entityIds, { max: 200 })
  if (ids.length === 0) return { data: {}, traceId: '' }
  const cacheScope = syncFollowCacheScope()

  const requestedIds = force
    ? ids
    : ids.filter((entityId) => readCachedFollowStatus(scopedFollowKey(cacheScope, entityType, entityId)) === null)
  let traceId = ''
  if (requestedIds.length > 0) {
    const startedAt = Date.now()
    const resp = await http.get('/api/follows/statuses', {
      params: { entityType, entityIds: requestedIds.join(',') }
    })
    const result = unwrapResultBody(resp.data, '批量查询关注状态')
    if (!result.data || typeof result.data !== 'object' || Array.isArray(result.data)) {
      throw new Error('批量查询关注状态响应非法')
    }
    if (syncFollowCacheScope() !== cacheScope) {
      return getFollowStatuses(entityType, ids, { force: true })
    }
    traceId = typeof result.traceId === 'string' ? result.traceId : ''
    for (const entityId of requestedIds) {
      const k = scopedFollowKey(cacheScope, entityType, entityId)
      if (followQueryMayWrite(k, startedAt)) {
        writeCachedFollowStatus(k, result.data[entityId] === true)
      }
    }
  }

  return {
    data: Object.fromEntries(ids.map((entityId) => [
      entityId,
      readCachedFollowStatus(scopedFollowKey(cacheScope, entityType, entityId)) === true
    ])),
    traceId
  }
}

export async function listFollowees(userId, { cursor = '', size = 10, entityType = 3 } = {}) {
  const resp = await http.get(`/api/follows/${userId}/followees/page`, { params: { cursor, size, entityType } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询关注列表')
  return { data: normalizeFollowRelationPage(data), traceId }
}

export async function listFollowers(userId, { cursor = '', size = 10, entityType = 3 } = {}) {
  const resp = await http.get(`/api/follows/${userId}/followers/page`, { params: { cursor, size, entityType } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询粉丝列表')
  return { data: normalizeFollowRelationPage(data), traceId }
}

function normalizeFollowRelationPage(data) {
  const page = data && typeof data === 'object' && !Array.isArray(data) ? data : {}
  const nextCursor = page.nextCursor == null ? '' : String(page.nextCursor)
  return {
    items: Array.isArray(page.items) ? page.items : [],
    nextCursor,
    hasNext: page.hasNext === true && nextCursor.length > 0
  }
}

export async function getLikeCounts(entityType, entityIds) {
  const ids = normalizeOpaqueIds(entityIds, { max: 200 })
  if (ids.length === 0) return { data: {}, traceId: '' }
  const resp = await http.get('/api/likes/counts', { params: { entityType, entityIds: ids.join(',') } })
  const { data, traceId } = unwrapResultBody(resp.data, '批量查询点赞数')
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('批量查询点赞数响应非法')
  }
  return { data, traceId }
}

export async function getLikeStatuses(entityType, entityIds) {
  const ids = normalizeOpaqueIds(entityIds, { max: 200 })
  if (ids.length === 0) return { data: {}, traceId: '' }
  const resp = await http.get('/api/likes/statuses', { params: { entityType, entityIds: ids.join(',') } })
  const { data, traceId } = unwrapResultBody(resp.data, '批量查询点赞状态')
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('批量查询点赞状态响应非法')
  }
  return { data, traceId }
}

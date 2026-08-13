// 用户相关 API：用户主页信息与用户摘要。

import http from '../http'
import { unwrapResultBody } from '../result'
import { normalizeOpaqueId, normalizeOpaqueIds, requireOpaqueId } from '../../utils/opaqueId'

const USER_CACHE_TTL_MS = 5 * 60 * 1000
const USER_CACHE_MAX_ENTRIES = 100
const userCache = new Map()
const userInflight = new Map()

function touchCacheEntry(userId, entry) {
  userCache.delete(userId)
  userCache.set(userId, entry)
}

function readCachedProfile(userId) {
  const entry = userCache.get(userId)
  if (!entry) return null
  if (entry.expiresAt <= Date.now()) {
    userCache.delete(userId)
    return null
  }
  touchCacheEntry(userId, entry)
  return entry.value
}

function writeCachedProfile(userId, value) {
  touchCacheEntry(userId, { value, expiresAt: Date.now() + USER_CACHE_TTL_MS })
  while (userCache.size > USER_CACHE_MAX_ENTRIES) {
    userCache.delete(userCache.keys().next().value)
  }
}

export function invalidateUserProfile(userId) {
  const uid = requireOpaqueId(userId, 'userId')
  userCache.delete(uid)
  const activeRequest = userInflight.get(uid)
  if (activeRequest) activeRequest.cacheable = false
}

export function clearUserProfileCache() {
  userCache.clear()
  for (const activeRequest of userInflight.values()) activeRequest.cacheable = false
}

function optionalNumber(value) {
  const next = Number(value)
  return Number.isFinite(next) ? next : null
}

function normalizeUserLevelProfileFields(raw) {
  const userLevel = optionalNumber(raw?.userLevel)
  const signInDaysInWindow = optionalNumber(raw?.signInDaysInWindow)
  const hasCompleteUserLevelData = userLevel !== null && signInDaysInWindow !== null

  const userLevelEnabled = raw?.userLevelEnabled === true
  const showUserLevel = userLevelEnabled && hasCompleteUserLevelData

  return {
    userLevel,
    signInDaysInWindow,
    userLevelEnabled,
    showUserLevel
  }
}

export async function getUserProfile(userId, { force = false } = {}) {
  const uid = requireOpaqueId(userId, 'userId')
  if (!force) {
    const cached = readCachedProfile(uid)
    if (cached) return cached
  }

  const activeRequest = userInflight.get(uid)
  if (!force && activeRequest?.cacheable) {
    return activeRequest.promise
  }
  if (force && activeRequest) activeRequest.cacheable = false

  const request = /** @type {{ cacheable: boolean, promise: Promise<any> | null }} */ ({
    cacheable: true,
    promise: null
  })
  const p = (async () => {
    const resp = await http.get(`/api/users/${uid}`)
    const { data, traceId } = unwrapResultBody(resp.data, '获取用户信息')
    const profile = data && typeof data === 'object' && !Array.isArray(data) ? data : {}
    const value = {
      ...profile,
      ...normalizeUserLevelProfileFields(data),
      _traceId: traceId
    }
    if (request.cacheable) {
      writeCachedProfile(uid, value)
    }
    return value
  })()

  request.promise = p
  userInflight.set(uid, request)
  try {
    return await p
  } finally {
    if (userInflight.get(uid) === request) userInflight.delete(uid)
  }
}

export async function listUserRecentPosts(userId, { page = 0, size = 3 } = {}) {
  const uid = requireOpaqueId(userId, 'userId')
  const resp = await http.get(`/api/users/${uid}/recent-posts`, { params: { page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '获取用户最近帖子')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listUserRecentComments(userId, { page = 0, size = 3 } = {}) {
  const uid = requireOpaqueId(userId, 'userId')
  const resp = await http.get(`/api/users/${uid}/recent-comments`, { params: { page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '获取用户最近评论')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function batchUserSummary(userIds) {
  const dedup = normalizeOpaqueIds(userIds)

  if (dedup.length === 0) {
    return { data: [], traceId: '' }
  }

  const resp = await http.post('/api/users/batch-summary', { userIds: dedup })
  const { data, traceId } = unwrapResultBody(resp.data, '批量用户摘要')
  const list = Array.isArray(data) ? data : []
  return {
    data: list.map((item) => ({
      ...item,
      id: normalizeOpaqueId(item?.id)
    })),
    traceId
  }
}

// 用户相关 API：用户主页信息与按用户名解析用户。

import http from '../http'
import { unwrapResultBody } from '../result'
import { normalizeOpaqueId, normalizeOpaqueIds, requireOpaqueId } from '../../utils/opaqueId'

const userCache = new Map()
const userInflight = new Map()

function optionalNumber(value) {
  const next = Number(value)
  return Number.isFinite(next) ? next : null
}

function normalizeUserLevelProfileFields(raw) {
  const userLevel = optionalNumber(raw?.userLevel)
  const signInDaysInWindow = optionalNumber(raw?.signInDaysInWindow)
  const hasCompleteUserLevelData = userLevel !== null && signInDaysInWindow !== null

  const explicitEnabled = raw?.userLevelEnabled === true ? true : (raw?.userLevelEnabled === false ? false : null)
  const showUserLevel = explicitEnabled === false ? false : hasCompleteUserLevelData

  return {
    userLevel,
    signInDaysInWindow,
    userLevelEnabled: explicitEnabled === null ? showUserLevel : explicitEnabled,
    showUserLevel
  }
}

export async function getUserProfile(userId, { force = false } = {}) {
  const uid = requireOpaqueId(userId, 'userId')
  if (!force && userCache.has(uid)) {
    return userCache.get(uid)
  }

  if (userInflight.has(uid)) {
    return userInflight.get(uid)
  }

  const p = (async () => {
    const resp = await http.get(`/api/users/${uid}`)
    const { data, traceId } = unwrapResultBody(resp.data, '获取用户信息')
    const value = {
      ...data,
      ...normalizeUserLevelProfileFields(data),
      _traceId: traceId
    }
    userCache.set(uid, value)
    return value
  })()

  userInflight.set(uid, p)
  try {
    return await p
  } finally {
    if (userInflight.get(uid) === p) userInflight.delete(uid)
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

export async function resolveUserByUsername(username) {
  const resp = await http.get('/api/users/resolve', { params: { username } })
  const { data, traceId } = unwrapResultBody(resp.data, '按用户名查询用户')
  return { ...data, _traceId: traceId }
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

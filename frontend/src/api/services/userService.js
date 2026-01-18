// 用户相关 API：用户主页信息与按用户名解析用户。

import http from '../http'
import { unwrapResultBody } from '../result'

const userCache = new Map()

export async function getUserProfile(userId, { force = false } = {}) {
  const uid = Number(userId || 0)
  if (!force && userCache.has(uid)) {
    return userCache.get(uid)
  }

  const resp = await http.get(`/api/users/${uid}`)
  const { data, traceId } = unwrapResultBody(resp.data, '获取用户信息')
  const value = { ...data, _traceId: traceId }
  userCache.set(uid, value)
  return value
}

export async function resolveUserByUsername(username) {
  const resp = await http.get('/api/users/resolve', { params: { username } })
  const { data, traceId } = unwrapResultBody(resp.data, '按用户名查询用户')
  return { ...data, _traceId: traceId }
}


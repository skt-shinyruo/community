import { defineStore } from 'pinia'
import { batchUserSummary } from '../api/services/userService'
import { getLikeCounts, getLikeStatuses } from '../api/services/socialService'
import { normalizeOpaqueId, normalizeOpaqueIds } from '../utils/opaqueId'

// TTL 约定：
// - 用户摘要：变化相对不频繁，可缓存更久
// - 点赞计数/状态：为了减少“写后立即刷新看到旧投影”的感知不一致，TTL 保持更短
const USER_TTL_MS = 60 * 1000
const LIKE_TTL_MS = 30 * 1000

function nowMs() {
  return Date.now()
}

function normalizeIds(ids, { max = 200 } = {}) {
  return normalizeOpaqueIds(ids, { max })
}

function isFresh(entry) {
  return entry && typeof entry.expiresAt === 'number' && entry.expiresAt > nowMs()
}

function likeKey(entityType, entityId) {
  return `${Number(entityType || 0)}:${normalizeOpaqueId(entityId)}`
}

export const usePostMetaCacheStore = defineStore('postMetaCache', {
  state: () => ({
    users: {}, // id -> { value, expiresAt }
    likeCounts: {}, // "entityType:entityId" -> { value, expiresAt }
    likeStatuses: {} // "entityType:entityId" -> { value, expiresAt } (与登录态相关，建议在 auth 变化时清理)
  }),
  actions: {
    clearLikeStatuses() {
      this.likeStatuses = {}
    },
    clearAll() {
      this.users = {}
      this.likeCounts = {}
      this.likeStatuses = {}
    },

    getUser(id) {
      const uid = normalizeOpaqueId(id)
      if (!uid) return null
      const entry = this.users[uid]
      if (isFresh(entry)) return entry.value
      return null
    },
    getLikeCount(entityType, entityId) {
      const k = likeKey(entityType, entityId)
      const entry = this.likeCounts[k]
      if (isFresh(entry)) return Number(entry.value || 0)
      return null
    },
    getLikeStatus(entityType, entityId) {
      const k = likeKey(entityType, entityId)
      const entry = this.likeStatuses[k]
      if (isFresh(entry)) return !!entry.value
      return null
    },
    setLikeCount(entityType, entityId, value) {
      const k = likeKey(entityType, entityId)
      this.likeCounts[k] = { value: Number(value || 0), expiresAt: nowMs() + LIKE_TTL_MS }
    },
    setLikeStatus(entityType, entityId, value) {
      const k = likeKey(entityType, entityId)
      this.likeStatuses[k] = { value: !!value, expiresAt: nowMs() + LIKE_TTL_MS }
    },

    async ensureUserSummaries(userIds) {
      const ids = normalizeIds(userIds)
      if (ids.length === 0) return {}

      const missing = []
      for (const id of ids) {
        if (!isFresh(this.users[id])) missing.push(id)
      }

      if (missing.length > 0) {
        const { data } = await batchUserSummary(missing)
        const t = nowMs()
        if (Array.isArray(data)) {
          for (const u of data) {
            const id = normalizeOpaqueId(u?.id)
            if (!id) continue
            this.users[id] = { value: u, expiresAt: t + USER_TTL_MS }
          }
        }
      }

      const out = {}
      for (const id of ids) {
        const entry = this.users[id]
        if (isFresh(entry)) out[id] = entry.value
      }
      return out
    },

    async ensureLikeCounts(entityType, entityIds) {
      const ids = normalizeIds(entityIds)
      if (ids.length === 0) return {}

      const missing = []
      for (const id of ids) {
        const k = likeKey(entityType, id)
        if (!isFresh(this.likeCounts[k])) missing.push(id)
      }

      if (missing.length > 0) {
        const { data } = await getLikeCounts(entityType, missing)
        const t = nowMs()
        for (const id of missing) {
          if (!Object.prototype.hasOwnProperty.call(data || {}, String(id))) {
            throw new Error(`点赞数缺少实体 ${id}`)
          }
          const v = Number(data[String(id)])
          if (!Number.isFinite(v)) {
            throw new Error(`点赞数非法 ${id}`)
          }
          this.likeCounts[likeKey(entityType, id)] = { value: v, expiresAt: t + LIKE_TTL_MS }
        }
      }

      const out = {}
      for (const id of ids) {
        const v = this.getLikeCount(entityType, id)
        if (typeof v === 'number') out[id] = v
      }
      return out
    },

    async ensureLikeStatuses(entityType, entityIds) {
      const ids = normalizeIds(entityIds)
      if (ids.length === 0) return {}

      const missing = []
      for (const id of ids) {
        const k = likeKey(entityType, id)
        if (!isFresh(this.likeStatuses[k])) missing.push(id)
      }

      if (missing.length > 0) {
        const { data } = await getLikeStatuses(entityType, missing)
        const t = nowMs()
        for (const id of missing) {
          if (!Object.prototype.hasOwnProperty.call(data || {}, String(id))) {
            throw new Error(`点赞状态缺少实体 ${id}`)
          }
          const raw = data[String(id)]
          if (typeof raw !== 'boolean') {
            throw new Error(`点赞状态非法 ${id}`)
          }
          const v = raw
          this.likeStatuses[likeKey(entityType, id)] = { value: v, expiresAt: t + LIKE_TTL_MS }
        }
      }

      const out = {}
      for (const id of ids) {
        const v = this.getLikeStatus(entityType, id)
        if (typeof v === 'boolean') out[id] = v
      }
      return out
    }
  }
})

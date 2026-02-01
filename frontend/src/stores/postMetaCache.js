import { defineStore } from 'pinia'
import { batchUserSummary } from '../api/services/userService'
import { getLikeCounts, getLikeStatuses } from '../api/services/socialService'

const TTL_MS = 60 * 1000

function nowMs() {
  return Date.now()
}

function normalizeIds(ids, { max = 200 } = {}) {
  const raw = Array.isArray(ids) ? ids : []
  const out = []
  const seen = new Set()
  for (const id of raw) {
    const v = Number(id || 0)
    if (!v || v <= 0) continue
    if (seen.has(v)) continue
    seen.add(v)
    out.push(v)
    if (out.length >= max) break
  }
  return out
}

function isFresh(entry) {
  return entry && typeof entry.expiresAt === 'number' && entry.expiresAt > nowMs()
}

function likeKey(entityType, entityId) {
  return `${Number(entityType || 0)}:${Number(entityId || 0)}`
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
      const uid = Number(id || 0)
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
      this.likeCounts[k] = { value: Number(value || 0), expiresAt: nowMs() + TTL_MS }
    },
    setLikeStatus(entityType, entityId, value) {
      const k = likeKey(entityType, entityId)
      this.likeStatuses[k] = { value: !!value, expiresAt: nowMs() + TTL_MS }
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
            const id = Number(u?.id || 0)
            if (!id) continue
            this.users[id] = { value: u, expiresAt: t + TTL_MS }
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
          const v = Number(data?.[String(id)] || 0)
          this.likeCounts[likeKey(entityType, id)] = { value: v, expiresAt: t + TTL_MS }
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
          const v = !!data?.[String(id)]
          this.likeStatuses[likeKey(entityType, id)] = { value: v, expiresAt: t + TTL_MS }
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

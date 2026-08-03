// socialPrefs：拉黑列表 + 订阅列表（前端读侧过滤 & UI 状态用）。

import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import { listBlockedUsers } from '../api/services/blockService'
import { listSubscribedCategories } from '../api/services/subscriptionService'
import { normalizeOpaqueId } from '../utils/opaqueId'

function identityScope(auth) {
  return `${Number(auth?.tokenGeneration || 0)}:${normalizeOpaqueId(auth?.userId)}`
}

export const useSocialPrefsStore = defineStore('socialPrefs', {
  state: () => ({
    blockedUserIds: [],
    blockedLoaded: false,
    blockedScope: '',
    blockedRequestId: 0,
    subscribedCategoryIds: [],
    subscribedLoaded: false,
    subscribedScope: '',
    subscribedRequestId: 0
  }),
  getters: {
    blockedSet: (s) =>
      new Set((Array.isArray(s.blockedUserIds) ? s.blockedUserIds : []).map((x) => normalizeOpaqueId(x)).filter(Boolean)),
    subscribedCategorySet: (s) =>
      new Set((Array.isArray(s.subscribedCategoryIds) ? s.subscribedCategoryIds : []).map((x) => normalizeOpaqueId(x)).filter(Boolean))
  },
  actions: {
    clear() {
      this.blockedUserIds = []
      this.blockedLoaded = false
      this.blockedScope = ''
      this.blockedRequestId += 1
      this.subscribedCategoryIds = []
      this.subscribedLoaded = false
      this.subscribedScope = ''
      this.subscribedRequestId += 1
    },

    async ensureBlocked(force = false) {
      const auth = useAuthStore()
      const requestScope = identityScope(auth)
      if (this.blockedScope !== requestScope) {
        this.blockedUserIds = []
        this.blockedLoaded = false
        this.blockedScope = requestScope
      }
      if (!auth.authed) {
        this.blockedUserIds = []
        this.blockedLoaded = false
        return
      }
      if (this.blockedLoaded && !force) return

      const requestId = ++this.blockedRequestId
      const resp = await listBlockedUsers()
      const currentScope = identityScope(useAuthStore())
      if (currentScope !== requestScope) {
        return this.ensureBlocked(false)
      }
      if (requestId !== this.blockedRequestId) return
      this.blockedUserIds = Array.isArray(resp?.data) ? resp.data : []
      this.blockedLoaded = true
    },

    async ensureSubscribedCategories(force = false) {
      const auth = useAuthStore()
      const requestScope = identityScope(auth)
      if (this.subscribedScope !== requestScope) {
        this.subscribedCategoryIds = []
        this.subscribedLoaded = false
        this.subscribedScope = requestScope
      }
      if (!auth.authed) {
        this.subscribedCategoryIds = []
        this.subscribedLoaded = false
        return
      }
      if (this.subscribedLoaded && !force) return

      const requestId = ++this.subscribedRequestId
      const resp = await listSubscribedCategories()
      const currentScope = identityScope(useAuthStore())
      if (currentScope !== requestScope) {
        return this.ensureSubscribedCategories(false)
      }
      if (requestId !== this.subscribedRequestId) return
      this.subscribedCategoryIds = Array.isArray(resp?.data) ? resp.data : []
      this.subscribedLoaded = true
    }
  }
})

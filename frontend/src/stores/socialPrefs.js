// socialPrefs：拉黑列表 + 订阅列表（前端读侧过滤 & UI 状态用）。

import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import { listBlockedUsers } from '../api/services/blockService'
import { listSubscribedCategories } from '../api/services/subscriptionService'

export const useSocialPrefsStore = defineStore('socialPrefs', {
  state: () => ({
    blockedUserIds: [],
    blockedLoaded: false,
    subscribedCategoryIds: [],
    subscribedLoaded: false
  }),
  getters: {
    blockedSet: (s) => new Set((Array.isArray(s.blockedUserIds) ? s.blockedUserIds : []).map((x) => Number(x || 0)).filter((x) => x > 0)),
    subscribedCategorySet: (s) =>
      new Set((Array.isArray(s.subscribedCategoryIds) ? s.subscribedCategoryIds : []).map((x) => Number(x || 0)).filter((x) => x > 0))
  },
  actions: {
    clear() {
      this.blockedUserIds = []
      this.blockedLoaded = false
      this.subscribedCategoryIds = []
      this.subscribedLoaded = false
    },

    async ensureBlocked(force = false) {
      const auth = useAuthStore()
      if (!auth.authed) {
        this.blockedUserIds = []
        this.blockedLoaded = false
        return
      }
      if (this.blockedLoaded && !force) return

      const resp = await listBlockedUsers()
      this.blockedUserIds = Array.isArray(resp?.data) ? resp.data : []
      this.blockedLoaded = true
    },

    async ensureSubscribedCategories(force = false) {
      const auth = useAuthStore()
      if (!auth.authed) {
        this.subscribedCategoryIds = []
        this.subscribedLoaded = false
        return
      }
      if (this.subscribedLoaded && !force) return

      const resp = await listSubscribedCategories()
      this.subscribedCategoryIds = Array.isArray(resp?.data) ? resp.data : []
      this.subscribedLoaded = true
    }
  }
})


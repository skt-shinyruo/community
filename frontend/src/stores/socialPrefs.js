// socialPrefs：拉黑列表（前端读侧过滤与 UI 状态用）。

import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import { identityScope } from './identityScope'
import { listBlockedUsers } from '../api/services/blockService'
import { normalizeOpaqueId } from '../utils/opaqueId'

export const useSocialPrefsStore = defineStore('socialPrefs', {
  state: () => ({
    blockedUserIds: /** @type {unknown[]} */ ([]),
    blockedLoaded: false,
    blockedScope: '',
    blockedRequestId: 0
  }),
  getters: {
    blockedSet: (s) =>
      new Set((Array.isArray(s.blockedUserIds) ? s.blockedUserIds : []).map((x) => normalizeOpaqueId(x)).filter(Boolean))
  },
  actions: {
    clear() {
      this.blockedUserIds = []
      this.blockedLoaded = false
      this.blockedScope = ''
      this.blockedRequestId += 1
    },

    async ensureBlocked(force = false, { silent = false } = {}) {
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
      const resp = await listBlockedUsers({ silent })
      const currentScope = identityScope(useAuthStore())
      if (currentScope !== requestScope) {
        return this.ensureBlocked(false, { silent })
      }
      if (requestId !== this.blockedRequestId) return
      this.blockedUserIds = Array.isArray(resp?.data) ? resp.data : []
      this.blockedLoaded = true
    }
  }
})

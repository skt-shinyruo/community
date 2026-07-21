import { defineStore } from 'pinia'
import { clearSessionHint, setSessionHint } from '../auth/sessionHint'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: '',
    me: null,
    tokenGeneration: 0
  }),
  getters: {
    authed: (s) => !!s.accessToken,
    userId: (s) => s.me?.userId ?? 0,
    username: (s) => s.me?.username ?? '',
    authorities: (s) => (Array.isArray(s.me?.authorities) ? s.me.authorities : []),
    isAdmin: (s) => (Array.isArray(s.me?.authorities) ? s.me.authorities.includes('ROLE_ADMIN') : false),
    isModerator: (s) => (Array.isArray(s.me?.authorities) ? s.me.authorities.includes('ROLE_MODERATOR') : false),
    isAdminOrModerator() {
      return this.isAdmin || this.isModerator
    }
  },
  actions: {
    setAccessToken(token) {
      const nextToken = token || ''
      if (!nextToken) {
        this.clear()
        return
      }
      if (this.accessToken !== nextToken) {
        this.accessToken = nextToken
        this.tokenGeneration += 1
      }
      setSessionHint()
    },
    installSession({ accessToken, me } = {}) {
      const nextToken = accessToken || ''
      if (!nextToken) {
        this.clear()
        return
      }
      if (this.accessToken !== nextToken) {
        this.accessToken = nextToken
        this.tokenGeneration += 1
      }
      if (me !== undefined) {
        this.me = me || null
      }
      setSessionHint()
    },
    setMe(me) {
      this.me = me || null
    },
    clear() {
      const hadSession = !!this.accessToken || this.me !== null
      this.accessToken = ''
      this.me = null
      if (hadSession) {
        this.tokenGeneration += 1
      }
      clearSessionHint()
    }
  }
})

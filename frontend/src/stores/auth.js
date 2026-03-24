import { defineStore } from 'pinia'
import { clearSessionHint, setSessionHint } from '../auth/sessionHint'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: '',
    me: null
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
      this.accessToken = token || ''
      // 登录态变化后，me 需要重新拉取；这里先清空，交由调用方刷新。
      this.me = null
      if (this.accessToken) {
        setSessionHint()
      } else {
        clearSessionHint()
      }
    },
    setMe(me) {
      this.me = me || null
    },
    clear() {
      this.accessToken = ''
      this.me = null
      clearSessionHint()
    }
  }
})

import { defineStore } from 'pinia'

const SESSION_HINT_KEY = 'community.session.hint'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: '',
    me: /** @type {null | { userId?: any, username?: string, authorities?: string[], [key: string]: any }} */ (null),
    identityState: 'anonymous',
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
    /** @param {{ accessToken?: string, me?: any }} [session] */
    installSession({ accessToken, me } = {}) {
      const nextToken = accessToken || ''
      if (!nextToken) {
        this.clear()
        return
      }
      if (this.accessToken !== nextToken) {
        this.accessToken = nextToken
        this.me = null
        this.identityState = 'unresolved'
        this.tokenGeneration += 1
      }
      if (me !== undefined) {
        this.me = me || null
        this.identityState = this.me ? 'resolved' : 'unresolved'
      }
      try {
        globalThis.localStorage?.setItem(SESSION_HINT_KEY, '1')
      } catch {
        // Best-effort only.
      }
    },
    setMe(me) {
      this.me = me || null
      this.identityState = this.accessToken && this.me ? 'resolved' : (this.accessToken ? 'unresolved' : 'anonymous')
    },
    clear() {
      const hadSession = !!this.accessToken || this.me !== null
      this.accessToken = ''
      this.me = null
      this.identityState = 'anonymous'
      if (hadSession) {
        this.tokenGeneration += 1
      }
      try {
        globalThis.localStorage?.removeItem(SESSION_HINT_KEY)
      } catch {
        // Best-effort only.
      }
    }
  }
})

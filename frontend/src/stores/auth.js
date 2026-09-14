import { defineStore } from 'pinia'
import { SESSION_HINT_KEY } from '../auth/sessionHint'
import { normalizeOpaqueId } from '../utils/opaqueId'

// 已解析身份只在真实身份切换时推进 identityEpoch：access token 轮换（静默刷新）
// 不属于身份切换，identityUserId 在轮换期间的 me=null 窗口内保持不变。
function syncResolvedIdentity(auth, me) {
  const resolvedId = normalizeOpaqueId(me?.userId)
  if (!resolvedId) return
  if (auth.identityUserId && auth.identityUserId !== resolvedId) {
    auth.identityEpoch += 1
  }
  auth.identityUserId = resolvedId
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: '',
    me: /** @type {null | { userId?: any, username?: string, authorities?: string[], [key: string]: any }} */ (null),
    identityState: 'anonymous',
    tokenGeneration: 0,
    identityEpoch: 0,
    identityUserId: ''
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
      syncResolvedIdentity(this, this.me)
      try {
        globalThis.localStorage?.setItem(SESSION_HINT_KEY, '1')
      } catch {
        // Best-effort only.
      }
    },
    setMe(me) {
      this.me = me || null
      this.identityState = this.accessToken && this.me ? 'resolved' : (this.accessToken ? 'unresolved' : 'anonymous')
      syncResolvedIdentity(this, this.me)
    },
    clear() {
      const hadSession = !!this.accessToken || this.me !== null
      this.accessToken = ''
      this.me = null
      this.identityState = 'anonymous'
      if (hadSession) {
        this.tokenGeneration += 1
        this.identityEpoch += 1
        this.identityUserId = ''
      }
      try {
        globalThis.localStorage?.removeItem(SESSION_HINT_KEY)
      } catch {
        // Best-effort only.
      }
    }
  }
})

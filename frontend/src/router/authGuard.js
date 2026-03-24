// 路由守卫：处理登录态与页面级权限（体验层），最终权限仍以后端为准。

import { ensureSessionReady } from '../auth/session'
import { useAuthStore } from '../stores/auth'

export async function authGuard(to) {
  const auth = useAuthStore()

  const requiresAuth = !!to.meta?.requiresAuth
  const roles = Array.isArray(to.meta?.roles) ? to.meta.roles : []
  const shouldResolveSession = requiresAuth || roles.length > 0 || to.name === 'login'

  if (shouldResolveSession) {
    const session = await ensureSessionReady({ auth })
    if (to.name === 'login') {
      if (session.state === 'ready' || (session.state === 'error' && auth.accessToken)) {
        return { name: 'posts' }
      }
      return
    }
    if (session.state === 'anonymous') {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    if (session.state === 'error' && roles.length > 0) {
      return false
    }
  }

  if (roles.length === 0) {
    return
  }

  const authorities = auth.authorities || []
  const ok = roles.some((r) => authorities.includes(r))
  if (!ok) {
    return { name: 'forbidden' }
  }
}

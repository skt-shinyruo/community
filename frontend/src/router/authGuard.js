// 路由守卫：处理登录态与页面级权限（体验层），最终权限仍以后端为准。

import { useAuthStore } from '../stores/auth'
import { me as fetchMe } from '../api/services/authService'

export async function authGuard(to) {
  const auth = useAuthStore()

  const requiresAuth = !!to.meta?.requiresAuth
  if (requiresAuth && !auth.accessToken) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.name === 'login' && auth.accessToken) {
    return { name: 'posts' }
  }

  const roles = Array.isArray(to.meta?.roles) ? to.meta.roles : []
  if (roles.length === 0) {
    return
  }

  // 需要角色的页面：尝试懒加载 me 信息
  if (!auth.me && auth.accessToken) {
    try {
      const { data } = await fetchMe()
      auth.setMe(data)
    } catch {
      // 忽略：让后端返回 401/403；UI 会在页面中给出提示
    }
  }

  const authorities = auth.authorities || []
  const ok = roles.some((r) => authorities.includes(r))
  if (!ok) {
    return { name: 'forbidden' }
  }
}

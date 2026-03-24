import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { authGuard } from './authGuard'
import { ensureSessionReady } from '../auth/session'
import { useAuthStore } from '../stores/auth'

vi.mock('../auth/session', () => ({
  ensureSessionReady: vi.fn()
}))

describe('authGuard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('should redirect to login when route requires auth and no token', async () => {
    const auth = useAuthStore()
    auth.clear()
    ensureSessionReady.mockResolvedValue({ state: 'anonymous' })

    const to = { name: 'dev', fullPath: '/dev', meta: { requiresAuth: true } }
    const result = await authGuard(to)
    expect(result).toEqual({ name: 'login', query: { redirect: '/dev' } })
  })

  it('should allow when route requires auth and token exists', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('t1')
    ensureSessionReady.mockResolvedValue({ state: 'ready' })

    const to = { name: 'dev', fullPath: '/dev', meta: { requiresAuth: true } }
    const result = await authGuard(to)
    expect(result).toBeUndefined()
  })

  it('should redirect away from login when already logged in', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('t1')
    ensureSessionReady.mockResolvedValue({ state: 'ready' })

    const to = { name: 'login', meta: {} }
    const result = await authGuard(to)
    expect(result).toEqual({ name: 'posts' })
  })

  it('should not redirect to forbidden when loading authorities fails', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('t1')
    ensureSessionReady.mockResolvedValue({ state: 'error' })

    const to = {
      name: 'growthAdmin',
      fullPath: '/admin/growth',
      meta: { requiresAuth: true, roles: ['ROLE_ADMIN'] }
    }
    const result = await authGuard(to)
    expect(result).toBe(false)
  })

  it('should allow auth-only route when profile loading is temporarily unavailable', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('t1')
    ensureSessionReady.mockResolvedValue({ state: 'error' })

    const to = {
      name: 'settings',
      fullPath: '/settings',
      meta: { requiresAuth: true }
    }
    const result = await authGuard(to)
    expect(result).toBeUndefined()
  })
})

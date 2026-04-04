import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { authGuard } from './authGuard'
import { ensureSessionReady, shouldBootstrapSession } from '../auth/session'
import { useAuthStore } from '../stores/auth'

vi.mock('../auth/session', () => ({
  ensureSessionReady: vi.fn(),
  shouldBootstrapSession: vi.fn()
}))

describe('authGuard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    shouldBootstrapSession.mockReturnValue(false)
  })

  it('should attempt silent restore for protected routes even when there is no token or session hint', async () => {
    const auth = useAuthStore()
    auth.clear()
    ensureSessionReady.mockResolvedValue({ state: 'anonymous' })

    const to = { name: 'dev', fullPath: '/dev', meta: { requiresAuth: true } }
    const result = await authGuard(to)
    expect(result).toEqual({ name: 'login', query: { redirect: '/dev' } })
    expect(ensureSessionReady).toHaveBeenCalledTimes(1)
  })

  it('should attempt session restore for protected routes when a previous session hint exists', async () => {
    const auth = useAuthStore()
    auth.clear()
    shouldBootstrapSession.mockReturnValue(true)
    ensureSessionReady.mockResolvedValue({ state: 'anonymous' })

    const to = { name: 'notices', fullPath: '/notices', meta: { requiresAuth: true } }
    const result = await authGuard(to)
    expect(result).toEqual({ name: 'login', query: { redirect: '/notices' } })
    expect(ensureSessionReady).toHaveBeenCalledTimes(1)
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
    shouldBootstrapSession.mockReturnValue(true)
    ensureSessionReady.mockResolvedValue({ state: 'ready' })

    const to = { name: 'login', meta: {} }
    const result = await authGuard(to)
    expect(result).toEqual({ name: 'posts' })
  })

  it('should not attempt session restore on login when there is no token and no session hint', async () => {
    const auth = useAuthStore()
    auth.clear()

    const to = { name: 'login', meta: {} }
    const result = await authGuard(to)
    expect(result).toBeUndefined()
    expect(ensureSessionReady).not.toHaveBeenCalled()
  })

  it('should not redirect to forbidden when loading authorities fails', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('t1')
    ensureSessionReady.mockResolvedValue({ state: 'error' })

    const to = {
      name: 'walletAdmin',
      fullPath: '/admin/wallet',
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

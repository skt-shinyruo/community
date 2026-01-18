import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { authGuard } from './authGuard'
import { useAuthStore } from '../stores/auth'

describe('authGuard', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should redirect to login when route requires auth and no token', async () => {
    const auth = useAuthStore()
    auth.clear()

    const to = { name: 'dev', fullPath: '/dev', meta: { requiresAuth: true } }
    const result = await authGuard(to)
    expect(result).toEqual({ name: 'login', query: { redirect: '/dev' } })
  })

  it('should allow when route requires auth and token exists', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('t1')

    const to = { name: 'dev', fullPath: '/dev', meta: { requiresAuth: true } }
    const result = await authGuard(to)
    expect(result).toBeUndefined()
  })

  it('should redirect away from login when already logged in', async () => {
    const auth = useAuthStore()
    auth.setAccessToken('t1')

    const to = { name: 'login', meta: {} }
    const result = await authGuard(to)
    expect(result).toEqual({ name: 'posts' })
  })
})

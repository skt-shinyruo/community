import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useAuthStore } from './auth'

describe('auth store session generations', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
  })

  it('advances generation only when a non-empty access token changes', () => {
    const auth = useAuthStore()

    expect(auth.tokenGeneration).toBe(0)

    auth.setAccessToken('token-1')
    expect(auth.tokenGeneration).toBe(1)

    auth.setAccessToken('token-1')
    expect(auth.tokenGeneration).toBe(1)

    auth.setAccessToken('token-2')
    expect(auth.tokenGeneration).toBe(2)
  })

  it('keeps the current profile while a refreshed token is being installed', () => {
    const auth = useAuthStore()
    const currentProfile = { userId: 7, username: 'alice' }
    auth.setMe(currentProfile)

    auth.setAccessToken('refreshed-token')

    expect(auth.me).toEqual(currentProfile)
  })

  it('installs token and profile together with explicit profile semantics', () => {
    const auth = useAuthStore()
    const oldProfile = { userId: 7, username: 'alice' }
    const newProfile = { userId: 8, username: 'bob' }
    auth.setAccessToken('token-1')
    auth.setMe(oldProfile)

    auth.installSession({ accessToken: 'token-2', me: newProfile })

    expect(auth.accessToken).toBe('token-2')
    expect(auth.me).toEqual(newProfile)
    expect(auth.tokenGeneration).toBe(2)
    expect(window.localStorage.getItem('community.session.hint')).toBe('1')

    auth.installSession({ accessToken: 'token-2', me: undefined })
    expect(auth.me).toEqual(newProfile)
    expect(auth.tokenGeneration).toBe(2)

    auth.installSession({ accessToken: 'token-2', me: null })
    expect(auth.me).toBeNull()
    expect(auth.tokenGeneration).toBe(2)
  })

  it('clears an effective session once and invalidates older async work', () => {
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-1',
      me: { userId: 7, username: 'alice' }
    })

    auth.clear()

    expect(auth.accessToken).toBe('')
    expect(auth.me).toBeNull()
    expect(auth.tokenGeneration).toBe(2)
    expect(window.localStorage.getItem('community.session.hint')).toBeNull()

    auth.clear()
    expect(auth.tokenGeneration).toBe(2)
  })

  it('treats an empty token as an explicit clear', () => {
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-1',
      me: { userId: 7, username: 'alice' }
    })

    auth.setAccessToken('')

    expect(auth.accessToken).toBe('')
    expect(auth.me).toBeNull()
    expect(auth.tokenGeneration).toBe(2)
  })
})

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useAuthStore } from './auth'
import { identityScope } from './identityScope'

describe('auth store session generations', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
  })

  it('advances generation only when a non-empty access token changes', () => {
    const auth = useAuthStore()

    expect(auth.tokenGeneration).toBe(0)

    auth.installSession({ accessToken: 'token-1' })
    expect(auth.tokenGeneration).toBe(1)

    auth.installSession({ accessToken: 'token-1' })
    expect(auth.tokenGeneration).toBe(1)

    auth.installSession({ accessToken: 'token-2' })
    expect(auth.tokenGeneration).toBe(2)
  })

  it('clears the current profile when a different token is installed', () => {
    const auth = useAuthStore()
    const currentProfile = { userId: 7, username: 'alice' }
    auth.setMe(currentProfile)

    auth.installSession({ accessToken: 'refreshed-token' })

    expect(auth.me).toBeNull()
    expect(auth.identityState).toBe('unresolved')
  })

  it('installs token and profile together with explicit profile semantics', () => {
    const auth = useAuthStore()
    const oldProfile = { userId: 7, username: 'alice' }
    const newProfile = { userId: 8, username: 'bob' }
    auth.installSession({ accessToken: 'token-1' })
    auth.setMe(oldProfile)

    auth.installSession({ accessToken: 'token-2', me: newProfile })

    expect(auth.accessToken).toBe('token-2')
    expect(auth.me).toEqual(newProfile)
    expect(auth.identityState).toBe('resolved')
    expect(auth.tokenGeneration).toBe(2)
    expect(window.localStorage.getItem('community.session.hint')).toBe('1')

    auth.installSession({ accessToken: 'token-2', me: undefined })
    expect(auth.me).toEqual(newProfile)
    expect(auth.tokenGeneration).toBe(2)

    auth.installSession({ accessToken: 'token-2', me: null })
    expect(auth.me).toBeNull()
    expect(auth.identityState).toBe('unresolved')
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
    expect(auth.identityState).toBe('anonymous')
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

    auth.installSession({ accessToken: '' })

    expect(auth.accessToken).toBe('')
    expect(auth.me).toBeNull()
    expect(auth.tokenGeneration).toBe(2)
  })
})

describe('auth store identity scope', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
  })

  it('stays stable across access token rotation, including the unresolved profile window', () => {
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-1',
      me: { userId: 7, username: 'alice' }
    })
    const scopeBefore = identityScope(auth)

    auth.installSession({ accessToken: 'rotated-token', me: null })
    expect(auth.tokenGeneration).toBe(2)
    expect(auth.me).toBeNull()
    expect(identityScope(auth)).toBe(scopeBefore)

    auth.setMe({ userId: 7, username: 'alice' })
    expect(identityScope(auth)).toBe(scopeBefore)
  })

  it('changes on logout even when the same account logs back in', () => {
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-1',
      me: { userId: 7, username: 'alice' }
    })
    const scopeBefore = identityScope(auth)

    auth.clear()
    expect(identityScope(auth)).not.toBe(scopeBefore)
    const anonymousScope = identityScope(auth)

    auth.installSession({
      accessToken: 'token-2',
      me: { userId: 7, username: 'alice' }
    })
    expect(identityScope(auth)).not.toBe(anonymousScope)
    expect(identityScope(auth)).not.toBe(scopeBefore)
  })

  it('changes when a session is replaced by a different account in place', () => {
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-a',
      me: { userId: 7, username: 'alice' }
    })
    const scopeBefore = identityScope(auth)

    auth.installSession({
      accessToken: 'token-b',
      me: { userId: 8, username: 'bob' }
    })

    expect(identityScope(auth)).not.toBe(scopeBefore)
  })

  it('changes when a rotated session resolves to a different account', () => {
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-a',
      me: { userId: 7, username: 'alice' }
    })
    const scopeBefore = identityScope(auth)

    auth.installSession({ accessToken: 'token-b', me: null })
    expect(identityScope(auth)).toBe(scopeBefore)

    auth.setMe({ userId: 8, username: 'bob' })
    expect(identityScope(auth)).not.toBe(scopeBefore)
  })

  it('resolves from anonymous exactly once when the profile arrives', () => {
    const auth = useAuthStore()
    const anonymousScope = identityScope(auth)

    auth.installSession({ accessToken: 'token-1', me: null })
    expect(identityScope(auth)).toBe(anonymousScope)

    auth.setMe({ userId: 7, username: 'alice' })
    const resolvedScope = identityScope(auth)
    expect(resolvedScope).not.toBe(anonymousScope)

    auth.installSession({ accessToken: 'token-1', me: { userId: 7, username: 'alice' } })
    expect(identityScope(auth)).toBe(resolvedScope)
  })
})

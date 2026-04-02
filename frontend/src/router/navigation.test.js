import { describe, expect, it } from 'vitest'

import {
  POSTS_FILTER,
  POSTS_ORDER,
  buildPostsQuery,
  canAccessNavItem,
  getMobileNavigation,
  getSidebarNavigation,
  isNavItemActive,
  normalizePostsFilter,
  normalizePostsOrder,
  normalizePostsQuery
} from './navigation'

describe('router/navigation', () => {
  it('normalizePostsOrder should normalize invalid values', () => {
    expect(normalizePostsOrder()).toBe(POSTS_ORDER.LATEST)
    expect(normalizePostsOrder('latest')).toBe(POSTS_ORDER.LATEST)
    expect(normalizePostsOrder('hot')).toBe(POSTS_ORDER.HOT)
    expect(normalizePostsOrder('unknown')).toBe(POSTS_ORDER.LATEST)
  })

  it('normalizePostsFilter should normalize invalid values', () => {
    expect(normalizePostsFilter()).toBe(POSTS_FILTER.ALL)
    expect(normalizePostsFilter('unread')).toBe(POSTS_FILTER.UNREAD)
    expect(normalizePostsFilter('top')).toBe(POSTS_FILTER.TOP)
    expect(normalizePostsFilter('wonderful')).toBe(POSTS_FILTER.WONDERFUL)
    expect(normalizePostsFilter('unknown')).toBe(POSTS_FILTER.ALL)
  })

  it('normalizePostsQuery should parse order/type from query', () => {
    expect(normalizePostsQuery({ order: 'hot', type: 'top' })).toEqual({
      order: POSTS_ORDER.HOT,
      filter: POSTS_FILTER.TOP,
      categoryId: 0,
      tag: '',
      subscribed: false
    })

    expect(normalizePostsQuery({ order: 'unknown', type: 'unknown' })).toEqual({
      order: POSTS_ORDER.LATEST,
      filter: POSTS_FILTER.ALL,
      categoryId: 0,
      tag: '',
      subscribed: false
    })
  })

  it('buildPostsQuery should omit defaults', () => {
    expect(buildPostsQuery()).toEqual({})
    expect(buildPostsQuery({ order: 'latest', filter: '' })).toEqual({})
    expect(buildPostsQuery({ order: 'hot', filter: '' })).toEqual({ order: 'hot' })
    expect(buildPostsQuery({ order: 'latest', filter: 'top' })).toEqual({ type: 'top' })
    expect(buildPostsQuery({ order: 'hot', filter: 'wonderful' })).toEqual({ order: 'hot', type: 'wonderful' })
    expect(buildPostsQuery({ subscribed: true })).toEqual({ subscribed: '1' })
  })

  it('canAccessNavItem should enforce auth and roles', () => {
    expect(canAccessNavItem({ requiresAuth: true }, { authed: false })).toBe(false)
    expect(canAccessNavItem({ requiresAuth: true }, { authed: true })).toBe(true)

    expect(canAccessNavItem({ hideWhenAuthed: true }, { authed: true })).toBe(false)
    expect(canAccessNavItem({ hideWhenAuthed: true }, { authed: false })).toBe(true)

    expect(canAccessNavItem({ roles: ['ROLE_ADMIN'] }, { authed: true, roles: ['ROLE_USER'] })).toBe(false)
    expect(canAccessNavItem({ roles: ['ROLE_ADMIN'] }, { authed: true, roles: ['ROLE_ADMIN'] })).toBe(true)
  })

  it('getSidebarNavigation should expose the approved desktop explore and me groups', () => {
    const anon = getSidebarNavigation({ authed: false })
    const anonGroupKeys = anon.map((g) => g.key)
    expect(anonGroupKeys).toEqual(['explore', 'auth'])

    const explore = anon.find((g) => g.key === 'explore')
    expect(explore?.items.map((it) => it.key)).toEqual(['posts', 'search'])

    const authItem = anon.flatMap((g) => g.items).find((it) => it.key === 'login')
    expect(authItem?.activeNames || []).not.toContain('activation')

    const authed = getSidebarNavigation({ authed: true, userId: 12, roles: ['ROLE_USER'] })
    const authedGroupKeys = authed.map((g) => g.key)
    expect(authedGroupKeys).toEqual(['explore', 'me'])

    const me = authed.find((g) => g.key === 'me')
    expect(me?.items.map((it) => it.key)).toEqual([
      'wallet',
      'bookmarks',
      'notices',
      'messages',
      'profile',
      'settings'
    ])

    const profile = me?.items.find((it) => it.key === 'profile')
    expect(profile?.to).toEqual({ name: 'userProfile', params: { userId: '12' } })
  })

  it('isNavItemActive should keep parent items active across route families', () => {
    const nav = getSidebarNavigation({ authed: true, userId: 1, roles: ['ROLE_USER'] })
    const allItems = nav.flatMap((g) => g.items)

    const posts = allItems.find((it) => it.key === 'posts')
    const wallet = allItems.find((it) => it.key === 'wallet')
    const notices = allItems.find((it) => it.key === 'notices')
    const messages = allItems.find((it) => it.key === 'messages')
    const profile = allItems.find((it) => it.key === 'profile')

    expect(isNavItemActive({ name: 'posts', query: {} }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { type: 'top' } }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { subscribed: '1' } }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'postDetail', query: { type: 'top' } }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'wallet' }, wallet)).toBe(true)
    expect(isNavItemActive({ name: 'noticeDetail' }, notices)).toBe(true)
    expect(isNavItemActive({ name: 'messageDetail' }, messages)).toBe(true)
    expect(isNavItemActive({ name: 'followees' }, profile)).toBe(true)
    expect(isNavItemActive({ name: 'followers' }, profile)).toBe(true)
  })

  it('getMobileNavigation should keep wallet visible while removing growth and leaderboard shortcuts', () => {
    const anon = getMobileNavigation({ authed: false })
    expect(anon.map((it) => it.key)).toEqual(['posts', 'search', 'me', 'more'])

    const anonMe = anon.find((it) => it.key === 'me')
    const anonMore = anon.find((it) => it.key === 'more')
    expect(anonMe?.to).toEqual({ name: 'login' })
    expect(anonMore?.to).toEqual({ name: 'wallet' })
    expect(isNavItemActive({ name: 'login' }, anonMe)).toBe(true)
    expect(isNavItemActive({ name: 'register' }, anonMe)).toBe(true)
    expect(isNavItemActive({ name: 'wallet' }, anonMore)).toBe(true)
    expect(isNavItemActive({ name: 'messageDetail' }, anonMe)).toBe(true)
    expect(isNavItemActive({ name: 'followers' }, anonMe)).toBe(true)
    expect(isNavItemActive({ name: 'leaderboard' }, anonMe)).toBe(false)
    expect(isNavItemActive({ name: 'leaderboard' }, anonMore)).toBe(false)

    const authed = getMobileNavigation({ authed: true, userId: 8 })
    expect(authed.map((it) => it.key)).toEqual(['posts', 'search', 'me', 'more'])
    expect(authed.map((it) => it.key)).not.toEqual(['posts', 'search', 'growth', 'messages', 'profile'])

    const authedMe = authed.find((it) => it.key === 'me')
    const authedMore = authed.find((it) => it.key === 'more')
    expect(authedMe?.to).toEqual({ name: 'userProfile', params: { userId: '8' } })
    expect(authedMore?.to).toEqual({ name: 'wallet' })
    expect(isNavItemActive({ name: 'wallet' }, authedMore)).toBe(true)
    expect(isNavItemActive({ name: 'messageDetail' }, authedMe)).toBe(true)
    expect(isNavItemActive({ name: 'followers' }, authedMe)).toBe(true)
    expect(isNavItemActive({ name: 'leaderboard' }, authedMe)).toBe(false)
    expect(isNavItemActive({ name: 'messages' }, authedMore)).toBe(false)

    const authedWithoutUserId = getMobileNavigation({ authed: true, userId: 0 })
    expect(authedWithoutUserId.map((it) => it.key)).toEqual(['posts', 'search', 'me', 'more'])

    const pendingHydrationMe = authedWithoutUserId.find((it) => it.key === 'me')
    expect(pendingHydrationMe?.to).toEqual({ name: 'wallet' })
    expect(pendingHydrationMe?.to).not.toEqual({ name: 'login' })
  })
})

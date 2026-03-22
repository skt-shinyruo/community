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

  it('getSidebarNavigation should filter groups by ctx', () => {
    const anon = getSidebarNavigation({ authed: false })
    const anonKeys = anon.flatMap((g) => g.items.map((it) => it.key))
    expect(anonKeys).toContain('posts')
    expect(anonKeys).toContain('search')
    expect(anonKeys).toContain('login')
    expect(anonKeys).not.toContain('messages')
    expect(anonKeys).not.toContain('profile')
    expect(anonKeys).not.toContain('analytics')

    const authed = getSidebarNavigation({ authed: true, userId: 12, roles: ['ROLE_USER'] })
    const authedKeys = authed.flatMap((g) => g.items.map((it) => it.key))
    expect(authedKeys).toContain('messages')
    expect(authedKeys).toContain('profile')
    expect(authedKeys).not.toContain('login')

    const profile = authed.flatMap((g) => g.items).find((it) => it.key === 'profile')
    expect(profile?.to).toEqual({ name: 'userProfile', params: { userId: '12' } })
  })

  it('isNavItemActive should match posts filter states', () => {
    const nav = getSidebarNavigation({ authed: true, userId: 1, roles: ['ROLE_USER'] })
    const allItems = nav.flatMap((g) => g.items)

    const posts = allItems.find((it) => it.key === 'posts')
    const unread = allItems.find((it) => it.key === 'postsUnread')
    const subscribed = allItems.find((it) => it.key === 'postsSubscribed')
    const top = allItems.find((it) => it.key === 'postsTop')
    const wonderful = allItems.find((it) => it.key === 'postsWonderful')

    expect(isNavItemActive({ name: 'posts', query: {} }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { type: 'top' } }, posts)).toBe(false)
    expect(isNavItemActive({ name: 'posts', query: { type: 'unread' } }, unread)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { subscribed: '1' } }, subscribed)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { type: 'top' } }, top)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { type: 'wonderful' } }, wonderful)).toBe(true)
    expect(isNavItemActive({ name: 'postDetail', query: { type: 'top' } }, posts)).toBe(true)
  })

  it('getMobileNavigation should keep only core items', () => {
    const anon = getMobileNavigation({ authed: false })
    const anonKeys = anon.map((it) => it.key)
    expect(anonKeys).toEqual(expect.arrayContaining(['posts', 'search']))
    expect(anonKeys).not.toContain('messages')
    expect(anonKeys).not.toContain('analytics')
    expect(anonKeys).not.toContain('profile')

    const authed = getMobileNavigation({ authed: true, userId: 8 })
    const authedKeys = authed.map((it) => it.key)
    expect(authedKeys).toEqual(expect.arrayContaining(['posts', 'search', 'messages', 'profile']))
    expect(authedKeys).not.toContain('bookmarks')
    expect(authedKeys).not.toContain('analytics')
  })
})

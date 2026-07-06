import { describe, expect, it } from 'vitest'

import {
  POSTS_FILTER,
  POSTS_ORDER,
  canAccessNavItem,
  getRouteWorkspaceLabel,
  getShellSearchRouteNames,
  getMobileNavigation,
  getSidebarNavigation,
  isNavItemActive,
  normalizePostsBoardId,
  normalizePostsCategoryId,
  normalizePostsFilter,
  normalizePostsOrder
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

  it('normalizePostsCategoryId should preserve UUID category ids', () => {
    const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    expect(normalizePostsCategoryId(categoryId)).toBe(categoryId)
    expect(normalizePostsCategoryId('')).toBe('')
  })

  it('normalizePostsBoardId should preserve UUID board ids', () => {
    const boardId = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    expect(normalizePostsBoardId(boardId)).toBe(boardId)
    expect(normalizePostsBoardId('')).toBe('')
  })

  it('canAccessNavItem should enforce auth and roles', () => {
    expect(canAccessNavItem({ requiresAuth: true }, { authed: false })).toBe(false)
    expect(canAccessNavItem({ requiresAuth: true }, { authed: true })).toBe(true)

    expect(canAccessNavItem({ hideWhenAuthed: true }, { authed: true })).toBe(false)
    expect(canAccessNavItem({ hideWhenAuthed: true }, { authed: false })).toBe(true)

    expect(canAccessNavItem({ roles: ['ROLE_ADMIN'] }, { authed: true, roles: ['ROLE_USER'] })).toBe(false)
    expect(canAccessNavItem({ roles: ['ROLE_ADMIN'] }, { authed: true, roles: ['ROLE_ADMIN'] })).toBe(true)
  })

  it('getShellSearchRouteNames should expose the routes with shell search', () => {
    expect(getShellSearchRouteNames()).toEqual(['posts', 'search', 'market'])
  })

  it('getSidebarNavigation should group routes by product workspaces', () => {
    const anon = getSidebarNavigation({ authed: false })
    expect(anon.map((g) => g.key)).toEqual(['community', 'trading', 'account'])
    expect(anon.find((g) => g.key === 'community')?.items.map((it) => it.key)).toEqual(['posts', 'search'])
    expect(anon.find((g) => g.key === 'trading')?.items.map((it) => it.key)).toEqual(['market'])
    expect(anon.find((g) => g.key === 'account')?.items.map((it) => it.key)).toEqual(['login'])
    expect(anon.flatMap((g) => g.items).find((it) => it.key === 'login')?.activeNames || []).not.toContain('activation')

    const authed = getSidebarNavigation({ authed: true, userId: 12, roles: ['ROLE_USER'] })
    expect(authed.map((g) => g.key)).toEqual(['community', 'trading', 'personal'])
    expect(authed.find((g) => g.key === 'community')?.items.map((it) => it.key)).toEqual([
      'posts',
      'search',
      'bookmarks',
      'profile'
    ])
    expect(authed.find((g) => g.key === 'trading')?.items.map((it) => it.key)).toEqual([
      'market',
      'marketPublish',
      'marketMyListings',
      'marketBuying',
      'marketSelling',
      'marketAddresses'
    ])
    expect(authed.find((g) => g.key === 'personal')?.items.map((it) => it.key)).toEqual([
      'wallet',
      'drive',
      'notices',
      'messages',
      'settings'
    ])

    const profile = authed.flatMap((g) => g.items).find((it) => it.key === 'profile')
    expect(profile?.to).toEqual({ name: 'userProfile', params: { userId: '12' } })
  })

  it('getSidebarNavigation should expose admin workspace by role', () => {
    const moderator = getSidebarNavigation({ authed: true, userId: 8, roles: ['ROLE_MODERATOR'] })
    expect(moderator.find((g) => g.key === 'admin')?.items.map((it) => it.key)).toEqual(['moderation', 'analytics'])

    const admin = getSidebarNavigation({ authed: true, userId: 8, roles: ['ROLE_ADMIN'] })
    expect(admin.find((g) => g.key === 'admin')?.items.map((it) => it.key)).toEqual([
      'moderation',
      'analytics',
      'userManagement',
      'walletAdmin',
      'adminMarketDisputes'
    ])
  })

  it('getSidebarNavigation should expose drive under personal workspace for authenticated users', () => {
    const authed = getSidebarNavigation({ authed: true, userId: '8', roles: ['ROLE_USER'] })
    expect(authed.find((g) => g.key === 'personal')?.items.map((it) => it.key)).toContain('drive')
  })

  it('isNavItemActive should keep parent items active across route families', () => {
    const nav = getSidebarNavigation({ authed: true, userId: 1, roles: ['ROLE_USER'] })
    const allItems = nav.flatMap((g) => g.items)

    const posts = allItems.find((it) => it.key === 'posts')
    const market = allItems.find((it) => it.key === 'market')
    const marketMyListings = allItems.find((it) => it.key === 'marketMyListings')
    const marketBuying = allItems.find((it) => it.key === 'marketBuying')
    const marketSelling = allItems.find((it) => it.key === 'marketSelling')
    const wallet = allItems.find((it) => it.key === 'wallet')
    const notices = allItems.find((it) => it.key === 'notices')
    const messages = allItems.find((it) => it.key === 'messages')
    const profile = allItems.find((it) => it.key === 'profile')

    expect(isNavItemActive({ name: 'postDetail' }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: {} }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { type: 'top' } }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { subscribed: '1' } }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'wallet' }, wallet)).toBe(true)
    expect(isNavItemActive({ name: 'marketDetail' }, market)).toBe(true)
    expect(isNavItemActive({ name: 'marketPublish' }, market)).toBe(false)
    expect(isNavItemActive({ name: 'marketInventory' }, market)).toBe(false)
    expect(isNavItemActive({ name: 'marketOrderDetail' }, market)).toBe(false)
    expect(isNavItemActive({ name: 'marketInventory' }, marketMyListings)).toBe(true)
    expect(isNavItemActive({ name: 'marketOrderDetail' }, marketBuying)).toBe(true)
    expect(isNavItemActive({ name: 'marketOrderDetail' }, marketSelling)).toBe(true)
    expect(isNavItemActive({ name: 'noticeDetail' }, notices)).toBe(true)
    expect(isNavItemActive({ name: 'messageDetail' }, messages)).toBe(true)
    expect(isNavItemActive({ name: 'followees' }, profile)).toBe(true)
    expect(isNavItemActive({ name: 'followers' }, profile)).toBe(true)
  })

  it('getRouteWorkspaceLabel should describe route scope for the topbar', () => {
    expect(getRouteWorkspaceLabel('posts')).toBe('Community')
    expect(getRouteWorkspaceLabel('search')).toBe('Community')
    expect(getRouteWorkspaceLabel('userProfile')).toBe('Community')
    expect(getRouteWorkspaceLabel('notices')).toBe('Inbox')
    expect(getRouteWorkspaceLabel('messageDetail')).toBe('Inbox')
    expect(getRouteWorkspaceLabel('market')).toBe('Trade & Assets')
    expect(getRouteWorkspaceLabel('wallet')).toBe('Trade & Assets')
    expect(getRouteWorkspaceLabel('drive')).toBe('Files')
    expect(getRouteWorkspaceLabel('settings')).toBe('Account')
    expect(getRouteWorkspaceLabel('moderation')).toBe('Operations')
  })

  it('getMobileNavigation should prioritize community attention loops', () => {
    const anon = getMobileNavigation({ authed: false })
    expect(anon.map((it) => it.key)).toEqual(['posts', 'search', 'notices', 'messages', 'me'])
    expect(anon.find((it) => it.key === 'me')?.to).toEqual({ name: 'login' })
    expect(anon.find((it) => it.key === 'notices')?.to).toEqual({ name: 'login' })
    expect(anon.find((it) => it.key === 'messages')?.to).toEqual({ name: 'login' })

    const authed = getMobileNavigation({ authed: true, userId: 8, roles: ['ROLE_USER'] })
    expect(authed.map((it) => it.key)).toEqual(['posts', 'search', 'notices', 'messages', 'me'])
    expect(authed.find((it) => it.key === 'notices')?.to).toEqual({ name: 'notices' })
    expect(authed.find((it) => it.key === 'messages')?.to).toEqual({ name: 'messages' })
    expect(authed.find((it) => it.key === 'me')?.to).toEqual({ name: 'userProfile', params: { userId: '8' } })

    const authedWithoutUserId = getMobileNavigation({ authed: true, userId: 0, roles: ['ROLE_USER'] })
    expect(authedWithoutUserId.map((it) => it.key)).toEqual(['posts', 'search', 'notices', 'messages', 'me'])
    expect(authedWithoutUserId.find((it) => it.key === 'me')?.to).toEqual({ name: 'wallet' })
  })
})

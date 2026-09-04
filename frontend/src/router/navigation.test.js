import { describe, expect, it } from 'vitest'

import {
  canAccessNavItem,
  getMobileNavigation,
  getSidebarNavigation,
  isNavItemActive,
} from './navigation'
import { getRouteWorkspaceLabel } from './routeCatalog'

describe('router/navigation', () => {
  it('canAccessNavItem should enforce auth and roles', () => {
    expect(canAccessNavItem({ requiresAuth: true }, { authed: false })).toBe(false)
    expect(canAccessNavItem({ requiresAuth: true }, { authed: true })).toBe(true)

    expect(canAccessNavItem({ hideWhenAuthed: true }, { authed: true })).toBe(false)
    expect(canAccessNavItem({ hideWhenAuthed: true }, { authed: false })).toBe(true)

    expect(canAccessNavItem({ roles: ['ROLE_ADMIN'] }, { authed: true, roles: ['ROLE_USER'] })).toBe(false)
    expect(canAccessNavItem({ roles: ['ROLE_ADMIN'] }, { authed: true, roles: ['ROLE_ADMIN'] })).toBe(true)
  })

  it('getSidebarNavigation should group routes by product workspaces', () => {
    const anon = getSidebarNavigation({ authed: false })
    expect(anon.map((g) => g.key)).toEqual(['community', 'market', 'account'])
    expect(anon.find((g) => g.key === 'community')?.items.map((it) => it.key)).toEqual(['posts', 'search'])
    expect(anon.find((g) => g.key === 'market')?.items.map((it) => it.key)).toEqual(['market'])
    expect(anon.find((g) => g.key === 'account')?.items.map((it) => it.key)).toEqual(['login'])
    expect(anon.flatMap((g) => g.items).find((it) => it.key === 'login')?.activeNames || []).not.toContain('activation')

    const authed = getSidebarNavigation({ authed: true, userId: 12, roles: ['ROLE_USER'] })
    expect(authed.map((g) => g.key)).toEqual(['community', 'market', 'personal'])
    expect(authed.find((g) => g.key === 'community')?.items.map((it) => it.key)).toEqual([
      'posts',
      'search',
      'bookmarks',
      'profile'
    ])
    // 市场是一级域：发布商品、我的出售、我的购买和出售订单进入市场页主操作，不再作为一级入口。
    expect(authed.find((g) => g.key === 'market')?.items.map((it) => it.key)).toEqual(['market'])
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

  it('getSidebarNavigation should bind unread badge keys to the inbox entries', () => {
    const authed = getSidebarNavigation({ authed: true, userId: 12, roles: ['ROLE_USER'] })
    const items = authed.flatMap((g) => g.items)
    expect(items.find((it) => it.key === 'notices')?.badge).toBe('notices')
    expect(items.find((it) => it.key === 'messages')?.badge).toBe('messages')
    expect(items.filter((it) => it.badge).map((it) => it.key)).toEqual(['notices', 'messages'])
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
    const wallet = allItems.find((it) => it.key === 'wallet')
    const notices = allItems.find((it) => it.key === 'notices')
    const messages = allItems.find((it) => it.key === 'messages')
    const profile = allItems.find((it) => it.key === 'profile')

    expect(isNavItemActive({ name: 'postDetail' }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: {} }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { type: 'top' } }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'posts', query: { subscribed: '1' } }, posts)).toBe(true)
    expect(isNavItemActive({ name: 'wallet' }, wallet)).toBe(true)
    // 市场域内全部路由（含二级目的地）都高亮同一个市场入口。
    expect(isNavItemActive({ name: 'marketDetail' }, market)).toBe(true)
    expect(isNavItemActive({ name: 'marketPublish' }, market)).toBe(true)
    expect(isNavItemActive({ name: 'marketInventory' }, market)).toBe(true)
    expect(isNavItemActive({ name: 'marketOrderDetail' }, market)).toBe(true)
    expect(isNavItemActive({ name: 'marketBuyingOrders' }, market)).toBe(true)
    expect(isNavItemActive({ name: 'marketSellingOrders' }, market)).toBe(true)
    expect(isNavItemActive({ name: 'posts' }, market)).toBe(false)
    expect(isNavItemActive({ name: 'noticeDetail' }, notices)).toBe(true)
    expect(isNavItemActive({ name: 'messageDetail' }, messages)).toBe(true)
    expect(isNavItemActive({ name: 'followees' }, profile)).toBe(true)
    expect(isNavItemActive({ name: 'followers' }, profile)).toBe(true)
  })

  it('getRouteWorkspaceLabel should describe route scope for the topbar in Chinese', () => {
    expect(getRouteWorkspaceLabel('posts')).toBe('社区')
    expect(getRouteWorkspaceLabel('search')).toBe('社区')
    expect(getRouteWorkspaceLabel('userProfile')).toBe('社区')
    expect(getRouteWorkspaceLabel('notices')).toBe('个人')
    expect(getRouteWorkspaceLabel('messageDetail')).toBe('个人')
    expect(getRouteWorkspaceLabel('market')).toBe('市场')
    expect(getRouteWorkspaceLabel('marketOrderDetail')).toBe('市场')
    expect(getRouteWorkspaceLabel('wallet')).toBe('个人')
    expect(getRouteWorkspaceLabel('drive')).toBe('个人')
    expect(getRouteWorkspaceLabel('settings')).toBe('个人')
    expect(getRouteWorkspaceLabel('moderation')).toBe('运营')
    expect(getRouteWorkspaceLabel('unknown')).toBe('社区')
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

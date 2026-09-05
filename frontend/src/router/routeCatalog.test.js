import { describe, expect, it } from 'vitest'

import {
  ROUTES,
  getRouteAccess,
  getRouteBreadcrumbItems,
  getRouteFamilyNames,
  getRouteWorkspaceLabel,
  routeMeta
} from './routeCatalog'

describe('router/routeCatalog', () => {
  it('owns the complete formal route-name inventory', () => {
    expect(Object.keys(ROUTES)).toContain('posts')
    expect(Object.keys(ROUTES)).toContain('notFound')
  })

  it('owns stable workspace labels', () => {
    expect(getRouteWorkspaceLabel('posts')).toBe('社区')
    expect(getRouteWorkspaceLabel('messageDetail')).toBe('个人')
    expect(getRouteWorkspaceLabel('marketOrderDetail')).toBe('市场')
    expect(getRouteWorkspaceLabel('driveShare')).toBe('个人')
    expect(getRouteWorkspaceLabel('moderation')).toBe('运营')
    expect(getRouteWorkspaceLabel('unknown')).toBe('社区')
  })

  it('owns route families including the full market domain family', () => {
    expect(getRouteFamilyNames('posts')).toEqual(['posts', 'postDetail'])
    expect(getRouteFamilyNames('profile')).toEqual(['userProfile', 'followees', 'followers'])
    expect(getRouteFamilyNames('market')).toEqual([
      'market',
      'marketDetail',
      'marketPublish',
      'marketMyListings',
      'marketInventory',
      'marketBuyingOrders',
      'marketSellingOrders',
      'marketOrderDetail'
    ])
  })

  it('owns stable breadcrumb projections while accepting dynamic params', () => {
    expect(getRouteBreadcrumbItems('postDetail', { postId: 'post-1' })).toEqual([
      { label: '帖子', to: { name: 'posts' } },
      { label: '帖子 #post-1' }
    ])
    expect(getRouteBreadcrumbItems('followees', { userId: 'user-1' })).toEqual([
      { label: '成员档案', to: { name: 'userProfile', params: { userId: 'user-1' } } },
      { label: '关注列表' }
    ])
    expect(getRouteBreadcrumbItems('marketInventory', { listingId: '21' })).toEqual([
      { label: '市场', to: { name: 'market' } },
      { label: '我的出售', to: { name: 'marketMyListings' } },
      { label: '库存管理' }
    ])
    expect(getRouteBreadcrumbItems('market')).toEqual([])
  })

  it('projects access metadata without sharing mutable role arrays', () => {
    expect(getRouteAccess('wallet')).toEqual({ requiresAuth: true })
    expect(getRouteAccess('moderation')).toEqual({
      requiresAuth: true,
      roles: ['ROLE_ADMIN', 'ROLE_MODERATOR']
    })
    expect(routeMeta('userManagement', { title: '用户管理', navGroup: 'admin' })).toEqual({
      title: '用户管理',
      navGroup: 'admin',
      requiresAuth: true,
      roles: ['ROLE_ADMIN']
    })

    const access = getRouteAccess('moderation')
    access.roles.push('ROLE_USER')
    expect(getRouteAccess('moderation').roles).toEqual(['ROLE_ADMIN', 'ROLE_MODERATOR'])
  })
})

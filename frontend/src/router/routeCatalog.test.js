import { describe, expect, it } from 'vitest'

import {
  getCatalogRouteNames,
  getRouteAccess,
  getRouteBreadcrumbItems,
  getRouteFamilyNames,
  getRouteWorkspaceLabel,
  routeMeta
} from './routeCatalog'

describe('router/routeCatalog', () => {
  it('owns the complete formal route-name inventory', () => {
    expect(getCatalogRouteNames()).toContain('posts')
    expect(getCatalogRouteNames()).toContain('notFound')
  })

  it('owns stable workspace labels', () => {
    expect(getRouteWorkspaceLabel('posts')).toBe('Community')
    expect(getRouteWorkspaceLabel('messageDetail')).toBe('Inbox')
    expect(getRouteWorkspaceLabel('marketOrderDetail')).toBe('Trade & Assets')
    expect(getRouteWorkspaceLabel('driveShare')).toBe('Files')
    expect(getRouteWorkspaceLabel('moderation')).toBe('Operations')
    expect(getRouteWorkspaceLabel('unknown')).toBe('Community')
  })

  it('owns route families including legitimate multi-family detail routes', () => {
    expect(getRouteFamilyNames('posts')).toEqual(['posts', 'postDetail'])
    expect(getRouteFamilyNames('profile')).toEqual(['userProfile', 'followees', 'followers'])
    expect(getRouteFamilyNames('marketBuying')).toEqual(['marketBuyingOrders', 'marketOrderDetail'])
    expect(getRouteFamilyNames('marketSelling')).toEqual(['marketSellingOrders', 'marketOrderDetail'])
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

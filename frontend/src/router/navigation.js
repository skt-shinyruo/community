import { hasOpaqueId, normalizeOpaqueId } from '../utils/opaqueId'
import {
  getRouteAccess,
  getRouteBreadcrumbItems,
  getRouteFamilyNames,
  getRouteWorkspaceLabel
} from './routeCatalog'

// 导航配置 SSOT：定义侧边栏/移动端的分组、权限与路由映射，并提供 posts 筛选/排序的纯函数工具。

export const POSTS_ORDER = Object.freeze({
  LATEST: 'latest',
  HOT: 'hot'
})

export const POSTS_FILTER = Object.freeze({
  ALL: '',
  TOP: 'top',
  WONDERFUL: 'wonderful',
  UNREAD: 'unread'
})

export function normalizePostsCategoryId(value) {
  return normalizeOpaqueId(value)
}

export function normalizePostsBoardId(value) {
  return normalizeOpaqueId(value)
}

export function normalizePostsOrder(value) {
  return value === POSTS_ORDER.HOT ? POSTS_ORDER.HOT : POSTS_ORDER.LATEST
}

export function normalizePostsFilter(value) {
  if (value === POSTS_FILTER.TOP) return POSTS_FILTER.TOP
  if (value === POSTS_FILTER.WONDERFUL) return POSTS_FILTER.WONDERFUL
  if (value === POSTS_FILTER.UNREAD) return POSTS_FILTER.UNREAD
  return POSTS_FILTER.ALL
}

export function normalizePostsSubscribed(value) {
  if (value === true) return true
  const s = String(value || '').trim().toLowerCase()
  return s === '1' || s === 'true' || s === 'yes'
}

export { getRouteWorkspaceLabel }

function normalizeRoles(roles) {
  return Array.isArray(roles) ? roles.filter(Boolean).map(String) : []
}

function hasAnyRole(userRoles, requiredRoles) {
  if (!Array.isArray(requiredRoles) || requiredRoles.length === 0) return true
  const set = new Set(normalizeRoles(userRoles))
  return requiredRoles.some((r) => set.has(String(r)))
}

export function canAccessNavItem(item, ctx = {}) {
  const authed = !!ctx.authed
  const roles = normalizeRoles(ctx.roles)
  const userId = hasOpaqueId(ctx.userId)

  if (!item) return false
  if (item.hidden === true) return false
  if (item.requiresAuth && !authed) return false
  if (item.requiresUserId && !userId) return false
  if (item.hideWhenAuthed && authed) return false
  if (!hasAnyRole(roles, item.roles)) return false

  return true
}

function resolveTo(to, ctx) {
  if (typeof to === 'function') return to(ctx)
  return to || null
}

function getRouteName(route) {
  return String(route?.name || '')
}

export function isNavItemActive(route, item) {
  if (!route || !item) return false

  if (typeof item.isActive === 'function') return !!item.isActive(route)

  const name = getRouteName(route)
  if (Array.isArray(item.activeNames) && item.activeNames.length > 0) {
    return item.activeNames.map(String).includes(name)
  }

  // 默认回退：如果 item.to 是 route location，则用 name 判断。
  const to = resolveTo(item.to, {})
  if (to && typeof to === 'object' && to.name) {
    return String(to.name) === name
  }
  return false
}

const SHELL_SEARCH_ROUTE_NAMES = Object.freeze(['posts', 'search', 'market'])

export function routeSupportsShellSearch(routeName) {
  return SHELL_SEARCH_ROUTE_NAMES.includes(String(routeName || ''))
}

// 导航 SSOT：侧边抽屉承载产品工作区，移动端底栏只承载高频入口。
const NAV_DEFS = Object.freeze([
  {
    key: 'community',
    title: '社区',
    items: [
      {
        key: 'posts',
        label: '帖子',
        icon: 'posts',
        to: () => ({ name: 'posts' }),
        activeNames: getRouteFamilyNames('posts')
      },
      {
        key: 'search',
        label: '搜索',
        icon: 'search',
        to: () => ({ name: 'search' }),
        activeNames: getRouteFamilyNames('search')
      },
      {
        key: 'bookmarks',
        label: '收藏',
        icon: 'bookmark',
        ...getRouteAccess('bookmarks'),
        to: () => ({ name: 'bookmarks' }),
        activeNames: getRouteFamilyNames('bookmarks')
      },
      {
        key: 'profile',
        label: '我的主页',
        icon: 'user',
        requiresAuth: true,
        requiresUserId: true,
        to: (ctx) => ({ name: 'userProfile', params: { userId: String(ctx?.userId || '') } }),
        activeNames: getRouteFamilyNames('profile')
      }
    ]
  },
  {
    key: 'trading',
    title: '交易',
    items: [
      {
        key: 'market',
        label: '市场',
        icon: 'sparkle',
        to: () => ({ name: 'market' }),
        activeNames: getRouteFamilyNames('market')
      },
      {
        key: 'marketPublish',
        label: '发布商品',
        icon: 'posts',
        ...getRouteAccess('marketPublish'),
        to: () => ({ name: 'marketPublish' }),
        activeNames: getRouteFamilyNames('marketPublish')
      },
      {
        key: 'marketMyListings',
        label: '我的出售',
        icon: 'analytics',
        ...getRouteAccess('marketMyListings'),
        to: () => ({ name: 'marketMyListings' }),
        activeNames: getRouteFamilyNames('marketMyListings')
      },
      {
        key: 'marketBuying',
        label: '我的购买',
        icon: 'bookmark',
        ...getRouteAccess('marketBuyingOrders'),
        to: () => ({ name: 'marketBuyingOrders' }),
        activeNames: getRouteFamilyNames('marketBuying')
      },
      {
        key: 'marketSelling',
        label: '出售订单',
        icon: 'analytics',
        ...getRouteAccess('marketSellingOrders'),
        to: () => ({ name: 'marketSellingOrders' }),
        activeNames: getRouteFamilyNames('marketSelling')
      },
      {
        key: 'marketAddresses',
        label: '收货地址',
        icon: 'bookmark',
        ...getRouteAccess('marketAddresses'),
        to: () => ({ name: 'marketAddresses' }),
        activeNames: getRouteFamilyNames('marketAddresses')
      }
    ]
  },
  {
    key: 'personal',
    title: '个人',
    items: [
      {
        key: 'wallet',
        label: '积分钱包',
        icon: 'sparkle',
        ...getRouteAccess('wallet'),
        to: () => ({ name: 'wallet' }),
        activeNames: getRouteFamilyNames('wallet')
      },
      {
        key: 'drive',
        label: '网盘',
        icon: 'folder',
        ...getRouteAccess('drive'),
        to: () => ({ name: 'drive' }),
        activeNames: getRouteFamilyNames('drive')
      },
      {
        key: 'notices',
        label: '通知',
        icon: 'bell',
        ...getRouteAccess('notices'),
        to: () => ({ name: 'notices' }),
        activeNames: getRouteFamilyNames('notices')
      },
      {
        key: 'messages',
        label: '私信',
        icon: 'messages',
        ...getRouteAccess('messages'),
        to: () => ({ name: 'messages' }),
        activeNames: getRouteFamilyNames('messages')
      },
      {
        key: 'settings',
        label: '设置',
        icon: 'settings',
        ...getRouteAccess('settings'),
        to: () => ({ name: 'settings' }),
        activeNames: getRouteFamilyNames('settings')
      }
    ]
  },
  {
    key: 'admin',
    title: '管理',
    items: [
      {
        key: 'moderation',
        label: '治理后台',
        icon: 'shield',
        ...getRouteAccess('moderation'),
        to: () => ({ name: 'moderation' }),
        activeNames: getRouteFamilyNames('moderation')
      },
      {
        key: 'analytics',
        label: '统计',
        icon: 'analytics',
        ...getRouteAccess('analytics'),
        to: () => ({ name: 'analytics' }),
        activeNames: getRouteFamilyNames('analytics')
      },
      {
        key: 'userManagement',
        label: '用户管理',
        icon: 'user',
        ...getRouteAccess('userManagement'),
        to: () => ({ name: 'userManagement' }),
        activeNames: getRouteFamilyNames('userManagement')
      },
      {
        key: 'walletAdmin',
        label: '钱包后台',
        icon: 'analytics',
        ...getRouteAccess('walletAdmin'),
        to: () => ({ name: 'walletAdmin' }),
        activeNames: getRouteFamilyNames('walletAdmin')
      },
      {
        key: 'adminMarketDisputes',
        label: '争议裁定',
        icon: 'shield',
        ...getRouteAccess('adminMarketDisputes'),
        to: () => ({ name: 'adminMarketDisputes' }),
        activeNames: getRouteFamilyNames('adminMarketDisputes')
      }
    ]
  },
  {
    key: 'account',
    title: '账户',
    items: [
      {
        key: 'login',
        label: '登录',
        icon: 'login',
        hideWhenAuthed: true,
        to: () => ({ name: 'login' }),
        activeNames: getRouteFamilyNames('login')
      }
    ]
  }
])

export function getSidebarNavigation(ctx = {}) {
  const safeCtx = {
    authed: !!ctx.authed,
    userId: ctx.userId || '',
    roles: normalizeRoles(ctx.roles)
  }

  return NAV_DEFS.map((g) => ({
    ...g,
    items: (g.items || [])
      .filter((it) => canAccessNavItem(it, safeCtx))
      .map((it) => ({ ...it, to: resolveTo(it.to, safeCtx) }))
  })).filter((g) => Array.isArray(g.items) && g.items.length > 0)
}

function findNavItem(groups, key) {
  return groups.flatMap((g) => g.items || []).find((it) => it.key === key) || null
}

function findNavGroupDef(key) {
  return NAV_DEFS.find((g) => g.key === key) || null
}

function collectActiveNames(items) {
  const names = new Set()

  for (const item of items || []) {
    if (!item) continue
    if (Array.isArray(item.activeNames) && item.activeNames.length > 0) {
      item.activeNames.map(String).forEach((name) => names.add(name))
      continue
    }

    const to = resolveTo(item.to, {})
    if (to && typeof to === 'object' && to.name) {
      names.add(String(to.name))
    }
  }

  return Array.from(names)
}

export function getMobileNavigation(ctx = {}) {
  const groups = getSidebarNavigation(ctx)
  const personalGroupDef = findNavGroupDef('personal')
  const adminGroupDef = findNavGroupDef('admin')
  const accountGroupDef = findNavGroupDef('account')
  const posts = findNavItem(groups, 'posts') || {
    key: 'posts',
    label: '帖子',
    icon: 'posts',
    to: { name: 'posts' },
    activeNames: getRouteFamilyNames('posts')
  }
  const search = findNavItem(groups, 'search') || {
    key: 'search',
    label: '搜索',
    icon: 'search',
    to: { name: 'search' },
    activeNames: getRouteFamilyNames('search')
  }
  const login = findNavItem(groups, 'login')
  const notices = findNavItem(groups, 'notices') || {
    key: 'notices',
    label: '通知',
    icon: 'bell',
    to: login?.to || { name: 'login' },
    activeNames: getRouteFamilyNames('notices')
  }
  const messages = findNavItem(groups, 'messages') || {
    key: 'messages',
    label: '私信',
    icon: 'messages',
    to: login?.to || { name: 'login' },
    activeNames: getRouteFamilyNames('messages')
  }
  const profile = findNavItem(groups, 'profile')
  const personalGroup = groups.find((group) => group?.key === 'personal') || null
  const firstPersonalItem = Array.isArray(personalGroup?.items) ? personalGroup.items[0] || null : null
  const meActiveNames = collectActiveNames([
    ...(profile ? [profile] : []),
    ...(((personalGroupDef && personalGroupDef.items) || []).filter((item) => item?.key === 'wallet' || item?.key === 'drive' || item?.key === 'settings')),
    ...(((accountGroupDef && accountGroupDef.items) || []).filter((item) => item?.key === 'login')),
    ...(((adminGroupDef && adminGroupDef.items) || []).filter((item) => item?.key === 'userManagement'))
  ])

  const me = {
    key: 'me',
    label: '我',
    icon: 'user',
    to: profile?.to || firstPersonalItem?.to || login?.to || { name: 'login' },
    activeNames: meActiveNames
  }

  return [posts, search, notices, messages, me]
}

export function getBreadcrumbItems(route) {
  const name = getRouteName(route)
  const params = route?.params && typeof route.params === 'object' ? route.params : {}
  return getRouteBreadcrumbItems(name, params)
}

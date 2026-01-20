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
  const n = Number(value || 0)
  return Number.isFinite(n) && n > 0 ? n : 0
}

export function normalizePostsTag(value) {
  const s = String(value || '').trim()
  return s ? s : ''
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

export function normalizePostsQuery(query) {
  const q = query && typeof query === 'object' ? query : {}
  return {
    order: normalizePostsOrder(q.order),
    filter: normalizePostsFilter(q.type),
    categoryId: normalizePostsCategoryId(q.categoryId),
    tag: normalizePostsTag(q.tag)
  }
}

export function buildPostsQuery({ order, filter, categoryId, tag } = {}) {
  const normalizedOrder = normalizePostsOrder(order)
  const normalizedFilter = normalizePostsFilter(filter)
  const normalizedCategoryId = normalizePostsCategoryId(categoryId)
  const normalizedTag = normalizePostsTag(tag)

  const next = {}
  // 默认值不写入 URL，减少噪音；由 normalizePostsQuery 兜底。
  if (normalizedOrder !== POSTS_ORDER.LATEST) next.order = normalizedOrder
  if (normalizedFilter) next.type = normalizedFilter
  if (normalizedCategoryId > 0) next.categoryId = String(normalizedCategoryId)
  if (normalizedTag) next.tag = normalizedTag
  return next
}

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
  const userId = Number(ctx.userId || 0)

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

function getRouteQuery(route) {
  const q = route?.query
  return q && typeof q === 'object' ? q : {}
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

export const POSTS_FILTER_OPTIONS = Object.freeze([
  { key: POSTS_FILTER.ALL, label: '全部' },
  { key: POSTS_FILTER.UNREAD, label: '未读' },
  { key: POSTS_FILTER.TOP, label: '置顶' },
  { key: POSTS_FILTER.WONDERFUL, label: '精华' }
])

export const POSTS_ORDER_OPTIONS = Object.freeze([
  { key: POSTS_ORDER.LATEST, label: '最新' },
  { key: POSTS_ORDER.HOT, label: '热门' }
])

// sidebar/mobile 共享的导航配置（不直接依赖 UI 组件，便于测试与复用）。
const NAV_DEFS = Object.freeze([
  {
    key: 'explore',
    title: '探索',
    items: [
      {
        key: 'posts',
        label: '帖子',
        icon: 'posts',
        to: () => ({ name: 'posts' }),
        isActive: (route) => {
          const name = getRouteName(route)
          if (name === 'postDetail') return true
          if (name !== 'posts') return false
          const { filter } = normalizePostsQuery(getRouteQuery(route))
          return filter === POSTS_FILTER.ALL
        }
      },
      {
        key: 'search',
        label: '搜索',
        icon: 'search',
        to: () => ({ name: 'search' }),
        activeNames: ['search']
      }
    ]
  },
  {
    key: 'filters',
    title: '筛选',
    items: [
      {
        key: 'postsUnread',
        label: '未读',
        icon: 'dot',
        requiresAuth: true,
        to: () => ({ name: 'posts', query: { type: POSTS_FILTER.UNREAD } }),
        isActive: (route) => {
          const name = getRouteName(route)
          if (name !== 'posts') return false
          const { filter } = normalizePostsQuery(getRouteQuery(route))
          return filter === POSTS_FILTER.UNREAD
        }
      },
      {
        key: 'postsTop',
        label: '置顶',
        icon: 'pin',
        to: () => ({ name: 'posts', query: { type: POSTS_FILTER.TOP } }),
        isActive: (route) => {
          const name = getRouteName(route)
          if (name !== 'posts') return false
          const { filter } = normalizePostsQuery(getRouteQuery(route))
          return filter === POSTS_FILTER.TOP
        }
      },
      {
        key: 'postsWonderful',
        label: '精华',
        icon: 'sparkle',
        to: () => ({ name: 'posts', query: { type: POSTS_FILTER.WONDERFUL } }),
        isActive: (route) => {
          const name = getRouteName(route)
          if (name !== 'posts') return false
          const { filter } = normalizePostsQuery(getRouteQuery(route))
          return filter === POSTS_FILTER.WONDERFUL
        }
      }
    ]
  },
  {
    key: 'me',
    title: '我的',
    items: [
      {
        key: 'notices',
        label: '通知',
        icon: 'bell',
        requiresAuth: true,
        to: () => ({ name: 'notices' }),
        activeNames: ['notices', 'noticeDetail']
      },
      {
        key: 'messages',
        label: '私信',
        icon: 'messages',
        requiresAuth: true,
        to: () => ({ name: 'messages' }),
        activeNames: ['messages', 'messageDetail']
      },
      {
        key: 'profile',
        label: '我的主页',
        icon: 'user',
        requiresAuth: true,
        requiresUserId: true,
        to: (ctx) => ({ name: 'userProfile', params: { userId: String(ctx?.userId || '') } }),
        activeNames: ['userProfile', 'followees', 'followers']
      },
      {
        key: 'settings',
        label: '设置',
        icon: 'settings',
        requiresAuth: true,
        to: () => ({ name: 'settings' }),
        activeNames: ['settings']
      }
    ]
  },
  {
    key: 'admin',
    title: '管理',
    items: [
      {
        key: 'analytics',
        label: '统计',
        icon: 'analytics',
        requiresAuth: true,
        roles: ['ROLE_ADMIN', 'ROLE_MODERATOR'],
        to: () => ({ name: 'analytics' }),
        activeNames: ['analytics']
      }
    ]
  },
  {
    key: 'auth',
    title: '账户',
    items: [
      {
        key: 'login',
        label: '登录',
        icon: 'login',
        hideWhenAuthed: true,
        to: () => ({ name: 'login' }),
        activeNames: ['login', 'register', 'passwordReset', 'activation']
      }
    ]
  }
])

export function getSidebarNavigation(ctx = {}) {
  const safeCtx = {
    authed: !!ctx.authed,
    userId: Number(ctx.userId || 0),
    roles: normalizeRoles(ctx.roles)
  }

  return NAV_DEFS.map((g) => ({
    ...g,
    items: (g.items || [])
      .filter((it) => canAccessNavItem(it, safeCtx))
      .map((it) => ({ ...it, to: resolveTo(it.to, safeCtx) }))
  })).filter((g) => Array.isArray(g.items) && g.items.length > 0)
}

export function getMobileNavigation(ctx = {}) {
  const groups = getSidebarNavigation(ctx)

  // 移动端底部只保留核心入口，避免拥挤：帖子/搜索/私信/我的。
  const allowKeys = new Set(['posts', 'search', 'messages', 'profile'])
  const flat = groups.flatMap((g) => g.items || [])
  return flat.filter((it) => allowKeys.has(it.key))
}

export function getBreadcrumbItems(route) {
  const name = getRouteName(route)
  const params = route?.params && typeof route.params === 'object' ? route.params : {}

  if (name === 'postDetail') {
    return [
      { label: '帖子', to: { name: 'posts' } },
      { label: `帖子 #${params.postId || ''}` }
    ]
  }

  if (name === 'userProfile') {
    return [{ label: `用户 #${params.userId || ''}` }]
  }

  if (name === 'followees') {
    return [
      { label: `用户 #${params.userId || ''}`, to: { name: 'userProfile', params: { userId: String(params.userId || '') } } },
      { label: '关注列表' }
    ]
  }

  if (name === 'followers') {
    return [
      { label: `用户 #${params.userId || ''}`, to: { name: 'userProfile', params: { userId: String(params.userId || '') } } },
      { label: '粉丝列表' }
    ]
  }

  return []
}

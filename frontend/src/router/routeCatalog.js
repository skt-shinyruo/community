export const ROUTES = Object.freeze({
  login: { workspace: '社区', navGroup: 'auth' },
  register: { workspace: '社区', navGroup: 'auth' },
  passwordReset: { workspace: '社区', navGroup: 'auth' },
  posts: { workspace: '社区', navGroup: 'explore' },
  postDetail: { workspace: '社区', navGroup: 'explore' },
  search: { workspace: '社区', navGroup: 'explore' },
  bookmarks: { workspace: '社区', navGroup: 'me', requiresAuth: true },
  userProfile: { workspace: '社区', navGroup: 'me' },
  followees: { workspace: '社区', navGroup: 'me' },
  followers: { workspace: '社区', navGroup: 'me' },
  notices: { workspace: '个人', navGroup: 'me', requiresAuth: true },
  noticeDetail: { workspace: '个人', navGroup: 'me', requiresAuth: true },
  messages: { workspace: '个人', navGroup: 'me', requiresAuth: true },
  messageDetail: { workspace: '个人', navGroup: 'me', requiresAuth: true },
  market: { workspace: '市场', navGroup: 'explore' },
  marketDetail: { workspace: '市场', navGroup: 'explore' },
  marketPublish: { workspace: '市场', navGroup: 'me', requiresAuth: true },
  marketMyListings: { workspace: '市场', navGroup: 'me', requiresAuth: true },
  marketInventory: { workspace: '市场', navGroup: 'me', requiresAuth: true },
  marketBuyingOrders: { workspace: '市场', navGroup: 'me', requiresAuth: true },
  marketSellingOrders: { workspace: '市场', navGroup: 'me', requiresAuth: true },
  marketOrderDetail: { workspace: '市场', navGroup: 'me', requiresAuth: true },
  marketAddresses: { workspace: '个人', navGroup: 'me', requiresAuth: true },
  wallet: { workspace: '个人', navGroup: 'me', requiresAuth: true },
  drive: { workspace: '个人', navGroup: 'me', requiresAuth: true },
  driveShare: { workspace: '个人', navGroup: 'public' },
  settings: { workspace: '个人', navGroup: 'me', requiresAuth: true },
  analytics: {
    workspace: '运营',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN', 'ROLE_MODERATOR']
  },
  moderation: {
    workspace: '运营',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN', 'ROLE_MODERATOR']
  },
  userManagement: {
    workspace: '运营',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN']
  },
  walletAdmin: {
    workspace: '运营',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN']
  },
  adminMarketDisputes: {
    workspace: '运营',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN']
  },
  forbidden: { workspace: '系统', navGroup: 'system' },
  notFound: { workspace: '系统', navGroup: 'system' }
})

const ROUTE_FAMILIES = Object.freeze({
  posts: ['posts', 'postDetail'],
  search: ['search'],
  bookmarks: ['bookmarks'],
  profile: ['userProfile', 'followees', 'followers'],
  // 市场是一级域：全部市场路由共用同一个侧边栏入口与选中态。
  market: [
    'market',
    'marketDetail',
    'marketPublish',
    'marketMyListings',
    'marketInventory',
    'marketBuyingOrders',
    'marketSellingOrders',
    'marketOrderDetail'
  ],
  wallet: ['wallet'],
  drive: ['drive'],
  notices: ['notices', 'noticeDetail'],
  messages: ['messages', 'messageDetail'],
  settings: ['settings'],
  moderation: ['moderation'],
  analytics: ['analytics'],
  userManagement: ['userManagement'],
  walletAdmin: ['walletAdmin'],
  adminMarketDisputes: ['adminMarketDisputes'],
  login: ['login', 'register', 'passwordReset']
})

const BREADCRUMBS = Object.freeze({
  postDetail: (params) => [
    { label: '帖子', to: { name: 'posts' } },
    { label: `帖子 #${params.postId || ''}` }
  ],
  userProfile: () => [{ label: '成员档案' }],
  followees: (params) => [
    { label: '成员档案', to: { name: 'userProfile', params: { userId: String(params.userId || '') } } },
    { label: '关注列表' }
  ],
  followers: (params) => [
    { label: '成员档案', to: { name: 'userProfile', params: { userId: String(params.userId || '') } } },
    { label: '粉丝列表' }
  ]
})

function routeEntry(routeName) {
  return ROUTES[String(routeName || '')] || null
}

export function getRouteWorkspaceLabel(routeName) {
  return routeEntry(routeName)?.workspace || '社区'
}

export function getRouteFamilyNames(familyKey) {
  return [...(ROUTE_FAMILIES[String(familyKey || '')] || [])]
}

export function getRouteBreadcrumbItems(routeName, params = {}) {
  const createItems = BREADCRUMBS[String(routeName || '')]
  return createItems ? createItems(params && typeof params === 'object' ? params : {}) : []
}

export function getRouteAccess(routeName) {
  const entry = routeEntry(routeName)
  if (!entry) return {}
  const access = {}
  if (entry.requiresAuth === true) access.requiresAuth = true
  if (Array.isArray(entry.roles)) access.roles = [...entry.roles]
  return access
}

export function routeMeta(routeName, metadata = {}) {
  const entry = routeEntry(routeName)
  if (!entry) return { ...metadata }
  return {
    ...metadata,
    navGroup: entry.navGroup,
    ...getRouteAccess(routeName)
  }
}

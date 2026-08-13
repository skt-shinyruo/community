const ROUTES = Object.freeze({
  login: { workspace: 'Community', navGroup: 'auth' },
  register: { workspace: 'Community', navGroup: 'auth' },
  passwordReset: { workspace: 'Community', navGroup: 'auth' },
  posts: { workspace: 'Community', navGroup: 'explore' },
  postDetail: { workspace: 'Community', navGroup: 'explore' },
  search: { workspace: 'Community', navGroup: 'explore' },
  bookmarks: { workspace: 'Community', navGroup: 'me', requiresAuth: true },
  userProfile: { workspace: 'Community', navGroup: 'me' },
  followees: { workspace: 'Community', navGroup: 'me' },
  followers: { workspace: 'Community', navGroup: 'me' },
  notices: { workspace: 'Inbox', navGroup: 'me', requiresAuth: true },
  noticeDetail: { workspace: 'Inbox', navGroup: 'me', requiresAuth: true },
  messages: { workspace: 'Inbox', navGroup: 'me', requiresAuth: true },
  messageDetail: { workspace: 'Inbox', navGroup: 'me', requiresAuth: true },
  market: { workspace: 'Trade & Assets', navGroup: 'explore' },
  marketDetail: { workspace: 'Trade & Assets', navGroup: 'explore' },
  marketPublish: { workspace: 'Trade & Assets', navGroup: 'me', requiresAuth: true },
  marketMyListings: { workspace: 'Trade & Assets', navGroup: 'me', requiresAuth: true },
  marketInventory: { workspace: 'Trade & Assets', navGroup: 'me', requiresAuth: true },
  marketBuyingOrders: { workspace: 'Trade & Assets', navGroup: 'me', requiresAuth: true },
  marketSellingOrders: { workspace: 'Trade & Assets', navGroup: 'me', requiresAuth: true },
  marketOrderDetail: { workspace: 'Trade & Assets', navGroup: 'me', requiresAuth: true },
  marketAddresses: { workspace: 'Trade & Assets', navGroup: 'me', requiresAuth: true },
  wallet: { workspace: 'Trade & Assets', navGroup: 'me', requiresAuth: true },
  drive: { workspace: 'Files', navGroup: 'me', requiresAuth: true },
  driveShare: { workspace: 'Files', navGroup: 'public' },
  settings: { workspace: 'Account', navGroup: 'me', requiresAuth: true },
  analytics: {
    workspace: 'Operations',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN', 'ROLE_MODERATOR']
  },
  moderation: {
    workspace: 'Operations',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN', 'ROLE_MODERATOR']
  },
  userManagement: {
    workspace: 'Operations',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN']
  },
  walletAdmin: {
    workspace: 'Operations',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN']
  },
  adminMarketDisputes: {
    workspace: 'Operations',
    navGroup: 'admin',
    requiresAuth: true,
    roles: ['ROLE_ADMIN']
  },
  forbidden: { workspace: 'System', navGroup: 'system' },
  notFound: { workspace: 'System', navGroup: 'system' }
})

const ROUTE_FAMILIES = Object.freeze({
  posts: ['posts', 'postDetail'],
  search: ['search'],
  bookmarks: ['bookmarks'],
  profile: ['userProfile', 'followees', 'followers'],
  market: ['market', 'marketDetail'],
  marketPublish: ['marketPublish'],
  marketMyListings: ['marketMyListings', 'marketInventory'],
  marketBuying: ['marketBuyingOrders', 'marketOrderDetail'],
  marketSelling: ['marketSellingOrders', 'marketOrderDetail'],
  marketAddresses: ['marketAddresses'],
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

export function getCatalogRouteNames() {
  return Object.keys(ROUTES)
}

export function catalogRouteName(routeName) {
  const normalized = String(routeName || '')
  if (!routeEntry(normalized)) throw new Error(`Unknown route catalog entry: ${normalized}`)
  return normalized
}

export function getRouteWorkspaceLabel(routeName) {
  return routeEntry(routeName)?.workspace || 'Community'
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

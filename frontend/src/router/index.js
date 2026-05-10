import { createRouter, createWebHashHistory } from 'vue-router'
import { authGuard } from './authGuard'

const LoginView = () => import('../views/LoginView.vue')
const HomeView = () => import('../views/HomeView.vue')
const EditorialPreviewView = () => import('../views/EditorialPreviewView.vue')
const PostsView = () => import('../views/PostsView.vue')
const PostDetailView = () => import('../views/PostDetailView.vue')
const UserProfileView = () => import('../views/UserProfileView.vue')
const RegisterView = () => import('../views/RegisterView.vue')
const PasswordResetView = () => import('../views/PasswordResetView.vue')
const SearchView = () => import('../views/SearchView.vue')
const MarketListView = () => import('../views/MarketListView.vue')
const MarketDetailView = () => import('../views/MarketDetailView.vue')
const MarketPublishView = () => import('../views/MarketPublishView.vue')
const MarketMyListingsView = () => import('../views/MarketMyListingsView.vue')
const MarketInventoryView = () => import('../views/MarketInventoryView.vue')
const MarketBuyingOrdersView = () => import('../views/MarketBuyingOrdersView.vue')
const MarketSellingOrdersView = () => import('../views/MarketSellingOrdersView.vue')
const MarketOrderDetailView = () => import('../views/MarketOrderDetailView.vue')
const MarketAddressesView = () => import('../views/MarketAddressesView.vue')
const WalletView = () => import('../views/WalletView.vue')
const DriveView = () => import('../views/DriveView.vue')
const DriveShareView = () => import('../views/DriveShareView.vue')
const ConversationsView = () => import('../views/ConversationsView.vue')
const ConversationDetailView = () => import('../views/ConversationDetailView.vue')
const NoticesView = () => import('../views/NoticesView.vue')
const NoticeDetailView = () => import('../views/NoticeDetailView.vue')
const AnalyticsView = () => import('../views/AnalyticsView.vue')
const SettingsView = () => import('../views/SettingsView.vue')
const FolloweesView = () => import('../views/FolloweesView.vue')
const FollowersView = () => import('../views/FollowersView.vue')
const BookmarksView = () => import('../views/BookmarksView.vue')
const WalletAdminView = () => import('../views/WalletAdminView.vue')
const AdminMarketDisputesView = () => import('../views/AdminMarketDisputesView.vue')
const ModerationView = () => import('../views/ModerationView.vue')
const OpsConsoleView = () => import('../views/OpsConsoleView.vue')
const UserManagementView = () => import('../views/UserManagementView.vue')
const ForbiddenView = () => import('../views/ForbiddenView.vue')
const NotFoundView = () => import('../views/NotFoundView.vue')

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/auth/login',
      name: 'login',
      component: LoginView,
      alias: ['/login'],
      meta: { title: '登录', subtitle: '回到讨论广场前，先确认你的身份。', navGroup: 'auth' }
    },
    {
      path: '/auth/register',
      name: 'register',
      component: RegisterView,
      meta: { title: '注册', subtitle: '创建你的身份，加入一场值得阅读的讨论。', navGroup: 'auth' }
    },
    {
      path: '/auth/password/reset',
      name: 'passwordReset',
      component: PasswordResetView,
      meta: { title: '找回密码', subtitle: '通过邮箱和验证码重新确认你的登录凭据。', navGroup: 'auth' }
    },
    { path: '/', redirect: { name: 'posts' } },
    {
      path: '/posts',
      name: 'posts',
      component: PostsView,
      meta: { title: '讨论首页', subtitle: '打开首页就进入最新讨论流，先看社区现在发生什么。', navGroup: 'explore' }
    },
    {
      path: '/posts/:postId',
      name: 'postDetail',
      component: PostDetailView,
      props: true,
      meta: {
        title: '帖子详情',
        subtitle: '先看主贴上下文，再顺着线程进入回复。',
        navGroup: 'explore'
      }
    },
    {
      path: '/search',
      name: 'search',
      component: SearchView,
      meta: { title: '搜索', subtitle: '从关键词、分类和标签里定位正在发生的讨论。', navGroup: 'explore' }
    },
    {
      path: '/market',
      name: 'market',
      component: MarketListView,
      meta: { title: '市场', subtitle: '一个入口浏览虚拟商品和实物商品。', navGroup: 'explore' }
    },
    {
      path: '/market/listings/:listingId',
      name: 'marketDetail',
      component: MarketDetailView,
      props: true,
      meta: { title: '商品详情', subtitle: '确认履约方式、库存与价格，再决定是否托管下单。', navGroup: 'explore' }
    },
    {
      path: '/wallet',
      name: 'wallet',
      component: WalletView,
      meta: { title: '积分钱包', subtitle: '查看余额、充值、提现与转账记录。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/publish',
      name: 'marketPublish',
      component: MarketPublishView,
      meta: { title: '发布商品', subtitle: '创建新的虚拟商品或实物商品。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/my-listings',
      name: 'marketMyListings',
      component: MarketMyListingsView,
      meta: { title: '我的出售', subtitle: '把发布、库存和卖单处理收成一个卖家工作面。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/my-listings/:listingId/inventory',
      name: 'marketInventory',
      component: MarketInventoryView,
      props: true,
      meta: { title: '库存管理', subtitle: '维护预存库存商品的卡密或兑换码。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/orders/buying',
      name: 'marketBuyingOrders',
      component: MarketBuyingOrdersView,
      meta: { title: '我的购买', subtitle: '查看托管、交付、确认与申诉状态。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/orders/selling',
      name: 'marketSellingOrders',
      component: MarketSellingOrdersView,
      meta: { title: '我的出售订单', subtitle: '集中处理交付、确认和争议。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/orders/:orderId',
      name: 'marketOrderDetail',
      component: MarketOrderDetailView,
      props: true,
      meta: { title: '订单详情', subtitle: '查看当前订单的托管、交付和争议状态。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/addresses',
      name: 'marketAddresses',
      component: MarketAddressesView,
      meta: { title: '收货地址', subtitle: '管理实物商品订单使用的收货地址。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/drive',
      name: 'drive',
      component: DriveView,
      meta: { title: '网盘', subtitle: '管理私有文件、分享链接和回收站。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/drive/s/:shareToken',
      name: 'driveShare',
      component: DriveShareView,
      props: true,
      meta: { title: '网盘分享', subtitle: '输入提取码后访问分享文件。', navGroup: 'public' }
    },
    {
      path: '/admin/wallet',
      name: 'walletAdmin',
      component: WalletAdminView,
      meta: { title: '钱包后台', subtitle: '冻结钱包、回滚交易与查看审计。', navGroup: 'admin', requiresAuth: true, roles: ['ROLE_ADMIN'] }
    },
    {
      path: '/admin/market/disputes',
      name: 'adminMarketDisputes',
      component: AdminMarketDisputesView,
      meta: { title: '争议裁定', subtitle: '管理员只处理最终裁定，不处理普通卖家动作。', navGroup: 'admin', requiresAuth: true, roles: ['ROLE_ADMIN'] }
    },
    {
      path: '/preview/editorial',
      redirect: { name: 'editorialPreviewB' }
    },
    {
      path: '/preview/editorial/a',
      name: 'editorialPreviewA',
      component: EditorialPreviewView,
      props: { variant: 'a' },
      meta: { title: '预览 A', subtitle: '作者与精选内容优先的首页方案。', navGroup: 'system' }
    },
    {
      path: '/preview/editorial/b',
      name: 'editorialPreviewB',
      component: EditorialPreviewView,
      props: { variant: 'b' },
      meta: { title: '预览 B', subtitle: '最新讨论流优先的首页方案。', navGroup: 'system' }
    },
    {
      path: '/preview/editorial/c',
      name: 'editorialPreviewC',
      component: EditorialPreviewView,
      props: { variant: 'c' },
      meta: { title: '预览 C', subtitle: '精选内容与最新讨论并行的混合首页方案。', navGroup: 'system' }
    },
    {
      path: '/messages',
      name: 'messages',
      component: ConversationsView,
      meta: { title: '私信', subtitle: '在同一个收件箱里处理会话与上下文。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/messages/:conversationId',
      name: 'messageDetail',
      component: ConversationDetailView,
      props: true,
      meta: {
        title: '私信线程',
        subtitle: '聚焦当前线程，而不是被工具式布局打断。',
        navGroup: 'me',
        requiresAuth: true
      }
    },
    {
      path: '/notices',
      name: 'notices',
      component: NoticesView,
      meta: { title: '通知', subtitle: '把互动、关注和治理提醒整理成收件箱。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/notices/:topic',
      name: 'noticeDetail',
      component: NoticeDetailView,
      props: true,
      meta: {
        title: '通知详情',
        subtitle: '按主题继续阅读，而不是把所有消息混在一起。',
        navGroup: 'me',
        requiresAuth: true
      }
    },
    {
      path: '/bookmarks',
      name: 'bookmarks',
      component: BookmarksView,
      meta: { title: '收藏', subtitle: '把值得反复回看的帖子留在自己的阅读清单里。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/analytics',
      name: 'analytics',
      component: AnalyticsView,
      meta: {
        title: '统计',
        subtitle: '安静地查看关键指标、时间范围与数据成熟度。',
        navGroup: 'admin',
        requiresAuth: true,
        roles: ['ROLE_ADMIN', 'ROLE_MODERATOR']
      }
    },
    {
      path: '/moderation',
      name: 'moderation',
      component: ModerationView,
      meta: {
        title: '治理后台',
        subtitle: '聚焦待处理举报、处置记录与高风险动作。',
        navGroup: 'admin',
        requiresAuth: true,
        roles: ['ROLE_ADMIN', 'ROLE_MODERATOR']
      }
    },
    {
      path: '/ops',
      name: 'opsConsole',
      component: OpsConsoleView,
      meta: {
        title: 'Ops Console',
        subtitle: '仅在确认风险与范围后使用的运维动作面板。',
        navGroup: 'admin',
        requiresAuth: true,
        roles: ['ROLE_ADMIN']
      }
    },
    {
      path: '/admin/users',
      name: 'userManagement',
      component: UserManagementView,
      meta: {
        title: '用户管理',
        subtitle: '在明确理由、风险和审计责任的前提下变更角色。',
        navGroup: 'admin',
        requiresAuth: true,
        roles: ['ROLE_ADMIN']
      }
    },
    {
      path: '/settings',
      name: 'settings',
      component: SettingsView,
      meta: { title: '设置', subtitle: '维护公开资料、头像与个人身份的一致性。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/users/:userId',
      name: 'userProfile',
      component: UserProfileView,
      props: true,
      meta: { title: '成员主页', subtitle: '查看这个成员的公开身份、关系和社区存在感。', navGroup: 'me' }
    },
    {
      path: '/users/:userId/followees',
      name: 'followees',
      component: FolloweesView,
      props: true,
      meta: { title: '关注列表', subtitle: '查看这位成员正在关注哪些人。', navGroup: 'me' }
    },
    {
      path: '/users/:userId/followers',
      name: 'followers',
      component: FollowersView,
      props: true,
      meta: { title: '粉丝列表', subtitle: '查看哪些成员正在留意这位用户的公开动态。', navGroup: 'me' }
    },
    {
      path: '/dev',
      name: 'dev',
      component: HomeView,
      meta: { title: '联调', subtitle: '开发与调试入口，不属于本轮产品级重设计。', navGroup: 'system', requiresAuth: true }
    },
    { path: '/403', name: 'forbidden', component: ForbiddenView, meta: { title: '无权限', subtitle: '你当前没有访问这一页所需的权限。', navGroup: 'system' } },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: NotFoundView, meta: { title: '未找到', subtitle: '当前地址没有对应内容。', navGroup: 'system' } }
  ]
})

router.beforeEach(authGuard)

export default router

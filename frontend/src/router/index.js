import { createRouter, createWebHashHistory } from 'vue-router'
import { authGuard } from './authGuard'
import { catalogRouteName, routeMeta } from './routeCatalog'

const LoginView = () => import('../views/LoginView.vue')
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
const MarketOrderListView = () => import('../views/MarketOrderListView.vue')
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
const FollowRelationListView = () => import('../views/FollowRelationListView.vue')
const BookmarksView = () => import('../views/BookmarksView.vue')
const WalletAdminView = () => import('../views/WalletAdminView.vue')
const AdminMarketDisputesView = () => import('../views/AdminMarketDisputesView.vue')
const ModerationView = () => import('../views/ModerationView.vue')
const UserManagementView = () => import('../views/UserManagementView.vue')
const ForbiddenView = () => import('../views/ForbiddenView.vue')
const NotFoundView = () => import('../views/NotFoundView.vue')

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/auth/login',
      name: catalogRouteName('login'),
      component: LoginView,
      meta: routeMeta('login', { title: '登录', subtitle: '回到讨论广场前，先确认你的身份。' })
    },
    {
      path: '/auth/register',
      name: catalogRouteName('register'),
      component: RegisterView,
      meta: routeMeta('register', { title: '注册', subtitle: '创建你的身份，加入一场值得阅读的讨论。' })
    },
    {
      path: '/auth/password/reset',
      name: catalogRouteName('passwordReset'),
      component: PasswordResetView,
      meta: routeMeta('passwordReset', { title: '找回密码', subtitle: '通过邮箱和验证码重新确认你的登录凭据。' })
    },
    { path: '/', redirect: { name: 'posts' } },
    {
      path: '/posts',
      name: catalogRouteName('posts'),
      component: PostsView,
      meta: routeMeta('posts', { title: '讨论首页', subtitle: '打开首页就进入最新讨论流，先看社区现在发生什么。' })
    },
    {
      path: '/posts/:postId',
      name: catalogRouteName('postDetail'),
      component: PostDetailView,
      props: true,
      meta: routeMeta('postDetail', {
        title: '帖子详情',
        subtitle: '先看主贴上下文，再顺着线程进入回复。'
      })
    },
    {
      path: '/search',
      name: catalogRouteName('search'),
      component: SearchView,
      meta: routeMeta('search', { title: '搜索', subtitle: '从关键词、分类和标签里定位正在发生的讨论。' })
    },
    {
      path: '/market',
      name: catalogRouteName('market'),
      component: MarketListView,
      meta: routeMeta('market', { title: '市场', subtitle: '一个入口浏览虚拟商品和实物商品。' })
    },
    {
      path: '/market/listings/:listingId',
      name: catalogRouteName('marketDetail'),
      component: MarketDetailView,
      props: true,
      meta: routeMeta('marketDetail', { title: '商品详情', subtitle: '确认履约方式、库存与价格，再决定是否托管下单。' })
    },
    {
      path: '/wallet',
      name: catalogRouteName('wallet'),
      component: WalletView,
      meta: routeMeta('wallet', { title: '积分钱包', subtitle: '查看站内积分余额、账务流水与转账记录。' })
    },
    {
      path: '/market/publish',
      name: catalogRouteName('marketPublish'),
      component: MarketPublishView,
      meta: routeMeta('marketPublish', { title: '发布商品', subtitle: '创建新的虚拟商品或实物商品。' })
    },
    {
      path: '/market/my-listings',
      name: catalogRouteName('marketMyListings'),
      component: MarketMyListingsView,
      meta: routeMeta('marketMyListings', { title: '我的出售', subtitle: '把发布、库存和卖单处理收成一个卖家工作面。' })
    },
    {
      path: '/market/my-listings/:listingId/inventory',
      name: catalogRouteName('marketInventory'),
      component: MarketInventoryView,
      props: true,
      meta: routeMeta('marketInventory', { title: '库存管理', subtitle: '维护预存库存商品的卡密或兑换码。' })
    },
    {
      path: '/market/orders/buying',
      name: catalogRouteName('marketBuyingOrders'),
      component: MarketOrderListView,
      props: { side: 'buying' },
      meta: routeMeta('marketBuyingOrders', { title: '我的购买', subtitle: '查看托管、交付、确认与申诉状态。' })
    },
    {
      path: '/market/orders/selling',
      name: catalogRouteName('marketSellingOrders'),
      component: MarketOrderListView,
      props: { side: 'selling' },
      meta: routeMeta('marketSellingOrders', { title: '我的出售订单', subtitle: '集中处理交付、确认和争议。' })
    },
    {
      path: '/market/orders/:orderId',
      name: catalogRouteName('marketOrderDetail'),
      component: MarketOrderDetailView,
      props: true,
      meta: routeMeta('marketOrderDetail', { title: '订单详情', subtitle: '查看当前订单的托管、交付和争议状态。' })
    },
    {
      path: '/market/addresses',
      name: catalogRouteName('marketAddresses'),
      component: MarketAddressesView,
      meta: routeMeta('marketAddresses', { title: '收货地址', subtitle: '管理实物商品订单使用的收货地址。' })
    },
    {
      path: '/drive',
      name: catalogRouteName('drive'),
      component: DriveView,
      meta: routeMeta('drive', { title: '网盘', subtitle: '管理私有文件、分享链接和回收站。' })
    },
    {
      path: '/drive/s/:shareToken',
      name: catalogRouteName('driveShare'),
      component: DriveShareView,
      props: true,
      meta: routeMeta('driveShare', { title: '网盘分享', subtitle: '输入提取码后访问分享文件。' })
    },
    {
      path: '/admin/wallet',
      name: catalogRouteName('walletAdmin'),
      component: WalletAdminView,
      meta: routeMeta('walletAdmin', { title: '钱包后台', subtitle: '冻结钱包、回滚交易与查看审计。' })
    },
    {
      path: '/admin/market/disputes',
      name: catalogRouteName('adminMarketDisputes'),
      component: AdminMarketDisputesView,
      meta: routeMeta('adminMarketDisputes', { title: '争议裁定', subtitle: '管理员只处理最终裁定，不处理普通卖家动作。' })
    },
    {
      path: '/messages',
      name: catalogRouteName('messages'),
      component: ConversationsView,
      meta: routeMeta('messages', { title: '私信', subtitle: '在同一个收件箱里处理会话与上下文。' })
    },
    {
      path: '/messages/:conversationId',
      name: catalogRouteName('messageDetail'),
      component: ConversationDetailView,
      props: true,
      meta: routeMeta('messageDetail', {
        title: '私信线程',
        subtitle: '聚焦当前线程，而不是被工具式布局打断。'
      })
    },
    {
      path: '/notices',
      name: catalogRouteName('notices'),
      component: NoticesView,
      meta: routeMeta('notices', { title: '通知', subtitle: '把互动、关注和治理提醒整理成收件箱。' })
    },
    {
      path: '/notices/:topic',
      name: catalogRouteName('noticeDetail'),
      component: NoticeDetailView,
      props: true,
      meta: routeMeta('noticeDetail', {
        title: '通知详情',
        subtitle: '按主题继续阅读，而不是把所有消息混在一起。'
      })
    },
    {
      path: '/bookmarks',
      name: catalogRouteName('bookmarks'),
      component: BookmarksView,
      meta: routeMeta('bookmarks', { title: '收藏', subtitle: '把值得反复回看的帖子留在自己的阅读清单里。' })
    },
    {
      path: '/analytics',
      name: catalogRouteName('analytics'),
      component: AnalyticsView,
      meta: routeMeta('analytics', {
        title: '统计',
        subtitle: '安静地查看关键指标、时间范围与数据成熟度。'
      })
    },
    {
      path: '/moderation',
      name: catalogRouteName('moderation'),
      component: ModerationView,
      meta: routeMeta('moderation', {
        title: '治理后台',
        subtitle: '聚焦待处理举报、处置记录与高风险动作。'
      })
    },
    {
      path: '/admin/users',
      name: catalogRouteName('userManagement'),
      component: UserManagementView,
      meta: routeMeta('userManagement', {
        title: '用户管理',
        subtitle: '在明确理由、风险和审计责任的前提下变更角色。'
      })
    },
    {
      path: '/settings',
      name: catalogRouteName('settings'),
      component: SettingsView,
      meta: routeMeta('settings', { title: '设置', subtitle: '维护公开资料、头像与个人身份的一致性。' })
    },
    {
      path: '/users/:userId',
      name: catalogRouteName('userProfile'),
      component: UserProfileView,
      props: true,
      meta: routeMeta('userProfile', { title: '成员主页', subtitle: '查看这个成员的公开身份、关系和社区存在感。' })
    },
    {
      path: '/users/:userId/followees',
      name: catalogRouteName('followees'),
      component: FollowRelationListView,
      props: (route) => ({ relationKind: 'followees', userId: route.params.userId }),
      meta: routeMeta('followees', { title: '关注列表', subtitle: '查看这位成员正在关注哪些人。' })
    },
    {
      path: '/users/:userId/followers',
      name: catalogRouteName('followers'),
      component: FollowRelationListView,
      props: (route) => ({ relationKind: 'followers', userId: route.params.userId }),
      meta: routeMeta('followers', { title: '粉丝列表', subtitle: '查看哪些成员正在留意这位用户的公开动态。' })
    },
    { path: '/403', name: catalogRouteName('forbidden'), component: ForbiddenView, meta: routeMeta('forbidden', { title: '无权限', subtitle: '你当前没有访问这一页所需的权限。' }) },
    { path: '/:pathMatch(.*)*', name: catalogRouteName('notFound'), component: NotFoundView, meta: routeMeta('notFound', { title: '未找到', subtitle: '当前地址没有对应内容。' }) }
  ]
})

router.beforeEach(authGuard)

export default router

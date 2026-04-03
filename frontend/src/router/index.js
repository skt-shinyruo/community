import { createRouter, createWebHashHistory } from 'vue-router'
import { authGuard } from './authGuard'

import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import EditorialPreviewView from '../views/EditorialPreviewView.vue'
import PostsView from '../views/PostsView.vue'
import PostDetailView from '../views/PostDetailView.vue'
import UserProfileView from '../views/UserProfileView.vue'
import RegisterView from '../views/RegisterView.vue'
import PasswordResetView from '../views/PasswordResetView.vue'
import SearchView from '../views/SearchView.vue'
import VirtualMarketListView from '../views/VirtualMarketListView.vue'
import VirtualMarketDetailView from '../views/VirtualMarketDetailView.vue'
import VirtualMarketPublishView from '../views/VirtualMarketPublishView.vue'
import VirtualMarketMyListingsView from '../views/VirtualMarketMyListingsView.vue'
import VirtualMarketInventoryView from '../views/VirtualMarketInventoryView.vue'
import VirtualMarketBuyingOrdersView from '../views/VirtualMarketBuyingOrdersView.vue'
import VirtualMarketSellingOrdersView from '../views/VirtualMarketSellingOrdersView.vue'
import VirtualMarketOrderDetailView from '../views/VirtualMarketOrderDetailView.vue'
import WalletView from '../views/WalletView.vue'
import ConversationsView from '../views/ConversationsView.vue'
import ConversationDetailView from '../views/ConversationDetailView.vue'
import NoticesView from '../views/NoticesView.vue'
import NoticeDetailView from '../views/NoticeDetailView.vue'
import AnalyticsView from '../views/AnalyticsView.vue'
import SettingsView from '../views/SettingsView.vue'
import FolloweesView from '../views/FolloweesView.vue'
import FollowersView from '../views/FollowersView.vue'
import BookmarksView from '../views/BookmarksView.vue'
import LeaderboardView from '../views/LeaderboardView.vue'
import GrowthCenterView from '../views/GrowthCenterView.vue'
import RewardShopView from '../views/RewardShopView.vue'
import RewardOrderHistoryView from '../views/RewardOrderHistoryView.vue'
import GrowthAdminView from '../views/GrowthAdminView.vue'
import WalletAdminView from '../views/WalletAdminView.vue'
import AdminVirtualDisputesView from '../views/AdminVirtualDisputesView.vue'
import RewardOpsView from '../views/RewardOpsView.vue'
import ModerationView from '../views/ModerationView.vue'
import OpsConsoleView from '../views/OpsConsoleView.vue'
import UserManagementView from '../views/UserManagementView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import NotFoundView from '../views/NotFoundView.vue'

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
      path: '/market/virtual',
      name: 'virtualMarket',
      component: VirtualMarketListView,
      meta: { title: '虚拟市场', subtitle: '浏览用户出售的虚拟商品。', navGroup: 'explore' }
    },
    {
      path: '/market/virtual/listings/:listingId',
      name: 'virtualMarketDetail',
      component: VirtualMarketDetailView,
      props: true,
      meta: { title: '商品详情', subtitle: '确认交付方式、库存与价格，再决定是否托管下单。', navGroup: 'explore' }
    },
    {
      path: '/wallet',
      name: 'wallet',
      component: WalletView,
      meta: { title: '积分钱包', subtitle: '查看余额、充值、提现与转账记录。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/virtual/publish',
      name: 'virtualMarketPublish',
      component: VirtualMarketPublishView,
      meta: { title: '发布商品', subtitle: '创建新的虚拟商品并决定是自动交付还是手工交付。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/virtual/my-listings',
      name: 'virtualMarketMyListings',
      component: VirtualMarketMyListingsView,
      meta: { title: '我的出售', subtitle: '把发布、库存和卖单处理收成一个卖家工作面。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/virtual/my-listings/:listingId/inventory',
      name: 'virtualMarketInventory',
      component: VirtualMarketInventoryView,
      props: true,
      meta: { title: '库存管理', subtitle: '维护预存库存商品的卡密或兑换码。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/virtual/orders/buying',
      name: 'virtualMarketBuyingOrders',
      component: VirtualMarketBuyingOrdersView,
      meta: { title: '我的购买', subtitle: '查看托管、交付、确认与申诉状态。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/virtual/orders/selling',
      name: 'virtualMarketSellingOrders',
      component: VirtualMarketSellingOrdersView,
      meta: { title: '我的出售订单', subtitle: '集中处理交付、确认和争议。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/market/virtual/orders/:orderId',
      name: 'virtualMarketOrderDetail',
      component: VirtualMarketOrderDetailView,
      props: true,
      meta: { title: '订单详情', subtitle: '查看当前订单的托管、交付和争议状态。', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/growth',
      name: 'growthCenter',
      component: GrowthCenterView,
      meta: { title: '旧资产入口', subtitle: '这个地址仅保留兼容说明，统一资产入口已迁入钱包。', navGroup: 'system', requiresAuth: true }
    },
    {
      path: '/rewards/shop',
      name: 'rewardShop',
      component: RewardShopView,
      meta: { title: '资产市场预览', subtitle: '旧地址仅保留过渡说明，后续消费场景将并入统一市场。', navGroup: 'system', requiresAuth: true }
    },
    {
      path: '/rewards/orders',
      name: 'rewardOrders',
      component: RewardOrderHistoryView,
      meta: { title: '历史订单', subtitle: '旧地址仅保留过渡说明，后续订单会统一收口。', navGroup: 'system', requiresAuth: true }
    },
    {
      path: '/admin/wallet',
      name: 'walletAdmin',
      component: WalletAdminView,
      meta: { title: '钱包后台', subtitle: '冻结钱包、回滚交易与查看审计。', navGroup: 'admin', requiresAuth: true, roles: ['ROLE_ADMIN'] }
    },
    {
      path: '/admin/market/virtual/disputes',
      name: 'adminVirtualDisputes',
      component: AdminVirtualDisputesView,
      meta: { title: '争议裁定', subtitle: '管理员只处理最终裁定，不处理普通卖家动作。', navGroup: 'admin', requiresAuth: true, roles: ['ROLE_ADMIN'] }
    },
    {
      path: '/admin/growth',
      name: 'growthAdmin',
      component: GrowthAdminView,
      meta: { title: '旧后台入口', subtitle: '旧资产后台已迁入钱包后台，这里只保留兼容说明。', navGroup: 'system', requiresAuth: true, roles: ['ROLE_ADMIN'] }
    },
    {
      path: '/admin/rewards',
      name: 'rewardOps',
      component: RewardOpsView,
      meta: { title: '旧运营入口', subtitle: '旧地址仅保留兼容说明，后续会并入统一市场运营台。', navGroup: 'system', requiresAuth: true, roles: ['ROLE_ADMIN'] }
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
      path: '/leaderboard',
      name: 'leaderboard',
      component: LeaderboardView,
      meta: { title: '成员概览', subtitle: '旧排行榜入口已下线，公开成员关系仍保留在个人主页。', navGroup: 'system' }
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

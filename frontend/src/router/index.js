import { createRouter, createWebHashHistory } from 'vue-router'
import { authGuard } from './authGuard'

import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import PostsView from '../views/PostsView.vue'
import PostDetailView from '../views/PostDetailView.vue'
import UserProfileView from '../views/UserProfileView.vue'
import RegisterView from '../views/RegisterView.vue'
import ActivationView from '../views/ActivationView.vue'
import PasswordResetView from '../views/PasswordResetView.vue'
import SearchView from '../views/SearchView.vue'
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
import ModerationView from '../views/ModerationView.vue'
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
      meta: { title: '登录', subtitle: '欢迎回来', navGroup: 'auth' }
    },
    {
      path: '/auth/register',
      name: 'register',
      component: RegisterView,
      meta: { title: '注册', subtitle: '创建账号', navGroup: 'auth' }
    },
    {
      path: '/auth/password/reset',
      name: 'passwordReset',
      component: PasswordResetView,
      meta: { title: '找回密码', subtitle: '重置账号密码', navGroup: 'auth' }
    },
    {
      path: '/auth/activation/:userId/:code',
      name: 'activation',
      component: ActivationView,
      props: true,
      meta: {
        title: (route) => `账号激活 #${route?.params?.userId || ''}`,
        subtitle: '激活账号以继续使用',
        navGroup: 'auth'
      }
    },
    { path: '/', redirect: { name: 'posts' } },
    {
      path: '/posts',
      name: 'posts',
      component: PostsView,
      meta: { title: '帖子', subtitle: '高信息密度浏览 · 支持最新/最热', navGroup: 'explore' }
    },
    {
      path: '/posts/:postId',
      name: 'postDetail',
      component: PostDetailView,
      props: true,
      meta: {
        title: (route) => `帖子 #${route?.params?.postId || ''}`,
        subtitle: '正文与评论',
        navGroup: 'explore'
      }
    },
    {
      path: '/search',
      name: 'search',
      component: SearchView,
      meta: { title: '搜索', subtitle: '全局搜索 · 关键词高亮', navGroup: 'explore' }
    },
    {
      path: '/messages',
      name: 'messages',
      component: ConversationsView,
      meta: { title: '私信', subtitle: '会话列表', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/messages/:conversationId',
      name: 'messageDetail',
      component: ConversationDetailView,
      props: true,
      meta: {
        title: (route) => `私信 #${route?.params?.conversationId || ''}`,
        subtitle: '聊天详情',
        navGroup: 'me',
        requiresAuth: true
      }
    },
    {
      path: '/notices',
      name: 'notices',
      component: NoticesView,
      meta: { title: '通知', subtitle: '系统与互动消息', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/notices/:topic',
      name: 'noticeDetail',
      component: NoticeDetailView,
      props: true,
      meta: {
        title: (route) => `通知：${route?.params?.topic || ''}`,
        subtitle: '通知详情',
        navGroup: 'me',
        requiresAuth: true
      }
    },
    {
      path: '/bookmarks',
      name: 'bookmarks',
      component: BookmarksView,
      meta: { title: '收藏', subtitle: '我收藏的帖子', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/leaderboard',
      name: 'leaderboard',
      component: LeaderboardView,
      meta: { title: '排行榜', subtitle: '按积分排序', navGroup: 'explore' }
    },
    {
      path: '/analytics',
      name: 'analytics',
      component: AnalyticsView,
      meta: {
        title: '统计',
        subtitle: '管理员/版主可见',
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
        subtitle: '举报队列与处置审计',
        navGroup: 'admin',
        requiresAuth: true,
        roles: ['ROLE_ADMIN', 'ROLE_MODERATOR']
      }
    },
    {
      path: '/settings',
      name: 'settings',
      component: SettingsView,
      meta: { title: '设置', subtitle: '账号与偏好', navGroup: 'me', requiresAuth: true }
    },
    {
      path: '/users/:userId',
      name: 'userProfile',
      component: UserProfileView,
      props: true,
      meta: { title: (route) => `用户 #${route?.params?.userId || ''}`, subtitle: '个人主页', navGroup: 'me' }
    },
    {
      path: '/users/:userId/followees',
      name: 'followees',
      component: FolloweesView,
      props: true,
      meta: { title: '关注列表', subtitle: '你关注的人', navGroup: 'me' }
    },
    {
      path: '/users/:userId/followers',
      name: 'followers',
      component: FollowersView,
      props: true,
      meta: { title: '粉丝列表', subtitle: '关注你的人', navGroup: 'me' }
    },
    {
      path: '/dev',
      name: 'dev',
      component: HomeView,
      meta: { title: '联调', subtitle: '开发与调试入口', navGroup: 'system', requiresAuth: true }
    },
    { path: '/403', name: 'forbidden', component: ForbiddenView, meta: { title: '无权限', navGroup: 'system' } },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: NotFoundView, meta: { title: '未找到', navGroup: 'system' } }
  ]
})

router.beforeEach(authGuard)

export default router

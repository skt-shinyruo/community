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
import ForbiddenView from '../views/ForbiddenView.vue'
import NotFoundView from '../views/NotFoundView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/auth/login', name: 'login', component: LoginView, alias: ['/login'] },
    { path: '/auth/register', name: 'register', component: RegisterView },
    { path: '/auth/password/reset', name: 'passwordReset', component: PasswordResetView },
    { path: '/auth/activation/:userId/:code', name: 'activation', component: ActivationView, props: true },
    { path: '/', redirect: { name: 'posts' } },
    { path: '/posts', name: 'posts', component: PostsView },
    { path: '/posts/:postId', name: 'postDetail', component: PostDetailView, props: true },
    { path: '/search', name: 'search', component: SearchView },
    { path: '/messages', name: 'messages', component: ConversationsView, meta: { requiresAuth: true } },
    {
      path: '/messages/:conversationId',
      name: 'messageDetail',
      component: ConversationDetailView,
      props: true,
      meta: { requiresAuth: true }
    },
    { path: '/notices', name: 'notices', component: NoticesView, meta: { requiresAuth: true } },
    { path: '/notices/:topic', name: 'noticeDetail', component: NoticeDetailView, props: true, meta: { requiresAuth: true } },
    {
      path: '/analytics',
      name: 'analytics',
      component: AnalyticsView,
      meta: { requiresAuth: true, roles: ['ROLE_ADMIN', 'ROLE_MODERATOR'] }
    },
    { path: '/settings', name: 'settings', component: SettingsView, meta: { requiresAuth: true } },
    { path: '/users/:userId', name: 'userProfile', component: UserProfileView, props: true },
    { path: '/users/:userId/followees', name: 'followees', component: FolloweesView, props: true },
    { path: '/users/:userId/followers', name: 'followers', component: FollowersView, props: true },
    { path: '/dev', name: 'dev', component: HomeView, meta: { requiresAuth: true } },
    { path: '/403', name: 'forbidden', component: ForbiddenView },
    { path: '/:pathMatch(.*)*', name: 'notFound', component: NotFoundView }
  ]
})

router.beforeEach(authGuard)

export default router

import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/', name: 'home', component: HomeView, meta: { requiresAuth: true } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta?.requiresAuth && !auth.accessToken) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.accessToken) {
    return { name: 'home' }
  }
})

export default router


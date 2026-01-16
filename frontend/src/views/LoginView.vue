<template>
  <div class="card">
    <div class="stack">
      <div>
        <div class="muted">用户名</div>
        <input class="input" v-model.trim="form.username" placeholder="username" autocomplete="username" />
      </div>
      <div>
        <div class="muted">密码</div>
        <input class="input" v-model.trim="form.password" placeholder="password" type="password" autocomplete="current-password" />
      </div>
      <div class="row">
        <button class="btn" @click="onLogin" :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button>
        <span class="error" v-if="error">{{ error }}</span>
      </div>
      <div class="muted">
        本地开发建议：先启动 gateway(8080) 与 auth-service(8082)，再运行前端 dev server，并确保 Vite proxy 生效。
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import http from '../api/http'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function onLogin() {
  error.value = ''
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    const resp = await http.post('/api/auth/login', {
      username: form.username,
      password: form.password
    })
    const token = resp?.data?.data?.accessToken
    if (!token) {
      error.value = resp?.data?.message || '登录失败'
      return
    }
    auth.setAccessToken(token)
    router.replace({ name: 'home' })
  } catch (e) {
    error.value = e?.response?.data?.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>


<template>
  <UiCard class="auth-view-card">
    <UiPageHeader>
      <template #title>登录</template>
      <template #subtitle>回到讨论广场前，先确认你的身份与当前登录状态。</template>
    </UiPageHeader>

    <form class="stack login-form" @submit.prevent="onLogin">
      <UiField label="用户名">
        <UiInput v-model.trim="form.username" placeholder="请输入用户名" autocomplete="username" />
      </UiField>

      <UiField label="密码">
        <UiInput v-model="form.password" placeholder="请输入密码" type="password" autocomplete="current-password" />
      </UiField>

      <UiField v-if="captchaRequired" label="验证码">
        <div class="row">
          <UiInput v-model.trim="form.captcha" placeholder="请输入验证码" autocomplete="off" class="captcha-input" />
          <button
            v-if="captchaSrc"
            type="button"
            class="captcha-refresh"
            title="点击刷新验证码"
            @click="refreshCaptcha"
          >
            <img :src="captchaSrc" alt="验证码" class="captcha-img" />
          </button>
        </div>
      </UiField>

      <div v-if="error" class="error">{{ error }}</div>

      <div class="auth-secondary-row">
        <UiButton variant="ghost" to="/auth/password/reset">忘记密码？</UiButton>
      </div>

      <UiButton type="submit" :disabled="loading" class="auth-submit-btn">
        {{ loading ? '登录中…' : '登录' }}
      </UiButton>

      <div class="row auth-links">
        <span class="muted">还没有账号？</span>
        <RouterLink to="/auth/register" class="auth-link">去注册</RouterLink>
        <span class="muted">·</span>
        <RouterLink to="/posts" class="muted">返回社区</RouterLink>
      </div>
    </form>
  </UiCard>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ensureSessionReady } from '../auth/session'
import { useAuthStore } from '../stores/auth'
import { backendErrorMessage, isCaptchaRejected } from '../api/backendError'
import { login as apiLogin, issueCaptcha } from '../api/services/authService'
import UiCard from '../components/ui/UiCard.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({ username: '', password: '', captcha: '' })
const loading = ref(false)
const error = ref('')
const captchaSrc = ref('')
const captchaId = ref('')
const captchaRequired = ref(false)

async function refreshCaptcha() {
  try {
    const { data } = await issueCaptcha()
    captchaId.value = data?.captchaId || ''
    captchaSrc.value = data?.imageBase64 ? `data:image/png;base64,${data.imageBase64}` : ''
  } catch {
    captchaId.value = ''
    captchaSrc.value = ''
  }
}

async function onLogin() {
  error.value = ''
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }
  if (captchaRequired.value && !form.captcha) {
     error.value = '请输入验证码'
     return
  }

  loading.value = true
  try {
    const captcha = captchaRequired.value ? { captchaId: captchaId.value, captchaCode: form.captcha } : {}
    const { data } = await apiLogin(form.username, form.password, captcha)
    const token = data?.accessToken
    if (!token) throw new Error('No access token returned')

    auth.installSession({ accessToken: token, me: null })
    const session = await ensureSessionReady({ auth })
    if (session.state === 'anonymous') {
      auth.clear()
      error.value = '登录状态已失效，请重新登录'
      return
    }

    const redirect = route.query.redirect
    router.replace(redirect && redirect.startsWith('/') ? redirect : { name: 'posts' })
  } catch (e) {
    if (isCaptchaRejected(e)) {
      captchaRequired.value = true
      error.value = backendErrorMessage(e, '需要验证码')
      await refreshCaptcha()
    } else {
      error.value = backendErrorMessage(e, '登录失败')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-form {
  margin-top: var(--space-4);
  gap: var(--space-4);
}

.captcha-input {
  flex: 1;
}

.captcha-refresh {
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: transparent;
  cursor: pointer;
  line-height: 0;
}

.captcha-refresh:hover {
  border-color: var(--border-strong);
}

.captcha-refresh:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.captcha-img {
  display: block;
  height: 40px;
  border-radius: var(--radius-md);
}

.auth-secondary-row {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-1);
}

.auth-submit-btn {
  min-height: calc(var(--control-height) + var(--space-1));
  font-size: var(--text-md);
}

.auth-links {
  justify-content: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  font-size: var(--text-sm);
  margin-top: var(--space-1);
}

.auth-link {
  font-weight: 700;
  color: var(--link-color);
}

.auth-link:hover {
  text-decoration: underline;
}
</style>

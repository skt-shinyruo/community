<template>
  <UiCard>
    <UiPageHeader>
      <template #title>登录</template>
      <template #subtitle>登录失败达到阈值后会触发验证码（risk-based）</template>
      <template #actions>
        <RouterLink class="btn secondary" to="/auth/register">去注册</RouterLink>
      </template>
    </UiPageHeader>

    <div class="stack" style="margin-top: 12px">
      <div class="stack" style="gap: 8px">
        <div class="muted" style="font-size: 12px">用户名</div>
        <UiInput v-model.trim="form.username" placeholder="username" autocomplete="username" />
      </div>

      <div class="stack" style="gap: 8px">
        <div class="muted" style="font-size: 12px">密码</div>
        <UiInput v-model.trim="form.password" placeholder="password" type="password" autocomplete="current-password" />
      </div>

      <div class="stack" style="gap: 10px">
        <div class="row" style="justify-content: space-between; flex-wrap: wrap">
          <div class="muted" style="font-size: 12px">验证码</div>
          <UiButton variant="secondary" v-if="captchaRequired" @click="refreshCaptcha" :disabled="loading">刷新</UiButton>
        </div>
        <div v-if="captchaRequired" class="row" style="align-items: center; flex-wrap: wrap">
          <UiInput v-model.trim="form.captcha" placeholder="captcha" autocomplete="off" style="max-width: 180px" />
          <img
            v-if="captchaSrc"
            :src="captchaSrc"
            alt="captcha"
            title="点击刷新验证码"
            style="height: 40px; width: 120px; cursor: pointer; border: 1px solid var(--border); border-radius: 10px"
            @click="refreshCaptcha"
          />
        </div>
        <div v-else class="muted" style="font-size: 12px">
          未触发验证码时可直接登录；若后端返回“需要验证码”，将自动显示验证码区域。
        </div>
      </div>

      <div class="row" style="justify-content: space-between; flex-wrap: wrap">
        <div class="row">
          <UiButton @click="onLogin" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</UiButton>
          <RouterLink class="btn ghost" to="/auth/password/reset">忘记密码</RouterLink>
          <span class="error" v-if="error">{{ error }}</span>
        </div>
        <RouterLink class="btn ghost" to="/posts">返回社区</RouterLink>
      </div>

      <div class="muted" style="font-size: 12px">
        本地开发：先启动 docker compose（gateway 映射到 12882），再运行前端 dev server（Vite proxy 默认转发 /api 到 12882，可用 VITE_DEV_PROXY_TARGET 覆盖）。
      </div>
    </div>
  </UiCard>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login as apiLogin, me as apiMe, issueCaptcha } from '../api/services/authService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'

const emit = defineEmits(['trace'])

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
    const { data, traceId } = await issueCaptcha()
    emit('trace', traceId || '')
    captchaId.value = data?.captchaId || ''
    captchaSrc.value = data?.imageBase64 ? `data:image/png;base64,${data.imageBase64}` : ''
  } catch {
    captchaId.value = ''
    captchaSrc.value = ''
  }
}

function safeRedirectPath(v) {
  if (typeof v !== 'string') return ''
  if (!v.startsWith('/')) return ''
  // 简单防护：避免 // 或包含协议的跳转
  if (v.startsWith('//') || v.includes('://')) return ''
  return v
}

async function onLogin() {
  error.value = ''
  if (!form.username || !form.password || !form.captcha) {
    if (!form.username || !form.password) {
      error.value = '请输入用户名/密码'
      return
    }
    if (captchaRequired.value) {
      error.value = '请输入验证码'
      return
    }
  }

  loading.value = true
  try {
    const captcha = captchaRequired.value ? { captchaId: captchaId.value, captchaCode: form.captcha } : {}
    const { data, traceId } = await apiLogin(form.username, form.password, captcha)
    emit('trace', traceId || '')
    const token = data?.accessToken
    if (!token) {
      error.value = '登录失败：未返回 accessToken'
      return
    }
    auth.setAccessToken(token)

    try {
      const me = await apiMe()
      auth.setMe(me?.data || null)
      if (me?.traceId) emit('trace', me.traceId)
    } catch {}

    const redirect = safeRedirectPath(route.query.redirect)
    if (redirect) {
      router.replace(redirect)
      return
    }

    router.replace({ name: 'posts' })
  } catch (e) {
    const code = e?.code
    if (code === 10005) {
      captchaRequired.value = true
      error.value = e?.message || '需要验证码，请完成验证后再试'
      await refreshCaptcha()
      return
    }
    if (code === 10006) {
      captchaRequired.value = true
      error.value = e?.message || '验证码不正确或已失效'
      form.captcha = ''
      await refreshCaptcha()
      return
    }

    error.value = e?.message || '登录失败'
    if (captchaRequired.value) {
      form.captcha = ''
      await refreshCaptcha()
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // risk-based：默认不展示验证码，等后端要求时再获取
})
</script>

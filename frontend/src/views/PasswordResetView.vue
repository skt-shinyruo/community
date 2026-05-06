<template>
  <UiCard class="auth-view-card reset-card">
    <UiPageHeader>
      <template #title>找回密码</template>
      <template #subtitle>
        <span v-if="mode === 'confirm'">使用邮件中的 token 重置密码</span>
        <span v-else>输入邮箱后发送重置链接（为避免用户枚举，响应不会区分邮箱是否存在）</span>
      </template>
      <template #actions>
        <RouterLink class="btn secondary" to="/auth/login">去登录</RouterLink>
      </template>
    </UiPageHeader>

    <div class="reset-form">
      <div v-if="error" class="error">{{ error }}</div>
      <div v-if="successMsg" class="muted">{{ successMsg }}</div>

      <div v-if="mode === 'request'" class="reset-stack">
        <div class="reset-field">
          <div class="reset-label">邮箱</div>
          <UiInput v-model.trim="form.email" placeholder="name@example.com" autocomplete="email" />
        </div>
      </div>

      <div v-else class="reset-stack">
        <div class="reset-field">
          <div class="reset-label">新密码</div>
          <UiInput v-model.trim="form.newPassword" placeholder="请输入新密码" type="password" autocomplete="new-password" />
        </div>
        <div class="muted reset-token-note">resetToken：{{ shortToken }}</div>
      </div>

      <div class="reset-stack">
        <div class="reset-row">
          <div class="reset-label">验证码</div>
          <UiButton variant="secondary" @click="refreshCaptcha" :disabled="loading">刷新</UiButton>
        </div>
        <div class="reset-captcha-row">
          <UiInput v-model.trim="form.captcha" placeholder="请输入验证码" autocomplete="off" class="reset-captcha-input" />
          <img
            v-if="captchaSrc"
            :src="captchaSrc"
            alt="验证码"
            title="点击刷新验证码"
            class="reset-captcha-img"
            @click="refreshCaptcha"
          />
        </div>
      </div>

      <div class="reset-actions">
        <div class="reset-primary-actions">
          <UiButton v-if="mode === 'request'" @click="onRequestReset" :disabled="loading">
            {{ loading ? '提交中…' : '发送重置链接' }}
          </UiButton>
          <UiButton v-else @click="onConfirmReset" :disabled="loading">
            {{ loading ? '重置中…' : '重置密码' }}
          </UiButton>
        </div>
        <RouterLink class="btn ghost" to="/posts">返回社区</RouterLink>
      </div>

      <UiState
        v-if="debugResetLink"
        variant="development"
        title="本地 / 测试重置链接"
        :description="debugResetLink"
      >
        <template #actions>
          <UiButton variant="secondary" @click="openResetLink">打开重置页</UiButton>
        </template>
      </UiState>
    </div>
  </UiCard>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { issueCaptcha, requestPasswordReset, confirmPasswordReset } from '../api/services/authService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'

const emit = defineEmits(['trace'])
const route = useRoute()
const router = useRouter()

const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''))
const mode = computed(() => (token.value ? 'confirm' : 'request'))
const shortToken = computed(() => {
  if (!token.value) return ''
  if (token.value.length <= 12) return token.value
  return `${token.value.slice(0, 6)}…${token.value.slice(-6)}`
})

const form = reactive({ email: '', newPassword: '', captcha: '' })
const captchaId = ref('')
const captchaSrc = ref('')
const loading = ref(false)
const error = ref('')
const successMsg = ref('')
const debugResetLink = ref('')

async function refreshCaptcha() {
  try {
    const { data, traceId } = await issueCaptcha()
    emit('trace', traceId || '')
    captchaId.value = data?.captchaId || ''
    captchaSrc.value = data?.imageBase64 ? `data:image/png;base64,${data.imageBase64}` : ''
    form.captcha = ''
  } catch {
    captchaId.value = ''
    captchaSrc.value = ''
  }
}

async function onRequestReset() {
  error.value = ''
  successMsg.value = ''
  debugResetLink.value = ''

  if (!form.email || !form.captcha) {
    error.value = '请输入邮箱/验证码'
    return
  }

  loading.value = true
  try {
    const { data, traceId } = await requestPasswordReset(form.email, {
      captchaId: captchaId.value,
      captchaCode: form.captcha
    })
    emit('trace', traceId || '')
    debugResetLink.value = data?.resetLink || ''
    if (debugResetLink.value) {
      successMsg.value = '已生成本地/测试重置链接（见下方），请继续完成重置。'
    } else {
      successMsg.value = data?.issued ? '已提交：若邮箱存在，将发送重置邮件；若长时间未收到，请联系管理员。' : '提交失败'
    }
    await refreshCaptcha()
  } catch (e) {
    error.value = e?.message || '提交失败'
    if (e?.code === 10005 || e?.code === 10006) {
      await refreshCaptcha()
    }
  } finally {
    loading.value = false
  }
}

async function onConfirmReset() {
  error.value = ''
  successMsg.value = ''

  if (!token.value) {
    error.value = '缺少 resetToken'
    return
  }
  if (!form.newPassword || !form.captcha) {
    error.value = '请输入新密码/验证码'
    return
  }

  loading.value = true
  try {
    const { data, traceId } = await confirmPasswordReset(token.value, form.newPassword, {
      captchaId: captchaId.value,
      captchaCode: form.captcha
    })
    emit('trace', traceId || '')
    if (!data) {
      error.value = '重置失败'
      await refreshCaptcha()
      return
    }
    successMsg.value = '密码已重置，请使用新密码登录。'
    await refreshCaptcha()
  } catch (e) {
    error.value = e?.message || '重置失败'
    if (e?.code === 10005 || e?.code === 10006 || e?.code === 10007) {
      await refreshCaptcha()
    }
  } finally {
    loading.value = false
  }
}

function openResetLink() {
  if (!debugResetLink.value) return
  try {
    const u = new URL(debugResetLink.value)
    router.push({ path: '/auth/password/reset', query: { token: u.searchParams.get('token') || '' } })
  } catch {
    error.value = 'resetLink 格式不正确'
  }
}

watch(
  () => token.value,
  async () => {
    error.value = ''
    successMsg.value = ''
    form.captcha = ''
    await refreshCaptcha()
  }
)

onMounted(refreshCaptcha)
</script>

<style scoped>
.reset-card {
  display: grid;
  gap: 12px;
}

.reset-form,
.reset-stack,
.reset-field,
.reset-form {
  margin-top: 12px;
}

.reset-label,
.reset-token-note,
.reset-debug-note {
  font-size: 12px;
}

.reset-row,
.reset-captcha-row,
.reset-actions,
.reset-primary-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.reset-row {
  justify-content: space-between;
}

.reset-captcha-input {
  max-width: 180px;
}

.reset-captcha-img {
  height: 40px;
  width: 120px;
  cursor: pointer;
  border: 1px solid var(--border);
  border-radius: 10px;
}

.reset-actions {
  justify-content: space-between;
}

</style>

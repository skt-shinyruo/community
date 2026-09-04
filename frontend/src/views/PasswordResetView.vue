<template>
  <UiCard class="auth-view-card reset-card">
    <UiPageHeader>
      <template #title>找回密码</template>
      <template #subtitle>
        <span v-if="mode === 'confirm'">使用邮件中的 token 重置密码</span>
        <span v-else>输入邮箱后发送重置链接（为避免用户枚举，响应不会区分邮箱是否存在）</span>
      </template>
      <template #actions>
        <UiButton variant="secondary" to="/auth/login" class="reset-login-link">去登录</UiButton>
      </template>
    </UiPageHeader>

    <div class="reset-form">
      <div v-if="error" class="error">{{ error }}</div>
      <div v-if="successMsg" class="muted">{{ successMsg }}</div>

      <UiField v-if="mode === 'request'" label="邮箱">
        <UiInput v-model.trim="form.email" placeholder="name@example.com" autocomplete="email" />
      </UiField>

      <template v-else>
        <UiField label="新密码">
          <UiInput v-model="form.newPassword" placeholder="请输入新密码" type="password" autocomplete="new-password" />
        </UiField>
        <div class="muted reset-token-note">resetToken：{{ shortToken }}</div>
      </template>

      <UiField label="验证码">
        <div class="reset-captcha-row">
          <UiInput v-model.trim="form.captcha" placeholder="请输入验证码" autocomplete="off" class="reset-captcha-input" />
          <button
            v-if="captchaSrc"
            type="button"
            class="captcha-refresh"
            title="点击刷新验证码"
            @click="refreshCaptcha"
          >
            <img :src="captchaSrc" alt="验证码" class="reset-captcha-img" />
          </button>
          <UiButton variant="secondary" @click="refreshCaptcha" :disabled="loading">刷新</UiButton>
        </div>
      </UiField>

      <div class="reset-actions">
        <div class="reset-primary-actions">
          <UiButton v-if="mode === 'request'" @click="onRequestReset" :disabled="loading">
            {{ loading ? '提交中…' : '发送重置链接' }}
          </UiButton>
          <UiButton v-else @click="onConfirmReset" :disabled="loading">
            {{ loading ? '重置中…' : '重置密码' }}
          </UiButton>
        </div>
        <UiButton variant="ghost" to="/posts">返回社区</UiButton>
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
import { backendErrorCode, backendErrorMessage, isCaptchaRejected } from '../api/backendError'
import { issueCaptcha, requestPasswordReset, confirmPasswordReset } from '../api/services/authService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiState from '../components/ui/UiState.vue'

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
    const { data } = await issueCaptcha()
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
    const { data } = await requestPasswordReset(form.email, {
      captchaId: captchaId.value,
      captchaCode: form.captcha
    })
    debugResetLink.value = data?.resetLink || ''
    if (debugResetLink.value) {
      successMsg.value = '已生成本地/测试重置链接（见下方），请继续完成重置。'
    } else {
      successMsg.value = data?.issued ? '已提交：若邮箱存在，将发送重置邮件；若长时间未收到，请联系管理员。' : '提交失败'
    }
    await refreshCaptcha()
  } catch (e) {
    error.value = backendErrorMessage(e, '提交失败')
    if (isCaptchaRejected(e)) {
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
    const { data } = await confirmPasswordReset(token.value, form.newPassword, {
      captchaId: captchaId.value,
      captchaCode: form.captcha
    })
    if (!data) {
      error.value = '重置失败'
      await refreshCaptcha()
      return
    }
    successMsg.value = '密码已重置，请使用新密码登录。'
    await refreshCaptcha()
  } catch (e) {
    error.value = backendErrorMessage(e, '重置失败')
    const code = backendErrorCode(e)
    if (isCaptchaRejected(e) || code === 10007) {
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
  gap: var(--space-3);
}

.reset-form {
  margin-top: var(--space-3);
  display: grid;
  gap: var(--space-3);
}

.reset-token-note {
  font-size: var(--text-xs);
}

.reset-login-link {
  flex-shrink: 0;
  white-space: nowrap;
}

.reset-captcha-row,
.reset-actions,
.reset-primary-actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  align-items: center;
}

.reset-captcha-input {
  max-width: 180px;
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

.reset-captcha-img {
  display: block;
  height: 40px;
  width: 120px;
  border-radius: var(--radius-md);
}

.reset-actions {
  justify-content: space-between;
}
</style>

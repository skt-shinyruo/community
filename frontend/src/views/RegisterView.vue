<template>
  <UiCard class="auth-view-card">
    <UiPageHeader>
      <template #title>注册</template>
      <template #subtitle>创建你的身份，加入一个以阅读和讨论为核心的社区空间。</template>
    </UiPageHeader>

    <div class="stack auth-form">
      <template v-if="flow.step === 'form'">
        <div class="auth-field">
          <div class="field-label">用户名</div>
          <UiInput v-model.trim="form.username" placeholder="请输入用户名" autocomplete="username" />
        </div>

        <div class="auth-field">
          <div class="field-label">邮箱</div>
          <UiInput v-model.trim="form.email" placeholder="name@example.com" autocomplete="email" />
        </div>

        <div class="auth-field">
          <div class="field-label">密码</div>
          <UiInput v-model="form.password" placeholder="请输入密码" type="password" autocomplete="new-password" />
        </div>

        <div class="auth-field">
          <div class="field-label">图形验证码</div>
          <div class="row captcha-row">
            <UiInput v-model.trim="form.captcha" placeholder="请输入验证码" autocomplete="off" class="captcha-input" />
            <img
              v-if="captchaSrc"
              :src="captchaSrc"
              alt="验证码"
              title="点击刷新验证码"
              class="captcha-img"
              @click="refreshCaptcha"
            />
          </div>
        </div>
      </template>

      <template v-else>
        <section class="verify-main">
          <div class="verify-block">
            <div class="verify-title">输入邮箱验证码</div>
            <div class="muted">
              验证码已发送至 {{ flow.maskedEmail || '你的邮箱' }}，输入后即可完成注册并登录。
            </div>
          </div>

          <div class="auth-field">
            <div class="field-label">邮箱验证码</div>
            <UiInput v-model.trim="form.emailCode" placeholder="请输入邮箱验证码" autocomplete="one-time-code" />
          </div>

          <UiButton @click="onVerifyCode" :disabled="loading" class="auth-submit-btn">
            {{ loading ? '验证中…' : '验证并登录' }}
          </UiButton>
        </section>

        <section class="verify-resend">
          <div class="verify-resend-head">
            <div class="verify-section-title">重新发送验证码</div>
            <div class="muted">
              如果没收到邮件，先完成下面的图形验证码，再点击重新发送。
            </div>
          </div>

          <div class="auth-field">
            <div class="field-label">图形验证码（重发用）</div>
            <div class="row captcha-row">
              <UiInput v-model.trim="form.captcha" placeholder="请输入重发所需的图形验证码" autocomplete="off" class="captcha-input" />
              <img
                v-if="captchaSrc"
                :src="captchaSrc"
                alt="验证码"
                title="点击刷新验证码"
                class="captcha-img"
                @click="refreshCaptcha"
              />
            </div>
          </div>

          <UiButton variant="secondary" @click="onResendCode" :disabled="loading">
            {{ loading ? '发送中…' : '重新发送验证码' }}
          </UiButton>
        </section>
      </template>

      <div v-if="error" class="error">{{ error }}</div>
      <div v-if="successMsg" class="success">{{ successMsg }}</div>

      <template v-if="flow.step === 'form'">
        <UiButton @click="onRegister" :disabled="loading" class="auth-submit-btn">
          {{ loading ? '注册中…' : '注册' }}
        </UiButton>
      </template>

      <div class="row auth-links">
        <span class="muted">已有账号？</span>
        <RouterLink to="/auth/login" class="auth-link">去登录</RouterLink>
        <span class="muted">·</span>
        <RouterLink to="/posts" class="muted">返回社区</RouterLink>
      </div>

      <UiState
        v-if="flow.debugEmailCode"
        variant="development"
        title="开发 / 测试验证码"
        :description="flow.debugEmailCode"
      />
    </div>
  </UiCard>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ensureSessionReady } from '../auth/session'
import { useAuthStore } from '../stores/auth'
import { backendErrorMessage, isCaptchaRejected } from '../api/backendError'
import { buildRegisterFlowState, clearRegisterFlowState, persistRegisterFlowState, resolveRegisterFlowError, restoreRegisterFlowState } from './registerFlowState'
import { register as apiRegister, resendRegisterCode, verifyRegisterCode, issueCaptcha } from '../api/services/authService'
import UiCard from '../components/ui/UiCard.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiState from '../components/ui/UiState.vue'

const emit = defineEmits(['trace'])
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({ username: '', password: '', email: '', captcha: '', emailCode: '' })
const loading = ref(false)
const error = ref('')
const successMsg = ref('')

const flow = ref(restoreRegisterFlowState())
const captchaId = ref('')
const captchaSrc = ref('')

if (flow.value.step === 'verify') {
  successMsg.value = flow.value.successMessage
}

function applyFlow(nextFlow) {
  const normalized = buildRegisterFlowState(nextFlow)
  flow.value = normalized
  if (normalized.step === 'verify') {
    persistRegisterFlowState(normalized)
  } else {
    clearRegisterFlowState()
  }
}

function resetPersistedFlow(message) {
  applyFlow(buildRegisterFlowState())
  form.emailCode = ''
  successMsg.value = ''
  error.value = message
}

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

async function onRegister() {
  error.value = ''
  successMsg.value = ''
  applyFlow(buildRegisterFlowState())

  if (!form.username || !form.password || !form.email || !form.captcha) {
    error.value = '请填写完整信息'
    return
  }

  loading.value = true
  try {
    const { data, traceId } = await apiRegister({
      username: form.username,
      password: form.password,
      email: form.email,
      captchaId: captchaId.value,
      captchaCode: form.captcha
    })
    emit('trace', traceId || '')
    applyFlow(buildRegisterFlowState(data))
    successMsg.value = flow.value.successMessage
    form.password = ''
    form.captcha = ''
    await refreshCaptcha()
  } catch (e) {
    error.value = backendErrorMessage(e) || '注册失败'
    if (isCaptchaRejected(e)) {
      await refreshCaptcha()
    }
  } finally {
    loading.value = false
  }
}

async function onResendCode() {
  error.value = ''
  successMsg.value = ''

  if (!flow.value.registrationToken) {
    error.value = '缺少注册上下文，请重新注册'
    return
  }
  if (!form.captcha) {
    error.value = '请输入图形验证码后再重发'
    return
  }

  loading.value = true
  try {
    const { data, traceId } = await resendRegisterCode(flow.value.registrationToken, {
      captchaId: captchaId.value,
      captchaCode: form.captcha
    })
    emit('trace', traceId || '')
    applyFlow({
      ...flow.value,
      maskedEmail: data?.maskedEmail || flow.value.maskedEmail,
      debugEmailCode: data?.debugEmailCode || '',
      emailCodeIssued: data?.issued === true
    })
    successMsg.value = `验证码已重新发送至 ${flow.value.maskedEmail || '你的邮箱'}。`
    form.captcha = ''
    await refreshCaptcha()
  } catch (e) {
    const resolved = resolveRegisterFlowError(e)
    if (resolved.resetFlow) {
      resetPersistedFlow(resolved.message || '注册上下文已失效，请重新注册')
      return
    }
    error.value = resolved.message || '重发失败'
    if (isCaptchaRejected(e)) {
      await refreshCaptcha()
    }
  } finally {
    loading.value = false
  }
}

async function onVerifyCode() {
  error.value = ''
  successMsg.value = ''

  if (!flow.value.registrationToken) {
    error.value = '缺少注册上下文，请重新注册'
    return
  }
  if (!form.emailCode) {
    error.value = '请输入邮箱验证码'
    return
  }

  loading.value = true
  try {
    const { data, traceId } = await verifyRegisterCode(flow.value.registrationToken, form.emailCode)
    emit('trace', traceId || '')
    const token = data?.accessToken
    if (!token) throw new Error('No access token returned')

    auth.setAccessToken(token)
    const session = await ensureSessionReady({ auth })
    if (session.state === 'anonymous') {
      auth.clear()
      error.value = '登录状态已失效，请重新登录'
      return
    }

    clearRegisterFlowState()
    const redirect = route.query.redirect
    router.replace(redirect && redirect.startsWith('/') ? redirect : { name: 'posts' })
  } catch (e) {
    const resolved = resolveRegisterFlowError(e)
    if (resolved.resetFlow) {
      resetPersistedFlow(resolved.message || '注册上下文已失效，请重新注册')
      return
    }
    error.value = resolved.message || '验证失败'
  }
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.auth-form {
  margin-top: 14px;
  gap: 14px;
}

.verify-main {
  display: grid;
  gap: 12px;
}

.auth-field {
  display: grid;
  gap: 8px;
}

.field-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-1);
}

.captcha-input {
  flex: 1;
}

.captcha-row {
  gap: 12px;
  align-items: center;
}

.captcha-img {
  height: 40px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid var(--border);
}

.auth-submit-btn {
  min-height: 44px;
  font-size: 15px;
}

.auth-links {
  justify-content: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 13px;
  margin-top: 2px;
}

.auth-link {
  font-weight: 700;
  color: var(--accent);
}

.auth-link:hover {
  text-decoration: underline;
}

.verify-block {
  display: grid;
  gap: 8px;
}

.verify-title {
  font-weight: 800;
}

.verify-resend {
  display: grid;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.verify-resend-head {
  display: grid;
  gap: 8px;
}

.verify-section-title {
  font-weight: 800;
}

</style>

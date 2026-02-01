<template>
  <UiCard>
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

    <div class="stack" style="margin-top: 12px">
      <div v-if="error" class="error">{{ error }}</div>
      <div v-if="successMsg" class="muted">{{ successMsg }}</div>

      <div v-if="mode === 'request'" class="stack" style="gap: 12px">
        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">邮箱</div>
          <UiInput v-model.trim="form.email" placeholder="name@example.com" autocomplete="email" />
        </div>
      </div>

      <div v-else class="stack" style="gap: 12px">
        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">新密码</div>
          <UiInput v-model.trim="form.newPassword" placeholder="请输入新密码" type="password" autocomplete="new-password" />
        </div>
        <div class="muted" style="font-size: 12px">resetToken：{{ shortToken }}</div>
      </div>

      <div class="stack" style="gap: 10px">
        <div class="row" style="justify-content: space-between; flex-wrap: wrap">
          <div class="muted" style="font-size: 12px">验证码</div>
          <UiButton variant="secondary" @click="refreshCaptcha" :disabled="loading">刷新</UiButton>
        </div>
        <div class="row" style="align-items: center; flex-wrap: wrap">
          <UiInput v-model.trim="form.captcha" placeholder="请输入验证码" autocomplete="off" style="max-width: 180px" />
          <img
            v-if="captchaSrc"
            :src="captchaSrc"
            alt="验证码"
            title="点击刷新验证码"
            style="height: 40px; width: 120px; cursor: pointer; border: 1px solid var(--border); border-radius: 10px"
            @click="refreshCaptcha"
          />
        </div>
      </div>

      <div class="row" style="justify-content: space-between; flex-wrap: wrap">
        <div class="row">
          <UiButton v-if="mode === 'request'" @click="onRequestReset" :disabled="loading">
            {{ loading ? '提交中…' : '发送重置链接' }}
          </UiButton>
          <UiButton v-else @click="onConfirmReset" :disabled="loading">
            {{ loading ? '重置中…' : '重置密码' }}
          </UiButton>
        </div>
        <RouterLink class="btn ghost" to="/posts">返回社区</RouterLink>
      </div>

      <UiCard v-if="debugResetLink" flat>
        <div class="stack" style="gap: 10px">
          <div style="font-weight: 800">本地/测试重置链接</div>
          <div class="muted" style="word-break: break-all; font-size: 12px">{{ debugResetLink }}</div>
          <UiButton variant="secondary" @click="openResetLink">打开重置页</UiButton>
          <div class="muted" style="font-size: 12px">说明：生产环境应通过邮件发送（此链接通常不回传）。</div>
        </div>
      </UiCard>
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

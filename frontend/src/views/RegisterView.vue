<template>
  <UiCard class="auth-view-card">
    <UiPageHeader>
      <template #title>注册</template>
      <template #subtitle>创建你的身份，加入一个以阅读和讨论为核心的社区空间。</template>
    </UiPageHeader>

    <div class="stack auth-form">
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
        <UiInput v-model.trim="form.password" placeholder="请输入密码" type="password" autocomplete="new-password" />
      </div>

      <div class="auth-field">
        <div class="field-label">验证码</div>
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

      <div v-if="error" class="error">{{ error }}</div>
      <div v-if="successMsg" class="success">{{ successMsg }}</div>

      <UiButton @click="onRegister" :disabled="loading" class="auth-submit-btn">
        {{ loading ? '注册中…' : '注册' }}
      </UiButton>

      <div class="row auth-links">
        <span class="muted">已有账号？</span>
        <RouterLink to="/auth/login" class="auth-link">去登录</RouterLink>
        <span class="muted">·</span>
        <RouterLink to="/posts" class="muted">返回社区</RouterLink>
      </div>
    </div>

    <template v-if="activationLink">
      <UiDivider />
      <div class="auth-debug-block">
        <div class="auth-debug-title">开发/测试激活链接</div>
        <div class="muted auth-debug-link">{{ activationLink }}</div>
        <div class="auth-debug-actions">
          <UiButton variant="secondary" @click="goActivation">打开激活页</UiButton>
        </div>
      </div>
    </template>
  </UiCard>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register as apiRegister, issueCaptcha } from '../api/services/authService'
import UiCard from '../components/ui/UiCard.vue'
import UiDivider from '../components/ui/UiDivider.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'

const emit = defineEmits(['trace'])
const router = useRouter()

const form = reactive({ username: '', password: '', email: '', captcha: '' })
const loading = ref(false)
const error = ref('')
const successMsg = ref('')

const resultUserId = ref(0)
const activationLink = ref('')
const captchaId = ref('')
const captchaSrc = ref('')

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
  activationLink.value = ''
  resultUserId.value = 0

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
    resultUserId.value = data?.userId ?? 0
    activationLink.value = data?.activationLink || ''
    if (activationLink.value) {
      successMsg.value = '注册成功：请使用下方链接完成激活（仅本地/测试环境回传）。'
    } else {
      successMsg.value = '注册成功：请前往邮箱完成激活；若长时间未收到邮件，请联系管理员。'
    }
  } catch (e) {
    error.value = e?.message || '注册失败'
    if (e?.code === 10006 || e?.code === 10005) {
      await refreshCaptcha()
    }
  } finally {
    loading.value = false
  }
}

function goActivation() {
  if (!activationLink.value) return
  try {
    const u = new URL(activationLink.value)
    const parts = u.pathname.split('/').filter(Boolean)
    const code = parts[parts.length - 1]
    const userId = parts[parts.length - 2]
    router.push({ name: 'activation', params: { userId, code } })
  } catch {
    error.value = '链接解析失败'
  }
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.auth-form {
  margin-top: 14px;
  gap: 14px;
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

.auth-debug-block {
  display: grid;
  gap: 10px;
}

.auth-debug-title {
  font-weight: 800;
  font-size: 13px;
}

.auth-debug-link {
  word-break: break-all;
  font-size: 12px;
}

.auth-debug-actions {
  display: flex;
  justify-content: flex-end;
}
</style>

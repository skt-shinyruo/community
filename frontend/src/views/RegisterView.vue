<!-- 注册页面：对齐 legacy 的注册入口与交互（最小可行）。 -->
<template>
  <UiCard>
    <UiPageHeader>
      <template #title>注册</template>
      <template #subtitle>创建账号后需完成激活</template>
      <template #actions>
        <RouterLink class="btn secondary" to="/auth/login">去登录</RouterLink>
      </template>
    </UiPageHeader>

    <div class="stack" style="margin-top: 12px">
      <div v-if="error" class="error">{{ error }}</div>
      <div v-if="successMsg" class="muted">{{ successMsg }}</div>

      <div class="stack" style="gap: 12px">
        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">用户名</div>
          <UiInput v-model.trim="form.username" placeholder="username" autocomplete="username" />
        </div>
        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">邮箱</div>
          <UiInput v-model.trim="form.email" placeholder="email" autocomplete="email" />
        </div>
        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">密码</div>
          <UiInput v-model.trim="form.password" placeholder="password" type="password" autocomplete="new-password" />
        </div>
        <div class="stack" style="gap: 10px">
          <div class="row" style="justify-content: space-between; flex-wrap: wrap">
            <div class="muted" style="font-size: 12px">验证码</div>
            <UiButton variant="secondary" @click="refreshCaptcha" :disabled="loading">刷新</UiButton>
          </div>
          <div class="row" style="align-items: center; flex-wrap: wrap">
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
        </div>
      </div>

      <div class="row" style="justify-content: space-between; flex-wrap: wrap">
        <div class="row">
          <UiButton @click="onRegister" :disabled="loading">{{ loading ? '提交中…' : '注册' }}</UiButton>
          <span class="muted" v-if="resultUserId">userId={{ resultUserId }}</span>
        </div>
        <RouterLink class="btn ghost" to="/posts">返回社区</RouterLink>
      </div>

      <UiCard v-if="activationLink" flat>
        <div class="stack" style="gap: 10px">
          <div style="font-weight: 800">本地/测试激活链接</div>
          <div class="muted" style="word-break: break-all; font-size: 12px">{{ activationLink }}</div>
          <UiButton variant="secondary" @click="goActivation">打开激活页</UiButton>
          <div class="muted" style="font-size: 12px">说明：生产环境应通过邮件激活（此链接通常不回传）。</div>
        </div>
      </UiCard>
    </div>
  </UiCard>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register as apiRegister, issueCaptcha } from '../api/services/authService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'

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
    error.value = '请填写用户名/邮箱/密码/验证码'
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
    successMsg.value = data?.activationIssued ? '注册成功：已生成激活方式，请尽快激活。' : '注册成功'
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
    error.value = '激活链接格式不正确'
  }
}

onMounted(refreshCaptcha)
</script>

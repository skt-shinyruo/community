<template>
  <div class="auth-page">
    <!-- Left: Brand / Visuals -->
    <div class="auth-visual">
       <div class="auth-brand">
         <div class="logo">C</div>
         <span class="brand-text">Community</span>
       </div>
       <div class="auth-quote">
         <h1>创建你的账号。</h1>
         <p>加入社区，与大家一起交流与分享。</p>
       </div>
    </div>
    
    <!-- Right: Form -->
    <div class="auth-form-container">
      <div style="width: 100%; max-width: 400px">
        <div style="margin-bottom: 32px">
          <h2 style="font-size: 28px; font-weight: 800; margin-bottom: 8px">开始注册</h2>
          <div class="muted">欢迎加入社区。</div>
        </div>

        <div class="stack" style="gap: 20px">
           <div class="stack" style="gap: 8px">
            <div style="font-size: 14px; font-weight: 600">用户名</div>
            <UiInput v-model.trim="form.username" placeholder="请输入用户名" class="auth-input" autocomplete="username" />
          </div>
          
           <div class="stack" style="gap: 8px">
            <div style="font-size: 14px; font-weight: 600">邮箱</div>
            <UiInput v-model.trim="form.email" placeholder="name@example.com" class="auth-input" autocomplete="email" />
          </div>

          <div class="stack" style="gap: 8px">
            <div style="font-size: 14px; font-weight: 600">密码</div>
            <UiInput v-model.trim="form.password" placeholder="请输入密码" type="password" class="auth-input" autocomplete="new-password" />
          </div>

           <div class="stack" style="gap: 8px">
             <div style="font-size: 14px; font-weight: 600">验证码</div>
              <div class="row" style="gap: 12px">
                <UiInput v-model.trim="form.captcha" placeholder="请输入验证码" class="auth-input" style="flex: 1" autocomplete="off" />
                 <img
                  v-if="captchaSrc"
                  :src="captchaSrc"
                  alt="验证码"
                  title="点击刷新验证码"
                  @click="refreshCaptcha"
                  style="height: 44px; border-radius: 8px; cursor: pointer; border: 1px solid var(--border)"
                />
              </div>
          </div>

          <div v-if="error" class="error">{{ error }}</div>
          <div v-if="successMsg" class="success">{{ successMsg }}</div>

          <UiButton @click="onRegister" :disabled="loading" class="primary" style="height: 48px; font-size: 16px; margin-top: 8px">
            {{ loading ? '注册中…' : '注册' }}
          </UiButton>

          <div class="row" style="justify-content: center; gap: 4px; font-size: 14px; margin-top: 16px">
            <span class="muted">已有账号？</span>
            <RouterLink to="/auth/login" style="font-weight: 600; color: var(--accent)">去登录</RouterLink>
          </div>
           <div style="text-align: center; margin-top: 8px">
             <RouterLink to="/posts" class="muted" style="font-size: 13px">返回社区</RouterLink>
           </div>
        </div>
        
        <!-- Activation Link for Dev -->
         <UiCard v-if="activationLink" flat style="margin-top: 24px">
            <div class="stack" style="gap: 10px">
              <div style="font-weight: 800; font-size: 14px">开发/测试激活链接</div>
              <div class="muted" style="word-break: break-all; font-size: 12px">{{ activationLink }}</div>
              <UiButton variant="secondary" @click="goActivation">打开激活页</UiButton>
            </div>
          </UiCard>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register as apiRegister, issueCaptcha } from '../api/services/authService'
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'

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
    successMsg.value = '注册成功，请前往邮箱完成激活'
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
.auth-page {
  display: flex;
  min-height: 100vh;
  background: var(--surface);
}
.auth-visual {
  flex: 1;
  background: linear-gradient(135deg, #FF3CAC 0%, #784BA0 50%, #2B86C5 100%);
  color: white;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 40px;
  position: relative;
  overflow: hidden;
}
.auth-visual::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.1);
}
.auth-brand {
    display: flex;
    align-items: center;
    gap: 12px;
    z-index: 1;
}
.logo {
    width: 40px; height: 40px; background: white; color: black;
    border-radius: 10px; font-weight: 900; display: flex; align-items: center; justify-content: center; font-size: 20px;
}
.brand-text { font-size: 20px; font-weight: 700; z-index: 1;}
.auth-quote { z-index: 1; }
.auth-quote h1 { font-size: 48px; line-height: 1.1; margin-bottom: 20px; font-weight: 800; }
.auth-quote p { font-size: 18px; opacity: 0.9; max-width: 400px; }

.auth-form-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .auth-visual { display: none; }
}

.auth-input {
  height: 48px;
  background: var(--bg);
  border: 1px solid transparent;
}
.auth-input:focus {
  background: var(--surface);
  border-color: var(--accent);
}
</style>

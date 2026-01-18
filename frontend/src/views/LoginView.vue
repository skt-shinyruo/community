<template>
  <div class="auth-page">
    <!-- Left: Brand / Visuals -->
    <div class="auth-visual">
       <div class="auth-brand">
         <div class="logo">C</div>
         <span class="brand-text">Community</span>
       </div>
       <div class="auth-quote">
         <h1>Join the conversation.</h1>
         <p>Experience a new way of connecting with people.</p>
       </div>
    </div>
    
    <!-- Right: Form -->
    <div class="auth-form-container">
      <div style="width: 100%; max-width: 400px">
        <div style="margin-bottom: 32px">
          <h2 style="font-size: 28px; font-weight: 800; margin-bottom: 8px">Welcome back</h2>
          <div class="muted">Enter your details to access your account.</div>
        </div>

        <div class="stack" style="gap: 20px">
          <div class="stack" style="gap: 8px">
            <div style="font-size: 14px; font-weight: 600">Username</div>
            <UiInput v-model.trim="form.username" placeholder="Enter your username" autocomplete="username" class="auth-input" />
          </div>

          <div class="stack" style="gap: 8px">
            <div style="font-size: 14px; font-weight: 600">Password</div>
            <UiInput v-model.trim="form.password" placeholder="Enter your password" type="password" autocomplete="current-password" class="auth-input" />
          </div>

           <div v-if="captchaRequired" class="stack" style="gap: 8px">
             <div style="font-size: 14px; font-weight: 600">Captcha</div>
              <div class="row" style="gap: 12px">
                <UiInput v-model.trim="form.captcha" placeholder="Code" class="auth-input" style="flex: 1" />
                 <img
                  v-if="captchaSrc"
                  :src="captchaSrc"
                  alt="captcha"
                  @click="refreshCaptcha"
                  style="height: 44px; border-radius: 8px; cursor: pointer; border: 1px solid var(--border)"
                />
              </div>
          </div>

          <div v-if="error" class="error">{{ error }}</div>

          <UiButton @click="onLogin" :disabled="loading" class="primary" style="height: 48px; font-size: 16px; margin-top: 8px">
            {{ loading ? 'Signing in...' : 'Sign In' }}
          </UiButton>

          <div class="row" style="justify-content: center; gap: 4px; font-size: 14px; margin-top: 16px">
            <span class="muted">Don't have an account?</span>
            <RouterLink to="/auth/register" style="font-weight: 600; color: var(--accent)">Sign up</RouterLink>
          </div>
          
           <div style="text-align: center; margin-top: 8px">
             <RouterLink to="/posts" class="muted" style="font-size: 13px">Back to Home</RouterLink>
           </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login as apiLogin, me as apiMe, issueCaptcha } from '../api/services/authService'
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

async function onLogin() {
  error.value = ''
  if (!form.username || !form.password) {
    error.value = 'Please enter username and password'
    return
  }
  if (captchaRequired.value && !form.captcha) {
     error.value = 'Please enter the captcha'
     return
  }

  loading.value = true
  try {
    const captcha = captchaRequired.value ? { captchaId: captchaId.value, captchaCode: form.captcha } : {}
    const { data, traceId } = await apiLogin(form.username, form.password, captcha)
    emit('trace', traceId || '')
    const token = data?.accessToken
    if (!token) throw new Error('No access token returned')
    
    auth.setAccessToken(token)
    try {
      const me = await apiMe()
      auth.setMe(me?.data || null)
    } catch {}

    const redirect = route.query.redirect
    router.replace(redirect && redirect.startsWith('/') ? redirect : { name: 'posts' })
  } catch (e) {
    const code = e?.code
    if (code === 10005 || code === 10006) {
      captchaRequired.value = true
      error.value = e?.message || 'Captcha required'
      await refreshCaptcha()
    } else {
      error.value = e?.message || 'Login failed'
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  min-height: 100vh;
  background: var(--surface);
}
.auth-visual {
  flex: 1;
  background: linear-gradient(135deg, #0d0d0d 0%, #1a1a1a 100%);
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
  top: -20%;
  right: -20%;
  width: 80%;
  height: 80%;
  background: radial-gradient(circle, rgba(0,113,227,0.2) 0%, transparent 60%);
  filter: blur(60px);
}
.auth-brand {
    display: flex;
    align-items: center;
    gap: 12px;
}
.logo {
    width: 40px; height: 40px; background: white; color: black;
    border-radius: 10px; font-weight: 900; display: flex; align-items: center; justify-content: center; font-size: 20px;
}
.brand-text { font-size: 20px; font-weight: 700; }
.auth-quote h1 { font-size: 48px; line-height: 1.1; margin-bottom: 20px; font-weight: 800; }
.auth-quote p { font-size: 18px; opacity: 0.7; max-width: 400px; }

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

:deep(.auth-input input) {
    height: 48px;
    background: var(--bg);
    border: 1px solid transparent;
}
:deep(.auth-input input:focus) {
    background: var(--surface);
    border-color: var(--accent);
    box-shadow: 0 0 0 2px rgba(0,113,227,0.1);
}
</style>

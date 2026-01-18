<template>
  <div class="auth-page">
    <!-- Left: Brand / Visuals -->
    <div class="auth-visual">
       <div class="auth-brand">
         <div class="logo">C</div>
         <span class="brand-text">Community</span>
       </div>
       <div class="auth-quote">
         <h1>Create your account.</h1>
         <p>Join thousands of developers sharing their knowledge.</p>
       </div>
    </div>
    
    <!-- Right: Form -->
    <div class="auth-form-container">
      <div style="width: 100%; max-width: 400px">
        <div style="margin-bottom: 32px">
          <h2 style="font-size: 28px; font-weight: 800; margin-bottom: 8px">Get Started</h2>
          <div class="muted">Currently open for registration.</div>
        </div>

        <div class="stack" style="gap: 20px">
           <div class="stack" style="gap: 8px">
            <div style="font-size: 14px; font-weight: 600">Username</div>
            <UiInput v-model.trim="form.username" placeholder="Choose a username" class="auth-input" />
          </div>
          
           <div class="stack" style="gap: 8px">
            <div style="font-size: 14px; font-weight: 600">Email</div>
            <UiInput v-model.trim="form.email" placeholder="name@example.com" class="auth-input" />
          </div>

          <div class="stack" style="gap: 8px">
            <div style="font-size: 14px; font-weight: 600">Password</div>
            <UiInput v-model.trim="form.password" placeholder="Create a password" type="password" class="auth-input" />
          </div>

           <div class="stack" style="gap: 8px">
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
          <div v-if="successMsg" class="success-msg">{{ successMsg }}</div>

          <UiButton @click="onRegister" :disabled="loading" class="primary" style="height: 48px; font-size: 16px; margin-top: 8px">
            {{ loading ? 'Creating account...' : 'Create Account' }}
          </UiButton>

          <div class="row" style="justify-content: center; gap: 4px; font-size: 14px; margin-top: 16px">
            <span class="muted">Already have an account?</span>
            <RouterLink to="/auth/login" style="font-weight: 600; color: var(--accent)">Log in</RouterLink>
          </div>
           <div style="text-align: center; margin-top: 8px">
             <RouterLink to="/posts" class="muted" style="font-size: 13px">Back to Home</RouterLink>
           </div>
        </div>
        
        <!-- Activation Link for Dev -->
         <UiCard v-if="activationLink" flat style="margin-top: 24px">
            <div class="stack" style="gap: 10px">
              <div style="font-weight: 800; font-size: 14px">Dev/Test Activation Link</div>
              <div class="muted" style="word-break: break-all; font-size: 12px">{{ activationLink }}</div>
              <UiButton variant="secondary" @click="goActivation" size="sm">Open Activation Page</UiButton>
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
    error.value = 'All fields are required'
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
    successMsg.value = 'Account created successfully!'
  } catch (e) {
    error.value = e?.message || 'Registration failed'
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
    error.value = 'Invalid link'
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
.success-msg { color: var(--green); margin-bottom: 8px; }

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

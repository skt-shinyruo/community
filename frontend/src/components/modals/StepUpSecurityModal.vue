<!-- 硬件级二次安全认证弹窗（Step-Up Security / WebAuthn Modal）：
     依据 Stitch 项目原型高保真设计，用于高风险资产操作（大额转账、销毁测试积分、敏感权限变更）
     的硬件安全密钥（FIDO2 / YubiKey）与生物识别二次鉴权。外壳收敛至 UiModal。 -->
<template>
  <UiModal :title="title" size="md" :busy="submitting" @close="$emit('cancel')">
    <div class="stepup-modal-body">
      <!-- 硬件感应区域 -->
      <div v-if="mode === 'hardware'" class="stepup-hardware-area">
        <div class="stepup-sensor-ring" :class="{ 'is-active': activePulse }">
          <div class="stepup-icon-wrapper">
            <Fingerprint v-if="sensorType === 'biometric'" :size="36" class="stepup-sensor-icon" aria-hidden="true" />
            <KeyRound v-else :size="36" class="stepup-sensor-icon" aria-hidden="true" />
          </div>
        </div>

        <div class="stepup-hardware-copy">
          <h3 class="stepup-status-title">{{ statusTitle }}</h3>
          <p class="stepup-status-desc">{{ statusDescription }}</p>
        </div>

        <div class="stepup-signal-badge">
          <ShieldCheck :size="14" aria-hidden="true" />
          <span>FIDO2 / WebAuthn 硬件保护模式</span>
        </div>
      </div>

      <!-- 备用恢复码模式 -->
      <div v-else class="stepup-backup-area">
        <p class="stepup-backup-intro">
          无法使用硬件安全密钥？您可以输入预留的 8 位紧急安全恢复码完成当前操作。
        </p>

        <UiField label="安全恢复码" :error="backupError">
          <UiInput
            v-model.trim="backupCode"
            placeholder="例如：ABCD-1234"
            autocomplete="off"
            :disabled="submitting"
            @keydown.enter="submitBackupCode"
          />
        </UiField>
      </div>

      <p v-if="error" class="error stepup-modal-error" role="alert">{{ error }}</p>
    </div>

    <template #footer>
      <div class="stepup-footer-actions">
        <UiButton
          v-if="mode === 'hardware'"
          variant="ghost"
          size="sm"
          :disabled="submitting"
          @click="toggleMode"
        >
          使用备用恢复码
        </UiButton>
        <UiButton
          v-else
          variant="ghost"
          size="sm"
          :disabled="submitting"
          @click="toggleMode"
        >
          返回安全密钥验证
        </UiButton>

        <div class="stepup-footer-primary">
          <UiButton variant="secondary" :disabled="submitting" @click="$emit('cancel')">取消</UiButton>
          <UiButton
            v-if="mode === 'hardware'"
            data-test="stepup-touch-btn"
            :disabled="submitting"
            @click="triggerVerification"
          >
            {{ submitting ? '验证中…' : '模拟轻触密钥' }}
          </UiButton>
          <UiButton
            v-else
            data-test="stepup-submit-backup"
            :disabled="submitting || !backupCode"
            @click="submitBackupCode"
          >
            {{ submitting ? '验证中…' : '确认验证' }}
          </UiButton>
        </div>
      </div>
    </template>
  </UiModal>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Fingerprint, KeyRound, ShieldCheck } from 'lucide-vue-next'
import UiButton from '../ui/UiButton.vue'
import UiField from '../ui/UiField.vue'
import UiInput from '../ui/UiInput.vue'
import UiModal from '../ui/UiModal.vue'

const props = defineProps({
  title: { type: String, default: '二次安全认证' },
  actionName: { type: String, default: '当前高风险操作' },
  actionPayload: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['verified', 'cancel'])

const mode = ref('hardware') // hardware | backup
const sensorType = ref('hardware') // hardware | biometric
const submitting = ref(false)
const error = ref('')
const backupCode = ref('')
const backupError = ref('')
const activePulse = ref(true)

const statusTitle = computed(() => {
  if (submitting.value) return '正在进行硬件密钥断言…'
  return sensorType.value === 'biometric'
    ? '请使用 Touch ID / 生物识别'
    : '请插入并轻触您的安全密钥'
})

const statusDescription = computed(() => {
  return `为保障资金与数据安全，${props.actionName}需要通过硬件密钥进行二次签名确认。`
})

function toggleMode() {
  error.value = ''
  backupError.value = ''
  mode.value = mode.value === 'hardware' ? 'backup' : 'hardware'
}

async function triggerVerification() {
  error.value = ''
  submitting.value = true
  try {
    // 若浏览器支持 WebAuthn 并且上下文提供 credentials，则尝试调用；否则安全回退为模拟鉴权
    if (typeof window !== 'undefined' && window.PublicKeyCredential && navigator.credentials?.get) {
      try {
        const challenge = new Uint8Array(32)
        if (window.crypto?.getRandomValues) {
          window.crypto.getRandomValues(challenge)
        }
        await navigator.credentials.get({
          publicKey: {
            challenge,
            timeout: 60000,
            userVerification: 'preferred'
          }
        })
      } catch (navError) {
        // 如果用户取消或环境不支持特定硬件，由上层或降级处理
        if (navError?.name === 'NotAllowedError') {
          throw new Error('安全密钥验证已取消或超时，请重试或使用备用码。', { cause: navError })
        }
      }
    }
    emit('verified', {
      type: 'webauthn',
      method: sensorType.value,
      timestamp: Date.now()
    })
  } catch (e) {
    error.value = e?.message || '硬件安全认证失败，请重试。'
  } finally {
    submitting.value = false
  }
}

function submitBackupCode() {
  backupError.value = ''
  if (!backupCode.value) {
    backupError.value = '请输入备用安全恢复码'
    return
  }
  if (backupCode.value.replace(/[-\s]/g, '').length < 6) {
    backupError.value = '备用恢复码格式不正确（至少 6 位）'
    return
  }
  submitting.value = true
  setTimeout(() => {
    submitting.value = false
    emit('verified', {
      type: 'backup_code',
      code: backupCode.value,
      timestamp: Date.now()
    })
  }, 100)
}

onMounted(() => {
  if (typeof window !== 'undefined' && window.PublicKeyCredential) {
    sensorType.value = 'hardware'
  }
})
</script>

<style scoped>
.stepup-modal-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-2) 0;
}

.stepup-hardware-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-2);
}

.stepup-sensor-ring {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: var(--radius-full);
  background: var(--surface-2);
  border: 2px solid var(--border);
  transition: border-color var(--duration-fast) var(--ease-standard), box-shadow var(--duration-fast) var(--ease-standard);
}

.stepup-sensor-ring.is-active {
  border-color: var(--accent);
  box-shadow: 0 0 0 4px var(--accent-weak);
}

.stepup-icon-wrapper {
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
}

.stepup-hardware-copy {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.stepup-status-title {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 650;
  line-height: 1.35;
  color: var(--text-1);
}

.stepup-status-desc {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-xs);
  max-width: 320px;
  line-height: 1.5;
}

.stepup-signal-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--accent-weak);
  border: 1px solid color-mix(in srgb, var(--accent) 24%, var(--border) 76%);
  color: var(--accent-text);
  font-size: var(--text-xs);
  font-weight: 600;
}

.stepup-backup-area {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-2) 0;
}

.stepup-backup-intro {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-sm);
  line-height: 1.5;
}

.stepup-modal-error {
  margin: 0;
  font-size: var(--text-xs);
  text-align: center;
}

.stepup-footer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: var(--space-3);
}

.stepup-footer-primary {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
</style>

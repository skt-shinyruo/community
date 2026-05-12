<template>
  <div class="page drive-share-page">
    <UiPageHeader>
      <template #title>{{ shareName }}</template>
      <template #subtitle>
        <span>{{ share.type === 'FOLDER' ? '文件夹分享' : '文件分享' }}</span>
        <span v-if="share.expiresAt" class="drive-header-dot" aria-hidden="true">·</span>
        <span v-if="share.expiresAt">{{ share.expiresAt }}</span>
      </template>
    </UiPageHeader>

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载分享…</div>

    <UiCard v-else class="drive-share-card">
      <div class="drive-share-summary">
        <div class="drive-share-summary-item">
          <span>分享类型</span>
          <strong>{{ share.type === 'FOLDER' ? '文件夹' : '文件' }}</strong>
        </div>
        <div class="drive-share-summary-item">
          <span>链接状态</span>
          <strong>{{ ticket ? '已验证' : '等待验证' }}</strong>
        </div>
        <div class="drive-share-summary-item">
          <span>内容名称</span>
          <strong>{{ shareName }}</strong>
        </div>
      </div>

      <form class="drive-share-form" @submit.prevent="verify">
        <label class="drive-field">
          <span>提取码</span>
          <UiInput v-model.trim="password" type="password" autocomplete="off" />
        </label>
        <UiButton :disabled="submitting" type="submit">
          {{ ticket ? '重新验证' : '访问分享' }}
        </UiButton>
        <p v-if="message" class="drive-share-message">{{ message }}</p>
      </form>

      <div v-if="ticket" class="drive-share-actions">
        <UiButton :disabled="submitting" @click="download">下载</UiButton>
        <p v-if="downloadUrl" class="muted drive-share-url">{{ downloadUrl }}</p>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  getDriveShareDownloadUrl,
  getPublicDriveShare,
  verifyDriveShare
} from '../api/services/driveService'

const props = defineProps({
  shareToken: {
    type: String,
    required: true
  }
})

const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const message = ref('')
const password = ref('')
const share = ref({})
const ticket = ref('')
const downloadUrl = ref('')

const shareName = computed(() => share.value?.name || share.value?.entryName || '分享文件')

async function loadShare() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const { data } = await getPublicDriveShare(props.shareToken)
    share.value = data || {}
  } catch (e) {
    error.value = e?.message || '加载分享失败'
  } finally {
    loading.value = false
  }
}

async function verify() {
  const safePassword = String(password.value || '').trim()
  if (!safePassword) {
    message.value = '请输入提取码'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const { data } = await verifyDriveShare(props.shareToken, safePassword)
    ticket.value = String(data?.ticket || '')
    message.value = ticket.value ? '验证成功' : '验证失败'
  } catch (e) {
    message.value = e?.message || '验证失败'
  } finally {
    submitting.value = false
  }
}

async function download() {
  if (!ticket.value) return
  submitting.value = true
  error.value = ''
  try {
    const { data } = await getDriveShareDownloadUrl(props.shareToken, ticket.value, String(share.value?.entryId || ''))
    downloadUrl.value = String(data?.url || '')
    if (downloadUrl.value && typeof window !== 'undefined') {
      window.open(downloadUrl.value, '_blank', 'noopener,noreferrer')
    }
  } catch (e) {
    message.value = e?.message || '获取下载链接失败'
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.shareToken,
  () => {
    ticket.value = ''
    downloadUrl.value = ''
    password.value = ''
    loadShare()
  },
  { immediate: true }
)
</script>

<style scoped>
.drive-share-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drive-share-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 760px;
}

.drive-share-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.drive-share-summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
}

.drive-share-summary-item span {
  font-size: 12px;
  color: var(--muted, #667085);
}

.drive-share-form,
.drive-share-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drive-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.drive-field span {
  font-size: 12px;
  color: var(--muted, #667085);
}

.drive-share-message,
.drive-share-url {
  margin: 0;
  word-break: break-all;
}

.drive-header-dot {
  opacity: 0.6;
}

@media (max-width: 720px) {
  .drive-share-summary {
    grid-template-columns: 1fr;
  }
}
</style>

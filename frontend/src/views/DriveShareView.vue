<template>
  <div class="page drive-share-page">
    <UiPageHeader>
      <template #title>{{ shareName }}</template>
      <template #subtitle>
        <template v-if="ticket">
          <span>{{ isFolderShare ? '文件夹分享' : '文件分享' }}</span>
          <span v-if="share.expiresAt" class="drive-header-dot" aria-hidden="true">·</span>
          <span v-if="share.expiresAt">{{ share.expiresAt }}</span>
        </template>
        <span v-else>等待验证</span>
      </template>
    </UiPageHeader>

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载分享…</div>

    <UiCard v-else class="drive-share-card">
      <div class="drive-share-summary">
        <div v-if="ticket" class="drive-share-summary-item">
          <span>分享类型</span>
          <strong>{{ isFolderShare ? '文件夹' : '文件' }}</strong>
        </div>
        <div class="drive-share-summary-item">
          <span>链接状态</span>
          <strong>{{ ticket ? '已验证' : '等待验证' }}</strong>
        </div>
        <div v-if="ticket" class="drive-share-summary-item">
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

      <div v-if="ticket && isFileShare" class="drive-share-actions">
        <UiButton :disabled="submitting" @click="download">下载</UiButton>
        <p v-if="downloadUrl" class="muted drive-share-url">{{ downloadUrl }}</p>
      </div>

      <div v-if="ticket && isFolderShare" class="drive-share-browser">
        <div class="drive-share-breadcrumb">
          <button
            type="button"
            class="drive-share-breadcrumb-item"
            data-test="share-breadcrumb-root"
            @click="goFolderTrail(-1)"
          >
            {{ shareName }}
          </button>
          <template v-for="(item, index) in folderTrail" :key="item.entryId">
            <span class="drive-share-breadcrumb-separator" aria-hidden="true">/</span>
            <button
              type="button"
              class="drive-share-breadcrumb-item"
              @click="goFolderTrail(index)"
            >
              {{ item.name }}
            </button>
          </template>
        </div>

        <UiState v-if="entriesError" variant="error">{{ entriesError }}</UiState>
        <div v-else-if="entriesLoading" class="muted">正在加载文件…</div>
        <div v-else-if="shareEntries.length === 0" class="muted">此文件夹为空</div>
        <ul v-else class="drive-share-entry-list">
          <li v-for="entry in shareEntries" :key="entry.entryId" class="drive-share-entry">
            <button
              v-if="entry.isFolder"
              type="button"
              class="drive-share-entry-name drive-share-entry-folder"
              data-test="share-entry-open"
              @click="enterFolder(entry)"
            >
              {{ entry.name }}
            </button>
            <span v-else class="drive-share-entry-name" data-test="share-entry-name">{{ entry.name }}</span>
            <span class="drive-share-entry-meta">{{ entry.isFolder ? '文件夹' : formatDriveBytes(entry.sizeBytes) }}</span>
            <UiButton
              v-if="entry.isFile"
              variant="secondary"
              :disabled="submitting"
              data-test="share-entry-download"
              @click="download(entry)"
            >
              下载
            </UiButton>
          </li>
        </ul>
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
  listDriveShareEntries,
  verifyDriveShare
} from '../api/services/driveService'
import { formatDriveBytes, normalizeDriveEntry } from './driveState'

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
const shareEntries = ref([])
const folderTrail = ref([])
const entriesLoading = ref(false)
const entriesError = ref('')

const shareName = computed(() => {
  if (!ticket.value) return '访问分享'
  return share.value?.name || share.value?.entryName || '分享文件'
})
const shareType = computed(() => ticket.value ? String(share.value?.entryType || share.value?.type || 'FILE').toUpperCase() : '')
const isFolderShare = computed(() => shareType.value === 'FOLDER')
const isFileShare = computed(() => Boolean(ticket.value) && !isFolderShare.value)

async function loadShare() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const { data } = await getPublicDriveShare(props.shareToken)
    share.value = {
      shareToken: String(data?.shareToken || props.shareToken || ''),
      requiresPassword: data?.requiresPassword !== false
    }
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
    share.value = { ...share.value, ...(data || {}) }
    ticket.value = String(data?.ticket || '')
    message.value = ticket.value ? '验证成功' : '验证失败'
    downloadUrl.value = ''
    if (ticket.value && isFolderShare.value) {
      folderTrail.value = []
      await loadShareEntries('')
    }
  } catch (e) {
    message.value = e?.message || '验证失败'
  } finally {
    submitting.value = false
  }
}

async function loadShareEntries(parentId = '') {
  if (!ticket.value) return
  entriesLoading.value = true
  entriesError.value = ''
  try {
    const { data } = await listDriveShareEntries(props.shareToken, ticket.value, parentId)
    shareEntries.value = Array.isArray(data) ? data.map(normalizeDriveEntry) : []
  } catch (e) {
    entriesError.value = e?.message || '加载分享文件失败'
  } finally {
    entriesLoading.value = false
  }
}

async function enterFolder(entry) {
  if (!entry?.isFolder) return
  folderTrail.value = [...folderTrail.value, { entryId: String(entry.entryId || ''), name: String(entry.name || '') }]
  await loadShareEntries(entry.entryId)
}

async function goFolderTrail(index) {
  if (index < 0) {
    folderTrail.value = []
    await loadShareEntries('')
    return
  }
  const nextTrail = folderTrail.value.slice(0, index + 1)
  folderTrail.value = nextTrail
  await loadShareEntries(nextTrail[nextTrail.length - 1]?.entryId || '')
}

async function download(entry = share.value) {
  if (!ticket.value) return
  const entryId = String(entry?.entryId || '')
  if (!entryId) return
  submitting.value = true
  error.value = ''
  try {
    const { data } = await getDriveShareDownloadUrl(props.shareToken, ticket.value, entryId)
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
    shareEntries.value = []
    folderTrail.value = []
    entriesError.value = ''
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
.drive-share-actions,
.drive-share-browser {
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

.drive-share-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
}

.drive-share-breadcrumb-item {
  border: 0;
  background: transparent;
  color: var(--link-color, #2563eb);
  cursor: pointer;
  padding: 0;
  font: inherit;
}

.drive-share-breadcrumb-separator {
  color: var(--muted, #667085);
}

.drive-share-entry-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.drive-share-entry {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
  border-radius: 8px;
}

.drive-share-entry-name {
  min-width: 0;
  overflow-wrap: anywhere;
}

.drive-share-entry-folder {
  border: 0;
  background: transparent;
  color: var(--link-color, #2563eb);
  cursor: pointer;
  padding: 0;
  text-align: left;
  font: inherit;
}

.drive-share-entry-meta {
  color: var(--muted, #667085);
  font-size: 12px;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .drive-share-summary {
    grid-template-columns: 1fr;
  }

  .drive-share-entry {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
  }
}
</style>

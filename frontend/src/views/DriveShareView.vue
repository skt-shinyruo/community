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

    <UiSkeleton v-if="loading" variant="card" label="正在加载分享" />
    <UiState v-else-if="error" variant="error" :title="error">
      <template #description>分享链接加载失败，可以重试；若多次失败，链接可能已失效或被撤销。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading" data-test="share-reload" @click="retryLoadShare">重试</UiButton>
      </template>
    </UiState>

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
        <UiField label="提取码" :error="fieldError">
          <UiInput v-model.trim="password" type="password" autocomplete="off" />
        </UiField>
        <UiButton :disabled="submitting" type="submit">
          {{ submitting ? '验证中…' : ticket ? '重新验证' : '访问分享' }}
        </UiButton>
        <p v-if="successMessage" class="drive-share-message">{{ successMessage }}</p>
      </form>

      <div v-if="ticket && isFileShare" class="drive-share-actions">
        <UiButton :disabled="submitting" @click="download">下载</UiButton>
        <p v-if="downloadError" class="drive-share-download-error" role="alert">{{ downloadError }}</p>
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

        <UiSkeleton v-if="entriesLoading" variant="list" :rows="3" label="正在加载分享文件" />
        <UiState v-else-if="entriesError" variant="error" :title="entriesError">
          <template #description>分享文件加载失败，可以重试。</template>
          <template #actions>
            <UiButton variant="secondary" :disabled="entriesLoading" data-test="share-entries-retry" @click="retryLoadEntries">重试</UiButton>
          </template>
        </UiState>
        <UiState v-else-if="shareEntries.length === 0" title="此文件夹为空">
          <template #description>当前文件夹没有可访问的内容。</template>
          <template v-if="folderTrail.length > 0" #actions>
            <UiButton variant="secondary" @click="goFolderTrail(folderTrail.length - 2)">返回上一级</UiButton>
          </template>
        </UiState>
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
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  getDriveShareDownloadUrl,
  getPublicDriveShare,
  listDriveShareEntries,
  verifyDriveShare
} from '../api/services/driveService'
import { createLatestRequestTracker } from '../utils/latestRequest'
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
const fieldError = ref('')
const successMessage = ref('')
const downloadError = ref('')
const password = ref('')
const share = ref({})
const ticket = ref('')
const downloadUrl = ref('')
const shareEntries = ref([])
const folderTrail = ref([])
const entriesLoading = ref(false)
const entriesError = ref('')
const shareRequestTracker = createLatestRequestTracker()
const submissionRequestTracker = createLatestRequestTracker()
const entriesRequestTracker = createLatestRequestTracker()
let shareContextGeneration = 0

const shareName = computed(() => {
  if (!ticket.value) return '访问分享'
  return share.value?.entryName || '分享文件'
})
const shareType = computed(() => ticket.value ? String(share.value?.entryType || '').toUpperCase() : '')
const isFolderShare = computed(() => shareType.value === 'FOLDER')
const isFileShare = computed(() => Boolean(ticket.value) && shareType.value === 'FILE')

function isCurrentShareContext(contextGeneration, shareToken) {
  return contextGeneration === shareContextGeneration && String(props.shareToken || '') === shareToken
}

function isCurrentShareRequest(tracker, requestToken, contextGeneration, shareToken) {
  return tracker.isCurrent(requestToken) && isCurrentShareContext(contextGeneration, shareToken)
}

async function loadShare(shareToken, contextGeneration) {
  const requestToken = shareRequestTracker.begin()
  loading.value = true
  error.value = ''
  fieldError.value = ''
  successMessage.value = ''
  downloadError.value = ''
  try {
    const { data } = await getPublicDriveShare(shareToken)
    if (!isCurrentShareRequest(shareRequestTracker, requestToken, contextGeneration, shareToken)) return
    share.value = {
      shareToken: String(data?.shareToken || ''),
      requiresPassword: data?.requiresPassword !== false
    }
  } catch (e) {
    if (isCurrentShareRequest(shareRequestTracker, requestToken, contextGeneration, shareToken)) {
      error.value = e?.message || '加载分享失败'
    }
  } finally {
    if (isCurrentShareRequest(shareRequestTracker, requestToken, contextGeneration, shareToken)) {
      loading.value = false
    }
  }
}

function retryLoadShare() {
  loadShare(String(props.shareToken || ''), shareContextGeneration)
}

async function verify() {
  if (submitting.value) return
  const safePassword = String(password.value || '').trim()
  successMessage.value = ''
  downloadError.value = ''
  if (!safePassword) {
    fieldError.value = '请输入提取码'
    return
  }
  const contextGeneration = shareContextGeneration
  const shareToken = String(props.shareToken || '')
  const requestToken = submissionRequestTracker.begin()
  entriesRequestTracker.invalidate()
  entriesLoading.value = false
  submitting.value = true
  error.value = ''
  fieldError.value = ''
  try {
    const { data } = await verifyDriveShare(shareToken, safePassword)
    if (!isCurrentShareRequest(submissionRequestTracker, requestToken, contextGeneration, shareToken)) return
    share.value = { ...share.value, ...(data || {}) }
    ticket.value = String(data?.ticket || '')
    if (ticket.value) {
      successMessage.value = '验证成功'
    } else {
      fieldError.value = '验证失败'
    }
    downloadUrl.value = ''
    if (ticket.value && isFolderShare.value) {
      shareEntries.value = []
      await loadShareEntries('', [])
    } else {
      shareEntries.value = []
      folderTrail.value = []
    }
  } catch (e) {
    if (isCurrentShareRequest(submissionRequestTracker, requestToken, contextGeneration, shareToken)) {
      fieldError.value = e?.message || '验证失败'
    }
  } finally {
    if (isCurrentShareRequest(submissionRequestTracker, requestToken, contextGeneration, shareToken)) {
      submitting.value = false
    }
  }
}

async function loadShareEntries(parentId = '', nextTrail = folderTrail.value) {
  if (!ticket.value) return
  const contextGeneration = shareContextGeneration
  const shareToken = String(props.shareToken || '')
  const requestTicket = ticket.value
  const requestToken = entriesRequestTracker.begin()
  entriesLoading.value = true
  entriesError.value = ''
  try {
    const { data } = await listDriveShareEntries(shareToken, requestTicket, parentId)
    if (!isCurrentShareRequest(entriesRequestTracker, requestToken, contextGeneration, shareToken) || ticket.value !== requestTicket) return
    shareEntries.value = Array.isArray(data) ? data.map(normalizeDriveEntry) : []
    folderTrail.value = nextTrail
  } catch (e) {
    if (isCurrentShareRequest(entriesRequestTracker, requestToken, contextGeneration, shareToken) && ticket.value === requestTicket) {
      entriesError.value = e?.message || '加载分享文件失败'
    }
  } finally {
    if (isCurrentShareRequest(entriesRequestTracker, requestToken, contextGeneration, shareToken) && ticket.value === requestTicket) {
      entriesLoading.value = false
    }
  }
}

function retryLoadEntries() {
  const currentParentId = folderTrail.value[folderTrail.value.length - 1]?.entryId || ''
  loadShareEntries(currentParentId, folderTrail.value)
}

async function enterFolder(entry) {
  if (!entry?.isFolder) return
  const nextTrail = [...folderTrail.value, { entryId: String(entry.entryId || ''), name: String(entry.name || '') }]
  await loadShareEntries(entry.entryId, nextTrail)
}

async function goFolderTrail(index) {
  if (index < 0) {
    await loadShareEntries('', [])
    return
  }
  const nextTrail = folderTrail.value.slice(0, index + 1)
  await loadShareEntries(nextTrail[nextTrail.length - 1]?.entryId || '', nextTrail)
}

async function download(entry = share.value) {
  if (!ticket.value || submitting.value) return
  const entryId = String(entry?.entryId || '')
  if (!entryId) return
  const contextGeneration = shareContextGeneration
  const shareToken = String(props.shareToken || '')
  const requestTicket = ticket.value
  const requestToken = submissionRequestTracker.begin()
  submitting.value = true
  error.value = ''
  downloadError.value = ''
  try {
    const { data } = await getDriveShareDownloadUrl(shareToken, requestTicket, entryId)
    if (!isCurrentShareRequest(submissionRequestTracker, requestToken, contextGeneration, shareToken) || ticket.value !== requestTicket) return
    downloadUrl.value = String(data?.url || '')
    if (downloadUrl.value && typeof window !== 'undefined') {
      window.open(downloadUrl.value, '_blank', 'noopener,noreferrer')
    }
  } catch (e) {
    if (isCurrentShareRequest(submissionRequestTracker, requestToken, contextGeneration, shareToken) && ticket.value === requestTicket) {
      downloadError.value = e?.message || '获取下载链接失败'
    }
  } finally {
    if (isCurrentShareRequest(submissionRequestTracker, requestToken, contextGeneration, shareToken) && ticket.value === requestTicket) {
      submitting.value = false
    }
  }
}

watch(
  () => props.shareToken,
  (nextShareToken) => {
    const contextGeneration = ++shareContextGeneration
    const shareToken = String(nextShareToken || '')
    shareRequestTracker.invalidate()
    submissionRequestTracker.invalidate()
    entriesRequestTracker.invalidate()
    loading.value = false
    submitting.value = false
    entriesLoading.value = false
    error.value = ''
    fieldError.value = ''
    successMessage.value = ''
    downloadError.value = ''
    share.value = {}
    ticket.value = ''
    downloadUrl.value = ''
    shareEntries.value = []
    folderTrail.value = []
    entriesError.value = ''
    password.value = ''
    loadShare(shareToken, contextGeneration)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  shareContextGeneration += 1
  shareRequestTracker.invalidate()
  submissionRequestTracker.invalidate()
  entriesRequestTracker.invalidate()
})
</script>

<style scoped>
.drive-share-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.drive-share-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 760px;
}

.drive-share-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.drive-share-summary-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
}

.drive-share-summary-item span {
  font-size: var(--text-xs);
  color: var(--text-3);
}

.drive-share-form,
.drive-share-actions,
.drive-share-browser {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.drive-share-message,
.drive-share-url,
.drive-share-download-error {
  margin: 0;
  word-break: break-all;
}

.drive-share-download-error {
  color: var(--danger);
  font-size: var(--text-sm);
}

.drive-header-dot {
  opacity: 0.6;
}

.drive-share-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  min-height: var(--control-height);
}

.drive-share-breadcrumb-item {
  border: 0;
  background: transparent;
  color: var(--link-color);
  cursor: pointer;
  padding: 0;
  font: inherit;
  border-radius: var(--radius-sm);
}

.drive-share-breadcrumb-item:hover {
  text-decoration: underline;
}

.drive-share-breadcrumb-item:focus-visible,
.drive-share-entry-folder:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.drive-share-breadcrumb-separator {
  color: var(--muted);
}

.drive-share-entry-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.drive-share-entry {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.drive-share-entry-name {
  min-width: 0;
  overflow-wrap: anywhere;
}

.drive-share-entry-folder {
  border: 0;
  background: transparent;
  color: var(--link-color);
  cursor: pointer;
  padding: 0;
  text-align: left;
  font: inherit;
  border-radius: var(--radius-sm);
}

.drive-share-entry-folder:hover {
  text-decoration: underline;
}

.drive-share-entry-meta {
  color: var(--text-3);
  font-size: var(--text-xs);
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

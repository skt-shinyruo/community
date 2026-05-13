<template>
  <div class="page drive-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>网盘</template>
      <template #subtitle>
        <span>{{ quota.label }}</span>
        <span class="drive-header-dot" aria-hidden="true">·</span>
        <span>{{ quota.usedPercent }}% 已用</span>
        <span class="drive-header-dot" aria-hidden="true">·</span>
        <span>私有文件、分享链接和社区附件</span>
      </template>
      <template #actions>
        <UiButton variant="secondary" :disabled="isBusy" @click="reload">
          {{ loading ? '刷新中…' : '刷新' }}
        </UiButton>
        <UiButton v-if="mode !== 'trash'" variant="secondary" :disabled="isBusy" @click="toggleFolderComposer">
          {{ creatingFolder ? '收起新建' : '新建文件夹' }}
        </UiButton>
        <label v-if="mode !== 'trash'" class="btn drive-upload-label" :class="{ 'is-disabled': isBusy }">
          上传
          <input class="sr-only" type="file" multiple :disabled="isBusy" @change="handleUploadChange">
        </label>
      </template>
    </UiPageHeader>

    <section class="drive-stats">
      <div class="drive-stat">
        <span>已用空间</span>
        <strong>{{ formatDriveBytes(quota.usedBytes) }}</strong>
      </div>
      <div class="drive-stat">
        <span>剩余空间</span>
        <strong>{{ formatDriveBytes(quota.remainingBytes) }}</strong>
      </div>
      <div class="drive-stat">
        <span>当前目录</span>
        <strong>{{ currentFolderLabel }}</strong>
      </div>
      <div class="drive-stat">
        <span>当前条目</span>
        <strong>{{ visibleEntries.length }}</strong>
      </div>
    </section>

    <div v-if="error" class="drive-banner drive-banner--error">{{ error }}</div>
    <div v-else-if="statusMessage" class="drive-banner">{{ statusMessage }}</div>

    <div class="drive-layout">
      <UiCard class="drive-panel drive-main-panel">
        <div class="drive-toolbar">
          <div class="drive-tabs" role="tablist" aria-label="网盘模式">
            <button type="button" class="drive-tab" :class="{ active: mode === 'files' }" @click="switchMode('files')">
              我的文件
            </button>
            <button type="button" class="drive-tab" :class="{ active: mode === 'shares' }" @click="switchMode('shares')">
              分享管理
            </button>
            <button type="button" class="drive-tab" :class="{ active: mode === 'trash' }" @click="switchMode('trash')">
              回收站
            </button>
          </div>

          <div class="drive-search">
            <UiInput
              v-model.trim="searchKeyword"
              type="search"
              placeholder="搜索文件"
              autocomplete="off"
              @keydown.enter.prevent="runSearch"
            />
            <UiButton variant="secondary" :disabled="isBusy" @click="runSearch">搜索</UiButton>
            <UiButton v-if="searchKeyword" variant="ghost" :disabled="isBusy" @click="clearSearch">清除</UiButton>
          </div>
        </div>

        <div v-if="creatingFolder && mode !== 'trash'" class="drive-inline-form">
          <label class="drive-field">
            <span>文件夹名称</span>
            <UiInput v-model.trim="folderNameDraft" placeholder="输入文件夹名称" autocomplete="off" />
          </label>
          <div class="drive-inline-actions">
            <UiButton :disabled="isBusy" @click="createFolder">确认</UiButton>
            <UiButton variant="ghost" :disabled="isBusy" @click="cancelCreateFolder">取消</UiButton>
          </div>
        </div>

        <div class="drive-breadcrumb" aria-label="文件夹路径">
          <button
            v-for="(item, index) in breadcrumbItems"
            :key="item.entryId || 'root'"
            type="button"
            class="drive-breadcrumb-item"
            :class="{ active: index === breadcrumbItems.length - 1 }"
            @click="goBreadcrumb(index)"
          >
            {{ item.name }}
          </button>
        </div>

        <UiState v-if="loading && visibleEntries.length === 0">
          正在加载网盘…
        </UiState>
        <UiState v-else-if="!loading && visibleEntries.length === 0">
          暂无文件
          <template #description>
            {{ mode === 'trash' ? '回收站目前是空的。' : '可以先创建文件夹，或者上传一个文件。' }}
          </template>
        </UiState>

        <div v-else class="drive-entry-list">
          <div
            v-for="entry in visibleEntries"
            :key="entry.entryId"
            class="drive-entry-row"
            :class="{ selected: entry.entryId === selectedEntryId }"
            role="button"
            tabindex="0"
            @click="selectEntry(entry)"
            @keydown.enter.prevent="selectEntry(entry)"
          >
            <div class="drive-entry-main">
              <strong class="drive-entry-name">{{ entry.name }}</strong>
              <span class="drive-entry-subtitle">
                {{ entry.isFolder ? '文件夹' : formatDriveBytes(entry.sizeBytes) }}
              </span>
            </div>
            <div class="drive-entry-meta">
              <span>{{ entry.statusLabel }}</span>
              <span>{{ entry.visibilityLabel }}</span>
            </div>
            <div class="drive-entry-actions">
              <UiButton
                v-if="mode === 'files' && entry.isFolder"
                variant="ghost"
                @click.stop="enterFolder(entry)"
              >
                进入
              </UiButton>
              <UiButton
                v-else-if="mode === 'trash'"
                variant="secondary"
                @click.stop="restoreEntry(entry)"
              >
                恢复
              </UiButton>
            </div>
          </div>
        </div>
      </UiCard>

      <UiCard class="drive-panel drive-detail-panel">
        <template v-if="selectedEntry">
          <UiPageHeader>
            <template #title>{{ selectedEntry.name }}</template>
            <template #subtitle>
              <span>{{ selectedEntry.isFolder ? '文件夹' : formatDriveBytes(selectedEntry.sizeBytes) }}</span>
              <span class="drive-header-dot" aria-hidden="true">·</span>
              <span>{{ selectedEntry.statusLabel }}</span>
            </template>
          </UiPageHeader>

          <dl class="drive-detail-grid">
            <div>
              <dt>类型</dt>
              <dd>{{ selectedEntry.isFolder ? '文件夹' : '文件' }}</dd>
            </div>
            <div>
              <dt>位置</dt>
              <dd>{{ currentFolderLabel }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ selectedEntry.statusLabel }}</dd>
            </div>
            <div>
              <dt>可见性</dt>
              <dd>{{ selectedEntry.visibilityLabel }}</dd>
            </div>
          </dl>

          <div v-if="mode !== 'trash'" class="drive-action-stack">
            <label class="drive-field">
              <span>重命名</span>
              <UiInput v-model.trim="renameDraft" placeholder="输入新名称" autocomplete="off" />
            </label>
            <div class="drive-action-row">
              <UiButton :disabled="isBusy || !renameDraft.trim()" @click="renameSelected">重命名</UiButton>
              <UiButton variant="secondary" :disabled="isBusy" @click="moveSelectedHere">移动到当前目录</UiButton>
            </div>
            <div class="drive-action-row">
              <UiButton v-if="selectedEntry.canDownload" variant="secondary" :disabled="isBusy" @click="downloadSelected">
                下载
              </UiButton>
              <UiButton v-if="selectedEntry.canShare" variant="secondary" :disabled="isBusy" @click="switchToShares">
                分享
              </UiButton>
              <UiButton v-if="selectedEntry.canTrash" variant="danger" :disabled="isBusy" @click="trashSelected">
                删除
              </UiButton>
            </div>
          </div>

          <div v-else class="drive-action-stack">
            <div class="drive-action-row">
              <UiButton variant="secondary" :disabled="isBusy" @click="restoreSelected">恢复到当前目录</UiButton>
              <UiButton variant="danger" :disabled="isBusy" @click="deleteSelectedPermanently">彻底删除</UiButton>
            </div>
          </div>
        </template>

        <UiState v-else>
          选择一个文件或文件夹查看详情
        </UiState>

        <section v-if="mode === 'shares'" class="drive-share-panel">
          <UiPageHeader>
            <template #title>分享管理</template>
            <template #subtitle>默认私有；生成链接后可用于帖子附件、成员分享或虚拟商品交付。</template>
          </UiPageHeader>

          <div class="drive-share-note">
            <span v-if="selectedEntry">{{ selectedEntry.canShare ? selectedEntry.name : '当前选择不可分享' }}</span>
            <span v-else>先选中文件或文件夹，再生成分享链接。</span>
          </div>

          <div class="drive-share-form">
            <label class="drive-field">
              <span>提取码</span>
              <UiInput v-model.trim="sharePassword" type="password" autocomplete="off" />
            </label>
            <label class="drive-field">
              <span>有效期</span>
              <input v-model="shareExpiresAt" class="input" type="datetime-local">
            </label>
            <UiButton :disabled="isBusy || !selectedEntry || !selectedEntry.canShare" @click="createShareForSelected">
              生成分享链接
            </UiButton>
            <div v-if="shareError" class="error">{{ shareError }}</div>
          </div>

          <div v-if="createdShares.length > 0" class="drive-share-list">
            <article v-for="item in createdShares" :key="item.shareId" class="drive-share-item">
              <div class="drive-share-item-main">
                <strong>{{ item.entryName }}</strong>
                <span>{{ item.expiresAt }}</span>
                <code class="drive-share-link">{{ item.shareUrl }}</code>
              </div>
              <div class="drive-share-item-actions">
                <UiButton variant="secondary" :disabled="isBusy" @click="copyShareLink(item)">复制链接</UiButton>
                <UiButton variant="dangerSecondary" :disabled="isBusy" @click="revokeCreatedShare(item)">撤销</UiButton>
              </div>
            </article>
          </div>
        </section>
      </UiCard>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  createDriveFolder,
  createDriveShare,
  createDriveUploadSession,
  deleteDriveEntryPermanently,
  getDriveDownloadUrl,
  getDriveSpace,
  listDriveEntries,
  listDriveTrash,
  moveDriveEntry,
  renameDriveEntry,
  restoreDriveEntry,
  revokeDriveShare,
  searchDriveEntries,
  trashDriveEntry,
  uploadDriveFile
} from '../api/services/driveService'
import { buildDriveBreadcrumb, formatDriveBytes, normalizeDriveEntry, normalizeDriveQuota, reduceDriveSelection, validateShareForm } from './driveState'

const ONE_DAY_MS = 24 * 60 * 60 * 1000

const loading = ref(false)
const busyAction = ref('')
const error = ref('')
const statusMessage = ref('')
const shareError = ref('')
const mode = ref('files')
const entries = ref([])
const trashEntries = ref([])
const selectedEntryId = ref('')
const space = ref({ quotaBytes: 10 * 1024 * 1024 * 1024, usedBytes: 0, remainingBytes: 10 * 1024 * 1024 * 1024 })
const folderTrail = ref([{ entryId: '', name: '我的文件' }])
const folderNameDraft = ref('')
const creatingFolder = ref(false)
const searchKeyword = ref('')
const renameDraft = ref('')
const sharePassword = ref('')
const shareExpiresAt = ref(toDatetimeLocalValue(new Date(Date.now() + ONE_DAY_MS)))
const createdShares = ref([])

const quota = computed(() => normalizeDriveQuota(space.value))
const currentFolderLabel = computed(() => folderTrail.value.map((item) => item.name).join(' / '))
const breadcrumbItems = computed(() => buildDriveBreadcrumb(folderTrail.value.slice(1)))
const visibleEntries = computed(() => (mode.value === 'trash' ? trashEntries.value : entries.value))
const selectedEntry = computed(() => visibleEntries.value.find((item) => item.entryId === selectedEntryId.value) || null)
const isBusy = computed(() => loading.value || busyAction.value !== '')

function toDatetimeLocalValue(date) {
  const safe = date instanceof Date ? date : new Date(date)
  const year = safe.getFullYear()
  const month = String(safe.getMonth() + 1).padStart(2, '0')
  const day = String(safe.getDate()).padStart(2, '0')
  const hour = String(safe.getHours()).padStart(2, '0')
  const minute = String(safe.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

function buildShareUrl(shareToken) {
  if (typeof window === 'undefined') {
    return `#/drive/s/${shareToken}`
  }
  return `${window.location.origin}/#/drive/s/${shareToken}`
}

function setBusy(label, fn) {
  busyAction.value = label
  error.value = ''
  shareError.value = ''
  statusMessage.value = ''
  return Promise.resolve()
    .then(fn)
    .catch((e) => {
      error.value = e?.message || '操作失败'
      throw e
    })
    .finally(() => {
      busyAction.value = ''
    })
}

function selectEntry(entry) {
  selectedEntryId.value = String(entry?.entryId || '')
  renameDraft.value = String(entry?.name || '')
}

async function loadSpace() {
  const { data } = await getDriveSpace()
  space.value = data || {}
}

async function loadFiles() {
  const keyword = String(searchKeyword.value || '').trim()
  const { data } = keyword
    ? await searchDriveEntries({ keyword })
    : await listDriveEntries({ parentId: currentFolderId.value })
  const list = Array.isArray(data) ? data.map(normalizeDriveEntry) : []
  entries.value = list
  selectedEntryId.value = reduceDriveSelection(selectedEntryId.value, list) || (list[0]?.entryId || '')
  renameDraft.value = selectedEntryId.value ? String(list.find((item) => item.entryId === selectedEntryId.value)?.name || '') : ''
}

async function loadTrash() {
  const { data } = await listDriveTrash()
  const list = Array.isArray(data) ? data.map(normalizeDriveEntry) : []
  trashEntries.value = list
  selectedEntryId.value = reduceDriveSelection(selectedEntryId.value, list) || (list[0]?.entryId || '')
  renameDraft.value = selectedEntryId.value ? String(list.find((item) => item.entryId === selectedEntryId.value)?.name || '') : ''
}

async function reload() {
  loading.value = true
  error.value = ''
  try {
    await loadSpace()
    if (mode.value === 'trash') {
      await loadTrash()
    } else {
      await loadFiles()
    }
  } catch (e) {
    error.value = e?.message || '加载网盘失败'
  } finally {
    loading.value = false
  }
}

const currentFolderId = computed(() => folderTrail.value[folderTrail.value.length - 1]?.entryId || '')

async function switchMode(next) {
  if (mode.value === next) {
    return
  }
  if (next !== 'files') {
    searchKeyword.value = ''
  }
  mode.value = next
  selectedEntryId.value = ''
  await reload()
}

async function runSearch() {
  mode.value = 'files'
  selectedEntryId.value = ''
  await reload()
}

async function clearSearch() {
  searchKeyword.value = ''
  await reload()
}

async function enterFolder(entry) {
  if (!entry?.isFolder) return
  mode.value = 'files'
  searchKeyword.value = ''
  folderTrail.value = [...folderTrail.value, { entryId: String(entry.entryId), name: String(entry.name || '') }]
  selectedEntryId.value = ''
  await reload()
}

async function goBreadcrumb(index) {
  if (index < 0 || index >= breadcrumbItems.value.length) return
  mode.value = 'files'
  searchKeyword.value = ''
  if (index === 0) {
    folderTrail.value = [{ entryId: '', name: '我的文件' }]
  } else {
    const path = folderTrail.value.slice(0, index + 1)
    folderTrail.value = path.length > 0 ? path : [{ entryId: '', name: '我的文件' }]
  }
  selectedEntryId.value = ''
  await reload()
}

function toggleFolderComposer() {
  creatingFolder.value = !creatingFolder.value
  if (creatingFolder.value) {
    folderNameDraft.value = ''
  }
}

function cancelCreateFolder() {
  creatingFolder.value = false
  folderNameDraft.value = ''
}

async function createFolder() {
  const name = String(folderNameDraft.value || '').trim()
  if (!name) {
    error.value = '请输入文件夹名称'
    return
  }
  await setBusy('folder', async () => {
    await createDriveFolder({ parentId: currentFolderId.value, name })
    folderNameDraft.value = ''
    creatingFolder.value = false
    statusMessage.value = '文件夹已创建'
    await reload()
  }).catch(() => {})
}

async function handleUploadChange(event) {
  const files = Array.from(event?.target?.files || [])
  if (files.length === 0) return
  await setBusy('upload', async () => {
    for (const file of files) {
      const session = await createDriveUploadSession({ parentId: currentFolderId.value, file })
      await uploadDriveFile({ session: session.data, file })
    }
    statusMessage.value = `已上传 ${files.length} 个文件`
    await reload()
  }).catch(() => {})
  if (event?.target) {
    event.target.value = ''
  }
}

async function renameSelected() {
  const entry = selectedEntry.value
  const newName = String(renameDraft.value || '').trim()
  if (!entry) return
  if (!newName) {
    error.value = '请输入新名称'
    return
  }
  await setBusy('rename', async () => {
    await renameDriveEntry(entry.entryId, { newName })
    statusMessage.value = '名称已更新'
    await reload()
  }).catch(() => {})
}

async function moveSelectedHere() {
  const entry = selectedEntry.value
  if (!entry) return
  await setBusy('move', async () => {
    await moveDriveEntry(entry.entryId, { targetParentId: currentFolderId.value })
    statusMessage.value = '条目已移动'
    await reload()
  }).catch(() => {})
}

async function downloadSelected() {
  const entry = selectedEntry.value
  if (!entry?.canDownload) return
  await setBusy('download', async () => {
    const { data } = await getDriveDownloadUrl(entry.entryId)
    if (data?.url && typeof window !== 'undefined') {
      window.open(data.url, '_blank', 'noopener,noreferrer')
    }
  }).catch(() => {})
}

async function trashSelected() {
  const entry = selectedEntry.value
  if (!entry?.canTrash) return
  await setBusy('trash', async () => {
    await trashDriveEntry(entry.entryId)
    statusMessage.value = '条目已移至回收站'
    await reload()
  }).catch(() => {})
}

async function restoreEntry(entry) {
  if (!entry?.canRestore) return
  await setBusy('restore', async () => {
    await restoreDriveEntry(entry.entryId, { targetParentId: currentFolderId.value })
    statusMessage.value = '条目已恢复'
    await reload()
  }).catch(() => {})
}

async function restoreSelected() {
  await restoreEntry(selectedEntry.value)
}

async function deleteSelectedPermanently() {
  const entry = selectedEntry.value
  if (!entry?.canDeletePermanently) return
  await setBusy('delete', async () => {
    await deleteDriveEntryPermanently(entry.entryId)
    statusMessage.value = '条目已彻底删除'
    await reload()
  }).catch(() => {})
}

function switchToShares() {
  mode.value = 'shares'
  shareError.value = ''
}

function normalizeCreatedShare(data, entry) {
  const shareToken = String(data?.shareToken || '')
  return {
    shareId: String(data?.shareId || shareToken || `${entry.entryId}:${Date.now()}`),
    entryId: String(data?.entryId || entry.entryId || ''),
    entryName: String(data?.entryName || entry.name || ''),
    shareToken,
    expiresAt: String(data?.expiresAt || ''),
    shareUrl: buildShareUrl(shareToken)
  }
}

async function createShareForSelected() {
  const entry = selectedEntry.value
  if (!entry?.canShare) {
    shareError.value = '请选择可分享的文件或文件夹'
    return
  }
  const validation = validateShareForm({
    password: sharePassword.value,
    expiresAt: shareExpiresAt.value
  }, new Date())
  if (!validation.valid) {
    shareError.value = validation.message
    return
  }
  await setBusy('share', async () => {
    const { data } = await createDriveShare(entry.entryId, {
      password: String(sharePassword.value || '').trim(),
      expiresAt: new Date(shareExpiresAt.value).toISOString()
    })
    createdShares.value = [normalizeCreatedShare(data, entry), ...createdShares.value].slice(0, 10)
    sharePassword.value = ''
    shareExpiresAt.value = toDatetimeLocalValue(new Date(Date.now() + ONE_DAY_MS))
    statusMessage.value = '分享链接已生成'
    mode.value = 'shares'
  }).catch(() => {})
}

async function revokeCreatedShare(item) {
  if (!item?.shareId) return
  await setBusy('revoke', async () => {
    await revokeDriveShare(item.shareId)
    createdShares.value = createdShares.value.filter((share) => share.shareId !== item.shareId)
    statusMessage.value = '分享已撤销'
  }).catch(() => {})
}

async function copyShareLink(item) {
  if (!item?.shareUrl) return
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(item.shareUrl)
    statusMessage.value = '分享链接已复制'
    return
  }
  statusMessage.value = item.shareUrl
}

onMounted(reload)
</script>

<style scoped>
.drive-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drive-header-dot {
  opacity: 0.6;
}

.drive-upload-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.drive-upload-label.is-disabled {
  pointer-events: none;
  opacity: 0.6;
}

.drive-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.drive-stat {
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
  border-radius: 8px;
  padding: 12px 14px;
  background: var(--panel-bg, rgba(255, 255, 255, 0.6));
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.drive-stat span {
  font-size: 12px;
  color: var(--muted, #667085);
}

.drive-stat strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-banner {
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
  border-radius: 8px;
  padding: 10px 12px;
  background: rgba(59, 130, 246, 0.08);
}

.drive-banner--error {
  background: rgba(239, 68, 68, 0.08);
}

.drive-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 0.9fr);
  gap: 16px;
  align-items: start;
}

.drive-panel {
  min-width: 0;
}

.drive-main-panel,
.drive-detail-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drive-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.drive-tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drive-tab {
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
  background: transparent;
  color: inherit;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
}

.drive-tab.active {
  background: rgba(59, 130, 246, 0.12);
  border-color: rgba(59, 130, 246, 0.35);
}

.drive-search {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-width: min(100%, 440px);
}

.drive-inline-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.drive-inline-actions,
.drive-action-row,
.drive-share-item-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drive-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.drive-field span {
  font-size: 12px;
  color: var(--muted, #667085);
}

.drive-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.drive-breadcrumb-item {
  border: 0;
  background: transparent;
  color: inherit;
  padding: 0;
  cursor: pointer;
}

.drive-breadcrumb-item.active {
  font-weight: 600;
}

.drive-entry-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.drive-entry-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
  cursor: pointer;
}

.drive-entry-row.selected {
  background: rgba(59, 130, 246, 0.08);
}

.drive-entry-main,
.drive-entry-meta,
.drive-entry-actions {
  min-width: 0;
}

.drive-entry-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drive-entry-name,
.drive-entry-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-entry-subtitle,
.drive-entry-meta {
  color: var(--muted, #667085);
  font-size: 12px;
}

.drive-entry-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.drive-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.drive-detail-grid div {
  min-width: 0;
}

.drive-detail-grid dt {
  font-size: 12px;
  color: var(--muted, #667085);
}

.drive-detail-grid dd {
  margin: 4px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-action-stack,
.drive-share-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drive-share-note {
  color: var(--muted, #667085);
  font-size: 13px;
}

.drive-share-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drive-share-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.drive-share-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
}

.drive-share-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drive-share-link {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 4px 6px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.05);
}

@media (max-width: 1100px) {
  .drive-layout {
    grid-template-columns: 1fr;
  }

  .drive-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .drive-stats {
    grid-template-columns: 1fr;
  }

  .drive-entry-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .drive-entry-meta {
    align-items: flex-start;
  }

  .drive-inline-form {
    grid-template-columns: 1fr;
  }
}
</style>

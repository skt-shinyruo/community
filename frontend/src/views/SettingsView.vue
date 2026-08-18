<template>
  <div class="page settings-page">
    <UiCard class="settings-panel">
      <section class="settings-section">
        <div class="settings-section-head">
          <div>
            <div class="settings-eyebrow">Public Identity</div>
            <h2>公开资料</h2>
            <p>你的头像和用户名会出现在帖子、评论与关注关系中。</p>
          </div>
        </div>

        <div class="settings-profile-card">
          <div class="settings-avatar-column">
            <UiAvatar :src="displayAvatarUrl" :name="auth.username || ''" :size="92" class="settings-profile-avatar" />
            <div class="settings-avatar-caption">
              <div class="settings-profile-name">{{ auth.username || '当前账号' }}</div>
              <div class="muted">成员身份会在公开讨论里复用。</div>
            </div>
          </div>

          <div class="settings-summary-grid">
            <div class="settings-summary-card">
              <div class="settings-summary-label">当前头像</div>
              <div class="settings-summary-value">{{ currentAvatarUrl ? '已设置' : '使用默认头像' }}</div>
              <div class="settings-summary-text">更新后会同步到个人主页与互动场景。</div>
            </div>
            <div class="settings-summary-card">
              <div class="settings-summary-label">上传状态</div>
              <div class="settings-summary-value">{{ uploadSession.objectId ? '已获取上传参数' : '尚未开始' }}</div>
              <div class="settings-summary-text">选择图片后创建上传会话并提交保存。</div>
            </div>
          </div>
        </div>
      </section>

      <section class="settings-section">
        <div class="settings-section-head">
          <div>
            <div class="settings-eyebrow">Workflow</div>
            <h2>头像上传</h2>
            <p>选择图片后直接使用 OSS 上传会话保存头像。</p>
          </div>
        </div>

        <div class="settings-upload-card">
          <div class="settings-upload-meta">
            <div class="settings-upload-meta-item">
              <span class="settings-upload-label">上传会话</span>
              <strong>{{ uploadSession.objectId ? '已获取' : '等待创建' }}</strong>
            </div>
            <div class="settings-upload-meta-item">
              <span class="settings-upload-label">大小限制</span>
              <strong>{{ uploadSession.constraints.maxBytes ? `${Math.round(uploadSession.constraints.maxBytes / 1024)}KB` : '获取后显示' }}</strong>
            </div>
            <div class="settings-upload-meta-item">
              <span class="settings-upload-label">预览状态</span>
              <strong>{{ previewUrl ? '已生成预览' : '尚未上传新头像' }}</strong>
            </div>
          </div>

          <div class="upload-area">
            <div class="settings-upload-note">
              <span>图片会按 OSS 上传会话提交，保存后同步到公开资料。</span>
            </div>

            <div class="settings-upload-actions">
              <input
                ref="avatarFileInput"
                class="input settings-avatar-file-input"
                type="file"
                name="avatar-file"
                accept="image/*"
                :disabled="loading"
                @change="onAvatarFilePicked"
              />
              <UiButton v-if="pickedFile" variant="ghost" :disabled="loading" @click="clearAvatarFile">清除</UiButton>
              <UiButton @click="uploadAndUpdate" :disabled="loading || !pickedFile">
                {{ uploadActionText }}
              </UiButton>
              <UiButton v-if="canCancelUpload" variant="secondary" @click="cancelUpload">取消上传</UiButton>
            </div>
          </div>

          <div v-if="error" class="error">{{ error }}</div>
          <div v-if="successMsg" class="success">{{ successMsg }}</div>
        </div>
      </section>

      <section class="settings-section">
        <div class="settings-section-head">
          <div>
            <div class="settings-eyebrow">Scope</div>
            <h2>当前可用项</h2>
            <p>当前可管理公开资料与头像；账号和隐私操作在可用前不会作为普通入口展示。</p>
          </div>
        </div>

        <div class="settings-note-card">
          <div class="settings-note-title">没有再保留占位式侧栏标签</div>
          <p>未接入的账号、安全、隐私操作不会继续以伪导航方式出现，避免给人“功能已经可用”的错误预期。</p>
        </div>
      </section>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { me as apiMe } from '../api/services/authService'
import { invalidateUserProfile } from '../api/services/userService'
import http from '../api/http'
import { unwrapResultBody } from '../api/result'
import { executeUploadSession, normalizeUploadSession } from '../api/uploadSession'
import UiCard from '../components/ui/UiCard.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiButton from '../components/ui/UiButton.vue'
import { normalizeOpaqueId } from '../utils/opaqueId'

const auth = useAuthStore()

const loading = ref(false)
const error = ref('')
const successMsg = ref('')
const uploadSession = reactive(normalizeUploadSession())

const pickedFile = ref(null)
const avatarFileInput = ref(null)
const uploadProgress = ref(null)
const uploadPhase = ref('idle')
const selectedPreviewUrl = ref('')
let uploadGeneration = 0
let uploadController = null

const sessionScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  auth.authed ? 'authenticated' : 'anonymous'
].join(':'))

const currentAvatarUrl = computed(() => String(auth?.me?.headerUrl || '').trim())

const previewUrl = computed(() => selectedPreviewUrl.value)

const displayAvatarUrl = computed(() => previewUrl.value || currentAvatarUrl.value)

const canCancelUpload = computed(() => loading.value && ['creating', 'uploading'].includes(uploadPhase.value))

const uploadActionText = computed(() => {
  if (!loading.value) return '上传并保存'
  if (uploadPhase.value === 'saving') return '保存中…'
  return uploadProgress.value == null ? '上传中…' : `上传中 ${uploadProgress.value}%`
})

function onAvatarFilePicked(event) {
  pickedFile.value = event?.target?.files?.[0] || null
}

function clearAvatarFile() {
  if (avatarFileInput.value) avatarFileInput.value.value = ''
  pickedFile.value = null
}

watch(pickedFile, (file, _previousFile, onCleanup) => {
  if (selectedPreviewUrl.value && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(selectedPreviewUrl.value)
  }
  selectedPreviewUrl.value = ''
  Object.assign(uploadSession, normalizeUploadSession())
  if (!file || typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') return

  const objectUrl = URL.createObjectURL(file)
  selectedPreviewUrl.value = objectUrl
  onCleanup(() => {
    if (typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
      URL.revokeObjectURL(objectUrl)
    }
  })
})

async function createUploadSession(file, userId, signal) {
  const resp = await http.post(`/api/users/${userId}/avatar/upload-sessions`, {
    fileName: file?.name || 'avatar',
    contentType: file?.type || 'application/octet-stream',
    contentLength: file?.size || 0,
    checksumSha256: ''
  }, { signal })
  const { data } = unwrapResultBody(resp.data, 'Create Avatar Upload Session')
  return {
    session: normalizeUploadSession(data || {})
  }
}

async function updateAvatar(objectId, userId) {
  const resp = await http.put(`/api/users/${userId}/avatar`, { objectId })
  unwrapResultBody(resp.data, 'Update Avatar')
}

function isCurrentUpload(generation, scope) {
  return generation === uploadGeneration && scope === sessionScope.value
}

async function uploadAndUpdate() {
  error.value = ''
  successMsg.value = ''
  const file = pickedFile.value
  const userId = normalizeOpaqueId(auth.userId)
  if (!file || !userId) return

  const generation = ++uploadGeneration
  const scope = sessionScope.value
  const controller = new AbortController()
  uploadController = controller
  uploadProgress.value = null
  uploadPhase.value = 'creating'
  loading.value = true
  try {
    const created = await createUploadSession(file, userId, controller.signal)
    if (!isCurrentUpload(generation, scope)) return
    Object.assign(uploadSession, created.session)

    uploadPhase.value = 'uploading'
    const { data } = await executeUploadSession({
      session: created.session,
      file,
      operation: 'Upload Avatar',
      signal: controller.signal,
      onProgress: ({ percent }) => {
        if (isCurrentUpload(generation, scope) && percent != null) uploadProgress.value = percent
      }
    })
    if (!isCurrentUpload(generation, scope)) return
    const objectId = String(data?.objectId || created.session.objectId || '').trim()
    if (!objectId) {
      throw new Error('头像对象缺失，请重新上传')
    }
    uploadPhase.value = 'saving'
    await updateAvatar(objectId, userId)
    if (!isCurrentUpload(generation, scope)) return
    invalidateUserProfile(userId)
    try {
      const { data } = await apiMe()
      if (!isCurrentUpload(generation, scope)) return
      auth.setMe(data)
    } catch {
      if (!isCurrentUpload(generation, scope)) return
      // ignore: 头像已更新，页面可通过刷新/重新进入触发 me 拉取。
    }
    if (!isCurrentUpload(generation, scope)) return
    successMsg.value = '头像已更新。'
  } catch (e) {
    if (!isCurrentUpload(generation, scope)) return
    error.value = e?.message || '更新失败'
  } finally {
    if (isCurrentUpload(generation, scope)) {
      loading.value = false
      uploadPhase.value = 'idle'
      uploadController = null
    }
  }
}

function cancelUpload() {
  if (!canCancelUpload.value) return
  uploadGeneration += 1
  uploadController?.abort()
  uploadController = null
  uploadProgress.value = null
  uploadPhase.value = 'idle'
  loading.value = false
  error.value = '上传已取消'
}

watch(sessionScope, () => {
  uploadGeneration += 1
  uploadController?.abort()
  uploadController = null
  uploadProgress.value = null
  uploadPhase.value = 'idle'
  loading.value = false
  error.value = ''
  successMsg.value = ''
  clearAvatarFile()
  Object.assign(uploadSession, normalizeUploadSession())
})
onBeforeUnmount(() => {
  uploadGeneration += 1
  uploadController?.abort()
  uploadController = null
  uploadPhase.value = 'idle'
})
</script>

<style scoped>
.settings-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.settings-section-head p,
.settings-note-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.settings-eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.settings-panel {
  display: grid;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.settings-section {
  padding: 24px;
  border-bottom: 1px solid var(--border);
  display: grid;
  gap: 18px;
}

.settings-section:last-child {
  border-bottom: none;
}

.settings-section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
}

.settings-section-head h2 {
  margin: 6px 0 4px;
  font-size: 1.15rem;
}

.settings-profile-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
  padding: 20px;
  display: grid;
  gap: 18px;
}

.settings-avatar-column {
  display: flex;
  align-items: center;
  gap: 18px;
}

.settings-avatar-caption {
  display: grid;
  gap: 4px;
}

.settings-profile-avatar {
  font-size: 36px;
}

.settings-profile-name {
  font-size: 1.1rem;
  font-weight: 800;
}

.settings-summary-grid,
.settings-upload-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.settings-summary-card,
.settings-upload-meta-item {
  padding: 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface);
  display: grid;
  gap: 6px;
}

.settings-summary-label,
.settings-upload-label {
  font-size: 12px;
  color: var(--text-3);
}

.settings-summary-value,
.settings-upload-meta-item strong {
  font-size: 15px;
  color: var(--text-1);
}

.settings-summary-text {
  color: var(--text-2);
  line-height: 1.55;
}

.settings-upload-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 20px;
  display: grid;
  gap: 16px;
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.upload-area {
  background: var(--surface);
  padding: 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  display: grid;
  gap: 12px;
}

.settings-upload-note,
.settings-upload-empty {
  color: var(--text-2);
  line-height: 1.55;
}

.settings-upload-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.settings-avatar-file-input {
  flex: 1 1 300px;
  min-width: min(100%, 300px);
}

.settings-note-card {
  border-radius: var(--radius-lg);
  padding: 18px 20px;
  border: 1px dashed color-mix(in srgb, var(--border) 72%, var(--text-3) 28%);
  background: color-mix(in srgb, var(--surface) 88%, var(--bg) 12%);
  display: grid;
  gap: 8px;
}

.settings-note-title {
  font-weight: 700;
  color: var(--text-1);
}

@media (max-width: 900px) {
  .settings-summary-grid,
  .settings-upload-meta {
    grid-template-columns: 1fr;
  }

  .settings-section,
  .settings-upload-card,
  .settings-profile-card {
    padding: 18px;
  }

  .settings-section-head,
  .settings-avatar-column,
  .settings-upload-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .settings-avatar-file-input {
    min-width: 0;
    width: 100%;
  }
}
</style>

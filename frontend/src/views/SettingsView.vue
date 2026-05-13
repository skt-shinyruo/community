<template>
  <div class="page settings-page">
    <UiCard class="settings-panel">
      <section class="settings-section">
        <div class="settings-section-head">
          <div>
            <div class="settings-eyebrow">Public Identity</div>
            <h2>公开资料</h2>
            <p>你的头像和用户名会出现在帖子、评论、排行榜与关注关系中。</p>
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
              <div class="settings-summary-text">更新后会同步到个人主页、排行榜与互动场景。</div>
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
              <UiFileInput
                v-model="pickedFile"
                name="avatar-file"
                accept="image/*"
                button-text="选择图片"
                :disabled="loading"
                clearable
                class="settings-avatar-file-input"
              />
              <UiButton @click="uploadAndUpdate" :disabled="loading || !pickedFile">
                {{ loading ? '上传中…' : '上传并保存' }}
              </UiButton>
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
import { computed, reactive, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { me as apiMe } from '../api/services/authService'
import http from '../api/http'
import { unwrapResultBody } from '../api/result'
import { executeUploadSession, normalizeUploadSession } from '../api/uploadSession'
import UiCard from '../components/ui/UiCard.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiFileInput from '../components/ui/UiFileInput.vue'

const emit = defineEmits(['trace'])
const auth = useAuthStore()

const loading = ref(false)
const error = ref('')
const successMsg = ref('')
const uploadSession = reactive(normalizeUploadSession())

const pickedFile = ref(null)
const selectedPreviewUrl = ref('')

const currentAvatarUrl = computed(() => String(auth?.me?.headerUrl || '').trim())

const previewUrl = computed(() => selectedPreviewUrl.value)

const displayAvatarUrl = computed(() => previewUrl.value || currentAvatarUrl.value)

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

async function createUploadSession(file) {
  error.value = ''
  successMsg.value = ''
  if (!auth.userId) return
  const resp = await http.post(`/api/users/${auth.userId}/avatar/upload-sessions`, {
    fileName: file?.name || 'avatar',
    contentType: file?.type || 'application/octet-stream',
    contentLength: file?.size || 0,
    checksumSha256: ''
  })
  const { data, traceId } = unwrapResultBody(resp.data, 'Create Avatar Upload Session')
  emit('trace', traceId || '')
  Object.assign(uploadSession, normalizeUploadSession(data || {}))
  return uploadSession
}

async function updateAvatar(objectId) {
  const resp = await http.put(`/api/users/${auth.userId}/avatar`, { objectId })
  const { traceId } = unwrapResultBody(resp.data, 'Update Avatar')
  emit('trace', traceId || '')
}

async function uploadAndUpdate() {
  error.value = ''
  successMsg.value = ''
  if (!pickedFile.value) return

  loading.value = true
  try {
    const session = await createUploadSession(pickedFile.value)
    const { data, traceId } = await executeUploadSession({
      http,
      session,
      file: pickedFile.value,
      operation: 'Upload Avatar'
    })
    emit('trace', traceId || '')

    const objectId = String(data?.objectId || session.objectId || '').trim()
    if (!objectId) {
      throw new Error('头像对象缺失，请重新上传')
    }
    await updateAvatar(objectId)
    try {
      const { data, traceId } = await apiMe()
      emit('trace', traceId || '')
      auth.setMe(data)
    } catch {
      // ignore: 头像已更新，页面可通过刷新/重新进入触发 me 拉取。
    }
    successMsg.value = '头像已更新。'
  } catch (e) {
    error.value = e?.message || '更新失败'
  } finally {
    loading.value = false
  }
}
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

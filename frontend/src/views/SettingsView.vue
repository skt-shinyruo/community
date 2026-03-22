<template>
  <div class="page settings-page">
    <UiCard flat class="settings-hero">
      <UiPageHeader>
        <template #title>设置</template>
        <template #subtitle>维护你的公开资料与头像，让个人身份在社区里保持清晰一致。</template>
      </UiPageHeader>

      <div class="settings-hero-grid">
        <div class="settings-hero-card">
          <span class="settings-eyebrow">Profile</span>
          <strong>{{ auth.username || '当前账号' }}</strong>
          <p>这里管理会公开展示给其他成员的信息，优先保持识别度与一致性。</p>
        </div>
        <div class="settings-hero-card">
          <span class="settings-eyebrow">Avatar</span>
          <strong>{{ token.fileName ? '准备更新' : '保持当前头像' }}</strong>
          <p>头像上传流程保持原有业务逻辑，但呈现方式会更接近完整产品设置页。</p>
        </div>
      </div>
    </UiCard>

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
              <div class="settings-summary-value">{{ token.fileName ? '已获取上传参数' : '尚未开始' }}</div>
              <div class="settings-summary-text">先获取上传参数，再选择本地图片并提交保存。</div>
            </div>
          </div>
        </div>
      </section>

      <section class="settings-section">
        <div class="settings-section-head">
          <div>
            <div class="settings-eyebrow">Workflow</div>
            <h2>头像上传</h2>
            <p>沿用现有上传流程，只调整布局和信息层级，让步骤更容易理解。</p>
          </div>
          <UiButton variant="secondary" @click="loadToken" :disabled="loading">
            {{ loading ? '获取中…' : '获取上传参数' }}
          </UiButton>
        </div>

        <div class="settings-upload-card">
          <div class="settings-upload-meta">
            <div class="settings-upload-meta-item">
              <span class="settings-upload-label">存储位置</span>
              <strong v-if="token.provider === 'local'">本地文件</strong>
              <strong v-else-if="token.provider === 'r2'">Cloudflare R2</strong>
              <strong v-else>等待获取上传参数</strong>
            </div>
            <div class="settings-upload-meta-item">
              <span class="settings-upload-label">大小限制</span>
              <strong>{{ token.maxBytes ? `${Math.round(token.maxBytes / 1024)}KB` : '获取后显示' }}</strong>
            </div>
            <div class="settings-upload-meta-item">
              <span class="settings-upload-label">预览状态</span>
              <strong>{{ previewUrl ? '已生成预览' : '尚未上传新头像' }}</strong>
            </div>
          </div>

          <div v-if="token.fileName" class="upload-area">
            <div class="settings-upload-note">
              <span v-if="token.provider === 'local'">图片会先上传到后端并落盘存储。</span>
              <span v-else-if="token.provider === 'r2'">图片会先上传到后端，再转存到 Cloudflare R2。</span>
              <span v-else>当前存储策略未知，请联系管理员确认。</span>
            </div>

            <div class="settings-upload-actions">
              <input type="file" name="avatar-file" accept="image/*" @change="onPickFile" class="input file-input" />
              <UiButton @click="uploadAndUpdate" :disabled="loading || !pickedFile">
                {{ loading ? '上传中…' : '上传并保存' }}
              </UiButton>
            </div>
          </div>

          <div v-else class="settings-upload-empty muted">
            先获取上传参数，再选择要替换的新头像文件。
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
            <p>本次页面重构先把已接入的资料与头像能力整理清楚，更多账号和隐私能力后续再接入。</p>
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
import { computed, reactive, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { me as apiMe } from '../api/services/authService'
import http from '../api/http'
import { unwrapResultBody } from '../api/result'
import UiCard from '../components/ui/UiCard.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'

const emit = defineEmits(['trace'])
const auth = useAuthStore()

const loading = ref(false)
const error = ref('')
const successMsg = ref('')
const token = reactive({
  provider: '',
  fileName: '',
  uploadUrl: '',
  uploadMethod: '',
  maxBytes: 0,
  mimeLimit: ''
})

const pickedFile = ref(null)

const currentAvatarUrl = computed(() => String(auth?.me?.headerUrl || '').trim())

const previewUrl = computed(() => {
  const fileName = String(token.fileName || '').trim()
  if (!fileName) return ''

  const base = String(http?.defaults?.baseURL || '').trim()
  // edge/同源模式：baseURL 为空，直接使用相对路径。
  if (!base) return `/files/${encodeURIComponent(fileName)}`
  return `${base.replace(/\/$/, '')}/files/${encodeURIComponent(fileName)}`
})

const displayAvatarUrl = computed(() => previewUrl.value || currentAvatarUrl.value)

async function loadToken() {
  error.value = ''
  successMsg.value = ''
  if (!auth.userId) return
  loading.value = true
  try {
    const resp = await http.get(`/api/users/${auth.userId}/avatar/upload-token`)
    const { data, traceId } = unwrapResultBody(resp.data, 'Get Token')
    emit('trace', traceId || '')
    token.provider = data?.provider || ''
    token.fileName = data?.fileName || ''
    token.uploadUrl = data?.uploadUrl || ''
    token.uploadMethod = data?.uploadMethod || ''
    token.maxBytes = data?.maxBytes || 0
    token.mimeLimit = data?.mimeLimit || ''
  } catch (e) {
    error.value = e?.message || '获取上传参数失败'
  } finally {
    loading.value = false
  }
}

function onPickFile(e) {
  const f = e?.target?.files?.[0]
  pickedFile.value = f || null
}

async function uploadToBackend({ file, fileName, uploadUrl }) {
  const form = new FormData()
  form.append('file', file)
  form.append('fileName', fileName)
  const resp = await http.post(uploadUrl, form)
  const { traceId } = unwrapResultBody(resp.data, 'Upload Avatar')
  emit('trace', traceId || '')
}

async function updateAvatar(fileName) {
  const resp = await http.put(`/api/users/${auth.userId}/avatar`, { fileName })
  const { traceId } = unwrapResultBody(resp.data, 'Update Avatar')
  emit('trace', traceId || '')
}

async function uploadAndUpdate() {
  error.value = ''
  successMsg.value = ''
  if (!pickedFile.value || !token.fileName) return

  loading.value = true
  try {
    const provider = String(token.provider || '').trim()
    if (provider === 'local' || provider === 'r2') {
      if (!token.uploadUrl) {
        throw new Error('uploadUrl 缺失，请重新获取上传参数')
      }
      await uploadToBackend({ file: pickedFile.value, fileName: token.fileName, uploadUrl: token.uploadUrl })
    } else {
      throw new Error('未知存储策略，请联系管理员')
    }

    await updateAvatar(token.fileName)
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

.settings-hero {
  display: grid;
  gap: var(--space-4);
}

.settings-hero-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.settings-hero-card {
  padding: 18px 20px;
  border-radius: var(--radius-lg);
  border: 1px solid color-mix(in srgb, var(--border) 84%, var(--accent) 16%);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 92%, white 8%), var(--surface));
  display: grid;
  gap: 6px;
}

.settings-hero-card strong {
  font-size: clamp(1.45rem, 3vw, 2rem);
  line-height: 1.1;
}

.settings-hero-card p,
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

.file-input {
  padding: 8px;
  height: auto;
  min-width: 240px;
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
  .settings-hero-grid,
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

  .file-input {
    min-width: 0;
    width: 100%;
  }
}
</style>

<template>
  <UiCard class="settings-panel">
    <section class="settings-section">
      <div class="settings-section-head">
        <h2>公开资料</h2>
        <p>你的头像和用户名会出现在帖子、评论与关注关系中。</p>
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
            <div class="settings-summary-value">{{ upload.session.objectId ? '已获取上传参数' : '尚未开始' }}</div>
            <div class="settings-summary-text">选择图片后创建上传会话并提交保存。</div>
          </div>
        </div>
      </div>
    </section>

    <section class="settings-section">
      <div class="settings-section-head">
        <h2>头像上传</h2>
        <p>选择图片后直接使用 OSS 上传会话保存头像。</p>
      </div>

      <div class="settings-upload-card">
        <div class="settings-upload-meta">
          <div class="settings-upload-meta-item">
            <span class="settings-upload-label">上传会话</span>
            <strong>{{ upload.session.objectId ? '已获取' : '等待创建' }}</strong>
          </div>
          <div class="settings-upload-meta-item">
            <span class="settings-upload-label">大小限制</span>
            <strong>{{ upload.session.constraints.maxBytes ? `${Math.round(upload.session.constraints.maxBytes / 1024)}KB` : '获取后显示' }}</strong>
          </div>
          <div class="settings-upload-meta-item">
            <span class="settings-upload-label">预览状态</span>
            <strong>{{ upload.previewUrl ? '已生成预览' : '尚未上传新头像' }}</strong>
          </div>
        </div>

        <div class="settings-upload-area">
          <UiField label="头像文件" help="图片会按 OSS 上传会话提交，保存后同步到公开资料。">
            <template #default="{ controlId, describedBy }">
              <input
                :id="controlId"
                :aria-describedby="describedBy || undefined"
                ref="avatarFileInput"
                class="settings-avatar-file-input"
                type="file"
                name="avatar-file"
                accept="image/*"
                :disabled="upload.loading"
                @change="onAvatarFilePicked"
              />
            </template>
          </UiField>

          <div class="settings-upload-actions">
            <UiButton :disabled="upload.loading || !upload.file" @click="upload.submit">
              {{ upload.actionText }}
            </UiButton>
            <UiButton v-if="upload.file" variant="ghost" :disabled="upload.loading" @click="clearAvatarFile">清除</UiButton>
            <UiButton v-if="upload.canCancel" variant="secondary" @click="upload.cancel">取消上传</UiButton>
          </div>

          <div v-if="upload.error" class="error" role="alert">{{ upload.error }}</div>
          <div v-if="upload.successMsg" class="success" role="status">{{ upload.successMsg }}</div>
        </div>
      </div>
    </section>
  </UiCard>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { identityScope } from '../../stores/identityScope'
import UiCard from '../../components/ui/UiCard.vue'
import UiAvatar from '../../components/ui/UiAvatar.vue'
import UiButton from '../../components/ui/UiButton.vue'
import UiField from '../../components/ui/UiField.vue'
import { useAvatarUploadWorkflow } from './useAvatarUploadWorkflow'

const auth = useAuthStore()

const avatarFileInput = ref(null)

const { model: upload, invalidate: invalidateUpload } = useAvatarUploadWorkflow()

const currentAvatarUrl = computed(() => String(auth?.me?.headerUrl || '').trim())

const displayAvatarUrl = computed(() => upload.previewUrl || currentAvatarUrl.value)

function onAvatarFilePicked(event) {
  upload.selectFile(event?.target?.files?.[0] || null)
}

function clearAvatarFile() {
  resetFileInput()
  upload.clearFile()
}

function resetFileInput() {
  if (avatarFileInput.value) avatarFileInput.value.value = ''
}

// 只能在这里清，否则旧身份的文件名残留、同名重选不再触发 change。
watch(() => identityScope(auth), resetFileInput)
onBeforeUnmount(() => {
  resetFileInput()
  invalidateUpload()
})
</script>

<style scoped>
.settings-panel {
  display: grid;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.settings-section {
  padding: var(--space-6);
  border-bottom: 1px solid var(--border);
  display: grid;
  gap: var(--space-5);
}

.settings-section:last-child {
  border-bottom: none;
}

.settings-section-head {
  display: grid;
  gap: var(--space-1);
}

.settings-section-head h2 {
  margin: 0;
  font-size: var(--text-lg);
  line-height: var(--line-tight);
}

.settings-section-head p {
  margin: 0;
  color: var(--text-2);
  line-height: var(--line-normal);
}

.settings-profile-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
  padding: var(--space-5);
  display: grid;
  gap: var(--space-5);
}

.settings-avatar-column {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.settings-avatar-caption {
  display: grid;
  gap: var(--space-1);
}

.settings-profile-avatar {
  font-size: 36px;
}

.settings-profile-name {
  font-size: var(--text-lg);
  font-weight: 700;
}

.settings-summary-grid,
.settings-upload-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.settings-summary-card,
.settings-upload-meta-item {
  padding: var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface);
  display: grid;
  gap: var(--space-2);
}

.settings-summary-label,
.settings-upload-label {
  font-size: var(--text-xs);
  color: var(--text-3);
}

.settings-summary-value,
.settings-upload-meta-item strong {
  font-size: var(--text-md);
  color: var(--text-1);
}

.settings-summary-text {
  color: var(--text-2);
  line-height: var(--line-normal);
}

.settings-upload-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  display: grid;
  gap: var(--space-4);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.settings-upload-area {
  background: var(--surface);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  display: grid;
  gap: var(--space-3);
}

.settings-upload-actions {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  align-items: center;
}

.settings-avatar-file-input {
  width: 100%;
  padding: var(--space-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg);
  color: var(--text-1);
  font-size: var(--text-sm);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.settings-avatar-file-input::file-selector-button {
  margin-right: var(--space-3);
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-1);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.settings-avatar-file-input:hover:not(:disabled) {
  border-color: var(--border-strong);
}

.settings-avatar-file-input:hover:not(:disabled)::file-selector-button {
  border-color: var(--border-strong);
  background: var(--hover-bg);
}

.settings-avatar-file-input:focus-visible {
  outline: none;
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}

.settings-avatar-file-input:disabled {
  background: var(--surface-2);
  color: var(--muted);
  cursor: not-allowed;
}

.settings-avatar-file-input:disabled::file-selector-button {
  color: var(--muted);
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .settings-summary-grid,
  .settings-upload-meta {
    grid-template-columns: 1fr;
  }

  .settings-section,
  .settings-upload-card,
  .settings-profile-card {
    padding: var(--space-5);
  }

  .settings-avatar-column,
  .settings-upload-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

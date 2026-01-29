<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>设置</template>
        <template #subtitle>账号与偏好</template>
      </UiPageHeader>

      <div class="settings-layout">
        <!-- Left Sidebar -->
        <div class="settings-sidebar">
          <div class="settings-nav">
            <div class="settings-nav-item active">资料</div>
            <div class="settings-nav-item">账号</div>
            <div class="settings-nav-item">隐私</div>
          </div>
        </div>

        <!-- Right Content -->
        <div class="settings-content">
          <div class="settings-header">
            <div style="font-weight: 800; font-size: 16px">公开资料</div>
            <div class="muted" style="font-size: 13px">更新头像等公开信息。</div>
          </div>

          <div class="settings-section">
            <div class="settings-avatar-row">
              <UiAvatar :src="avatarUrl" :name="auth.username || ''" :size="80" style="font-size: 32px" />
              <div class="stack" style="gap: 12px; flex: 1">
                <div style="font-weight: 700">头像</div>
                <div class="row" style="gap: 8px; flex-wrap: wrap">
                  <UiButton variant="secondary" @click="loadToken" :disabled="loading">
                    {{ loading ? '获取中…' : '获取上传 Token' }}
                  </UiButton>
                </div>

                <div v-if="token.uploadToken" class="upload-area">
                  <div class="muted" style="font-size: 12px; margin-bottom: 8px">Token 已生成，可以开始上传。</div>
                  <div class="row" style="gap: 8px; flex-wrap: wrap">
                    <input type="file" accept="image/*" @change="onPickFile" class="input file-input" />
                    <UiButton @click="uploadAndUpdate" :disabled="loading || !pickedFile">
                      {{ loading ? '上传中…' : '上传并保存' }}
                    </UiButton>
                  </div>
                </div>
                <div v-if="error" class="error">{{ error }}</div>
                <div v-if="successMsg" class="success">{{ successMsg }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
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
const token = reactive({ uploadToken: '', fileName: '', bucketUrl: '' })

const pickedFile = ref(null)

const avatarUrl = computed(() => {
  const fileName = String(token.fileName || '').trim()
  if (!fileName) return ''

  const base = String(http?.defaults?.baseURL || '').trim()
  // edge/同源模式：baseURL 为空，直接使用相对路径。
  if (!base) return `/files/${encodeURIComponent(fileName)}`
  return `${base.replace(/\/$/, '')}/files/${encodeURIComponent(fileName)}`
})

async function loadToken() {
  error.value = ''
  successMsg.value = ''
  if (!auth.userId) return
  loading.value = true
  try {
    const resp = await http.get(`/api/users/${auth.userId}/avatar/upload-token`)
    const { data, traceId } = unwrapResultBody(resp.data, 'Get Token')
    emit('trace', traceId || '')
    token.uploadToken = data?.uploadToken || ''
    token.fileName = data?.fileName || ''
    token.bucketUrl = data?.bucketUrl || ''
  } catch (e) {
    error.value = e?.message || '获取上传 Token 失败'
  } finally {
    loading.value = false
  }
}

function onPickFile(e) {
  const f = e?.target?.files?.[0]
  pickedFile.value = f || null
}

async function uploadToQiniu({ file, key, uploadToken }) {
  // Mock upload if no real Qiniu (fallback for dev)
  // Check if we are in a dev environment with no real qiniu config
  // For now, assume simple fetch like before
  const url = 'https://upload.qiniup.com'
  const form = new FormData()
  form.append('token', uploadToken)
  form.append('key', key)
  form.append('file', file)
  
  // Try real upload, if fails, we might just skip to simulated update in local dev?
  // But let's keep it real.
  const resp = await fetch(url, { method: 'POST', body: form })
  if (!resp.ok) {
     // In local dev without internet or valid token, this will fail.
     // For this UI demo, we might want to skip this if it fails?
     // Let's throw for now.
    throw new Error(`Upload failed: ${resp.status}`)
  }
  return resp.text()
}

async function updateAvatar(fileName) {
  const resp = await http.put(`/api/users/${auth.userId}/avatar`, { fileName })
  const { traceId } = unwrapResultBody(resp.data, 'Update Avatar')
  emit('trace', traceId || '')
}

async function uploadAndUpdate() {
  error.value = ''
  successMsg.value = ''
  if (!pickedFile.value || !token.uploadToken) return

  loading.value = true
  try {
    await uploadToQiniu({ file: pickedFile.value, key: token.fileName, uploadToken: token.uploadToken })
    await updateAvatar(token.fileName)
    successMsg.value = '头像已更新。'
  } catch (e) {
    error.value = e?.message || '更新失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.settings-layout {
  display: flex;
  gap: 0;
  margin-top: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.settings-sidebar {
  width: 240px;
  background: color-mix(in srgb, var(--bg) 78%, var(--surface) 22%);
  padding: 24px 16px;
  border-right: 1px solid var(--border);
}
.settings-nav-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 500;
  color: var(--text-2);
  margin-bottom: 4px;
}
.settings-nav-item:hover { background: var(--surface-2); color: var(--text-1); }
.settings-nav-item.active { background: var(--surface); color: var(--text-1); font-weight: 600; box-shadow: var(--shadow-sm); border: 1px solid var(--border); }

.settings-content {
  flex: 1;
  padding: 28px;
}
.settings-header { border-bottom: 1px solid var(--border); padding-bottom: 24px; }

.settings-section {
  margin-top: 24px;
}

.settings-avatar-row {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

.file-input {
  padding: 8px;
  height: auto;
}

.upload-area {
    background: var(--bg);
    padding: 12px;
    border-radius: 8px;
}

@media (max-width: 900px) {
  .settings-layout {
    flex-direction: column;
  }

  .settings-sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--border);
    padding: 12px;
  }

  .settings-nav {
    display: flex;
    gap: 6px;
    overflow: auto;
    padding-bottom: 4px;
  }

  .settings-nav-item {
    margin-bottom: 0;
    white-space: nowrap;
  }

  .settings-content {
    padding: 18px;
  }

  .settings-avatar-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

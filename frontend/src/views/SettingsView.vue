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
              <UiAvatar :src="displayAvatarUrl" :name="auth.username || ''" :size="80" style="font-size: 32px" />
              <div class="stack" style="gap: 12px; flex: 1">
                <div style="font-weight: 700">头像</div>
                <div class="row" style="gap: 8px; flex-wrap: wrap">
                  <UiButton variant="secondary" @click="loadToken" :disabled="loading">
                    {{ loading ? '获取中…' : '获取上传参数' }}
                  </UiButton>
                </div>

                <div v-if="token.fileName" class="upload-area">
                  <div class="muted" style="font-size: 12px; margin-bottom: 8px">
                    <span v-if="token.provider === 'local'">当前存储：本地文件（文件上传到后端并落盘）。</span>
                    <span v-else-if="token.provider === 'r2'">当前存储：Cloudflare R2（文件先上传到后端，再转存到 R2）。</span>
                    <span v-else>当前存储：未知（请联系管理员）。</span>
                    <span v-if="token.maxBytes"> · 限制 {{ Math.round(token.maxBytes / 1024) }}KB</span>
                  </div>
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

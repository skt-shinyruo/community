<!-- 设置页：对齐 legacy 的头像设置能力（生成上传凭证 + 上传 + 回写头像 URL）。 -->
<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>设置</template>
        <template #subtitle>头像设置 · 生成上传凭证并回写头像 URL</template>
        <template #actions>
          <UiButton @click="loadToken" :disabled="loading">{{ loading ? '加载中…' : '获取上传凭证' }}</UiButton>
        </template>
      </UiPageHeader>

      <div style="margin-top: 12px">
        <div v-if="error" class="error">{{ error }}</div>
        <div v-if="!auth.userId" class="error">未获取到当前用户信息，请重新登录。</div>
        <div v-else class="muted" style="font-size: 12px">
          API：/api/users/{userId}/avatar/upload-token + /api/users/{userId}/avatar
        </div>
      </div>
    </UiCard>

    <UiCard v-if="token.uploadToken">
      <UiPageHeader>
        <template #title>头像</template>
        <template #subtitle>fileName={{ token.fileName }} · bucket={{ token.bucketUrl }}</template>
      </UiPageHeader>

      <div class="stack" style="margin-top: 12px">
        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">选择头像文件（可选）</div>
          <input class="input" type="file" accept="image/*" @change="onPickFile" />
        </div>

        <div class="row" style="flex-wrap: wrap">
          <UiButton @click="uploadAndUpdate" :disabled="loading || !pickedFile">
            {{ loading ? '处理中…' : '上传并更新头像' }}
          </UiButton>
          <UiButton variant="secondary" @click="manualUpdate" :disabled="loading || !manualFileName">
            {{ loading ? '处理中…' : '仅回写 fileName' }}
          </UiButton>
        </div>

        <div class="muted" style="font-size: 12px">
          说明：本地/CI 通常未配置七牛 Key，此时可用“仅回写 fileName”验证链路；生产需配置 Qiniu 相关环境变量。
        </div>

        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">手动 fileName（可选）</div>
          <UiInput v-model.trim="manualFileName" placeholder="fileName" />
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import http from '../api/http'
import { unwrapResultBody } from '../api/result'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'

const emit = defineEmits(['trace'])
const auth = useAuthStore()

const loading = ref(false)
const error = ref('')
const token = reactive({ uploadToken: '', fileName: '', bucketUrl: '' })

const pickedFile = ref(null)
const manualFileName = ref('')

async function loadToken() {
  error.value = ''
  if (!auth.userId) {
    error.value = '未获取到当前用户信息'
    return
  }
  loading.value = true
  try {
    const resp = await http.get(`/api/users/${auth.userId}/avatar/upload-token`)
    const { data, traceId } = unwrapResultBody(resp.data, '获取上传凭证')
    emit('trace', traceId || '')
    token.uploadToken = data?.uploadToken || ''
    token.fileName = data?.fileName || ''
    token.bucketUrl = data?.bucketUrl || ''
    manualFileName.value = token.fileName
  } catch (e) {
    error.value = e?.message || '获取上传凭证失败'
  } finally {
    loading.value = false
  }
}

function onPickFile(e) {
  const f = e?.target?.files?.[0]
  pickedFile.value = f || null
}

async function uploadToQiniu({ file, key, uploadToken }) {
  // 七牛标准上传入口（多数场景可用）。如需区域化上传，可在后续扩展为可配置。
  const url = 'https://upload.qiniup.com'
  const form = new FormData()
  form.append('token', uploadToken)
  form.append('key', key)
  form.append('file', file)
  const resp = await fetch(url, { method: 'POST', body: form })
  if (!resp.ok) {
    throw new Error(`七牛上传失败: HTTP ${resp.status}`)
  }
  return resp.text()
}

async function updateAvatar(fileName) {
  const resp = await http.put(`/api/users/${auth.userId}/avatar`, { fileName })
  const { traceId } = unwrapResultBody(resp.data, '更新头像')
  emit('trace', traceId || '')
}

async function uploadAndUpdate() {
  error.value = ''
  if (!pickedFile.value) {
    error.value = '请先选择文件'
    return
  }
  if (!token.uploadToken || !token.fileName) {
    error.value = '请先获取上传凭证'
    return
  }

  loading.value = true
  try {
    await uploadToQiniu({ file: pickedFile.value, key: token.fileName, uploadToken: token.uploadToken })
    await updateAvatar(token.fileName)
    error.value = '头像已更新'
  } catch (e) {
    error.value = e?.message || '上传或更新失败'
  } finally {
    loading.value = false
  }
}

async function manualUpdate() {
  error.value = ''
  if (!manualFileName.value) {
    error.value = 'fileName 不能为空'
    return
  }
  loading.value = true
  try {
    await updateAvatar(manualFileName.value)
    error.value = '头像已更新'
  } catch (e) {
    error.value = e?.message || '更新失败'
  } finally {
    loading.value = false
  }
}
</script>

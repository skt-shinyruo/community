<template>
  <div class="post-media-upload-block">
    <div class="post-media-upload-status" aria-live="polite">{{ statusText }}</div>

    <input
      v-if="!hasAsset"
      ref="mediaFileInput"
      class="post-media-file-input"
      type="file"
      :id="`post-media-file-${index}`"
      :name="`post-media-file-${index}`"
      :disabled="disabled || isUploading"
      :accept="accept"
      @change="onFileChange"
    />

    <UiInput
      v-if="isFile"
      :model-value="displayName"
      :disabled="disabled"
      placeholder="文件名"
      class="post-media-upload-input"
      @update:model-value="updateBlock({ displayName: $event })"
    />
    <UiInput
      v-else
      :model-value="caption"
      :disabled="disabled"
      placeholder="说明"
      class="post-media-upload-input"
      @update:model-value="updateBlock({ caption: $event })"
    />

    <div class="post-media-upload-actions">
      <UiButton v-if="isUploading" variant="secondary" @click="cancelUpload">
        取消上传
      </UiButton>
      <UiButton v-if="isFailed" variant="secondary" :disabled="disabled || isUploading || !selectedFile" @click="retryUpload">
        重试
      </UiButton>
      <UiButton v-if="selectedFile && !isUploading && !hasAsset" variant="ghost" :disabled="disabled" @click="clearSelectedFile">
        清除
      </UiButton>
      <UiButton variant="ghost" :disabled="disabled || isUploading" :aria-label="removeLabel" @click="$emit('remove')">
        移除
      </UiButton>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import UiButton from '../ui/UiButton.vue'
import UiInput from '../ui/UiInput.vue'
import { inferMediaKind, preparePostMediaUpload, uploadPostMediaFile } from '../../api/services/postMediaService'

const props = defineProps({
  block: { type: Object, required: true },
  index: { type: Number, required: true },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:block', 'remove'])

const selectedFile = ref(null)
const mediaFileInput = ref(null)
let uploadController = null
let uploadRun = 0

const mediaType = computed(() => String(props.block?.type || 'file').toLowerCase())
const isFile = computed(() => mediaType.value === 'file')
const hasAsset = computed(() => !!String(props.block?.assetId || '').trim())
const isUploading = computed(() => props.block?.uploadState === 'uploading')
const isFailed = computed(() => ['failed', 'cancelled'].includes(props.block?.uploadState))
const caption = computed(() => String(props.block?.caption || ''))
const displayName = computed(() => String(props.block?.displayName || selectedFile.value?.name || ''))
const accept = computed(() => {
  if (mediaType.value === 'image') return 'image/*'
  if (mediaType.value === 'video') return 'video/*'
  return ''
})
const mediaKind = computed(() => {
  if (mediaType.value === 'image') return 'IMAGE'
  if (mediaType.value === 'video') return 'VIDEO'
  return inferMediaKind(selectedFile.value)
})
const statusText = computed(() => {
  if (props.block?.uploadState === 'uploading') {
    const progress = Number(props.block?.uploadProgress)
    return Number.isFinite(progress) ? `上传中 ${progress}%` : '上传中'
  }
  if (props.block?.uploadState === 'cancelled') return '上传已取消'
  if (props.block?.uploadState === 'completed') return '上传完成'
  if (props.block?.uploadState === 'failed') return '上传失败'
  return '等待选择文件'
})
const removeLabel = computed(() => `移除${blockTypeLabel.value}块 ${props.index + 1}`)
const blockTypeLabel = computed(() => {
  if (mediaType.value === 'image') return '图片'
  if (mediaType.value === 'video') return '视频'
  return '文件'
})

function updateBlock(patch) {
  emit('update:block', {
    ...props.block,
    ...patch
  })
}

function onFileChange(event) {
  onFilePicked(event?.target?.files?.[0] || null)
}

function clearSelectedFile() {
  if (mediaFileInput.value) mediaFileInput.value.value = ''
  onFilePicked(null)
}

async function onFilePicked(file) {
  cancelUpload({ silent: true })
  selectedFile.value = file || null
  if (!file) {
    updateBlock({ assetId: '', uploadState: 'idle' })
    return
  }
  if (props.disabled) return
  await uploadFile(file)
}

async function retryUpload() {
  if (!selectedFile.value || props.disabled) return
  await uploadFile(selectedFile.value)
}

async function uploadFile(file) {
  const run = ++uploadRun
  const controller = new AbortController()
  uploadController = controller
  updateBlock({
    assetId: '',
    uploadState: 'uploading',
    uploadProgress: 0,
    ...(isFile.value && !props.block?.displayName ? { displayName: file.name || '' } : {})
  })

  try {
    const session = await preparePostMediaUpload({
      file,
      mediaKind: mediaKind.value,
      signal: controller.signal
    })
    if (run !== uploadRun) return
    const assetId = String(session?.data?.assetId || '').trim()
    if (!assetId) {
      throw new Error('missing asset id')
    }
    await uploadPostMediaFile({
      session: session?.data,
      file,
      signal: controller.signal,
      onProgress: ({ percent }) => {
        if (run !== uploadRun || percent == null) return
        updateBlock({ uploadProgress: percent })
      }
    })
    if (run !== uploadRun) return
    updateBlock({
      assetId,
      uploadState: 'completed',
      uploadProgress: 100,
      ...(isFile.value && !props.block?.displayName ? { displayName: file.name || '' } : {})
    })
  } catch {
    if (run !== uploadRun) return
    updateBlock({
      assetId: '',
      uploadState: controller.signal.aborted ? 'cancelled' : 'failed',
      uploadProgress: null
    })
  } finally {
    if (run === uploadRun) uploadController = null
  }
}

function cancelUpload({ silent = false } = {}) {
  if (!uploadController) return
  uploadRun += 1
  uploadController.abort()
  uploadController = null
  if (!silent) {
    updateBlock({ assetId: '', uploadState: 'cancelled', uploadProgress: null })
  }
}

onBeforeUnmount(() => cancelUpload({ silent: true }))
</script>

<style scoped>
/* 文件选择框沿用输入原语外观（退役全局 .input 的令牌化等价）。 */
.post-media-file-input {
  width: 100%;
  height: var(--control-height);
  padding: 0 var(--control-padding-x);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  outline: none;
  background: var(--bg);
  color: var(--text-1);
  font-size: var(--text-sm);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.post-media-file-input:hover:not(:disabled) {
  border-color: var(--border-strong);
}

.post-media-file-input:focus {
  border-color: var(--accent);
}

.post-media-file-input:focus-visible {
  box-shadow: var(--focus-ring);
}

.post-media-file-input:disabled {
  background: var(--surface-2);
  color: var(--muted);
  cursor: not-allowed;
}
</style>

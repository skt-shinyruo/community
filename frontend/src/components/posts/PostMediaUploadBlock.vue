<template>
  <div class="post-media-upload-block">
    <div class="post-media-upload-status" aria-live="polite">{{ statusText }}</div>

    <UiFileInput
      v-if="!hasAsset"
      :id="`post-media-file-${index}`"
      :name="`post-media-file-${index}`"
      :model-value="selectedFile"
      :disabled="disabled || isUploading"
      :accept="accept"
      button-text="选择文件"
      empty-text="未选择文件"
      clearable
      @update:modelValue="onFilePicked"
    />

    <UiInput
      v-if="isFile"
      :model-value="displayName"
      :disabled="disabled"
      placeholder="文件名"
      class="post-media-upload-input"
      @update:modelValue="updateBlock({ displayName: $event })"
    />
    <UiInput
      v-else
      :model-value="caption"
      :disabled="disabled"
      placeholder="说明"
      class="post-media-upload-input"
      @update:modelValue="updateBlock({ caption: $event })"
    />

    <div class="post-media-upload-actions">
      <UiButton v-if="isFailed" variant="secondary" :disabled="disabled || isUploading || !selectedFile" @click="retryUpload">
        重试
      </UiButton>
      <UiButton variant="ghost" :disabled="disabled || isUploading" :aria-label="removeLabel" @click="$emit('remove')">
        移除
      </UiButton>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import UiButton from '../ui/UiButton.vue'
import UiFileInput from '../ui/UiFileInput.vue'
import UiInput from '../ui/UiInput.vue'
import { inferMediaKind, preparePostMediaUpload, uploadPostMediaFile } from '../../api/services/postMediaService'

const props = defineProps({
  block: { type: Object, required: true },
  index: { type: Number, required: true },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:block', 'remove'])

const selectedFile = ref(null)

const mediaType = computed(() => String(props.block?.type || 'file').toLowerCase())
const isFile = computed(() => mediaType.value === 'file')
const hasAsset = computed(() => !!String(props.block?.assetId || '').trim())
const isUploading = computed(() => props.block?.uploadState === 'uploading')
const isFailed = computed(() => props.block?.uploadState === 'failed')
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
  if (props.block?.uploadState === 'uploading') return '上传中'
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

async function onFilePicked(file) {
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
  updateBlock({
    assetId: '',
    uploadState: 'uploading',
    ...(isFile.value && !props.block?.displayName ? { displayName: file.name || '' } : {})
  })

  try {
    const session = await preparePostMediaUpload({ file, mediaKind: mediaKind.value })
    await uploadPostMediaFile({ session: session?.data, file })
    updateBlock({
      assetId: String(session?.data?.assetId || ''),
      uploadState: 'completed',
      ...(isFile.value && !props.block?.displayName ? { displayName: file.name || '' } : {})
    })
  } catch {
    updateBlock({ assetId: '', uploadState: 'failed' })
  }
}
</script>

<!-- 编辑弹窗：用于帖子/评论的窗口内编辑。外壳与焦点行为收敛到 UiModal。 -->
<template>
  <UiModal :title="headerTitle" size="lg" :busy="loading" @close="$emit('close')">
    <div class="edit-content-modal-body">
      <UiField v-if="mode === 'post'" label="标题">
        <UiInput v-model.trim="title" placeholder="标题" :disabled="loading" />
      </UiField>

      <UiField label="内容">
        <PostBlockEditor
          v-if="mode === 'post'"
          v-model="blocks"
          :disabled="loading"
        />
        <UiTextarea
          v-else
          v-model.trim="content"
          :rows="6"
          placeholder="支持 Markdown"
          :disabled="loading"
        />
      </UiField>

      <p v-if="error" class="error edit-content-modal-error" role="alert">{{ error }}</p>
    </div>

    <template #footer>
      <UiButton variant="secondary" :disabled="loading" @click="$emit('close')">取消</UiButton>
      <UiButton :disabled="loading" @click="submit">{{ loading ? '保存中…' : '保存' }}</UiButton>
    </template>
  </UiModal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import UiButton from '../ui/UiButton.vue'
import UiField from '../ui/UiField.vue'
import UiInput from '../ui/UiInput.vue'
import UiModal from '../ui/UiModal.vue'
import UiTextarea from '../ui/UiTextarea.vue'
import PostBlockEditor from '../posts/PostBlockEditor.vue'

const props = defineProps({
  mode: { type: String, default: 'post' }, // post | comment
  loading: { type: Boolean, default: false },
  initialTitle: { type: String, default: '' },
  initialContent: { type: String, default: '' },
  initialBlocks: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'submit'])

const title = ref(String(props.initialTitle || ''))
const content = ref(String(props.initialContent || ''))
const blocks = ref(normalizeBlocks(props.initialBlocks))
const error = ref('')

watch(
  () => props.initialTitle,
  (v) => {
    title.value = String(v || '')
  }
)
watch(
  () => props.initialContent,
  (v) => {
    content.value = String(v || '')
  }
)
watch(
  () => props.initialBlocks,
  (v) => {
    blocks.value = normalizeBlocks(v)
  },
  { deep: true }
)

const headerTitle = computed(() => (props.mode === 'comment' ? '编辑评论' : '编辑帖子'))

async function submit() {
  error.value = ''
  const blockValidationError = props.mode === 'post' ? validateMediaBlocks(blocks.value) : ''
  const payload = {
    title: title.value,
    content: content.value,
    blocks: publishableBlocks(blocks.value)
  }
  if (props.mode === 'post' && !String(payload.title || '').trim()) {
    error.value = '标题不能为空'
    return
  }
  if (blockValidationError) {
    error.value = blockValidationError
    return
  }
  if (props.mode === 'post' && payload.blocks.length === 0) {
    error.value = '内容不能为空'
    return
  }
  if (props.mode !== 'post' && !String(payload.content || '').trim()) {
    error.value = '内容不能为空'
    return
  }
  emit('submit', payload)
}

function normalizeBlocks(value) {
  const list = Array.isArray(value) ? value : []
  return list.length > 0 ? list : [{ type: 'paragraph', text: '' }]
}

function isMediaBlock(block) {
  return ['image', 'video', 'file'].includes(String(block?.type || '').toLowerCase())
}

function hasLocalMediaSelection(block) {
  return !!(
    block?.selectedFile ||
    block?.file ||
    block?.previewUrl ||
    block?.localPreviewUrl ||
    block?.uploadId ||
    block?.uploadError ||
    block?.error
  )
}

function validateMediaBlocks(value) {
  for (const block of normalizeBlocks(value)) {
    if (!isMediaBlock(block)) continue
    const state = String(block?.uploadState || '').toLowerCase()
    const hasAsset = !!String(block?.assetId || '').trim()
    if (state === 'uploading' || state === 'pending') return '媒体仍在上传，请等待上传完成后再保存'
    if (state === 'failed') return '媒体上传失败，请重试或移除后再保存'
    if (state === 'completed' && !hasAsset) return '媒体上传失败，请重试或移除后再保存'
    if (!hasAsset && hasLocalMediaSelection(block)) return '媒体仍在上传，请等待上传完成后再保存'
  }
  return ''
}

function publishableBlocks(value) {
  return normalizeBlocks(value)
    .filter((block) => {
      const type = String(block?.type || '').toLowerCase()
      if (type === 'paragraph' || type === 'code') return String(block?.text || '').trim()
      return !!String(block?.assetId || '').trim()
    })
    .map((block) => {
      const type = String(block?.type || '').toLowerCase()
      const clean = { type }
      if (type === 'paragraph' || type === 'code') clean.text = String(block.text || '')
      if (type === 'code' && block.language) clean.language = String(block.language)
      if (['image', 'video', 'file'].includes(type)) clean.assetId = String(block.assetId || '').trim()
      if ((type === 'image' || type === 'video') && block.caption) clean.caption = String(block.caption)
      if (type === 'file' && block.displayName) clean.displayName = String(block.displayName)
      if (block.metadata && typeof block.metadata === 'object') clean.metadata = block.metadata
      return clean
    })
}
</script>

<style scoped>
.edit-content-modal-body {
  display: grid;
  gap: var(--space-3);
}

.edit-content-modal-error {
  margin: 0;
  font-size: var(--text-xs);
}
</style>

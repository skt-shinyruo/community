<template>
  <div class="post-block-editor">
    <div v-for="(block, index) in blocks" :key="block.clientId" class="post-block">
      <template v-if="isTextBlock(block)">
        <UiTextarea
          :model-value="block.text"
          :data-test="`block-text-${index}`"
          :disabled="disabled"
          :placeholder="block.type === 'code' ? '代码' : '正文内容...'"
          :rows="block.type === 'code' ? 5 : 4"
          class="post-block-editor-textarea"
          @update:model-value="updateBlock(index, { text: $event })"
        />
        <div v-if="block.type === 'code'" class="post-block-code-header">
          <span class="post-block-code-tag">代码块</span>
          <UiInput
            :model-value="block.language"
            :disabled="disabled"
            size="sm"
            placeholder="语言（如 ts / py / java）"
            class="post-block-editor-language"
            @update:model-value="updateBlock(index, { language: $event })"
          />
        </div>
        <UiTextarea
          :model-value="block.text"
          :data-test="`block-text-${index}`"
          :disabled="disabled"
          :placeholder="block.type === 'code' ? '代码' : '正文内容...'"
          :rows="block.type === 'code' ? 5 : 4"
          class="post-block-editor-textarea"
          :class="{ 'post-block-editor-textarea--code': block.type === 'code' }"
          @update:model-value="updateBlock(index, { text: $event })"
        />
        <div class="post-block-editor-actions">
          <UiButton
            variant="ghost"
            size="sm"
            :disabled="disabled"
            :aria-label="removeLabel(block, index)"
            @click="removeBlock(index)"
          >
            移除
          </UiButton>
        </div>
      </template>

      <PostMediaUploadBlock
        v-else
        :block="block"
        :index="index"
        :disabled="disabled"
        @update:block="replaceBlock(index, $event)"
        @remove="removeBlock(index)"
      />
    </div>

    <div class="post-block-editor-toolbar">
      <UiButton variant="secondary" size="sm" data-test="add-paragraph-block" :disabled="disabled" @click="addBlock('paragraph')">
        <Pilcrow :size="13" aria-hidden="true" />
        <span>段落</span>
      </UiButton>
      <UiButton variant="secondary" size="sm" data-test="add-image-block" :disabled="disabled" @click="addBlock('image')">
        <ImageIcon :size="13" aria-hidden="true" />
        <span>图片</span>
      </UiButton>
      <UiButton variant="secondary" size="sm" data-test="add-video-block" :disabled="disabled" @click="addBlock('video')">
        <Video :size="13" aria-hidden="true" />
        <span>视频</span>
      </UiButton>
      <UiButton variant="secondary" size="sm" data-test="add-file-block" :disabled="disabled" @click="addBlock('file')">
        <FileText :size="13" aria-hidden="true" />
        <span>文件</span>
      </UiButton>
      <UiButton variant="secondary" size="sm" data-test="add-code-block" :disabled="disabled" @click="addBlock('code')">
        <Code :size="13" aria-hidden="true" />
        <span>代码</span>
      </UiButton>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Code, FileText, Image as ImageIcon, Pilcrow, Video } from 'lucide-vue-next'
import UiButton from '../ui/UiButton.vue'
import UiInput from '../ui/UiInput.vue'
import UiTextarea from '../ui/UiTextarea.vue'
import PostMediaUploadBlock from './PostMediaUploadBlock.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

let nextClientId = 1

const blocks = ref(normalizeBlocks(props.modelValue))

function createClientId() {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${nextClientId++}`
  return `post-block-${random}`
}

function defaultBlock(type) {
  if (type === 'code') return { clientId: createClientId(), type: 'code', text: '', language: '' }
  if (type === 'image') return { clientId: createClientId(), type: 'image', assetId: '', caption: '', uploadState: 'idle', uploadProgress: null }
  if (type === 'video') return { clientId: createClientId(), type: 'video', assetId: '', caption: '', uploadState: 'idle', uploadProgress: null }
  if (type === 'file') return { clientId: createClientId(), type: 'file', assetId: '', displayName: '', uploadState: 'idle', uploadProgress: null }
  return { clientId: createClientId(), type: 'paragraph', text: '' }
}

function clientFields(source) {
  return {
    clientId: String(source.clientId || createClientId())
  }
}

function normalizeUploadProgress(value) {
  if (value == null || value === '') return null
  const progress = Number(value)
  if (!Number.isFinite(progress)) return null
  return Math.min(100, Math.max(0, Math.round(progress)))
}

function normalizeBlock(block) {
  const source = block && typeof block === 'object' ? block : {}
  const type = String(source.type || 'paragraph').toLowerCase()
  if (type === 'code') {
    return {
      ...clientFields(source),
      type: 'code',
      text: String(source.text || ''),
      language: String(source.language || '')
    }
  }
  if (type === 'image') {
    return {
      ...clientFields(source),
      type: 'image',
      assetId: String(source.assetId || ''),
      caption: String(source.caption || ''),
      uploadState: String(source.uploadState || 'idle'),
      uploadProgress: normalizeUploadProgress(source.uploadProgress)
    }
  }
  if (type === 'video') {
    return {
      ...clientFields(source),
      type: 'video',
      assetId: String(source.assetId || ''),
      caption: String(source.caption || ''),
      uploadState: String(source.uploadState || 'idle'),
      uploadProgress: normalizeUploadProgress(source.uploadProgress)
    }
  }
  if (type === 'file') {
    return {
      ...clientFields(source),
      type: 'file',
      assetId: String(source.assetId || ''),
      displayName: String(source.displayName || ''),
      uploadState: String(source.uploadState || 'idle'),
      uploadProgress: normalizeUploadProgress(source.uploadProgress)
    }
  }
  return { ...clientFields(source), type: 'paragraph', text: String(source.text || '') }
}

function normalizeBlocks(value) {
  const normalized = (Array.isArray(value) ? value : []).map(normalizeBlock)
  return normalized.length > 0 ? normalized : [defaultBlock('paragraph')]
}

function emitBlocks(nextBlocks) {
  blocks.value = nextBlocks.map(normalizeBlock)
  emit('update:modelValue', blocks.value)
}

function isTextBlock(block) {
  return block?.type === 'paragraph' || block?.type === 'code'
}

function blockTypeLabel(block) {
  if (block?.type === 'code') return '代码'
  if (block?.type === 'image') return '图片'
  if (block?.type === 'video') return '视频'
  if (block?.type === 'file') return '文件'
  return '段落'
}

function removeLabel(block, index) {
  return `移除${blockTypeLabel(block)}块 ${index + 1}`
}

function updateBlock(index, patch) {
  const next = blocks.value.map((block, i) => (i === index ? normalizeBlock({ ...block, ...patch }) : block))
  emitBlocks(next)
}

function replaceBlock(index, block) {
  const next = blocks.value.map((existing, i) => (i === index ? normalizeBlock(block) : existing))
  emitBlocks(next)
}

function removeBlock(index) {
  const next = blocks.value.filter((_, i) => i !== index)
  emitBlocks(next.length > 0 ? next : [defaultBlock('paragraph')])
}

function addBlock(type) {
  emitBlocks([...blocks.value, defaultBlock(type)])
}

watch(
  () => props.modelValue,
  (value) => {
    blocks.value = normalizeBlocks(value)
  },
  { deep: true }
)
</script>

<style scoped>
.post-block-editor {
  display: grid;
  gap: var(--space-3);
}

.post-block {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  transition: border-color var(--duration-fast) var(--ease-standard);
}

.post-block:hover {
  border-color: var(--border-strong);
}

.post-block-code-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.post-block-code-tag {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--accent-text);
  background: var(--accent-weak);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}

.post-block-editor-language {
  max-width: 220px;
}

:deep(.post-block-editor-textarea--code) {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  tab-size: 2;
  line-height: var(--line-normal);
  background: var(--bg);
}

.post-block-editor-actions {
  display: flex;
  justify-content: flex-end;
}

.post-block-editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--surface-2);
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
}
</style>


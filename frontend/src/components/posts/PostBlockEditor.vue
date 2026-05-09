<template>
  <div class="post-block-editor">
    <div v-for="(block, index) in blocks" :key="block.key || index" class="post-block">
      <template v-if="isTextBlock(block)">
        <UiTextarea
          :model-value="block.text"
          :data-test="`block-text-${index}`"
          :disabled="disabled"
          :placeholder="block.type === 'code' ? '代码' : '正文内容...'"
          :rows="block.type === 'code' ? 5 : 4"
          class="post-block-editor-textarea"
          @update:modelValue="updateBlock(index, { text: $event })"
        />
        <UiInput
          v-if="block.type === 'code'"
          :model-value="block.language"
          :disabled="disabled"
          placeholder="语言"
          class="post-block-editor-language"
          @update:modelValue="updateBlock(index, { language: $event })"
        />
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
      <UiButton variant="secondary" data-test="add-paragraph-block" :disabled="disabled" @click="addBlock('paragraph')">
        段落
      </UiButton>
      <UiButton variant="secondary" data-test="add-image-block" :disabled="disabled" @click="addBlock('image')">
        图片
      </UiButton>
      <UiButton variant="secondary" data-test="add-video-block" :disabled="disabled" @click="addBlock('video')">
        视频
      </UiButton>
      <UiButton variant="secondary" data-test="add-file-block" :disabled="disabled" @click="addBlock('file')">
        文件
      </UiButton>
      <UiButton variant="secondary" data-test="add-code-block" :disabled="disabled" @click="addBlock('code')">
        代码
      </UiButton>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import UiButton from '../ui/UiButton.vue'
import UiInput from '../ui/UiInput.vue'
import UiTextarea from '../ui/UiTextarea.vue'
import PostMediaUploadBlock from './PostMediaUploadBlock.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

const blocks = ref(normalizeBlocks(props.modelValue))

function defaultBlock(type) {
  if (type === 'code') return { type: 'code', text: '', language: '' }
  if (type === 'image') return { type: 'image', assetId: '', caption: '', uploadState: 'idle' }
  if (type === 'video') return { type: 'video', assetId: '', caption: '', uploadState: 'idle' }
  if (type === 'file') return { type: 'file', assetId: '', displayName: '', uploadState: 'idle' }
  return { type: 'paragraph', text: '' }
}

function normalizeBlock(block) {
  const source = block && typeof block === 'object' ? block : {}
  const type = String(source.type || 'paragraph').toLowerCase()
  if (type === 'code') return { type: 'code', text: String(source.text || ''), language: String(source.language || '') }
  if (type === 'image') {
    return {
      type: 'image',
      assetId: String(source.assetId || ''),
      caption: String(source.caption || ''),
      uploadState: String(source.uploadState || 'idle')
    }
  }
  if (type === 'video') {
    return {
      type: 'video',
      assetId: String(source.assetId || ''),
      caption: String(source.caption || ''),
      uploadState: String(source.uploadState || 'idle')
    }
  }
  if (type === 'file') {
    return {
      type: 'file',
      assetId: String(source.assetId || ''),
      displayName: String(source.displayName || ''),
      uploadState: String(source.uploadState || 'idle')
    }
  }
  return { type: 'paragraph', text: String(source.text || '') }
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

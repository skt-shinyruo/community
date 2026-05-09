<template>
  <div class="post-block-renderer">
    <template v-for="(block, index) in normalizedBlocks" :key="block.id || `${block.type}-${index}`">
      <p v-if="block.type === 'paragraph'" class="post-render-block post-render-paragraph">
        {{ block.text }}
      </p>

      <pre v-else-if="block.type === 'code'" class="post-render-block post-render-code"><code>{{ block.text }}</code></pre>

      <figure v-else-if="block.type === 'image'" class="post-render-block post-render-media">
        <img v-if="imageUrl(block)" :src="imageUrl(block)" :alt="block.caption || '图片'" loading="lazy" />
        <div v-else class="post-render-placeholder">图片暂不可用</div>
        <figcaption v-if="block.caption">{{ block.caption }}</figcaption>
      </figure>

      <figure v-else-if="block.type === 'video'" class="post-render-block post-render-media">
        <video v-if="videoSources(block).length > 0" controls :poster="block.media?.posterUrl || undefined">
          <source
            v-for="source in videoSources(block)"
            :key="source.url"
            :src="source.url"
            :type="source.contentType || 'video/mp4'"
          />
        </video>
        <div v-else class="post-render-video-state">
          <strong>{{ videoStateText(block) }}</strong>
          <a v-if="downloadUrl(block)" :href="downloadUrl(block)" download>下载原视频</a>
        </div>
        <figcaption v-if="block.caption">{{ block.caption }}</figcaption>
      </figure>

      <a
        v-else-if="block.type === 'file'"
        class="post-render-block post-render-file"
        :href="downloadUrl(block)"
        download
      >
        <span class="post-render-file-name">{{ block.displayName || block.media?.fileName || '附件' }}</span>
        <span class="post-render-file-meta">{{ formatBytes(block.media?.contentLength) }}</span>
      </a>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  blocks: { type: Array, default: () => [] }
})

const normalizedBlocks = computed(() => (Array.isArray(props.blocks) ? props.blocks : [])
  .map((block) => {
    const raw = block && typeof block === 'object' ? block : {}
    return {
      ...raw,
      type: String(raw.type || 'paragraph').toLowerCase(),
      text: raw.text == null ? '' : String(raw.text),
      caption: raw.caption == null ? '' : String(raw.caption),
      displayName: raw.displayName == null ? '' : String(raw.displayName),
      media: raw.media && typeof raw.media === 'object' ? raw.media : null
    }
  })
  .filter((block) => {
    if (block.type === 'paragraph' || block.type === 'code') return block.text.trim()
    return ['image', 'video', 'file'].includes(block.type)
  }))

function imageUrl(block) {
  return String(block?.media?.url || block?.media?.downloadUrl || '').trim()
}

function downloadUrl(block) {
  return String(block?.media?.downloadUrl || block?.media?.url || '').trim()
}

function videoSources(block) {
  return Array.isArray(block?.media?.sources) ? block.media.sources.filter((source) => source?.url) : []
}

function videoStateText(block) {
  const state = String(block?.media?.videoState || '').toUpperCase()
  if (state === 'FAILED') return '视频处理失败'
  if (state === 'READY') return '视频已处理'
  return '转码处理中'
}

function formatBytes(value) {
  const bytes = Number(value || 0)
  if (!Number.isFinite(bytes) || bytes <= 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

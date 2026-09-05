<!-- 头像组件：支持图片与 fallback（首字母/占位），图片加载失败自动回退。 -->
<template>
  <span class="avatar" :style="{ width: px(size), height: px(size) }" :title="title">
    <img 
      v-if="src && !hasError" 
      class="avatar-img" 
      :src="src" 
      :alt="alt || 'avatar'" 
      :style="{ width: px(size), height: px(size) }" 
      @error="onError"
    />
    <span v-else class="avatar-fallback" :style="{ fontSize: px(size * 0.4) }">{{ fallback }}</span>
  </span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  size: { type: Number, default: 28 },
  name: { type: String, default: '' },
  title: { type: String, default: '' }
})

const hasError = ref(false)

watch(() => props.src, () => {
  hasError.value = false
})

function onError() {
  hasError.value = true
}

function px(n) {
  return `${Number(n || 0)}px`
}

const fallback = computed(() => {
  const n = String(props.name || '').trim()
  return n ? n.slice(0, 1).toUpperCase() : '?'
})
</script>

<!-- 样式自全局 components.css 退役后迁入（原 .avatar 一族，类名与外观不变）。 -->
<style scoped>
.avatar {
  border-radius: 50%;
  background: color-mix(in srgb, var(--surface) 54%, var(--surface-2) 46%);
  border: 1px solid var(--border);
  display: inline-grid;
  place-items: center;
  overflow: hidden;
}

.avatar-img {
  display: block;
  object-fit: cover;
}

.avatar-fallback {
  font-size: var(--text-xs);
  font-weight: 800;
  color: var(--text-2);
}
</style>

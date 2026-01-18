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

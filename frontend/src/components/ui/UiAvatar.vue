<!-- 头像组件：支持图片与 fallback（首字母/占位）。 -->
<template>
  <span class="avatar" :style="{ width: px(size), height: px(size) }" :title="title">
    <img v-if="src" class="avatar-img" :src="src" :alt="alt || 'avatar'" :style="{ width: px(size), height: px(size) }" />
    <span v-else class="avatar-fallback">{{ fallback }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  size: { type: Number, default: 28 },
  name: { type: String, default: '' },
  title: { type: String, default: '' }
})

function px(n) {
  return `${Number(n || 0)}px`
}

const fallback = computed(() => {
  const n = String(props.name || '').trim()
  return n ? n.slice(0, 1).toUpperCase() : '?'
})
</script>


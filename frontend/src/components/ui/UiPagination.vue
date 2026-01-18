<!-- 简易分页组件：上一页/下一页 + 当前页显示（0-based 内部，UI 显示 1-based）。 -->
<template>
  <div class="row" style="justify-content: space-between; flex-wrap: wrap">
    <div class="muted">第 {{ page + 1 }} 页</div>
    <div class="row">
      <UiButton variant="secondary" :disabled="disabled || page <= 0" @click="$emit('prev')">上一页</UiButton>
      <UiButton :disabled="disabled || !hasNext" @click="$emit('next')">下一页</UiButton>
    </div>
  </div>
</template>

<script setup>
import UiButton from './UiButton.vue'

defineProps({
  page: { type: Number, required: true },
  hasNext: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false }
})

defineEmits(['prev', 'next'])
</script>

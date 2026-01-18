<!-- 全局 Toast：用于展示统一的提示信息（info/error），支持关闭。 -->
<template>
  <div v-if="message" class="toast" :class="type === 'error' ? 'error' : ''">
    <div style="min-width: 0">
      <div class="toast-title">{{ type === 'error' ? '发生错误' : '提示' }}</div>
      <div class="toast-message">{{ message }}</div>
    </div>
    <button class="btn ghost" @click="$emit('close')">关闭</button>
  </div>
</template>

<script setup>
defineProps({
  type: { type: String, default: '' }, // info | error
  message: { type: String, default: '' }
})

defineEmits(['close'])
</script>


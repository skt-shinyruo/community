<!-- 基础卡片容器：统一边框、圆角与内边距。 -->
<template>
  <div class="card" :class="{ flat, 'object-card': object }">
    <slot />
  </div>
</template>

<script setup>
defineProps({
  flat: { type: Boolean, default: false },
  object: { type: Boolean, default: false }
})
</script>

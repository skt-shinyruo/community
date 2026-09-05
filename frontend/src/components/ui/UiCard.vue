<!-- 基础卡片容器：统一边框、圆角与内边距。 -->
<template>
  <div class="card" :class="{ flat }">
    <slot />
  </div>
</template>

<script setup>
defineProps({
  flat: { type: Boolean, default: false }
})
</script>

<!-- 样式自全局 components.css 退役后迁入（原 .card / .card.flat，类名与外观不变）。 -->
<style scoped>
/* :where() 保持迁入前的全局低权重级联：调用方作用域覆盖类照常获胜。 */
:where(.card) {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--card-padding);
  box-shadow: none;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

:where(.card:hover) {
  border-color: var(--border-strong);
}

:where(.card.flat) {
  box-shadow: none;
  border-color: transparent;
  background: transparent;
  padding: 0;
}
</style>

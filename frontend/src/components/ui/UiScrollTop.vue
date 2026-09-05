<template>
  <Transition name="fade">
    <UiIconButton
      v-if="visible"
      class="scroll-top"
      aria-label="回到顶部"
      title="回到顶部"
      @click="scrollToTop"
    >
      <ArrowUp :size="24" aria-hidden="true" />
    </UiIconButton>
  </Transition>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ArrowUp } from 'lucide-vue-next'
import UiIconButton from './UiIconButton.vue'

const visible = ref(false)

function checkScroll() {
  visible.value = window.scrollY > 300
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', checkScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', checkScroll)
})
</script>

<style scoped>
.scroll-top {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--accent);
  color: var(--accent-contrast);
  border: none;
  box-shadow: var(--shadow-lg);
  z-index: var(--z-nav);
  /* transform/filter 走悬停动效；opacity 保留 0.3s 显隐节奏（作用域规则优先级高于全局 .fade-*） */
  transition: transform 0.2s, filter 0.2s, opacity 0.3s;
}
/* 覆盖 UiIconButton 的幽灵态 hover/active，保持 accent 实心圆形外观 */
.scroll-top:hover,
.scroll-top:active {
  background: var(--accent);
  border-color: transparent;
  color: var(--accent-contrast);
}
.scroll-top:hover {
  transform: translateY(-2px);
  filter: brightness(1.1);
}
</style>

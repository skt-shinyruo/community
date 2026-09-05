<!-- 基础按钮组件：统一 primary/secondary 与 disabled 的交互表现；to / href 形态吸收链接外观按钮。 -->
<template>
  <RouterLink
    v-if="to"
    :to="to"
    class="btn"
    :class="[variantClass, sizeClass]"
    :title="title"
    :aria-disabled="disabled ? 'true' : undefined"
    @click="onLinkClick"
  >
    <slot />
  </RouterLink>
  <a
    v-else-if="href"
    :href="href"
    class="btn"
    :class="[variantClass, sizeClass]"
    :title="title"
    :aria-disabled="disabled ? 'true' : undefined"
    @click="onLinkClick"
  >
    <slot />
  </a>
  <button
    v-else
    class="btn"
    :class="[variantClass, sizeClass]"
    :type="type"
    :disabled="disabled"
    :title="title"
    @click="$emit('click', $event)"
  >
    <slot />
  </button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'primary' }, // primary | secondary | ghost | danger | dangerSecondary
  size: { type: String, default: 'md' }, // md | sm
  type: { type: String, default: 'button' },
  title: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  to: { type: [String, Object], default: '' },
  href: { type: String, default: '' }
})

const emit = defineEmits(['click'])

const VARIANT_CLASS_MAP = Object.freeze({
  secondary: 'secondary',
  ghost: 'ghost',
  danger: 'danger',
  dangerSecondary: 'danger secondary'
})

const SIZE_CLASS_MAP = Object.freeze({ sm: 'sm' })

const variantClass = computed(() => {
  return VARIANT_CLASS_MAP[props.variant] || ''
})

const sizeClass = computed(() => SIZE_CLASS_MAP[props.size] || '')

function onLinkClick(event) {
  if (props.disabled) {
    event?.preventDefault?.()
    return
  }
  emit('click', event)
}
</script>

<!-- 样式自全局 components.css 退役后迁入（原 .btn 一族，类名与外观不变）。 -->
<style scoped>
/* :where() 保持迁入前的全局低权重级联：调用方作用域覆盖类照常获胜。 */
:where(.btn) {
  height: var(--control-height);
  padding: 0 var(--control-padding-x);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-weight: 700;
  font-size: var(--text-sm);
  background: var(--accent);
  color: var(--accent-contrast);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  user-select: none;
  transition: background-color 0.15s ease-out, border-color 0.15s ease-out, color 0.15s ease-out, box-shadow 0.15s ease-out;
  box-shadow: var(--shadow-sm);
}

:where(.btn.sm) {
  height: clamp(28px, calc(var(--control-height) - 4px), 36px);
  padding: 0 calc(var(--control-padding-x) - 4px);
  font-size: var(--text-xs);
}

:where(.btn:hover) {
  background: var(--accent-hover);
  box-shadow: var(--shadow-sm);
}

:where(.btn:active) {
  background: var(--accent);
}

:where(.btn:disabled) {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

:where(.btn:focus-visible) {
  box-shadow: var(--shadow-sm), var(--focus-ring);
}

:where(.btn.secondary) {
  background: color-mix(in srgb, var(--surface) 84%, var(--bg) 16%);
  color: var(--text-1);
  border-color: var(--border);
  box-shadow: var(--shadow-sm);
}

:where(.btn.secondary:hover) {
  background: var(--surface-2);
  border-color: var(--border-strong);
  color: var(--text-1);
}

:where(.btn.secondary:focus-visible) {
  box-shadow: var(--shadow-sm), var(--focus-ring);
}

:where(.btn.ghost) {
  background: transparent;
  color: var(--text-2);
  border-color: color-mix(in srgb, var(--border) 45%, transparent);
  box-shadow: none;
}

:where(.btn.ghost:hover) {
  background: color-mix(in srgb, var(--surface-2) 70%, transparent);
  color: var(--text-1);
}

:where(.btn.ghost:focus-visible) {
  box-shadow: var(--focus-ring);
}

:where(.btn.danger) {
  background: var(--danger);
  color: var(--accent-contrast);
}

:where(.btn.danger:hover) {
  background: var(--danger-hover);
}

:where(.btn.danger.secondary) {
  background: var(--surface);
  border-color: var(--border);
  color: var(--danger);
}

:where(.btn.danger.secondary:hover) {
  background: var(--danger-weak);
  border-color: var(--danger-weak);
}
</style>

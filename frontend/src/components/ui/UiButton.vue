<!-- 基础按钮组件：统一 primary/secondary 与 disabled 的交互表现；to / href 形态吸收链接外观按钮。 -->
<template>
  <RouterLink
    v-if="to"
    :to="to"
    class="btn"
    :class="variantClass"
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
    :class="variantClass"
    :title="title"
    :aria-disabled="disabled ? 'true' : undefined"
    @click="onLinkClick"
  >
    <slot />
  </a>
  <button
    v-else
    class="btn"
    :class="variantClass"
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

const variantClass = computed(() => {
  return VARIANT_CLASS_MAP[props.variant] || ''
})

function onLinkClick(event) {
  if (props.disabled) {
    event?.preventDefault?.()
    return
  }
  emit('click', event)
}
</script>

<!-- UiRoleBadge：统一身份徽章（ADMIN/MODERATOR 等）。 -->
<template>
  <UiBadge v-if="label" :variant="variant" :style="styleObject">{{ label }}</UiBadge>
</template>

<script setup>
import { computed } from 'vue'
import UiBadge from './UiBadge.vue'

const props = defineProps({
  user: { type: Object, default: null },
  size: { type: String, default: 'sm' } // sm | md
})

function getAuthorities(user) {
  return Array.isArray(user?.authorities) ? user.authorities.map(String) : []
}

const label = computed(() => {
  const u = props.user || null
  if (!u) return ''

  const authorities = getAuthorities(u)
  if (authorities.includes('ROLE_ADMIN') || Number(u?.type || 0) === 1) return 'ADMIN'
  if (authorities.includes('ROLE_MODERATOR') || Number(u?.type || 0) === 2) return 'MOD'
  return ''
})

const variant = computed(() => {
  if (label.value === 'ADMIN') return 'accent'
  if (label.value === 'MOD') return 'warning'
  return 'default'
})

const styleObject = computed(() => {
  if (props.size === 'md') return { height: '18px', fontSize: '11px' }
  return { height: '16px', fontSize: '10px' }
})
</script>


<template>
  <div class="breadcrumb row">
    <RouterLink to="/" class="crumb-link">首页</RouterLink>
    <template v-for="(item, index) in resolvedItems" :key="index">
      <span class="crumb-sep">/</span>
      <RouterLink v-if="item.to" :to="item.to" class="crumb-link">{{ item.label }}</RouterLink>
      <span v-else class="crumb-text">{{ item.label }}</span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getBreadcrumbItems } from '../../router/navigation'

const props = defineProps({
  // 兼容手动传入（旧用法）：[{ label, to? }]
  items: { type: Array, default: () => [] },
  // 自动推导：当 items 为空时，根据 route + navigation 配置生成。
  auto: { type: Boolean, default: true }
})

const route = useRoute()

const resolvedItems = computed(() => {
  const manual = Array.isArray(props.items) ? props.items : []
  if (manual.length > 0) return manual
  if (!props.auto) return []
  return getBreadcrumbItems(route)
})
</script>

<style scoped>
.breadcrumb {
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 12px;
}
.crumb-link {
  color: var(--text-2);
  text-decoration: none;
}
.crumb-link:hover {
  text-decoration: underline;
  color: var(--text-1);
}
.crumb-sep {
  margin: 0 6px;
  color: var(--muted);
}
.crumb-text {
  color: var(--text-1);
  font-weight: 500;
}
</style>

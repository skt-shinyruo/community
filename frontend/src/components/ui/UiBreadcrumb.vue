<template>
  <div class="breadcrumb row">
    <template v-if="controlled">
      <template v-for="(item, index) in items" :key="index">
        <span v-if="index > 0" class="crumb-sep">/</span>
        <span v-if="index === items.length - 1" class="crumb-text" aria-current="page">{{ item.label }}</span>
        <button
          v-else
          type="button"
          class="crumb-link crumb-button"
          :disabled="disabled"
          @click="$emit('select', index)"
        >{{ item.label }}</button>
      </template>
    </template>
    <template v-else>
      <RouterLink to="/" class="crumb-link">首页</RouterLink>
      <template v-for="(item, index) in resolvedItems" :key="index">
        <span class="crumb-sep">/</span>
        <RouterLink v-if="item.to" :to="item.to" class="crumb-link">{{ item.label }}</RouterLink>
        <span v-else class="crumb-text">{{ item.label }}</span>
      </template>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getRouteBreadcrumbItems } from '../../router/routeCatalog'

// items 缺省时沿用路由面包屑（首页 + routeCatalog 登记项）；传入 items 进入受控模式，
// 用于 Drive 文件夹路径这类状态驱动路径：非末级项是可键盘操作的按钮，点击发出 select(index)，
// 末级项是带 aria-current 的当前位置文本。
const props = defineProps({
  items: { type: Array, default: null }, // [{ label }]
  disabled: { type: Boolean, default: false }
})

defineEmits(['select'])

const route = useRoute()
const controlled = computed(() => Array.isArray(props.items))
const resolvedItems = computed(() => getRouteBreadcrumbItems(String(route?.name || ''), route?.params || {}))
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
.crumb-button {
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  cursor: pointer;
}
.crumb-button:disabled {
  color: var(--muted);
  cursor: not-allowed;
  text-decoration: none;
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

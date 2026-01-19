<template>
  <div class="breadcrumb row">
    <RouterLink to="/" class="crumb-link">首页</RouterLink>
    <template v-for="(item, index) in items" :key="index">
      <span class="crumb-sep">/</span>
      <RouterLink v-if="item.to" :to="item.to" class="crumb-link">{{ item.label }}</RouterLink>
      <span v-else class="crumb-text">{{ item.label }}</span>
    </template>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] } // { label, to? }[]
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

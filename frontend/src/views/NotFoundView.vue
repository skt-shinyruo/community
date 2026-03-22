<!-- 404 页面：未知路由兜底。 -->
<template>
  <div class="page reading">
    <UiCard class="system-state-card">
      <UiPageHeader>
        <template #title>404 页面不存在</template>
        <template #subtitle>当前地址没有对应内容。你可以返回讨论区，重新进入有效页面。</template>
      </UiPageHeader>
      <div class="system-state-actions">
        <RouterLink class="btn secondary" to="/posts">返回帖子列表</RouterLink>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
</script>

<style scoped>
.system-state-card {
  display: grid;
  gap: 12px;
}

.system-state-actions {
  margin-top: 12px;
}
</style>

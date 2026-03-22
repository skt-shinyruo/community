<!-- 无权限页面：用于路由守卫的体验层拦截。 -->
<template>
  <div class="page reading">
    <UiCard class="system-state-card">
      <UiPageHeader>
        <template #title>403 无权限</template>
        <template #subtitle>你当前没有访问该页面的权限。最终结果仍以服务端鉴权为准。</template>
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

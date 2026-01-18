<!-- 无权限页面：用于路由守卫的体验层拦截。 -->
<template>
  <div class="page reading">
    <UiCard>
      <UiPageHeader>
        <template #title>403 无权限</template>
        <template #subtitle>你当前没有访问该页面的权限（最终以服务端鉴权为准）</template>
      </UiPageHeader>
      <div style="margin-top: 12px">
        <RouterLink class="btn secondary" to="/posts">返回帖子列表</RouterLink>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
</script>

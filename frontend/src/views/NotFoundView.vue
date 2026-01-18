<!-- 404 页面：未知路由兜底。 -->
<template>
  <div class="page reading">
    <UiCard>
      <UiPageHeader>
        <template #title>404 页面不存在</template>
        <template #subtitle>请检查访问路径是否正确</template>
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

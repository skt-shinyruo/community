<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <UiCard class="market-panel">
      <UiPageHeader>
        <template #title>库存管理</template>
        <template #subtitle>预存库存商品在这里维护卡密或兑换码，当前路由已独立出来。</template>
      </UiPageHeader>

      <p class="market-note">商品 ID：{{ route.params.listingId }}</p>
      <p class="muted">下一阶段会把库存查询和失效动作也接到这里；当前先保留独立入口和说明。</p>
    </UiCard>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'

const route = useRoute()
</script>

<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <section class="market-hero market-hero--compact">
      <div>
        <span class="market-kicker">我的出售</span>
        <h1>卖家工作面先独立出来</h1>
        <p>这一版先把发布、库存与订单入口拆开，后续再补卖家列表查询与批量动作。</p>
      </div>
      <div class="market-hero-actions">
        <RouterLink class="btn" :to="{ name: 'virtualMarketPublish' }">继续发布</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'virtualMarketSellingOrders' }">查看卖单</RouterLink>
      </div>
    </section>

    <UiCard class="market-panel">
      <UiPageHeader>
        <template #title>卖家待办</template>
        <template #subtitle>当前重点是保证卖家知道“发商品、发库存、发货、处理申诉”这四条主线。</template>
      </UiPageHeader>

      <ul class="market-bullets">
        <li>去发布页创建新的虚拟商品。</li>
        <li>预存库存商品从库存页追加卡密。</li>
        <li>手工交付商品从“我的出售订单”进入订单详情发货。</li>
      </ul>
    </UiCard>
  </div>
</template>

<script setup>
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
</script>

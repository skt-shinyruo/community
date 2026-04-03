<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <section class="market-hero market-hero--compact">
      <div>
        <span class="market-kicker">我的出售订单</span>
        <h1>卖家关注的是交付和争议</h1>
        <p>当前先把卖家工作面和买家工作面拆开，避免再次混成奖励订单历史。</p>
      </div>
    </section>

    <UiCard class="market-panel">
      <UiPageHeader>
        <template #title>卖家动作清单</template>
        <template #subtitle>手工交付订单需要发货，申诉订单需要给出接受或拒绝意见。</template>
      </UiPageHeader>

      <ul class="market-bullets">
        <li>状态为“已托管”的手工交付订单，进入详情提交交付内容。</li>
        <li>状态为“申诉中”的订单，及时查看管理员裁定前的处理窗口。</li>
      </ul>
    </UiCard>
  </div>
</template>

<script setup>
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
</script>

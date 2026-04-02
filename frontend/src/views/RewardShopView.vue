<template>
  <div class="page market-preview-page">
    <UiBreadcrumb />

    <UiCard class="market-preview-shell">
      <UiPageHeader>
        <template #title>资产市场预览</template>
        <template #subtitle>旧的消费入口不再作为主导航公开页保留，后续会并入统一市场与订单体系。</template>
      </UiPageHeader>

      <div class="market-preview-grid">
        <section class="market-preview-card">
          <h2>先看钱包</h2>
          <p>当前版本先把统一资产、充值、提现与转账做稳定，消费型场景随后再并到市场。</p>
        </section>
        <section class="market-preview-card">
          <h2>再看订单</h2>
          <p>历史消费记录不会丢，但会从旧入口迁到新的订单视图里继续承接。</p>
        </section>
      </div>

      <div class="market-preview-actions">
        <RouterLink class="btn" :to="{ name: 'wallet' }">前往积分钱包</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'rewardOrders' }">查看历史订单</RouterLink>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
</script>

<style scoped>
.market-preview-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.market-preview-shell,
.market-preview-card {
  display: grid;
  gap: 14px;
}

.market-preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.market-preview-card {
  padding: 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.market-preview-card h2 {
  margin: 0;
  font-size: 1.05rem;
}

.market-preview-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.market-preview-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 720px) {
  .market-preview-grid {
    grid-template-columns: 1fr;
  }
}
</style>

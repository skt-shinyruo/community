<template>
  <div class="page legacy-orders-page">
    <UiBreadcrumb />

    <UiCard class="legacy-orders-shell">
      <UiPageHeader>
        <template #title>历史订单入口</template>
        <template #subtitle>旧的消费记录页不再作为主导航入口保留，后续订单会统一迁入新的市场与钱包语义里。</template>
      </UiPageHeader>

      <div class="legacy-orders-grid">
        <section class="legacy-orders-card">
          <h2>记录不会丢</h2>
          <p>现有消费记录会继续保留，只是不再挂在旧入口上单独承接。</p>
        </section>
        <section class="legacy-orders-card">
          <h2>入口已收口</h2>
          <p>现在优先从钱包出发理解资产，再逐步过渡到新的订单与市场页面。</p>
        </section>
      </div>

      <div class="legacy-orders-actions">
        <RouterLink class="btn" :to="{ name: 'wallet' }">前往积分钱包</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'rewardShop' }">返回市场预览</RouterLink>
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
.legacy-orders-page {
  max-width: 920px;
  margin: 0 auto;
  gap: var(--space-5);
}

.legacy-orders-shell,
.legacy-orders-card {
  display: grid;
  gap: 14px;
}

.legacy-orders-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.legacy-orders-card {
  padding: 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.legacy-orders-card h2 {
  margin: 0;
  font-size: 1.05rem;
}

.legacy-orders-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.legacy-orders-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 720px) {
  .legacy-orders-grid {
    grid-template-columns: 1fr;
  }
}
</style>

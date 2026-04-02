<template>
  <div class="page legacy-ops-page">
    <UiBreadcrumb />

    <UiCard class="legacy-ops-shell">
      <UiPageHeader>
        <template #title>运营入口已迁移</template>
        <template #subtitle>旧的奖励运营后台地址只保留兼容说明，后续运营动作会统一并入钱包后台与新的市场运营台。</template>
      </UiPageHeader>

      <div class="legacy-ops-grid">
        <section class="legacy-ops-card">
          <h2>钱包后台</h2>
          <p>冻结钱包、回滚交易等高风险资产动作已经收口到统一的钱包后台。</p>
        </section>
        <section class="legacy-ops-card">
          <h2>历史运营页</h2>
          <p>商品与履约运营不会继续挂在这条旧路由上，当前页面只负责承接已有链接。</p>
        </section>
      </div>

      <div class="legacy-ops-actions">
        <RouterLink class="btn" :to="{ name: 'walletAdmin' }">前往钱包后台</RouterLink>
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
.legacy-ops-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.legacy-ops-shell,
.legacy-ops-card {
  display: grid;
  gap: 14px;
}

.legacy-ops-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.legacy-ops-card {
  padding: 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.legacy-ops-card h2 {
  margin: 0;
  font-size: 1.05rem;
}

.legacy-ops-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.legacy-ops-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 860px) {
  .legacy-ops-grid {
    grid-template-columns: 1fr;
  }
}
</style>

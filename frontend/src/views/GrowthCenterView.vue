<template>
  <div class="page legacy-asset-page">
    <UiBreadcrumb />

    <UiCard class="legacy-asset-shell">
      <UiPageHeader>
        <template #title>统一钱包入口</template>
        <template #subtitle>旧的资产相关入口已经收拢为一个钱包视图，这里只保留过渡说明与跳转。</template>
      </UiPageHeader>

      <div class="legacy-asset-grid">
        <section class="legacy-asset-card">
          <h2>一个余额</h2>
          <p>用户只看到一个积分余额，不再拆成多套公开语义或多个入口。</p>
        </section>
        <section class="legacy-asset-card">
          <h2>一个动作面</h2>
          <p>充值、提现和转账都统一进入钱包页，避免继续保留旧入口造成理解分裂。</p>
        </section>
        <section class="legacy-asset-card">
          <h2>一个主入口</h2>
          <p>主导航已切换到钱包，本页仅作为兼容地址存在。</p>
        </section>
      </div>

      <div class="legacy-asset-actions">
        <RouterLink class="btn" :to="{ name: 'wallet' }">前往积分钱包</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'posts' }">返回讨论区</RouterLink>
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
.legacy-asset-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.legacy-asset-shell,
.legacy-asset-card {
  display: grid;
  gap: 14px;
}

.legacy-asset-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.legacy-asset-card {
  padding: 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.legacy-asset-card h2 {
  margin: 0;
  font-size: 1.05rem;
}

.legacy-asset-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.legacy-asset-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 860px) {
  .legacy-asset-grid {
    grid-template-columns: 1fr;
  }
}
</style>

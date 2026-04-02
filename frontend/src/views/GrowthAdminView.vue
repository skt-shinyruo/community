<template>
  <div class="page legacy-admin-page">
    <UiBreadcrumb />

    <UiCard class="legacy-admin-shell">
      <UiPageHeader>
        <template #title>后台入口已迁移</template>
        <template #subtitle>旧的资产后台地址只保留兼容说明，冻结钱包与回滚交易已经收口到钱包后台。</template>
      </UiPageHeader>

      <div class="legacy-admin-grid">
        <section class="legacy-admin-card">
          <h2>冻结钱包</h2>
          <p>风险处置不再依赖旧资产语义，统一改为钱包状态控制。</p>
        </section>
        <section class="legacy-admin-card">
          <h2>回滚交易</h2>
          <p>异常处理改为围绕钱包交易执行，避免继续暴露旧调账概念。</p>
        </section>
      </div>

      <div class="legacy-admin-actions">
        <RouterLink class="btn" :to="{ name: 'walletAdmin' }">前往钱包后台</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'analytics' }">返回统计页</RouterLink>
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
.legacy-admin-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.legacy-admin-shell,
.legacy-admin-card {
  display: grid;
  gap: 14px;
}

.legacy-admin-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.legacy-admin-card {
  padding: 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.legacy-admin-card h2 {
  margin: 0;
  font-size: 1.05rem;
}

.legacy-admin-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.legacy-admin-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 860px) {
  .legacy-admin-grid {
    grid-template-columns: 1fr;
  }
}
</style>

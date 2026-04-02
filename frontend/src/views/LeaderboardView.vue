<template>
  <div class="page member-overview-page">
    <UiBreadcrumb />

    <UiCard class="member-overview-shell">
      <UiPageHeader>
        <template #title>公开成员概览</template>
        <template #subtitle>面向所有人的排序页已经下线，成员关系和公开动态继续保留在个人主页与讨论流里。</template>
      </UiPageHeader>

      <div class="member-overview-grid">
        <section class="member-overview-card">
          <h2>成员主页仍可访问</h2>
          <p>关注关系、最近动态和公开资料继续保留在成员主页，不再依赖统一排序页承载。</p>
        </section>
        <section class="member-overview-card">
          <h2>讨论流仍是主入口</h2>
          <p>如果想判断谁正在持续发声，直接回到帖子流与搜索页会更贴近真实语境。</p>
        </section>
      </div>

      <div class="member-overview-actions">
        <RouterLink class="btn" :to="{ name: 'posts' }">回到帖子流</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'search' }">打开搜索</RouterLink>
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
.member-overview-page {
  max-width: 900px;
  margin: 0 auto;
  gap: var(--space-5);
}

.member-overview-shell,
.member-overview-card {
  display: grid;
  gap: 14px;
}

.member-overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.member-overview-card {
  padding: 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.member-overview-card h2 {
  margin: 0;
  font-size: 1.05rem;
}

.member-overview-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.member-overview-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 720px) {
  .member-overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>

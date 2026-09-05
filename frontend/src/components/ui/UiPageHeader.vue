<!-- PageHeader：统一页面标题区（标题/副标题/操作区）。 -->
<template>
  <div class="page-header">
    <div class="page-header-left">
      <div class="page-title">
        <slot name="title" />
      </div>
      <div v-if="$slots.subtitle" class="page-subtitle muted">
        <slot name="subtitle" />
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<!-- 样式自全局 components.css 退役后迁入（原 .page-header 一族，类名与外观不变）。 -->
<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.page-header-left {
  min-width: 0;
  display: grid;
  gap: var(--space-1);
}

.page-title {
  font-weight: 800;
  font-size: var(--text-2xl);
  line-height: var(--line-tight);
}

.page-subtitle {
  font-size: var(--text-sm);
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>


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


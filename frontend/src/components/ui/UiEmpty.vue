<!-- 空状态提示组件。 -->
<template>
  <div class="empty-state stack">
    <div class="empty-icon">
       <!-- Search Empty -->
       <svg v-if="type === 'search'" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--muted)" stroke-width="1">
          <circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line>
       </svg>
       <!-- Error -->
       <svg v-else-if="type === 'error'" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--muted)" stroke-width="1">
          <circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line>
       </svg>
       <!-- Default / Data -->
       <svg v-else width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--muted)" stroke-width="1">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
          <polyline points="14 2 14 8 20 8"></polyline>
          <line x1="16" y1="13" x2="8" y2="13"></line>
          <line x1="16" y1="17" x2="8" y2="17"></line>
          <polyline points="10 9 9 9 8 9"></polyline>
       </svg>
    </div>
    <div class="empty-text"><slot>暂无数据</slot></div>
  </div>
</template>

<script setup>
defineProps({
  type: { type: String, default: 'data' } // data, search, error
})
</script>

<style scoped>
.empty-state {
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}
.empty-text {
  color: var(--text-2);
  margin-top: 16px;
  font-size: 15px;
}
</style>

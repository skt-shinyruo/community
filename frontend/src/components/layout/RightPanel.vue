<!-- RightPanel：右侧上下文面板（热门话题、社区规范）。 -->
<template>
  <div class="right-panel">
    <div class="right-panel-header">
      <div style="font-weight: 700; font-size: 16px">探索</div>
    </div>

    <div class="right-panel-body">
      <!-- Trending Section -->
      <div class="card flat">
        <div class="stack" style="gap: 12px">
          <div style="font-weight: 600; font-size: 14px; color: var(--text-2)">热门话题</div>
          <div class="stack" style="gap: 8px">
            <div v-for="i in 4" :key="i" class="row topic-item" style="gap: 10px; cursor: pointer">
              <div class="topic-rank">{{ i }}</div>
              <div style="flex: 1; font-weight: 500">#社区设计规范讨论</div>
              <div class="muted" style="font-size: 12px">230 讨</div>
            </div>
          </div>
        </div>
      </div>

      <div class="divider"></div>

      <!-- Guidelines -->
      <div class="card flat">
        <div class="stack" style="gap: 12px">
          <div style="font-weight: 600; font-size: 14px; color: var(--text-2)">社区规范</div>
          <div class="stack" style="gap: 8px; font-size: 13px; color: var(--text-1)">
            <div class="row" style="align-items: flex-start">
              <span>📌</span>
              <span style="line-height: 1.4">友善交流，理性讨论，共建优质社区氛围。</span>
            </div>
            <div class="row" style="align-items: flex-start">
              <span>🚫</span>
              <span style="line-height: 1.4">禁止发布广告、灌水及违规内容。</span>
            </div>
          </div>
        </div>
      </div>

      <div class="divider"></div>

      <!-- Footer/Copyright -->
      <div class="muted" style="font-size: 12px; line-height: 1.6">
        © 2026 Community Inc.<br />
        <a href="#" style="text-decoration: underline">Privacy</a> · <a href="#" style="text-decoration: underline">Terms</a>
      </div>
    </div>
  </div>
</template>

<script setup>
// Static content for now
</script>

<style scoped>
.topic-item {
  padding: 6px 0;
  transition: opacity 0.2s;
}
.topic-item:hover {
  opacity: 0.8;
}

.topic-rank {
  font-weight: 700;
  color: var(--accent);
  width: 16px;
  text-align: center;
}
</style>


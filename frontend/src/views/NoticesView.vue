<template>
  <div class="page notices-page">
    <UiCard flat class="notices-hero">
      <UiPageHeader>
        <template #title>通知</template>
        <template #subtitle>把互动、关注和治理提醒整理成可快速处理的收件箱。</template>
        <template #actions>
          <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? '刷新中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>

      <div class="notices-hero-grid">
        <div class="notices-hero-card">
          <span class="notices-hero-label">通知分类</span>
          <strong>{{ items.length }}</strong>
          <p>把不同来源的提醒拆成独立流，避免一页堆满杂乱的系统信息。</p>
        </div>
        <div class="notices-hero-card">
          <span class="notices-hero-label">待处理</span>
          <strong>{{ items.reduce((total, it) => total + Number(it?.unreadCount || 0), 0) }}</strong>
          <p>优先查看仍有未读内容的通知流，直接回到更具体的详情页。</p>
        </div>
      </div>
    </UiCard>

    <UiCard class="notices-shell">
      <div class="notices-shell-head">
        <div>
          <div class="notices-eyebrow">Inbox</div>
          <h2>所有通知流</h2>
          <p>每个通知主题会保留自己的阅读状态和上下文，不再像测试面板一样平铺展示。</p>
        </div>
        <div class="muted notices-head-meta">
          {{ items.filter((it) => Number(it?.unreadCount || 0) > 0).length }} 个主题有新动态
        </div>
      </div>

      <UiEmpty v-if="error" type="error" class="notices-state">{{ error }}</UiEmpty>
      <UiEmpty v-else-if="items.length === 0 && !loading" class="notices-state">
        暂无通知
        <template #description>当有人与你互动，或系统需要提醒你时，这里会按主题出现新的通知流。</template>
      </UiEmpty>
      <div v-else-if="loading && items.length === 0" class="muted notices-state">正在同步通知…</div>

      <div v-else class="inbox-list">
        <RouterLink v-for="it in items" :key="it.topic" :to="`/notices/${it.topic}`" class="inbox-item" :class="{ unread: it.unreadCount > 0 }">
          <div class="inbox-icon" :class="it.topic" aria-hidden="true">
              <svg v-if="it.topic === 'like'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path
                  d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
                ></path>
              </svg>
              <svg v-else-if="it.topic === 'comment'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path
                  d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"
                ></path>
              </svg>
              <svg v-else-if="it.topic === 'follow'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="8.5" cy="7" r="4"></circle>
                <line x1="20" y1="8" x2="20" y2="14"></line>
                <line x1="23" y1="11" x2="17" y2="11"></line>
              </svg>
              <svg v-else-if="it.topic === 'moderation'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2l7 4v6c0 5-3 9-7 10-4-1-7-5-7-10V6l7-4z"></path>
              </svg>
              <span v-else class="inbox-icon-fallback">#</span>
            </div>

            <div class="inbox-content">
              <div class="inbox-top">
                <div class="inbox-title-row">
                  <div class="inbox-title">{{ getTopicTitle(it.topic) }}</div>
                  <span v-if="it.unreadCount > 0" class="inbox-badge">有新内容</span>
                </div>
                <div class="inbox-sub">
                  {{
                    it.topic === 'comment'
                      ? '有人回复了你的帖子或评论。'
                      : it.topic === 'like'
                        ? '有人对你的内容表达了认可。'
                        : it.topic === 'follow'
                          ? '有人开始关注你的公开动态。'
                          : it.topic === 'moderation'
                            ? '治理状态或处理结果发生了更新。'
                            : '查看这一类通知的最新动态。'
                  }}
                </div>
              </div>
              <div class="inbox-meta">
                <span>共 {{ it.noticeCount }} 条</span>
                <span :class="{ 'unread-text': it.unreadCount > 0 }">未读 {{ it.unreadCount }}</span>
              </div>
            </div>

            <div class="inbox-tail" aria-hidden="true">
              <span v-if="it.unreadCount > 0">{{ it.unreadCount }}</span>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9 18 15 12 9 6"></polyline>
              </svg>
            </div>
          </RouterLink>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { topicSummary } from '../api/services/noticeService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])
const loading = ref(false)
const error = ref('')
const items = ref([])

function getTopicTitle(topic) {
  const map = {
    like: '点赞',
    comment: '评论',
    follow: '关注',
    moderation: '治理'
  }
  return map[topic] || topic
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await topicSummary()
    items.value = data
    emit('trace', traceId || '')
  } catch (e) {
    error.value = e?.message || '加载通知失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.notices-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.notices-hero {
  display: grid;
  gap: var(--space-4);
}

.notices-hero-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.notices-hero-card {
  padding: 18px 20px;
  border-radius: var(--radius-lg);
  border: 1px solid color-mix(in srgb, var(--border) 84%, var(--accent) 16%);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 92%, white 8%), var(--surface));
  display: grid;
  gap: 6px;
}

.notices-hero-card strong {
  font-size: clamp(1.75rem, 3vw, 2.3rem);
  line-height: 1;
}

.notices-hero-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.55;
}

.notices-hero-label,
.notices-eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.notices-shell {
  padding: 0;
  overflow: hidden;
}

.notices-shell-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.notices-shell-head h2 {
  margin: 6px 0 4px;
  font-size: 1.15rem;
}

.notices-shell-head p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.55;
}

.notices-head-meta {
  white-space: nowrap;
}

.notices-state {
  padding: 48px 24px;
}

.inbox-list {
  display: flex;
  flex-direction: column;
}

.inbox-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px 24px;
  background: transparent;
  border-bottom: 1px solid var(--border);
  text-decoration: none;
  color: var(--text-1);
  transition: background 0.18s ease-out, border-color 0.18s ease-out;
}

.inbox-item:last-child {
  border-bottom: none;
}

.inbox-item:hover {
  background: color-mix(in srgb, var(--surface) 92%, var(--accent-weak) 8%);
}

.inbox-item.unread {
  background: color-mix(in srgb, var(--surface) 88%, var(--accent-weak) 12%);
}

.inbox-item:focus-visible {
  box-shadow: inset 0 0 0 1px var(--border-strong), var(--focus-ring);
  outline: none;
}

.inbox-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.inbox-icon-fallback {
  font-weight: 900;
}

.inbox-icon.like { background: var(--danger-weak); color: var(--danger); }
.inbox-icon.comment { background: var(--accent-weak); color: var(--accent); }
.inbox-icon.follow { background: var(--success-weak); color: var(--success); }
.inbox-icon.moderation { background: var(--warning-weak); color: var(--warning); }

.inbox-content {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 10px;
}

.inbox-top {
  display: grid;
  gap: 6px;
}

.inbox-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.inbox-title {
  font-weight: 800;
  font-size: 15px;
}

.inbox-badge {
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 700;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 18%, white 82%);
}

.inbox-sub {
  font-size: 14px;
  color: var(--text-2);
  line-height: 1.55;
}

.inbox-meta {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--text-3);
}

.unread-text {
  color: var(--accent);
  font-weight: 700;
}

.inbox-tail {
  min-width: 34px;
  height: 34px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  background: color-mix(in srgb, var(--surface) 70%, var(--bg) 30%);
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 768px) {
  .notices-hero-grid {
    grid-template-columns: 1fr;
  }

  .notices-shell-head,
  .inbox-item {
    padding-left: 18px;
    padding-right: 18px;
  }

  .inbox-icon {
    width: 42px;
    height: 42px;
    border-radius: 10px;
  }

  .notices-shell-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

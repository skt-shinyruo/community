<!-- RightPanel：右侧上下文面板（热门话题、社区规范）。 -->
<template>
  <div class="right-panel">
    <div class="right-panel-header">
      <div class="right-panel-title">探索</div>
    </div>

    <div class="right-panel-body">
      <!-- Quick Filters -->
      <section class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">快速筛选</div>
        </div>
        <div class="quick-links">
          <RouterLink class="quick-link" :to="{ name: 'posts' }">最新</RouterLink>
          <RouterLink class="quick-link" :to="{ name: 'posts', query: { order: 'hot' } }">热门</RouterLink>
          <RouterLink v-if="auth.authed" class="quick-link" :to="{ name: 'posts', query: { type: 'unread' } }">未读</RouterLink>
          <RouterLink class="quick-link" :to="{ name: 'posts', query: { type: 'top' } }">置顶</RouterLink>
          <RouterLink class="quick-link" :to="{ name: 'posts', query: { type: 'wonderful' } }">精华</RouterLink>
        </div>
      </section>

      <!-- Trending Section -->
      <section class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">热门话题</div>
        </div>
        <div class="right-card-list">
          <button v-for="i in 4" :key="i" class="topic-item" type="button">
            <span class="topic-rank">{{ i }}</span>
            <span class="topic-title">#社区设计规范讨论</span>
            <span class="topic-meta">230 讨论</span>
          </button>
        </div>
      </section>

      <!-- Categories -->
      <section class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">分类</div>
        </div>
        <div class="right-card-list" v-if="categories.length > 0">
          <div v-for="c in categories" :key="c.id" class="category-row">
            <RouterLink
              class="category-item"
              :to="{ name: 'posts', query: { categoryId: String(c.id) } }"
              :title="c.description || c.name"
            >
              <span class="category-name">{{ c.name }}</span>
              <span class="category-meta">{{ Number(c.postCount || 0) }}</span>
            </RouterLink>

            <button
              v-if="auth.authed"
              class="btn-icon sm"
              type="button"
              :aria-label="isSubscribedCategory(c.id) ? '取消订阅分类' : '订阅分类'"
              :title="isSubscribedCategory(c.id) ? '已订阅：点击取消' : '订阅该分类'"
              @click.stop="toggleSubscribeCategory(c.id)"
            >
              <svg
                v-if="isSubscribedCategory(c.id)"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="currentColor"
                aria-hidden="true"
              >
                <path d="M12 2l3 7h7l-5.5 4.5L18.5 21 12 16.8 5.5 21l2-7.5L2 9h7z" />
              </svg>
              <svg
                v-else
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                aria-hidden="true"
              >
                <polygon points="12 2 15 9 22 9 16.5 13.5 18.5 21 12 16.8 5.5 21 7.5 13.5 2 9 9 9" />
              </svg>
            </button>
          </div>
        </div>
        <div v-else class="muted" style="font-size: 12px">暂无分类</div>
      </section>

      <!-- Tags / Keywords -->
      <section class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">热门标签</div>
        </div>
        <div class="tag-cloud">
          <RouterLink
            v-for="t in hotTags"
            :key="t.name"
            class="tag-pill"
            :to="{ name: 'posts', query: { tag: t.name } }"
            :title="`${t.useCount || 0} 使用`"
          >
            #{{ t.name }}
          </RouterLink>
        </div>
        <div class="muted" style="font-size: 12px; margin-top: 10px">点击标签将过滤帖子列表。</div>
      </section>

      <!-- Guidelines -->
      <section class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">社区规范</div>
        </div>
        <div class="guidelines">
          <div class="guideline-row">
            <span class="guideline-icon" aria-hidden="true">📌</span>
            <span class="guideline-text">友善交流，理性讨论，共建优质社区氛围。</span>
          </div>
          <div class="guideline-row">
            <span class="guideline-icon" aria-hidden="true">🚫</span>
            <span class="guideline-text">禁止发布广告、灌水及违规内容。</span>
          </div>
        </div>
      </section>

      <section v-if="auth.isAdminOrModerator" class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">管理</div>
        </div>
        <div class="admin-links">
          <RouterLink class="admin-link" :to="{ name: 'moderation' }">治理后台</RouterLink>
          <RouterLink class="admin-link" :to="{ name: 'analytics' }">统计面板</RouterLink>
        </div>
      </section>

      <!-- Footer/Copyright -->
      <div class="right-footer muted">
        © 2026 Community Inc.<br />
        <a href="#" style="text-decoration: underline">Privacy</a> · <a href="#" style="text-decoration: underline">Terms</a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useTaxonomyStore } from '../../stores/taxonomy'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { subscribeCategory, unsubscribeCategory } from '../../api/services/subscriptionService'
import { RouterLink } from 'vue-router'

const auth = useAuthStore()
const taxonomy = useTaxonomyStore()
const prefs = useSocialPrefsStore()

const categories = computed(() => (Array.isArray(taxonomy.categories) ? taxonomy.categories : []))
const hotTags = computed(() => (Array.isArray(taxonomy.hotTags) ? taxonomy.hotTags : []))

function isSubscribedCategory(categoryId) {
  return prefs.subscribedCategorySet.has(Number(categoryId || 0))
}

async function toggleSubscribeCategory(categoryId) {
  const cid = Number(categoryId || 0)
  if (!cid) return
  try {
    if (isSubscribedCategory(cid)) {
      await unsubscribeCategory(cid)
    } else {
      await subscribeCategory(cid)
    }
    await prefs.ensureSubscribedCategories(true)
  } catch (e) {
    if (typeof window !== 'undefined' && window.$toast) {
      window.$toast({ type: 'error', title: '订阅失败', text: e?.message || '操作失败' })
    }
  }
}

onMounted(() => {
  taxonomy.ensureCategories()
  taxonomy.ensureHotTags(8)
  if (auth.authed) prefs.ensureSubscribedCategories()
})

watch(
  () => auth.authed,
  (v) => {
    if (v) prefs.ensureSubscribedCategories(true)
    else prefs.clear()
  }
)
</script>
<style scoped>
.right-panel-title {
  font-weight: 800;
  font-size: 16px;
  letter-spacing: 0.2px;
}

.right-card {
  padding: 14px 14px;
}

.right-card:hover {
  transform: none;
  box-shadow: var(--shadow-sm);
  border-color: var(--border);
}

.right-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.right-card-title {
  font-weight: 700;
  font-size: 13px;
  color: var(--text-2);
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-link {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.quick-link:hover {
  background: var(--surface-2);
  border-color: var(--border-strong);
  color: var(--text-1);
  text-decoration: none;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-pill {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--accent) 6%, var(--surface) 94%);
  color: var(--text-1);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.tag-pill:hover {
  background: color-mix(in srgb, var(--accent) 10%, var(--surface) 90%);
  border-color: color-mix(in srgb, var(--accent) 18%, var(--border) 82%);
  color: var(--text-1);
  text-decoration: none;
}

.admin-links {
  display: grid;
  gap: 8px;
}

.admin-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: var(--surface);
  color: var(--text-1);
  font-weight: 700;
  text-decoration: none;
}

.admin-link:hover {
  background: var(--surface-2);
  border-color: var(--border);
  text-decoration: none;
}

.right-card-list {
  display: grid;
  gap: 6px;
}

.category-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.topic-item {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: all 0.18s ease-out;
  text-align: left;
}
.topic-item:hover {
  background: var(--surface-2);
  border-color: var(--border);
}

.topic-rank {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 12px;
  color: var(--text-3);
}

.topic-title {
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-meta {
  font-size: 12px;
  color: var(--text-3);
}

.topic-item:nth-child(1) .topic-rank {
  background: #fef08a; /* Goldish */
  color: #854d0e;
}

.topic-item:nth-child(2) .topic-rank {
  background: #e2e8f0; /* Silver */
  color: #475569;
}

.topic-item:nth-child(3) .topic-rank {
  background: #fed7aa; /* Bronze */
  color: #9a3412;
}

.category-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: all 0.18s ease-out;
  text-align: left;
  text-decoration: none;
  color: inherit;
}

.category-item:hover {
  background: var(--surface-2);
  border-color: var(--border);
  text-decoration: none;
}

.category-name {
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-meta {
  font-size: 12px;
  color: var(--text-3);
  font-weight: 700;
}

.guidelines {
  display: grid;
  gap: 10px;
  font-size: 13px;
  color: var(--text-1);
}

.guideline-row {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 10px;
  align-items: start;
}

.guideline-text {
  line-height: 1.5;
}

.right-footer {
  font-size: 12px;
  line-height: 1.6;
  padding: 2px 4px;
}
</style>

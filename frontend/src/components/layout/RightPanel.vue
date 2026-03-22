<!-- RightPanel：公共产品的上下文辅助栏，只展示真实可用的信息与入口。 -->
<template>
  <div class="right-panel">
    <div class="right-panel-header">
      <div>
        <div class="right-panel-eyebrow">{{ eyebrow }}</div>
        <div class="right-panel-title">{{ title }}</div>
      </div>
    </div>

    <div class="right-panel-body">
      <section class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">快速入口</div>
        </div>
        <div class="quick-links">
          <RouterLink class="quick-link" :to="{ name: 'posts' }">最新讨论</RouterLink>
          <RouterLink class="quick-link" :to="{ name: 'posts', query: { order: 'hot' } }">热门话题</RouterLink>
          <RouterLink class="quick-link" :to="{ name: 'leaderboard' }">作者排行</RouterLink>
          <RouterLink v-if="auth.authed" class="quick-link" :to="{ name: 'bookmarks' }">我的收藏</RouterLink>
          <RouterLink v-if="auth.authed" class="quick-link" :to="{ name: 'posts', query: { subscribed: '1' } }">关注分类</RouterLink>
        </div>
      </section>

      <section class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">讨论提示</div>
        </div>
        <div class="guidelines">
          <div class="guideline-row">
            <div class="guideline-kicker">01</div>
            <span class="guideline-text">先读清上下文，再进入讨论。标题、摘要和最新评论应该能帮助你快速建立判断。</span>
          </div>
          <div class="guideline-row">
            <div class="guideline-kicker">02</div>
            <span class="guideline-text">标签和分类只是线索，不是主角。真正重要的是讨论本身是否值得展开。</span>
          </div>
          <div class="guideline-row">
            <div class="guideline-kicker">03</div>
            <span class="guideline-text">如果你准备回复，尽量引用观点而不是只回情绪，这样线程会更清晰。</span>
          </div>
        </div>
      </section>

      <section class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">分类</div>
          <div class="right-card-hint">{{ categories.length }} 项</div>
        </div>
        <div v-if="categories.length > 0" class="right-card-list">
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
        <UiEmpty v-else class="right-empty">暂无分类</UiEmpty>
      </section>

      <section class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">热门标签</div>
          <div class="right-card-hint">来自真实使用数据</div>
        </div>
        <div v-if="hotTags.length > 0" class="tag-cloud">
          <RouterLink
            v-for="t in hotTags"
            :key="t.name"
            class="tag-pill"
            :to="{ name: 'posts', query: { tag: t.name } }"
            :title="`${t.useCount || 0} 次使用`"
          >
            #{{ t.name }}
          </RouterLink>
        </div>
        <UiEmpty v-else class="right-empty">暂无热门标签</UiEmpty>
      </section>

      <section v-if="auth.isAdminOrModerator" class="card right-card">
        <div class="right-card-header">
          <div class="right-card-title">治理入口</div>
        </div>
        <div class="admin-links">
          <RouterLink class="admin-link" :to="{ name: 'moderation' }">治理后台</RouterLink>
          <RouterLink class="admin-link" :to="{ name: 'analytics' }">统计面板</RouterLink>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useTaxonomyStore } from '../../stores/taxonomy'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { subscribeCategory, unsubscribeCategory } from '../../api/services/subscriptionService'
import UiEmpty from '../ui/UiEmpty.vue'

const route = useRoute()
const auth = useAuthStore()
const taxonomy = useTaxonomyStore()
const prefs = useSocialPrefsStore()

const categories = computed(() => (Array.isArray(taxonomy.categories) ? taxonomy.categories : []))
const hotTags = computed(() => (Array.isArray(taxonomy.hotTags) ? taxonomy.hotTags : []))

const eyebrow = computed(() => {
  if (String(route.name || '') === 'postDetail') return 'Thread Context'
  if (String(route.name || '') === 'search') return 'Search Context'
  if (String(route.name || '') === 'userProfile') return 'Member Context'
  return 'Side Context'
})

const title = computed(() => {
  if (String(route.name || '') === 'postDetail') return '线程侧栏'
  if (String(route.name || '') === 'search') return '搜索侧栏'
  if (String(route.name || '') === 'userProfile') return '成员侧栏'
  return '上下文侧栏'
})

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
.right-panel-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--editorial-accent);
  margin-bottom: 6px;
}

.right-panel-title {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 28px;
  letter-spacing: -0.03em;
  color: var(--editorial-ink);
}

.right-card {
  padding: 18px;
  border-radius: 24px;
  border-color: color-mix(in srgb, var(--editorial-rule) 84%, #fff 16%);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), color-mix(in srgb, var(--editorial-paper-2) 92%, #fff 8%));
  box-shadow: 0 18px 28px rgba(0, 0, 0, 0.08);
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
  margin-bottom: 12px;
}

.right-card-title {
  font-weight: 800;
  font-size: 13px;
  color: var(--editorial-ink);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.right-card-hint {
  font-size: 11px;
  color: var(--text-3);
}

.quick-links,
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-link,
.tag-pill {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--editorial-rule);
  text-decoration: none;
  font-size: 12px;
  font-weight: 800;
}

.quick-link {
  background: rgba(255, 255, 255, 0.92);
  color: var(--editorial-ink);
}

.quick-link:hover,
.tag-pill:hover {
  text-decoration: none;
  border-color: var(--border-strong);
}

.tag-pill {
  background: color-mix(in srgb, var(--editorial-accent) 9%, var(--surface) 91%);
  color: var(--editorial-ink);
}

.guidelines,
.right-card-list,
.admin-links {
  display: grid;
  gap: 10px;
}

.guideline-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.guideline-kicker {
  width: 34px;
  height: 24px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 800;
  color: var(--editorial-accent);
  background: var(--editorial-accent-soft);
}

.guideline-text {
  color: var(--text-2);
  font-size: 13px;
  line-height: 1.55;
}

.category-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.category-item,
.admin-link {
  display: grid;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 16px;
  text-decoration: none;
  color: inherit;
  border: 1px solid transparent;
}

.category-item {
  grid-template-columns: minmax(0, 1fr) auto;
  flex: 1;
}

.category-item:hover,
.admin-link:hover {
  background: color-mix(in srgb, var(--editorial-accent) 6%, var(--surface) 94%);
  border-color: var(--editorial-rule);
  text-decoration: none;
}

.category-name {
  font-weight: 700;
  color: var(--text-1);
}

.category-meta {
  font-size: 12px;
  color: var(--text-3);
}

.admin-link {
  background: var(--surface);
  font-weight: 700;
}

.right-empty :deep(.empty) {
  padding: 0;
}

html[data-theme='dark'] .right-card {
  border-color: #2f2f2f;
  background: linear-gradient(180deg, #161616, #101010);
  box-shadow: 0 18px 28px rgba(0, 0, 0, 0.22);
}

html[data-theme='dark'] .quick-link,
html[data-theme='dark'] .tag-pill,
html[data-theme='dark'] .admin-link {
  background: #141414;
  border-color: #2f2f2f;
}

html[data-theme='dark'] .category-item:hover,
html[data-theme='dark'] .admin-link:hover {
  background: #1a1a1a;
}
</style>

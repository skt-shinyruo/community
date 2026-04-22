<template>
  <div class="page bookmarks-page">
    <UiBreadcrumb />

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading" class="muted bookmarks-state">正在加载收藏内容…</div>

    <UiCard class="bookmarks-shell" v-else>
      <div class="bookmarks-shell-head">
        <UiPageHeader>
          <template #title>我的收藏</template>
          <template #subtitle>把值得回来的帖子整理成一份更像阅读清单的个人列表。</template>
          <template #actions>
            <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
          </template>
        </UiPageHeader>
      </div>

      <div class="bookmarks-list">
        <UiEmpty v-if="items.length === 0" class="bookmarks-empty">
          暂无收藏
          <template #description>你收藏过的帖子会出现在这里，适合作为稍后继续阅读的个人清单。</template>
        </UiEmpty>

        <article v-for="p in items" :key="p.id" class="bookmark-item" @click="openPost(p)">
          <div class="bookmark-head">
            <div class="bookmark-taxonomy" @click.stop>
              <UiBadge v-if="p.type === 1" variant="accent" class="bookmark-status-badge">置顶</UiBadge>
              <UiBadge v-if="p.status === 1" variant="success" class="bookmark-status-badge">精华</UiBadge>
              <RouterLink
                v-if="p.categoryId"
                class="taxonomy-link"
                :to="{ name: 'posts', query: { categoryId: String(p.categoryId) } }"
              >
                <span class="tag topic-category">{{ categoryLabel(p.categoryId) }}</span>
              </RouterLink>

              <RouterLink
                v-for="t in (Array.isArray(p.tags) ? p.tags : [])"
                :key="t"
                class="taxonomy-link"
                :to="{ name: 'posts', query: { tag: t } }"
              >
                <span class="tag">#{{ t }}</span>
              </RouterLink>
            </div>

            <div class="bookmark-activity" :title="formatTime(p.lastActivityTime || p.createTime)">
              最近活跃 {{ formatTimeAgo(p.lastActivityTime || p.createTime) }}
            </div>
          </div>

          <h2 class="bookmark-title">{{ p.title }}</h2>

          <p v-if="p.content" class="bookmark-snippet">
            {{ p.content.slice(0, 140) }}{{ (p.content?.length || 0) > 140 ? '…' : '' }}
          </p>

          <div class="bookmark-footer">
            <div class="bookmark-stats">
              <span>{{ Number(p.commentCount || 0) }} 回复</span>
              <span>收藏后可随时回到原帖继续讨论</span>
            </div>
            <div class="bookmark-open">打开帖子</div>
          </div>
        </article>

        <div class="bookmark-load-more">
          <UiButton v-if="hasNext" variant="secondary" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </UiButton>
          <div v-else-if="items.length > 0" class="muted bookmarks-end">已经到底了</div>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import UiBadge from '../components/ui/UiBadge.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listBookmarks } from '../api/services/bookmarkService'
import { useTaxonomyStore } from '../stores/taxonomy'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useAuthStore } from '../stores/auth'
import { formatTime, formatTimeAgo } from '../utils/time'
import { normalizeOpaqueId } from '../utils/opaqueId'

const router = useRouter()
const taxonomy = useTaxonomyStore()
const prefs = useSocialPrefsStore()
const auth = useAuthStore()

const items = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')

const page = ref(0)
const size = 10
const hasNext = ref(true)

function categoryLabel(id) {
  const cid = normalizeOpaqueId(id)
  if (!cid) return ''
  const c = taxonomy.categoriesById.get(cid)
  return c?.name || `分类#${cid}`
}

function openPost(p) {
  if (!p) return
  router.push({ name: 'postDetail', params: { postId: String(p.id) } })
}

async function load(append = false) {
  if (!auth.authed) return
  if (append) loadingMore.value = true
  else loading.value = true

  if (!append) error.value = ''
  try {
    await taxonomy.ensureCategories()
    await prefs.ensureBlocked()

    const resp = await listBookmarks({ page: page.value, size })
    const raw = Array.isArray(resp?.data) ? resp.data : []
    const filtered = prefs.blockedSet.size > 0 ? raw.filter((p) => !prefs.blockedSet.has(normalizeOpaqueId(p?.userId))) : raw

    hasNext.value = raw.length >= size
    items.value = append ? [...items.value, ...filtered] : filtered
  } catch (e) {
    if (!append) error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function reload() {
  page.value = 0
  hasNext.value = true
  await load(false)
}

async function loadMore() {
  if (!hasNext.value) return
  page.value += 1
  await load(true)
}

onMounted(reload)
watch(
  () => auth.authed,
  (v) => {
    if (v) reload()
    else {
      items.value = []
      error.value = ''
    }
  }
)
</script>

<style scoped>
.bookmarks-page {
  max-width: 1000px;
  margin: 0 auto;
  gap: var(--space-5);
}

.bookmarks-eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.bookmarks-state {
  padding: 20px 0;
}

.bookmark-status-badge {
  height: 18px;
  font-size: 11px;
}

.bookmarks-shell {
  padding: 0;
  overflow: hidden;
}

.bookmarks-shell-head {
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.bookmarks-shell-head :deep(.page-header) {
  gap: 0;
}

.bookmarks-shell-head :deep(.page-header-subtitle) {
  margin: 4px 0 0;
}

.bookmarks-list {
  display: grid;
}

.bookmarks-empty {
  padding: 48px 24px;
}

.bookmark-item {
  cursor: pointer;
  padding: 22px 24px;
  border-bottom: 1px solid var(--border);
  display: grid;
  gap: 14px;
  transition: background 0.18s ease;
}

.bookmark-item:last-of-type {
  border-bottom: none;
}

.bookmark-item:hover {
  background: color-mix(in srgb, var(--surface) 92%, var(--accent-weak) 8%);
}

.bookmark-head,
.bookmark-footer {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.bookmark-taxonomy,
.bookmark-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.bookmark-title {
  font-weight: 800;
  color: var(--text-1);
  line-height: 1.3;
  font-size: 1.05rem;
  word-break: break-word;
  margin: 0;
}

.bookmark-snippet {
  margin: 0;
  color: var(--text-2);
  line-height: 1.65;
}

.bookmark-activity,
.bookmark-stats {
  font-size: 12px;
  color: var(--text-3);
}

.bookmark-open {
  font-size: 12px;
  font-weight: 700;
  color: var(--accent);
  white-space: nowrap;
}

.bookmark-load-more {
  display: flex;
  justify-content: center;
  padding: 18px 24px 24px;
}

.bookmarks-end {
  padding: 8px 0;
}

@media (max-width: 768px) {
  .bookmarks-shell-head,
  .bookmark-item,
  .bookmark-load-more {
    padding-left: 18px;
    padding-right: 18px;
  }

  .bookmark-head,
  .bookmark-footer {
    flex-direction: column;
  }
}
</style>

<template>
  <div class="page" style="max-width: 1000px; margin: 0 auto">
    <UiBreadcrumb />

    <UiCard>
      <UiPageHeader>
        <template #title>我的收藏</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
        </template>
      </UiPageHeader>

      <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
      <div v-else-if="loading" class="muted" style="padding: 16px">加载中…</div>

      <div v-else class="stack" style="gap: 12px">
        <UiEmpty v-if="items.length === 0">暂无收藏</UiEmpty>

        <div v-for="p in items" :key="p.id" class="card flat bookmark-item" @click="openPost(p)">
          <div class="row" style="justify-content: space-between; gap: 12px; align-items: flex-start">
            <div style="min-width: 0">
              <div class="row" style="gap: 8px; flex-wrap: wrap; align-items: center">
                <UiBadge v-if="p.type === 1" variant="accent" style="height: 18px; font-size: 11px">置顶</UiBadge>
                <UiBadge v-if="p.status === 1" variant="success" style="height: 18px; font-size: 11px">精华</UiBadge>
                <div class="bookmark-title">{{ p.title }}</div>
              </div>

              <div class="row muted" style="gap: 8px; margin-top: 8px; flex-wrap: wrap; font-size: 12px" @click.stop>
                <RouterLink
                  v-if="Number(p.categoryId || 0) > 0"
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
            </div>

            <div class="muted" style="text-align: right; font-size: 12px; white-space: nowrap">
              <div>{{ Number(p.commentCount || 0) }} 回复</div>
              <div style="margin-top: 6px" :title="formatTime(p.lastActivityTime || p.createTime)">
                {{ formatTimeAgo(p.lastActivityTime || p.createTime) }}
              </div>
            </div>
          </div>
        </div>

        <div class="row" style="justify-content: center; margin-top: 8px">
          <UiButton v-if="hasNext" variant="secondary" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </UiButton>
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
  const cid = Number(id || 0)
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
    const filtered = prefs.blockedSet.size > 0 ? raw.filter((p) => !prefs.blockedSet.has(Number(p?.userId || 0))) : raw

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
.bookmark-item {
  cursor: pointer;
}

.bookmark-item:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.bookmark-title {
  font-weight: 800;
  color: var(--text-1);
  line-height: 1.3;
  font-size: 14px;
  word-break: break-word;
}
</style>


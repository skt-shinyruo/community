<!-- 搜索页面：对齐 legacy 的“全局搜索”用户体验（关键词 + 分页 + 高亮）。 -->
<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>搜索</template>
        <template #subtitle>全局搜索 · 支持关键词高亮</template>
        <template #actions>
          <UiButton variant="secondary" v-if="auth.isAdmin" @click="openReindexConfirm" :disabled="loading">
            {{ loading ? '处理中…' : '重建索引' }}
          </UiButton>
          <UiButton @click="onSearch" :disabled="loading">{{ loading ? '搜索中…' : '搜索' }}</UiButton>
        </template>
      </UiPageHeader>

      <div class="row" style="align-items: flex-end; flex-wrap: wrap; margin-top: 12px">
        <div style="flex: 1; min-width: 240px">
          <div class="muted" style="font-size: 12px">关键词</div>
          <UiInput v-model.trim="keyword" placeholder="输入关键词后回车或点击搜索" @keydown.enter="onSearch" />
        </div>
        <div style="width: 180px">
          <div class="muted" style="font-size: 12px">每页条数</div>
          <UiInput v-model.number="size" type="number" min="1" max="50" />
        </div>
      </div>

      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
    </UiCard>

    <UiCard>
      <div style="margin-bottom: 12px">
        <UiPagination :page="page" :has-next="hasNext" @prev="prevPage" @next="nextPage" />
      </div>

      <UiEmpty v-if="items.length === 0">暂无结果</UiEmpty>
      <div v-else class="stack" style="gap: 8px">
        <div class="card flat" v-for="it in items" :key="it.postId" style="padding: 12px">
          <RouterLink :to="`/posts/${it.postId}`" style="font-weight: 800" v-html="titleHtml(it)" />
          <div class="muted" style="margin-top: 6px" v-html="contentHtml(it)" />
          <div class="muted" style="margin-top: 6px; font-size: 12px">
            postId={{ it.postId }} · score={{ Number(it.score || 0).toFixed(2) }}
          </div>
        </div>
      </div>
    </UiCard>

    <UiModalConfirm
      v-if="reindexConfirmOpen"
      title="确认重建索引"
      message="该操作可能耗时较长，会触发全文索引重建。是否继续？"
      @cancel="reindexConfirmOpen = false"
      @confirm="onConfirmReindex"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { searchPosts, reindex } from '../api/services/searchService'
import { emOnlyHtml } from '../utils/highlight'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'

const emit = defineEmits(['trace'])

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const keyword = ref('')
const page = ref(0)
const size = ref(10)

const loading = ref(false)
const error = ref('')
const items = ref([])
const hasNext = computed(() => items.value.length === Number(size.value || 10))
const reindexConfirmOpen = ref(false)

function titleHtml(it) {
  return emOnlyHtml(it?.highlightedTitle || it?.title || '')
}

function contentHtml(it) {
  const c = it?.highlightedContent || ''
  return c ? emOnlyHtml(c) : ''
}

async function run() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await searchPosts({ keyword: keyword.value, page: page.value, size: size.value })
    items.value = data
    emit('trace', traceId || '')
  } catch (e) {
    error.value = e?.message || '搜索失败'
  } finally {
    loading.value = false
  }
}

async function onSearch() {
  page.value = 0
  const q = keyword.value || ''
  router.replace({ name: 'search', query: q ? { ...route.query, q } : {} })
  await run()
}

async function nextPage() {
  if (!hasNext.value) return
  page.value += 1
  await run()
}

async function prevPage() {
  page.value = Math.max(0, page.value - 1)
  await run()
}

async function onReindex() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await reindex()
    emit('trace', traceId || '')
    error.value = `重建索引完成：count=${data?.count ?? 0}`
  } catch (e) {
    error.value = e?.message || '重建索引失败'
  } finally {
    loading.value = false
  }
}

function openReindexConfirm() {
  reindexConfirmOpen.value = true
}

async function onConfirmReindex() {
  reindexConfirmOpen.value = false
  await onReindex()
}

function syncFromRoute() {
  const q = route.query?.q
  if (typeof q === 'string' && q !== keyword.value) {
    keyword.value = q
    page.value = 0
    run()
  }
  if (!q && keyword.value) {
    // 保持用户输入，但不强制清空；避免浏览器返回导致体验突兀。
  }
}

onMounted(syncFromRoute)
watch(() => route.query?.q, syncFromRoute)
</script>

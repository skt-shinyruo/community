<template>
  <div class="page" style="max-width: 800px; margin: 0 auto">
    <UiPageHeader>
      <template #title>Search</template>
      <template #subtitle>Find posts and people.</template>
      <template #actions>
        <UiButton variant="secondary" v-if="auth.isAdmin" @click="openReindexConfirm" :disabled="loading">
          {{ loading ? 'Processing…' : 'Reindex' }}
        </UiButton>
      </template>
    </UiPageHeader>

    <!-- Search Box -->
    <UiCard style="margin-top: 12px; padding: 16px">
       <div class="row" style="gap: 12px">
          <div style="position: relative; flex: 1">
             <span style="position: absolute; left: 10px; top: 12px; color: var(--muted)">
                 <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
             </span>
             <input 
                v-model.trim="keyword" 
                class="input" 
                style="padding-left: 36px" 
                placeholder="Search keywords..." 
                @keydown.enter="onSearch"
              />
          </div>
          <UiButton @click="onSearch" :disabled="loading" style="min-width: 80px">{{ loading ? 'Running...' : 'Search' }}</UiButton>
       </div>
    </UiCard>

    <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>

    <!-- Results Feed -->
    <div style="margin-top: 24px" class="stack" style="gap: 16px">
       <UiEmpty v-if="!loading && items.length === 0">No results found.</UiEmpty>
       
       <div v-else class="stack" style="gap: 16px">
          <div class="card post-card-b" v-for="it in items" :key="it.postId" @click="$router.push(`/posts/${it.postId}`)">
              <!-- Content Column (Full Width, no vote/left col for search result usually, or keep consistent?) -->
              <!-- Let's keep consistent layout but simplified -->
              <div class="content-column">
                 <div class="post-title-b" v-html="titleHtml(it)"></div>
                 <div class="post-preview-b muted" v-html="contentHtml(it)"></div>
                 <div class="row muted" style="gap: 16px; font-size: 12px; margin-top: 8px">
                    <span>Relevance: {{ Number(it.score || 0).toFixed(2) }}</span>
                    <span>ID: {{ it.postId }}</span>
                 </div>
              </div>
          </div>
       </div>
    </div>
    
    <!-- Pagination (Simple) -->
    <div style="margin-top: 24px; display: flex; justify-content: center; gap: 12px" v-if="items.length > 0 || page > 0">
       <UiButton variant="secondary" @click="prevPage" :disabled="page <= 0 || loading">Previous</UiButton>
       <UiButton variant="secondary" @click="nextPage" :disabled="!hasNext || loading">Next</UiButton>
    </div>

    <UiModalConfirm
      v-if="reindexConfirmOpen"
      title="Rebuild Index"
      message="This may take a while. Continue?"
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
const hasNext = computed(() => items.value.length === Number(size.value))
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
    error.value = e?.message || 'Search failed'
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
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function prevPage() {
  page.value = Math.max(0, page.value - 1)
  await run()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function onReindex() {
  try {
    const { data } = await reindex()
    alert(`Reindex complete: ${data?.count} items.`)
  } catch (e) {
    alert(e.message)
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
}

onMounted(syncFromRoute)
watch(() => route.query?.q, syncFromRoute)
</script>

<style scoped>
.post-card-b {
  display: flex;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all 0.2s;
}
.post-card-b:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.content-column {
  padding: 16px;
  flex: 1;
}
.post-title-b {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-1);
}
.post-preview-b {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-2);
}

:deep(em) {
  font-style: normal;
  background: rgba(255, 159, 10, 0.2);
  color: #d97706;
  padding: 0 2px;
  border-radius: 4px;
  font-weight: 600;
}
</style>

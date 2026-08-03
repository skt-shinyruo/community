<template>
  <div class="page relations-page">
    <UiBreadcrumb />

    <div v-if="error && items.length > 0" class="error relations-banner">{{ error }}</div>

    <UiCard class="relations-shell">
      <div class="relations-shell-head">
        <UiPageHeader>
          <template #title>关注</template>
          <template #subtitle>查看这位成员正在持续关注的公开身份与关系变化。</template>
          <template #actions>
            <UiButton variant="secondary" @click="refresh" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
          </template>
        </UiPageHeader>
      </div>

      <div class="relations-toolbar">
        <UiPagination :page="page" :has-next="hasNext" :disabled="loading" @prev="prevPage" @next="nextPage" />
      </div>

      <UiState v-if="error && items.length === 0" variant="error" class="relations-state">{{ error }}</UiState>
      <div v-else-if="loading && items.length === 0" class="muted relations-state">正在加载关注关系…</div>
      <UiState v-else-if="items.length === 0" class="relations-state">
        暂无数据
        <template #description>当前没有可显示的关注关系，稍后刷新再看即可。</template>
      </UiState>

      <div v-else class="relations-list">
        <article class="relation-card" v-for="it in items" :key="it.targetId">
          <div class="relation-main">
            <UiAvatar :src="it.user?.headerUrl || ''" :name="it.user?.username || ''" :size="44" />
            <div class="relation-copy">
              <div class="relation-name-row">
                <RouterLink :to="`/users/${it.targetId}`" class="relation-name">
                  {{ it.user?.username || '社区成员' }}
                </RouterLink>
                <span class="relation-pill">已关注</span>
              </div>
              <div class="relation-summary">可以继续查看对方主页、公开动态与社交状态。</div>
              <div class="relation-meta">建立关系于 {{ formatTime(it.followTime) }}</div>
            </div>
          </div>

          <div class="relation-actions" v-if="authed && meId !== it.targetId">
            <UiButton v-if="!it.hasFollowed" :disabled="isMutating(it.targetId)" @click="doFollow(it)">关注</UiButton>
            <UiButton variant="secondary" v-else :disabled="isMutating(it.targetId)" @click="doUnfollow(it)">取关</UiButton>
          </div>
        </article>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { listFollowees, followUser, unfollowUser } from '../api/services/socialService'
import { formatTime } from '../utils/time'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { hydrateFollowRelations } from './followRelationHydration'
import UiCard from '../components/ui/UiCard.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiState from '../components/ui/UiState.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'

const emit = defineEmits(['trace'])
const props = defineProps({ userId: String })

const auth = useAuthStore()
const authed = computed(() => auth.authed)
const meId = computed(() => normalizeOpaqueId(auth.userId))
const userId = computed(() => normalizeOpaqueId(props.userId))

const page = ref(0)
const size = ref(10)

const loading = ref(false)
const error = ref('')
const items = ref([])
const hasNext = ref(true)
const mutatingTargetIds = ref(new Set())
let requestGeneration = 0
let mutationGeneration = 0
const activeMutations = new Map()

const viewScope = computed(() => [
  userId.value,
  auth.tokenGeneration,
  meId.value,
  authed.value ? 'authenticated' : 'anonymous'
].join(':'))

async function load(targetPage = page.value) {
  const profileUserId = userId.value
  if (!profileUserId) return
  const scope = viewScope.value
  const generation = ++requestGeneration
  const viewer = { authed: authed.value, viewerUserId: meId.value }
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await listFollowees(profileUserId, { page: targetPage, size: size.value })
    if (generation !== requestGeneration || scope !== viewScope.value) return

    const nextItems = Array.isArray(data) ? data : []
    const hydrated = await hydrateFollowRelations(nextItems, viewer)
    if (generation !== requestGeneration || scope !== viewScope.value) return

    hasNext.value = nextItems.length >= Number(size.value || 10)
    emit('trace', traceId || '')
    if (targetPage > page.value && nextItems.length === 0) return
    page.value = targetPage
    items.value = hydrated
  } catch (e) {
    if (generation !== requestGeneration || scope !== viewScope.value) return
    error.value = e?.message || '加载失败'
  } finally {
    if (generation === requestGeneration) loading.value = false
  }
}

function isMutating(targetId) {
  return mutatingTargetIds.value.has(normalizeOpaqueId(targetId))
}

function beginMutation(targetId) {
  const normalizedTargetId = normalizeOpaqueId(targetId)
  if (!normalizedTargetId || mutatingTargetIds.value.has(normalizedTargetId)) return null
  const token = ++mutationGeneration
  activeMutations.set(normalizedTargetId, token)
  mutatingTargetIds.value = new Set([...mutatingTargetIds.value, normalizedTargetId])
  return { normalizedTargetId, token, scope: viewScope.value }
}

function currentMutationItem(mutation) {
  if (mutation.scope !== viewScope.value || activeMutations.get(mutation.normalizedTargetId) !== mutation.token) {
    return null
  }
  return items.value.find((item) => normalizeOpaqueId(item?.targetId) === mutation.normalizedTargetId) || null
}

function finishMutation(mutation) {
  if (activeMutations.get(mutation.normalizedTargetId) !== mutation.token) return
  activeMutations.delete(mutation.normalizedTargetId)
  const next = new Set(mutatingTargetIds.value)
  next.delete(mutation.normalizedTargetId)
  mutatingTargetIds.value = next
}

async function doFollow(it) {
  if (!authed.value) return
  const mutation = beginMutation(it?.targetId)
  if (!mutation) return
  try {
    const r = await followUser(3, mutation.normalizedTargetId)
    const currentItem = currentMutationItem(mutation)
    if (!currentItem) return
    emit('trace', r?.traceId || '')
    currentItem.hasFollowed = true
  } catch (e) {
    if (!currentMutationItem(mutation)) return
    error.value = e?.message || '关注失败'
  } finally {
    finishMutation(mutation)
  }
}

async function doUnfollow(it) {
  if (!authed.value) return
  const mutation = beginMutation(it?.targetId)
  if (!mutation) return
  try {
    const r = await unfollowUser(3, mutation.normalizedTargetId)
    const currentItem = currentMutationItem(mutation)
    if (!currentItem) return
    emit('trace', r?.traceId || '')
    currentItem.hasFollowed = false
  } catch (e) {
    if (!currentMutationItem(mutation)) return
    error.value = e?.message || '取关失败'
  } finally {
    finishMutation(mutation)
  }
}

async function nextPage() {
  if (loading.value || !hasNext.value) return
  await load(page.value + 1)
}

async function prevPage() {
  if (loading.value) return
  await load(Math.max(0, page.value - 1))
}

async function refresh() {
  await load(page.value)
}

onMounted(() => load(0))
watch(viewScope, () => {
  requestGeneration += 1
  activeMutations.clear()
  mutatingTargetIds.value = new Set()
  page.value = 0
  items.value = []
  hasNext.value = true
  loading.value = false
  error.value = ''
  if (userId.value) load(0)
})
onBeforeUnmount(() => {
  requestGeneration += 1
  activeMutations.clear()
})
</script>

<style scoped>
.relations-page {
  max-width: 980px;
  margin: 0 auto;
  gap: var(--space-5);
}

.relations-eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.relations-banner {
  margin-top: -6px;
}

.relations-shell {
  padding: 0;
  overflow: hidden;
}

.relations-shell-head {
  padding: 22px 24px 12px;
}

.relations-shell-head :deep(.page-header) {
  gap: 0;
}

.relations-shell-head :deep(.page-header-subtitle) {
  margin: 4px 0 0;
}

.relations-toolbar {
  padding: 0 24px 18px;
  border-bottom: 1px solid var(--border);
}

.relations-state {
  padding: 48px 24px;
}

.relations-list {
  display: grid;
}

.relation-card {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.relation-card:last-child {
  border-bottom: none;
}

.relation-main {
  display: flex;
  gap: 14px;
  align-items: center;
  min-width: 0;
  flex: 1;
}

.relation-copy {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.relation-name-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.relation-name {
  font-weight: 800;
  color: var(--text-1);
  text-decoration: none;
}

.relation-name:hover {
  color: var(--accent);
}

.relation-pill {
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 700;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 18%, white 82%);
}

.relation-summary,
.relation-meta {
  color: var(--text-2);
}

.relation-summary {
  line-height: 1.55;
}

.relation-meta {
  font-size: 12px;
  color: var(--text-3);
}

.relation-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 768px) {
  .relations-shell-head,
  .relations-toolbar,
  .relation-card {
    padding-left: 18px;
    padding-right: 18px;
  }

  .relation-card {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

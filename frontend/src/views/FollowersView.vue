<template>
  <div class="page relations-page">
    <UiBreadcrumb />

    <div v-if="error && items.length > 0" class="error relations-banner">{{ error }}</div>

    <UiCard class="relations-shell">
      <div class="relations-shell-head">
        <UiPageHeader>
          <template #title>粉丝</template>
          <template #subtitle>查看哪些成员正在关注这位用户的公开发言与社区存在感。</template>
          <template #actions>
            <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
          </template>
        </UiPageHeader>
      </div>

      <div class="relations-toolbar">
        <UiPagination :page="page" :has-next="hasNext" @prev="prevPage" @next="nextPage" />
      </div>

      <UiState v-if="error && items.length === 0" variant="error" class="relations-state">{{ error }}</UiState>
      <div v-else-if="loading && items.length === 0" class="muted relations-state">正在加载粉丝关系…</div>
      <UiState v-else-if="items.length === 0" class="relations-state">
        暂无数据
        <template #description>当前没有可显示的粉丝关系，稍后刷新再看即可。</template>
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
                <span class="relation-pill">新关注者</span>
              </div>
              <div class="relation-summary">对方正在关注这位成员的公开动态，也可以继续查看其个人主页。</div>
              <div class="relation-meta">建立关系于 {{ formatTime(it.followTime) }}</div>
            </div>
          </div>

          <div class="relation-actions" v-if="authed && meId !== it.targetId">
            <UiButton v-if="!it.hasFollowed" @click="doFollow(it)">关注</UiButton>
            <UiButton variant="secondary" v-else @click="doUnfollow(it)">取关</UiButton>
          </div>
        </article>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { listFollowers, getFollowStatus, followUser, unfollowUser } from '../api/services/socialService'
import { getUserProfile } from '../api/services/userService'
import { formatTime } from '../utils/time'
import { normalizeOpaqueId, sameOpaqueId } from '../utils/opaqueId'
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
const hasNext = computed(() => items.value.length === Number(size.value || 10))

async function hydrate(list) {
  const out = []
  for (const it of list) {
    const targetId = normalizeOpaqueId(it?.targetId)
    let user = null
    try {
      user = await getUserProfile(targetId)
    } catch {}

    let hasFollowed = false
    if (authed.value && targetId && !sameOpaqueId(targetId, meId.value)) {
      try {
        const r = await getFollowStatus(3, targetId)
        hasFollowed = !!r?.data
      } catch {}
    }

    out.push({ ...it, user, hasFollowed })
  }
  return out
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await listFollowers(userId.value, { page: page.value, size: size.value })
    emit('trace', traceId || '')
    items.value = await hydrate(data)
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function doFollow(it) {
  if (!authed.value) return
  try {
    const r = await followUser(3, it.targetId)
    emit('trace', r?.traceId || '')
    it.hasFollowed = true
  } catch (e) {
    error.value = e?.message || '关注失败'
  }
}

async function doUnfollow(it) {
  if (!authed.value) return
  try {
    const r = await unfollowUser(3, it.targetId)
    emit('trace', r?.traceId || '')
    it.hasFollowed = false
  } catch (e) {
    error.value = e?.message || '取关失败'
  }
}

async function nextPage() {
  if (!hasNext.value) return
  page.value += 1
  await load()
}

async function prevPage() {
  page.value = Math.max(0, page.value - 1)
  await load()
}

onMounted(load)
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

<template>
  <div class="page leaderboard-page">
    <UiBreadcrumb />

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading" class="muted leaderboard-state">正在加载排行榜…</div>

    <UiCard v-else class="leaderboard-shell">
      <div class="leaderboard-shell-head">
        <UiPageHeader>
          <template #title>排行榜</template>
          <template #subtitle>查看当前社区里最活跃、最有影响力的公开身份。</template>
          <template #actions>
            <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
          </template>
        </UiPageHeader>
      </div>

      <UiEmpty v-if="items.length === 0" class="leaderboard-empty">暂无数据</UiEmpty>

      <div v-else class="leaderboard-list">
        <article
          v-for="u in items"
          :key="u.userId"
          class="leaderboard-row"
          :class="{
            'is-first': Number(u.rank) === 1,
            'is-second': Number(u.rank) === 2,
            'is-third': Number(u.rank) === 3
          }"
          @click="openUser(u.userId)"
        >
          <div class="rank">{{ u.rank }}</div>
          <UiAvatar :src="u.headerUrl || ''" :name="u.username || ''" :size="44" />
          <div class="leaderboard-main">
            <div class="leaderboard-name-row">
              <div class="username">{{ u.username || '社区成员' }}</div>
              <span class="tag">LV {{ Number(u.level || 1) }}</span>
              <span v-if="Number(u.rank) <= 3" class="leaderboard-rank-pill">Top {{ u.rank }}</span>
            </div>
            <div class="leaderboard-subtitle">公开积分越高，在社区中的存在感越强。</div>
          </div>
          <div class="score">
            <div class="score-value">{{ Number(u.score || 0) }}</div>
            <div class="score-label">积分</div>
          </div>
        </article>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { getLeaderboard } from '../api/services/leaderboardService'

const router = useRouter()

const items = ref([])
const loading = ref(false)
const error = ref('')

function openUser(userId) {
  const uid = Number(userId || 0)
  if (!uid) return
  router.push({ name: 'userProfile', params: { userId: String(uid) } })
}

async function reload() {
  error.value = ''
  loading.value = true
  try {
    const resp = await getLeaderboard({ limit: 50 })
    items.value = Array.isArray(resp?.data) ? resp.data : []
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.leaderboard-page {
  max-width: 900px;
  margin: 0 auto;
  gap: var(--space-5);
}

.leaderboard-eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.leaderboard-state {
  padding: 20px 0;
}

.leaderboard-shell {
  padding: 0;
  overflow: hidden;
}

.leaderboard-shell-head {
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.leaderboard-shell-head :deep(.page-header) {
  gap: 0;
}

.leaderboard-shell-head :deep(.page-header-subtitle) {
  margin: 4px 0 0;
}

.leaderboard-empty {
  padding: 48px 24px;
}

.leaderboard-list {
  display: grid;
}

.leaderboard-row {
  cursor: pointer;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
  display: flex;
  gap: 14px;
  align-items: center;
  transition: background 0.18s ease;
}

.leaderboard-row:last-child {
  border-bottom: none;
}

.leaderboard-row:hover {
  background: color-mix(in srgb, var(--surface) 92%, var(--accent-weak) 8%);
}

.leaderboard-row.is-first {
  background: linear-gradient(90deg, color-mix(in srgb, var(--warning-weak) 40%, var(--surface) 60%), transparent);
}

.leaderboard-row.is-second {
  background: linear-gradient(90deg, color-mix(in srgb, var(--surface) 84%, white 16%), transparent);
}

.leaderboard-row.is-third {
  background: linear-gradient(90deg, color-mix(in srgb, var(--accent-weak) 28%, var(--surface) 72%), transparent);
}

.rank {
  width: 38px;
  text-align: center;
  font-weight: 900;
  color: var(--text-2);
  font-size: 1.05rem;
  flex-shrink: 0;
}

.leaderboard-main {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 6px;
}

.leaderboard-name-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.username {
  font-weight: 800;
  color: var(--text-1);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.leaderboard-rank-pill {
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 700;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 18%, white 82%);
}

.leaderboard-subtitle {
  font-size: 13px;
  color: var(--text-3);
}

.score {
  text-align: right;
  min-width: 82px;
}

.score-value {
  font-weight: 800;
  font-size: 1.05rem;
}

.score-label {
  font-size: 12px;
  color: var(--text-3);
}

@media (max-width: 768px) {
  .leaderboard-shell-head,
  .leaderboard-row {
    padding-left: 18px;
    padding-right: 18px;
  }

  .leaderboard-row {
    align-items: flex-start;
  }
}
</style>

<template>
  <div class="page" style="max-width: 900px; margin: 0 auto">
    <UiBreadcrumb />

    <UiCard>
      <UiPageHeader>
        <template #title>排行榜</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
        </template>
      </UiPageHeader>

      <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
      <div v-else-if="loading" class="muted" style="padding: 16px">加载中…</div>

      <div v-else class="stack" style="gap: 12px">
        <UiEmpty v-if="items.length === 0">暂无数据</UiEmpty>

        <div v-for="u in items" :key="u.userId" class="card flat leaderboard-row" @click="openUser(u.userId)">
          <div class="row" style="gap: 12px; align-items: center">
            <div class="rank">{{ u.rank }}</div>
            <UiAvatar :src="u.headerUrl || ''" :name="u.username || ''" :size="36" />
            <div style="min-width: 0; flex: 1">
              <div class="row" style="gap: 8px; align-items: center; flex-wrap: wrap">
                <div class="username">{{ u.username || `user#${u.userId}` }}</div>
                <span class="tag">LV {{ Number(u.level || 1) }}</span>
              </div>
              <div class="muted" style="font-size: 12px">ID {{ u.userId }}</div>
            </div>
            <div class="score">
              <div style="font-weight: 800">{{ Number(u.score || 0) }}</div>
              <div class="muted" style="font-size: 12px">积分</div>
            </div>
          </div>
        </div>
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
.leaderboard-row {
  cursor: pointer;
}
.leaderboard-row:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}
.rank {
  width: 34px;
  text-align: center;
  font-weight: 900;
  color: var(--text-2);
}
.username {
  font-weight: 800;
  color: var(--text-1);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.score {
  text-align: right;
  min-width: 70px;
}
</style>


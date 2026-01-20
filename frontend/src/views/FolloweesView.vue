<!-- 关注列表页：对齐 legacy /followees/{userId} 页面能力（分页 + 用户摘要）。 -->
<template>
  <div class="page">
    <UiBreadcrumb />
    <UiCard>
      <UiPageHeader>
        <template #title>关注列表</template>
        <template #subtitle>userId={{ userId }}</template>
        <template #actions>
          <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>
    </UiCard>

    <UiCard>
      <div style="margin-bottom: 12px">
        <UiPagination :page="page" :has-next="hasNext" @prev="prevPage" @next="nextPage" />
      </div>

      <UiEmpty v-if="error && items.length === 0" type="error">{{ error }}</UiEmpty>
      <div v-else-if="loading && items.length === 0" class="muted" style="padding: 12px; text-align: center">加载中…</div>
      <UiEmpty v-else-if="items.length === 0">暂无数据</UiEmpty>
      <div v-else class="stack" style="gap: 8px">
        <div class="card flat" v-for="it in items" :key="it.targetId" style="padding: 12px">
          <div class="row" style="justify-content: space-between; align-items: flex-start; flex-wrap: wrap">
            <div class="row" style="align-items: center; gap: 10px; flex-wrap: wrap">
              <UiAvatar :src="it.user?.headerUrl || ''" :name="it.user?.username || ''" :size="36" />
              <div class="stack" style="gap: 4px">
                <RouterLink :to="`/users/${it.targetId}`" style="font-weight: 800">
                  {{ it.user?.username || `user#${it.targetId}` }}
                </RouterLink>
                <div class="muted" style="font-size: 12px">followTime={{ formatTime(it.followTime) }}</div>
              </div>
            </div>
            <div class="row" v-if="authed && meId !== it.targetId" style="flex-wrap: wrap">
              <UiButton v-if="!it.hasFollowed" @click="doFollow(it)">关注</UiButton>
              <UiButton variant="secondary" v-else @click="doUnfollow(it)">取关</UiButton>
            </div>
          </div>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { listFollowees, getFollowStatus, followUser, unfollowUser } from '../api/services/socialService'
import { getUserProfile } from '../api/services/userService'
import { formatTime } from '../utils/time'
import UiCard from '../components/ui/UiCard.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'

const emit = defineEmits(['trace'])
const props = defineProps({ userId: String })

const auth = useAuthStore()
const authed = computed(() => auth.authed)
const meId = computed(() => Number(auth.userId || 0))
const userId = computed(() => String(props.userId || '0'))

const page = ref(0)
const size = ref(10)

const loading = ref(false)
const error = ref('')
const items = ref([])
const hasNext = computed(() => items.value.length === Number(size.value || 10))

async function hydrate(list) {
  const out = []
  for (const it of list) {
    const targetId = Number(it?.targetId || 0)
    let user = null
    try {
      user = await getUserProfile(targetId)
    } catch {}

    let hasFollowed = false
    if (authed.value && targetId && targetId !== meId.value) {
      try {
        const r = await getFollowStatus(3, targetId)
        hasFollowed = !!r?.data
      } catch {}
    } else if (authed.value && meId.value && meId.value === Number(userId.value)) {
      // 查看“我自己的关注列表”，默认我已关注这些人
      hasFollowed = true
    }

    out.push({ ...it, user, hasFollowed })
  }
  return out
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await listFollowees(userId.value, { page: page.value, size: size.value })
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
    const r = await followUser(3, it.targetId, it.targetId)
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

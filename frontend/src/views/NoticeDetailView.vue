<!-- 通知详情页：按 topic 展示通知列表，并支持标记已读。 -->
<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>通知详情</template>
        <template #subtitle>topic={{ topic }}</template>
        <template #actions>
          <UiButton variant="secondary" @click="markAllRead" :disabled="loading || items.length === 0">标记本页已读</UiButton>
          <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
          <RouterLink class="btn ghost" to="/notices">返回通知汇总</RouterLink>
        </template>
      </UiPageHeader>

      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
    </UiCard>

    <UiCard>
      <div style="margin-bottom: 12px">
        <UiPagination :page="page" :has-next="hasNext" @prev="prevPage" @next="nextPage" />
      </div>

      <UiEmpty v-if="items.length === 0">暂无通知</UiEmpty>
      <div v-else class="stack" style="gap: 8px">
        <div class="card flat" v-for="n in items" :key="n.id" style="padding: 12px">
          <div class="muted" style="font-size: 12px">id={{ n.id }} · {{ formatTime(n.createTime) }} · status={{ n.status }}</div>
          <div class="muted" style="margin-top: 6px">{{ formatNotice(n) }}</div>
          <div v-if="noticePostId(n)" style="margin-top: 10px">
            <RouterLink class="btn secondary" :to="`/posts/${noticePostId(n)}`">查看相关帖子</RouterLink>
          </div>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { listNotices, markRead } from '../api/services/noticeService'
import { safeJsonParse } from '../utils/safeJson'
import { formatTime } from '../utils/time'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])
const props = defineProps({ topic: String })

const topic = computed(() => String(props.topic || ''))
const page = ref(0)
const size = ref(10)

const loading = ref(false)
const error = ref('')
const items = ref([])

const hasNext = computed(() => items.value.length === Number(size.value || 10))

function formatNotice(msg) {
  const raw = safeJsonParse(msg?.content, null)
  const type = raw?.type || ''
  const payload = raw?.payload || {}
  if (type === 'COMMENT_CREATED') {
    return `有人评论了你：postId=${payload?.postId ?? '-'}`
  }
  if (type === 'LIKE_CREATED') {
    return `有人点赞了你：entityType=${payload?.entityType ?? '-'} entityId=${payload?.entityId ?? '-'}`
  }
  if (type === 'FOLLOW_CREATED') {
    return `有人关注了你：userId=${payload?.actorUserId ?? '-'}`
  }
  if (type === 'MODERATION_ACTION_APPLIED') {
    const action = payload?.action ?? '-'
    const reason = payload?.reason ?? ''
    const duration = payload?.durationSeconds
    const extra = duration ? ` duration=${duration}s` : ''
    const targetType = payload?.targetType ?? '-'
    const targetId = payload?.targetId ?? '-'
    return `治理结果：action=${action}${extra} targetType=${targetType} targetId=${targetId}${reason ? ` reason=${reason}` : ''}`
  }
  return `通知：${type || 'unknown'}`
}

function noticePostId(msg) {
  const raw = safeJsonParse(msg?.content, null)
  const type = raw?.type || ''
  const payload = raw?.payload || {}
  const pid = payload?.postId
  if (pid) return Number(pid)
  if (type === 'MODERATION_ACTION_APPLIED' && Number(payload?.targetType || 0) === 1) {
    return Number(payload?.targetId || 0)
  }
  return 0
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await listNotices(topic.value, { page: page.value, size: size.value })
    items.value = data
    emit('trace', traceId || '')
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function markAllRead() {
  if (items.value.length === 0) return
  error.value = ''
  loading.value = true
  try {
    const ids = items.value.map((x) => x?.id).filter((x) => typeof x === 'number' && x > 0)
    const { traceId } = await markRead(ids)
    emit('trace', traceId || '')
    await load()
  } catch (e) {
    error.value = e?.message || '标记已读失败'
  } finally {
    loading.value = false
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

<!-- 通知汇总页：展示 comment/like/follow 三类通知的汇总信息。 -->
<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>通知</template>
        <template #subtitle>汇总 · comment/like/follow</template>
        <template #actions>
          <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>
      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
    </UiCard>

    <UiCard>
      <UiEmpty v-if="items.length === 0">暂无通知</UiEmpty>
      <div v-else class="stack" style="gap: 8px">
        <div class="card flat" v-for="it in items" :key="it.topic" style="padding: 12px">
          <div class="row" style="justify-content: space-between; flex-wrap: wrap; align-items: flex-start">
            <div class="stack" style="gap: 6px">
              <div style="font-weight: 900">topic={{ it.topic }}</div>
              <div class="muted" style="font-size: 12px">总数={{ it.noticeCount }} · 未读={{ it.unreadCount }}</div>
            </div>
            <RouterLink class="btn secondary" :to="`/notices/${it.topic}`">查看详情</RouterLink>
          </div>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { topicSummary } from '../api/services/noticeService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])

const loading = ref(false)
const error = ref('')
const items = ref([])

async function load() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await topicSummary()
    items.value = data
    emit('trace', traceId || '')
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

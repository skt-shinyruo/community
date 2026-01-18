<!-- 私信会话列表页：展示聚合字段与对端用户信息。 -->
<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>私信</template>
        <template #subtitle>会话列表 · 支持未读数</template>
        <template #actions>
          <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>
      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
    </UiCard>

    <UiCard>
      <UiEmpty v-if="items.length === 0">暂无会话</UiEmpty>
      <div v-else class="stack" style="gap: 8px">
        <div class="card flat" v-for="c in items" :key="c.conversationId" style="padding: 12px">
          <div class="row" style="justify-content: space-between; flex-wrap: wrap; align-items: flex-start">
            <div class="stack" style="gap: 6px; min-width: 0">
              <div style="font-weight: 900">
                {{ c?.targetUser?.username || `user#${c?.targetUser?.id ?? '-'}` }}
              </div>
              <div class="muted" style="font-size: 12px">
                conversationId={{ c.conversationId }} · 消息数={{ c.letterCount }} · 未读={{ c.unreadCount }}
              </div>
              <div class="muted" v-if="c.lastMessage?.content" style="font-size: 12px">最后一条：{{ c.lastMessage.content }}</div>
            </div>
            <RouterLink class="btn secondary" :to="`/messages/${encodeURIComponent(c.conversationId)}`">进入</RouterLink>
          </div>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listConversationItems } from '../api/services/messageService'
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
    const { data, traceId } = await listConversationItems({ page: 0, size: 20 })
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

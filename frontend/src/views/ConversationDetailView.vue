<!-- 私信详情页：展示消息列表并支持发送与标记已读。 -->
<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>私信详情</template>
        <template #subtitle>conversationId={{ conversationId }}</template>
        <template #actions>
          <UiButton variant="secondary" @click="load" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
          <RouterLink class="btn secondary" to="/messages">返回会话列表</RouterLink>
        </template>
      </UiPageHeader>
      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
    </UiCard>

    <UiCard>
      <UiEmpty v-if="items.length === 0">暂无消息</UiEmpty>
      <div v-else class="stack" style="gap: 8px">
        <div
          class="card flat"
          v-for="m in items"
          :key="m.id"
          style="padding: 10px"
          :style="{ background: m.fromId === meId ? 'var(--accent-weak)' : 'var(--surface)' }"
        >
          <div class="muted" style="font-size: 12px">
            id={{ m.id }} · from={{ m.fromId }} → to={{ m.toId }} · {{ formatTime(m.createTime) }} · status={{ m.status }}
          </div>
          <div class="comment-body" style="margin-top: 6px">{{ m.content }}</div>
        </div>
      </div>
    </UiCard>

    <UiCard>
      <UiPageHeader>
        <template #title>发送消息</template>
        <template #subtitle>POST /api/messages</template>
        <template #actions>
          <UiButton @click="send" :disabled="sending">{{ sending ? '发送中…' : '发送' }}</UiButton>
        </template>
      </UiPageHeader>
      <div class="stack" style="margin-top: 12px">
        <UiTextarea v-model.trim="content" :rows="4" placeholder="输入消息内容…" />
        <div v-if="sendError" class="error">{{ sendError }}</div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { listLetters, markRead, sendMessage } from '../api/services/messageService'
import { formatTime } from '../utils/time'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'

const emit = defineEmits(['trace'])
const props = defineProps({ conversationId: String })

const auth = useAuthStore()
const meId = computed(() => Number(auth.userId || 0))
const conversationId = computed(() => String(props.conversationId || ''))

const loading = ref(false)
const error = ref('')
const items = ref([])

const content = ref('')
const sending = ref(false)
const sendError = ref('')

function parseTargetId() {
  const cid = conversationId.value
  const parts = cid.split('_').map((x) => parseInt(x, 10)).filter((x) => Number.isFinite(x))
  if (parts.length !== 2) return 0
  const [a, b] = parts
  return a === meId.value ? b : a
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await listLetters(conversationId.value, { page: 0, size: 50 })
    items.value = data
    emit('trace', traceId || '')

    // 标记本页“发给我”的未读消息为已读
    const unreadIds = data
      .filter((m) => m?.toId === meId.value && m?.status === 0)
      .map((m) => m.id)
      .filter((id) => typeof id === 'number' && id > 0)
    if (unreadIds.length > 0) {
      const r = await markRead(unreadIds)
      emit('trace', r?.traceId || '')
    }
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function send() {
  sendError.value = ''
  if (!content.value) {
    sendError.value = '内容不能为空'
    return
  }
  const toId = parseTargetId()
  if (!toId) {
    sendError.value = 'conversationId 非法，无法解析对端用户'
    return
  }
  sending.value = true
  try {
    const { traceId } = await sendMessage({ toId, content: content.value })
    emit('trace', traceId || '')
    content.value = ''
    await load()
  } catch (e) {
    sendError.value = e?.message || '发送失败'
  } finally {
    sending.value = false
  }
}

onMounted(load)
</script>

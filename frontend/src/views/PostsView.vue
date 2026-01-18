<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>帖子</template>
        <template #subtitle>高信息密度浏览 · 支持最新/最热</template>
        <template #actions>
          <select class="input" v-model="order" style="width: 140px">
            <option value="latest">latest</option>
            <option value="hot">hot</option>
          </select>
          <UiButton variant="secondary" @click="reload" :disabled="loading">
            {{ loading ? '加载中…' : '刷新' }}
          </UiButton>
        </template>
      </UiPageHeader>

      <div v-if="error" class="error">{{ error }}</div>

      <div style="margin-top: 12px">
        <UiPagination :page="page" :has-next="hasNext" @prev="prevPage" @next="nextPage" />
      </div>

      <div style="margin-top: 12px">
        <UiEmpty v-if="items.length === 0">暂无数据</UiEmpty>
        <div v-else class="stack" style="gap: 8px">
          <div class="card flat" v-for="p in items" :key="p.id" style="padding: 12px">
            <div class="row" style="justify-content: space-between; align-items: flex-start; flex-wrap: wrap">
              <div class="stack" style="gap: 8px; min-width: 0">
                <div class="row" style="gap: 8px; flex-wrap: wrap; min-width: 0">
                  <UiBadge v-if="p.type === 1" variant="accent">置顶</UiBadge>
                  <UiBadge v-if="p.status === 1" variant="success">加精</UiBadge>
                  <UiBadge v-if="p.status === 2" variant="danger">已删除</UiBadge>
                  <RouterLink
                    :to="{ name: 'postDetail', params: { postId: String(p.id) } }"
                    style="font-weight: 800; min-width: 0; overflow: hidden; text-overflow: ellipsis"
                  >
                    {{ p.title }}
                  </RouterLink>
                </div>

                <div class="row muted" style="gap: 8px; flex-wrap: wrap">
                  <UiAvatar :src="p.author?.headerUrl || ''" :name="p.author?.username || ''" :size="22" />
                  <RouterLink :to="{ name: 'userProfile', params: { userId: String(p.userId) } }">
                    {{ p.author?.username || `user#${p.userId}` }}
                  </RouterLink>
                  <span>·</span>
                  <span>点赞 {{ p.likeCount }}</span>
                  <span>·</span>
                  <span>评论 {{ p.commentCount }}</span>
                </div>
              </div>

              <div class="muted" style="white-space: nowrap; font-size: 12px">{{ formatTime(p.createTime) }}</div>
            </div>
          </div>
        </div>
      </div>
    </UiCard>

    <UiCard v-if="authed">
      <UiPageHeader>
        <template #title>发帖</template>
        <template #subtitle>发布到社区 · 支持长文本</template>
        <template #actions>
          <UiButton @click="createPost" :disabled="creating">
            {{ creating ? '提交中…' : '发布' }}
          </UiButton>
        </template>
      </UiPageHeader>

      <div class="stack" style="margin-top: 12px">
        <UiInput v-model.trim="newTitle" placeholder="标题" autocomplete="off" />
        <UiTextarea v-model.trim="newContent" placeholder="正文内容" :rows="6" />

        <div v-if="createError" class="error">{{ createError }}</div>
        <div v-else-if="createdPostId" class="muted">
          已发布 postId={{ createdPostId }} ·
          <RouterLink :to="{ name: 'postDetail', params: { postId: String(createdPostId) } }">查看</RouterLink>
        </div>
      </div>
    </UiCard>

    <UiCard v-else>
      <UiEmpty>登录后可发帖。</UiEmpty>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import { listPosts, createPost as apiCreatePost } from '../api/services/postService'
import { getUserProfile } from '../api/services/userService'
import { getLikeCount } from '../api/services/socialService'
import { formatTime } from '../utils/time'

const emit = defineEmits(['trace'])

const auth = useAuthStore()
const authed = computed(() => !!auth.accessToken)

const order = ref('latest')
const page = ref(0)
const size = ref(10)
const items = ref([])
const hasNext = computed(() => items.value.length === Number(size.value || 10))
const loading = ref(false)
const error = ref('')

const newTitle = ref('')
const newContent = ref('')
const creating = ref(false)
const createError = ref('')
const createdPostId = ref(0)

async function hydrate(list) {
  return Promise.all(
    (Array.isArray(list) ? list : []).map(async (p) => {
      const userId = Number(p?.userId || 0)
      const postId = Number(p?.id || 0)
      const [author, like] = await Promise.all([
        userId ? getUserProfile(userId).catch(() => null) : Promise.resolve(null),
        postId ? getLikeCount(1, postId).catch(() => ({ data: 0 })) : Promise.resolve({ data: 0 })
      ])
      return { ...p, author, likeCount: Number(like?.data || 0) }
    })
  )
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    const resp = await listPosts({ order: order.value, page: page.value, size: size.value })
    emit('trace', resp?.traceId || '')
    items.value = await hydrate(resp?.data || [])
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function createPost() {
  createError.value = ''
  createdPostId.value = 0
  if (!newTitle.value || !newContent.value) {
    createError.value = 'title/content 不能为空'
    return
  }
  creating.value = true
  try {
    const resp = await apiCreatePost({ title: newTitle.value, content: newContent.value })
    createdPostId.value = resp?.data?.postId ?? 0
    emit('trace', resp?.traceId || '')
    newTitle.value = ''
    newContent.value = ''
    page.value = 0
    await load()
  } catch (e) {
    createError.value = e?.message || '发布失败'
  } finally {
    creating.value = false
  }
}

async function nextPage() {
  if (!hasNext.value || loading.value) return
  page.value += 1
  await load()
}

async function prevPage() {
  if (loading.value) return
  page.value = Math.max(0, page.value - 1)
  await load()
}

async function reload() {
  page.value = 0
  await load()
}

watch(order, () => {
  reload()
})

onMounted(load)
</script>

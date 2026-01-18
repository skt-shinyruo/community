<template>
  <div class="page">
    <!-- Publish Area -->
    <div v-if="authed" class="card" :class="{ 'focused': isPublishFocused }" style="transition: all 0.3s ease">
      <div v-if="!isPublishFocused" @click="isPublishFocused = true" class="row cursor-pointer" style="padding: 4px 0">
        <UiAvatar :src="me?.headerUrl" :name="me?.username || ''" :size="32" />
        <div class="input-fake muted" style="flex: 1">分享你的新鲜事...</div>
        <UiButton variant="ghost" disabled>发布</UiButton>
      </div>

      <div v-else class="stack" style="gap: 12px">
        <div class="row" style="justify-content: space-between">
          <div style="font-weight: 600">创建新帖子</div>
          <button class="btn ghost" style="width: 28px; height: 28px; padding: 0" @click="isPublishFocused = false">×</button>
        </div>
        <UiInput v-model.trim="newTitle" placeholder="标题" autocomplete="off" style="font-weight: 600" />
        <UiTextarea v-model.trim="newContent" placeholder="正文内容..." :rows="6" style="resize: none" />
        
        <div class="row" style="justify-content: space-between">
          <div class="error" style="font-size: 13px">{{ createError }}</div>
          <div class="row">
            <UiButton @click="createPost" :disabled="creating" style="min-width: 80px">
              {{ creating ? '发布中' : '发布' }}
            </UiButton>
          </div>
        </div>
      </div>
    </div>

    <!-- Feed Header -->
    <div class="row" style="justify-content: space-between; margin-top: 8px; margin-bottom: 8px">
      <div class="row" style="gap: 16px">
        <a href="#" :class="{ active: order === 'latest' }" class="sort-link" @click.prevent="order = 'latest'">最新</a>
        <a href="#" :class="{ active: order === 'hot' }" class="sort-link" @click.prevent="order = 'hot'">热门</a>
      </div>
      <UiButton variant="ghost" @click="reload" :disabled="loading" style="height: 32px">
        刷新
      </UiButton>
    </div>

    <!-- Skeletons Loading State -->
    <div v-if="loading && items.length === 0" class="stack" style="gap: 16px">
       <div v-for="i in 3" :key="i" class="skeleton-card">
          <div class="skeleton" style="width: 48px; height: 100%"></div>
          <div style="flex: 1">
             <div class="skeleton skeleton-text" style="width: 30%"></div>
             <div class="skeleton skeleton-text" style="width: 80%; height: 1.4em; margin-bottom: 12px"></div>
             <div class="skeleton skeleton-text" style="width: 100%; height: 3em"></div>
          </div>
       </div>
    </div>

    <!-- Post List -->
    <div v-if="error" class="error">{{ error }}</div>

    <div class="stack" style="gap: 16px">
      <UiEmpty v-if="!loading && items.length === 0 && !error">暂无内容</UiEmpty>
      
      <div 
        class="card post-card-b" 
        v-for="p in items" 
        :key="p.id"
        @click="$router.push({ name: 'postDetail', params: { postId: String(p.id) } })"
      >
        <!-- Left Vote Column -->
        <div class="vote-column" @click.stop>
          <button class="vote-btn up" :class="{ active: p.liked }" @click="togglePostLike(p)">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 19V5M5 12l7-7 7 7"/></svg>
          </button>
          <div class="vote-count" :class="{ active: p.liked }">{{ p.likeCount || 0 }}</div>
          <button class="vote-btn down">
             <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M19 12l-7 7-7-7"/></svg>
          </button>
        </div>

        <!-- Content Column -->
        <div class="content-column">
           <div class="row" style="margin-bottom: 6px; font-size: 12px; color: var(--text-2)">
              <div class="row" style="gap: 6px" @click.stop="$router.push({ name: 'userProfile', params: { userId: String(p.userId) } })">
                 <UiAvatar 
                  :src="p.author?.headerUrl || ''" 
                  :name="p.author?.username || ''" 
                  :size="20" 
                  style="cursor: pointer"
                />
                <span class="hover-underline" style="font-weight: 600; color: var(--text-1)">{{ p.author?.username || `user#${p.userId}` }}</span>
              </div>
              <span>·</span>
              <span>{{ formatTime(p.createTime) }}</span>
              <div class="row" style="gap: 6px; margin-left: 4px" v-if="p.type === 1 || p.status >= 1">
                <UiBadge v-if="p.type === 1" variant="accent" style="height: 18px; font-size: 11px">置顶</UiBadge>
                <UiBadge v-if="p.status === 1" variant="success" style="height: 18px; font-size: 11px">精</UiBadge>
              </div>
           </div>

           <div class="post-title-b">{{ p.title }}</div>
           <div class="post-preview-b muted">{{ p.content?.slice(0, 120) }}{{ (p.content?.length || 0) > 120 ? '...' : '' }}</div>
           
           <div class="row muted" style="gap: 16px; font-size: 12px; margin-top: 8px">
             <div class="icon-label-b">
               <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>
               {{ p.commentCount || 0 }} 评论
             </div>
             <div class="icon-label-b">
               <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="1"></circle><circle cx="19" cy="12" r="1"></circle><circle cx="5" cy="12" r="1"></circle></svg>
               分享
             </div>
           </div>
        </div>
      </div>
    </div>

    <!-- Load More -->
    <div style="margin-top: 24px; text-align: center" v-if="hasNext || loading">
      <UiButton v-if="loading" variant="ghost" disabled>加载中...</UiButton>
      <UiButton v-else variant="secondary" @click="loadMore" style="width: 100%">加载更多</UiButton>
    </div>
    <div v-if="!hasNext && items.length > 0" class="muted" style="text-align: center; margin-top: 24px; font-size: 13px">
       没有更多内容了
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import { listPosts, createPost as apiCreatePost } from '../api/services/postService'
import { getUserProfile } from '../api/services/userService'
import { getLikeCount, setLike } from '../api/services/socialService'
import { formatTime } from '../utils/time'

const emit = defineEmits(['trace'])
const showToast = inject('showToast', () => {})

const auth = useAuthStore()
const authed = computed(() => !!auth.accessToken)
const me = computed(() => auth.user || {}) 

const order = ref('latest')
const page = ref(0)
const size = ref(10)
const items = ref([])
// We assume hasNext if last fetch returned full size
const hasNext = ref(true) 
const loading = ref(false)
const error = ref('')

// Publish interaction
const isPublishFocused = ref(false)
const newTitle = ref('')
const newContent = ref('')
const creating = ref(false)
const createError = ref('')

// Simple cache to avoid N+1 user calls slightly (though HTTP browser cache also helps)
const userCache = new Map()

async function hydrate(list) {
  return Promise.all(
    (Array.isArray(list) ? list : []).map(async (p) => {
      const userId = Number(p?.userId || 0)
      const postId = Number(p?.id || 0)
      
      let author = null
      if (userId) {
         if (userCache.has(userId)) author = userCache.get(userId)
         else {
            try {
               author = await getUserProfile(userId)
               userCache.set(userId, author)
            } catch { author = null }
         }
      }
      
      // Still N+1 likes, but acceptable for demo unless batched API exists
      const like = postId ? await getLikeCount(1, postId).catch(() => ({ data: 0 })) : { data: 0 }
      
      return { ...p, author, likeCount: Number(like?.data || 0) }
    })
  )
}

async function load(append = false) {
  if (loading.value) return
  error.value = ''
  loading.value = true
  try {
    const resp = await listPosts({ order: order.value, page: page.value, size: size.value })
    emit('trace', resp?.traceId || '')
    const newItems = await hydrate(resp?.data || [])
    
    if (newItems.length < size.value) hasNext.value = false
    else hasNext.value = true
    
    if (append) {
       items.value = [...items.value, ...newItems]
    } else {
       items.value = newItems
    }
  } catch (e) {
    if (!append) error.value = e?.message || '加载失败'
    else showToast({ type: 'error', text: '加载更多失败' })
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  page.value += 1
  await load(true)
}

async function reload() {
  page.value = 0
  hasNext.value = true
  await load(false)
}

async function togglePostLike(p) {
  if (!authed.value || !p) return showToast({ type: 'warning', text: '请先登录' })
  try {
     const originalLiked = p.liked
     p.liked = !p.liked
     p.likeCount += p.liked ? 1 : -1
     
     await setLike({
      entityType: 1,
      entityId: Number(p.id),
      entityUserId: Number(p.userId || 0),
      postId: Number(p.id),
      liked: null 
    })
  } catch (e) {
    // Revert
    // p.liked = !p.liked
  }
}

async function createPost() {
  createError.value = ''
  if (!newTitle.value || !newContent.value) {
    createError.value = '请填写完整内容'
    return
  }
  creating.value = true
  try {
    const resp = await apiCreatePost({ title: newTitle.value, content: newContent.value })
    emit('trace', resp?.traceId || '')
    
    showToast({ type: 'success', title: '发布成功', text: '你的帖子已发布' })
    
    newTitle.value = ''
    newContent.value = ''
    isPublishFocused.value = false 
    await reload()
  } catch (e) {
    createError.value = e?.message || '发布失败'
  } finally {
    creating.value = false
  }
}

watch(order, () => {
  reload()
})

onMounted(reload)
</script>

<style scoped>
.input-fake {
  background: var(--bg);
  padding: 8px 12px;
  border-radius: 20px;
  font-size: 14px;
  cursor: text;
  transition: background 0.2s;
}
.input-fake:hover {
  background: color-mix(in srgb, var(--bg) 95%, var(--text-1) 5%);
}

.sort-link {
  font-weight: 600;
  color: var(--muted);
  padding: 4px 0;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}
.sort-link:hover {
  color: var(--text-1);
  text-decoration: none;
}
.sort-link.active {
  color: var(--text-1);
  border-bottom-color: var(--accent);
}

.post-card-b {
  display: flex;
  padding: 0;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.2s cubic-bezier(0.25, 1, 0.5, 1);
}

.post-card-b:hover {
  border-color: var(--border-strong);
}

.vote-column {
  width: 48px;
  background: color-mix(in srgb, var(--bg) 50%, transparent); /* Slightly distinct bg */
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 12px;
  border-right: 1px solid transparent;
}

.content-column {
  flex: 1;
  padding: 8px 12px 8px 8px; /* Compact padding */
  min-width: 0;
}

.vote-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  color: var(--muted);
  border-radius: 4px;
}
.vote-btn:hover {
  background: rgba(0,0,0,0.05);
  color: var(--accent);
}
.vote-btn.up.active {
  color: #ff4500; /* Reddit orange */
}
.vote-count {
  font-size: 13px;
  font-weight: 700;
  margin: 4px 0;
  color: var(--text-2);
}
.vote-count.active {
  color: #ff4500;
}

.post-title-b {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
  margin-bottom: 4px;
  color: var(--text-1);
}

.post-preview-b {
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-2);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.icon-label-b {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border-radius: 4px;
  transition: background 0.2s;
  color: var(--text-2);
  font-weight: 600;
}
.icon-label-b:hover {
  background: var(--surface-2);
}
</style>

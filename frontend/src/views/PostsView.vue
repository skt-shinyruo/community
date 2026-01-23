<template>
  <div class="page">
    <!-- Publish Area -->
    <div v-if="authed" class="card" :class="{ 'focused': isPublishFocused }" style="transition: all 0.3s ease">
      <div v-if="!isPublishFocused" @click="isPublishFocused = true" class="row cursor-pointer" style="padding: 4px 0">
        <UiAvatar :src="me?.headerUrl" :name="me?.username || ''" :size="32" />
        <div class="input-fake muted" style="flex: 1">
           <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"></path><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></svg>
           <span>分享你的新鲜事...</span>
        </div>
        <UiButton variant="ghost" disabled>发布</UiButton>
      </div>

      <div v-else class="stack" style="gap: 12px">
        <div class="row" style="justify-content: space-between">
          <div style="font-weight: 600">创建新帖子</div>
          <button
            class="btn ghost"
            type="button"
            aria-label="关闭发帖编辑器"
            style="width: 28px; height: 28px; padding: 0"
            @click="isPublishFocused = false"
          >
            ×
          </button>
        </div>
        <UiInput v-model.trim="newTitle" placeholder="标题" autocomplete="off" style="font-weight: 600" />
        <UiTextarea v-model.trim="newContent" placeholder="正文内容..." :rows="6" style="resize: none" />

        <div class="row" style="gap: 12px; flex-wrap: wrap">
          <div style="flex: 0 0 200px; min-width: 200px">
            <div class="muted" style="font-size: 12px; margin-bottom: 6px">分类（可选）</div>
            <select v-model="newCategoryId" class="input" :disabled="creating">
              <option value="">不选择</option>
              <option v-for="c in categories" :key="c.id" :value="String(c.id)">{{ c.name }}</option>
            </select>
          </div>

          <div style="flex: 1; min-width: 240px">
            <div class="muted" style="font-size: 12px; margin-bottom: 6px">标签（回车/逗号添加，最多 5 个）</div>
            <UiInput
              v-model.trim="newTagDraft"
              placeholder="例如：Java（输入后回车确认）"
              autocomplete="off"
              :disabled="creating"
              :list="composerTagSuggest.length > 0 ? composerTagDatalistId : null"
              @keydown.enter.prevent="commitNewTags"
              @keydown="onTagDraftKeydown"
              @blur="commitNewTags"
            />
            <datalist v-if="composerTagSuggest.length > 0" :id="composerTagDatalistId">
              <option v-for="t in composerTagSuggest" :key="t.name" :value="t.name"></option>
            </datalist>
            <div v-if="newTagError" class="error" style="margin-top: 6px; font-size: 12px">{{ newTagError }}</div>

            <div v-if="newTags.length > 0" class="row" style="gap: 6px; flex-wrap: wrap; margin-top: 8px">
              <button
                v-for="t in newTags"
                :key="t"
                type="button"
                class="tag-btn"
                :title="`移除标签 ${t}`"
                @click="removeNewTag(t)"
              >
                <span class="tag">#{{ t }}</span>
                <span class="tag-btn-x" aria-hidden="true">×</span>
              </button>
            </div>
          </div>
        </div>
        
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

    <!-- Feed Toolbar -->
    <div style="margin-top: 8px; margin-bottom: 8px">
      <FeedToolbar
        :order="order"
        :filter="filter"
        :subscribed="subscribed"
        :show-subscribed-toggle="authed"
        :category-id="categoryId"
        :tag="tag"
        :categories="categories"
        :tag-suggestions="tagSuggestions"
        :order-options="orderOptions"
        :filter-options="filterOptions"
        :show-clear="showClear"
        :disabled="loading"
        @update:order="setOrder"
        @update:filter="setFilter"
        @update:subscribed="setSubscribed"
        @update:categoryId="setCategoryId"
        @update:tag="setTag"
        @refresh="reload"
        @clear="clearQuery"
      />
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
    <UiEmpty v-if="error && items.length === 0" type="error">{{ error }}</UiEmpty>
    <div v-else-if="error" class="error">{{ error }}</div>

    <div class="stack" style="gap: 16px">
      <div v-if="blockedHiddenCount > 0" class="muted" style="font-size: 12px">
        已隐藏 {{ blockedHiddenCount }} 条来自已屏蔽用户的帖子
      </div>

      <UiEmpty v-if="!loading && items.length === 0 && !error">
        暂无内容
        <template #description>
          <span>试试切换到「热门」或点击刷新；</span>
          <span v-if="!authed">登录后可发布第一篇帖子。</span>
          <span v-else>你可以发布一篇帖子，开启讨论。</span>
        </template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
          <UiButton
            v-if="!authed"
            variant="ghost"
            @click="router.push({ name: 'login', query: { redirect: route.fullPath || '/posts' } })"
          >
            登录
          </UiButton>
          <UiButton v-else variant="ghost" @click="isPublishFocused = true">发帖</UiButton>
        </template>
      </UiEmpty>

      <div v-if="shouldShowNewHint" class="topic-new-hint">
        <div class="topic-new-hint-left">
          自上次访问后新增 <span class="topic-new-hint-num">{{ newSinceLastSeenCount }}</span> 条
        </div>
        <div class="topic-new-hint-actions">
          <UiButton variant="secondary" style="height: 28px" @click="scrollToLastSeenDivider">上次位置</UiButton>
          <UiButton variant="ghost" style="height: 28px" @click="newHintDismissed = true">收起</UiButton>
        </div>
      </div>
      
      <div v-if="items.length > 0" class="topic-list">
        <div class="topic-head" aria-hidden="true">
          <div class="topic-head-title">话题</div>
          <div class="topic-head-col">回复</div>
          <div class="topic-head-col">赞</div>
          <div class="topic-head-col">活动</div>
        </div>

        <template v-for="(p, idx) in items" :key="p.id">
          <div v-if="shouldShowLastSeenDivider && idx === newDividerIndex" ref="lastSeenDividerRef" class="topic-divider">
            上次看到这里
          </div>

          <div
            class="topic-row"
            :class="{ 'is-unread': isUnread(p) }"
            role="link"
            tabindex="0"
            @keydown.enter="openPost(p)"
            @click="openPost(p)"
          >
          <div class="topic-main">
            <div class="topic-title-row">
              <div class="topic-title-left">
                <span v-if="isUnread(p)" class="topic-unread-dot" title="未读" aria-label="未读"></span>
                <div class="topic-title" :class="{ 'is-unread': isUnread(p) }">{{ p.title }}</div>
              </div>
              <div class="topic-badges" v-if="p.type === 1 || p.status >= 1">
                <UiBadge v-if="p.type === 1" variant="accent" style="height: 18px; font-size: 11px">置顶</UiBadge>
                <UiBadge v-if="p.status === 1" variant="success" style="height: 18px; font-size: 11px">精</UiBadge>
              </div>
            </div>

            <div
              v-if="(Number(p.categoryId || 0) > 0) || (Array.isArray(p.tags) && p.tags.length > 0)"
              class="topic-taxonomy"
              @click.stop
            >
              <button
                v-if="Number(p.categoryId || 0) > 0"
                class="topic-taxonomy-btn"
                type="button"
                :aria-label="`筛选分类 ${categoryLabel(p.categoryId)}`"
                @click="replaceQuery({ categoryId: p.categoryId })"
              >
                <span class="tag topic-category">{{ categoryLabel(p.categoryId) }}</span>
              </button>

              <button
                v-for="t in (Array.isArray(p.tags) ? p.tags : [])"
                :key="t"
                class="topic-taxonomy-btn"
                type="button"
                :aria-label="`筛选标签 ${t}`"
                @click="replaceQuery({ tag: t })"
              >
                <span class="tag">#{{ t }}</span>
              </button>
            </div>

            <div class="topic-snippet" v-if="p.content">
              {{ p.content?.slice(0, 140) }}{{ (p.content?.length || 0) > 140 ? '...' : '' }}
            </div>

            <div class="topic-meta">
              <div
                class="topic-author"
                @click.stop="router.push({ name: 'userProfile', params: { userId: String(p.userId) } })"
              >
                <UiAvatar :src="p.author?.headerUrl || ''" :name="p.author?.username || ''" :size="18" />
                <span class="topic-author-name">
                  {{ p.author?.username || `user#${p.userId}` }}
                </span>
              </div>
              <span>·</span>
              <span :title="formatTime(p.createTime)">发布 {{ formatTimeAgo(p.createTime) }}</span>
            </div>
          </div>

          <div class="topic-col topic-col-replies" :title="`回复 ${Number(p.commentCount || 0)}`">
            {{ Number(p.commentCount || 0) }}
          </div>

          <div class="topic-col topic-col-likes" @click.stop>
            <button
              class="topic-stat-btn"
              :class="{ active: p.liked }"
              type="button"
              :aria-label="p.liked ? '取消点赞' : '点赞'"
              @click="togglePostLike(p)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M12 19V5M5 12l7-7 7 7" />
              </svg>
              <span>{{ p.likeCount || 0 }}</span>
            </button>
          </div>

          <div class="topic-col topic-col-activity">
            <div
              v-if="activityUserId(p)"
              class="topic-activity"
              :title="formatTime(activityTime(p))"
            >
              <button
                class="topic-activity-userbtn"
                type="button"
                :aria-label="`查看用户 ${activityUser(p)?.username || `user#${activityUserId(p)}`}`"
                @click.stop="router.push({ name: 'userProfile', params: { userId: String(activityUserId(p)) } })"
              >
                <UiAvatar
                  :src="activityUser(p)?.headerUrl || ''"
                  :name="activityUser(p)?.username || ''"
                  :size="18"
                />
                <span class="topic-activity-user">
                  {{ activityUser(p)?.username || `user#${activityUserId(p)}` }}
                </span>
              </button>
              <span class="topic-activity-time">
                {{ formatTimeAgo(activityTime(p)) }}
              </span>
            </div>
            <div v-else class="topic-activity muted" :title="formatTime(activityTime(p))">
              {{ formatTimeAgo(activityTime(p)) }}
            </div>
          </div>
        </div>
        </template>
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
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import FeedToolbar from '../components/posts/FeedToolbar.vue'
import { listPosts, createPost as apiCreatePost } from '../api/services/postService'
import { getUserProfile } from '../api/services/userService'
import { getLikeCount, getLikeStatus, setLike } from '../api/services/socialService'
import { suggestTags as apiSuggestTags } from '../api/services/taxonomyService'
import { formatTime, formatTimeAgo } from '../utils/time'
import { getPostReadAt, getPostsListBaselineAt, markPostRead, touchPostsListSeen } from '../utils/readTracker'
import { useTaxonomyStore } from '../stores/taxonomy'
import {
  POSTS_FILTER,
  POSTS_FILTER_OPTIONS,
  POSTS_ORDER,
  POSTS_ORDER_OPTIONS,
  normalizePostsCategoryId,
  normalizePostsFilter,
  normalizePostsOrder,
  normalizePostsSubscribed
} from '../router/navigation'

const emit = defineEmits(['trace'])
const showToast = inject('showToast', () => {})

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const authed = computed(() => !!auth.accessToken)
const me = computed(() => auth.me || {})

const order = computed(() => normalizePostsOrder(route.query?.order))
const filter = computed(() => normalizePostsFilter(route.query?.type))
const subscribed = computed(() => normalizePostsSubscribed(route.query?.subscribed))
const categoryId = computed(() => normalizePostsCategoryId(route.query?.categoryId))
const tag = computed(() => {
  const raw = String(route.query?.tag || '').trim()
  return raw.startsWith('#') ? raw.slice(1).trim() : raw
})

const orderOptions = POSTS_ORDER_OPTIONS
const filterOptions = POSTS_FILTER_OPTIONS

const taxonomy = useTaxonomyStore()
const categories = computed(() => (Array.isArray(taxonomy.categories) ? taxonomy.categories : []))

function categoryLabel(id) {
  const cid = Number(id || 0)
  if (!cid) return ''
  const c = taxonomy.categoriesById.get(cid)
  return c?.name || `分类#${cid}`
}

const showClear = computed(
  () =>
    order.value !== POSTS_ORDER.LATEST ||
    filter.value !== POSTS_FILTER.ALL ||
    subscribed.value === true ||
    categoryId.value > 0 ||
    !!tag.value
)

const socialPrefs = useSocialPrefsStore()
const blockedSet = computed(() => socialPrefs.blockedSet)
const blockedHiddenCount = ref(0)

function replaceQuery(partial) {
  const next = { ...(route.query || {}) }

  if (Object.prototype.hasOwnProperty.call(partial, 'order')) {
    const nextOrder = normalizePostsOrder(partial.order)
    if (nextOrder === POSTS_ORDER.LATEST) delete next.order
    else next.order = nextOrder
  }

  if (Object.prototype.hasOwnProperty.call(partial, 'filter')) {
    const nextFilter = normalizePostsFilter(partial.filter)
    if (!nextFilter) delete next.type
    else next.type = nextFilter
  }

  if (Object.prototype.hasOwnProperty.call(partial, 'categoryId')) {
    const nextCategoryId = normalizePostsCategoryId(partial.categoryId)
    if (!nextCategoryId) delete next.categoryId
    else next.categoryId = String(nextCategoryId)
  }

  if (Object.prototype.hasOwnProperty.call(partial, 'tag')) {
    let nextTag = String(partial.tag || '').trim()
    if (nextTag.startsWith('#')) nextTag = nextTag.slice(1).trim()
    if (!nextTag) delete next.tag
    else next.tag = nextTag
  }

  if (Object.prototype.hasOwnProperty.call(partial, 'subscribed')) {
    const nextSubscribed = normalizePostsSubscribed(partial.subscribed)
    if (!nextSubscribed) delete next.subscribed
    else next.subscribed = '1'
  }

  router.replace({ name: 'posts', query: next })
}

function setOrder(v) {
  replaceQuery({ order: v })
}

function setFilter(v) {
  replaceQuery({ filter: v })
}

function setCategoryId(v) {
  replaceQuery({ categoryId: v })
}

function setTag(v) {
  replaceQuery({ tag: v })
}

function setSubscribed(v) {
  replaceQuery({ subscribed: v })
}

function clearQuery() {
  replaceQuery({ order: POSTS_ORDER.LATEST, filter: POSTS_FILTER.ALL, subscribed: false, categoryId: 0, tag: '' })
}

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
const newCategoryId = ref('')
const newTagDraft = ref('')
const newTags = ref([])
const newTagError = ref('')

const composerTagDatalistId = 'composer-tag-suggest'
const composerTagSuggest = ref([])
	const creating = ref(false)
	const createError = ref('')
	
	const seenBaselineAt = ref(0)
	let touchedLatestOnce = false

let lastLoadToken = 0

function toMs(v) {
  if (!v) return 0
  const t = new Date(v).getTime()
  return Number.isFinite(t) ? t : 0
}

function activityTime(p) {
  return p?.lastActivityTime || p?.lastReplyTime || p?.createTime || null
}

function activityUserId(p) {
  const v = Number(p?.lastReplyUserId || 0)
  if (v > 0) return v
  return Number(p?.userId || 0)
}

function activityUser(p) {
  return p?.lastReplyAuthor || p?.author || null
}

const TAG_MAX = 5
const TAG_MAX_LEN = 20
const TAG_PATTERN = /^[\p{L}\p{N}_-]{1,20}$/u

function normalizeTagToken(raw) {
  let t = String(raw || '').trim()
  if (t.startsWith('#')) t = t.slice(1).trim()
  t = t.replaceAll(/\s+/g, '-').trim()
  return t
}

function addOneTag(token) {
  const t = normalizeTagToken(token)
  if (!t) return
  if (t.length > TAG_MAX_LEN) {
    throw new Error(`标签过长（单个标签最长 ${TAG_MAX_LEN}）`)
  }
  if (!TAG_PATTERN.test(t)) {
    throw new Error('标签格式非法（仅允许中英文、数字、_、-）')
  }

  const list = Array.isArray(newTags.value) ? [...newTags.value] : []
  const key = t.toLowerCase()
  const exists = list.some((x) => String(x || '').toLowerCase() === key)
  if (!exists) {
    if (list.length >= TAG_MAX) {
      throw new Error(`标签最多 ${TAG_MAX} 个`)
    }
    list.push(t)
    newTags.value = list
  }
}

function commitNewTags() {
  newTagError.value = ''
  const raw = String(newTagDraft.value || '').trim()
  if (!raw) return

  try {
    const parts = raw
      .split(/[\\s,，]+/g)
      .map((s) => String(s || '').trim())
      .filter(Boolean)

    for (const p of parts) {
      addOneTag(p)
    }

    newTagDraft.value = ''
  } catch (e) {
    newTagError.value = e?.message || '标签不合法'
  }
}

function onTagDraftKeydown(e) {
  const key = String(e?.key || '')
  if (key === ',' || key === '，') {
    e?.preventDefault?.()
    commitNewTags()
  }
}

function removeNewTag(t) {
  const key = String(t || '').toLowerCase()
  newTags.value = (Array.isArray(newTags.value) ? newTags.value : []).filter((x) => String(x || '').toLowerCase() !== key)
}

let suggestTimer = 0
let suggestToken = 0

watch(newTagDraft, (v) => {
  if (suggestTimer) window.clearTimeout(suggestTimer)
  const q = String(v || '').trim()
  if (!q) {
    composerTagSuggest.value = (Array.isArray(taxonomy.hotTags) ? taxonomy.hotTags : []).slice(0, 8)
    return
  }
  const token = ++suggestToken
  suggestTimer = window.setTimeout(async () => {
    try {
      const resp = await apiSuggestTags({ q, limit: 8 })
      if (token !== suggestToken) return
      composerTagSuggest.value = Array.isArray(resp?.data) ? resp.data : []
    } catch {
      // ignore：suggest 失败时不阻塞发帖
    }
  }, 180)
})

watch(isPublishFocused, (v) => {
  if (!v) return
  // 在分类页发帖时，默认带上当前分类
  if (!newCategoryId.value && Number(categoryId.value || 0) > 0) {
    newCategoryId.value = String(categoryId.value)
  }
})

const tagSuggestions = computed(() =>
  (Array.isArray(taxonomy.hotTags) ? taxonomy.hotTags : [])
    .map((t) => t?.name)
    .filter(Boolean)
    .slice(0, 12)
)

const lastSeenDividerRef = ref(null)
const newHintDismissed = ref(false)

const isLatestView = computed(() => order.value === POSTS_ORDER.LATEST && filter.value === POSTS_FILTER.ALL && page.value === 0)

const newSinceLastSeenCount = computed(() => {
  if (!isLatestView.value) return 0
  const baseline = Number(seenBaselineAt.value || 0)
  if (!baseline) return 0
  return (Array.isArray(items.value) ? items.value : []).reduce((acc, p) => {
    return toMs(activityTime(p)) > baseline ? acc + 1 : acc
  }, 0)
})

const newDividerIndex = computed(() => {
  if (!isLatestView.value) return -1
  const baseline = Number(seenBaselineAt.value || 0)
  if (!baseline) return -1
  const list = Array.isArray(items.value) ? items.value : []
  for (let i = 0; i < list.length; i += 1) {
    const t = toMs(activityTime(list[i]))
    if (t > 0 && t <= baseline) {
      return i
    }
  }
  return -1
})

const shouldShowLastSeenDivider = computed(
  () => isLatestView.value && newDividerIndex.value > 0 && newDividerIndex.value < (items.value?.length || 0)
)

const shouldShowNewHint = computed(() => isLatestView.value && newSinceLastSeenCount.value > 0 && !newHintDismissed.value)

function getLastSeenDividerEl() {
  const r = lastSeenDividerRef.value
  if (Array.isArray(r)) return r[0] || null
  return r
}

function scrollToLastSeenDivider() {
  const el = getLastSeenDividerEl()
  if (!el || typeof el.scrollIntoView !== 'function') return
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function isUnread(p) {
  if (!authed.value || !p) return false
  const last = toMs(activityTime(p))
  if (!last) return false
  const readAt = getPostReadAt(p?.id)
  const baseline = readAt > 0 ? readAt : seenBaselineAt.value
  return last > Number(baseline || 0)
}

function openPost(p) {
  if (!p) return
  if (authed.value) markPostRead(p?.id)
  router.push({ name: 'postDetail', params: { postId: String(p.id) } })
}

async function hydratePostMeta(p) {
  if (!p) return
  const userId = Number(p?.userId || 0)
  const postId = Number(p?.id || 0)
  const lastReplyUserId = Number(p?.lastReplyUserId || 0)

  const [author, like, liked, lastReplyAuthor] = await Promise.all([
    userId ? getUserProfile(userId).catch(() => null) : Promise.resolve(null),
    postId ? getLikeCount(1, postId).catch(() => ({ data: 0 })) : Promise.resolve({ data: 0 }),
    authed.value && postId ? getLikeStatus(1, postId).catch(() => ({ data: false })) : Promise.resolve({ data: false }),
    lastReplyUserId > 0 ? getUserProfile(lastReplyUserId).catch(() => null) : Promise.resolve(null)
  ])

  p.author = author
  p.likeCount = Number(like?.data || 0)
  p.liked = !!liked?.data
  p.lastReplyAuthor = lastReplyAuthor || null
}

async function runPool(tasks, limit, token) {
  const queue = Array.isArray(tasks) ? [...tasks] : []
  const workers = Array.from({ length: Math.max(1, Number(limit || 1)) }).map(async () => {
    while (queue.length > 0) {
      if (token !== lastLoadToken) return
      const fn = queue.shift()
      if (typeof fn !== 'function') continue
      await fn()
    }
  })
  await Promise.all(workers)
}

function scheduleHydrate(list, token) {
  if (!Array.isArray(list) || list.length === 0) return

  // 让首屏先渲染：异步补水作者/点赞数，避免阻塞列表展示。
  setTimeout(() => {
    if (token !== lastLoadToken) return
    const tasks = list.map((p) => () => hydratePostMeta(p))
    runPool(tasks, 6, token)
  }, 0)
}

function applyFilter(list) {
  const ft = filter.value
  if (!ft) return list
  if (ft === POSTS_FILTER.UNREAD) return list.filter((p) => isUnread(p))
  if (ft === POSTS_FILTER.TOP) return list.filter((p) => Number(p?.type || 0) === 1)
  if (ft === POSTS_FILTER.WONDERFUL) return list.filter((p) => Number(p?.status || 0) === 1)
  return list
}

async function load(append = false) {
  if (append && loading.value) return

  const token = ++lastLoadToken
  if (!append) error.value = ''
  loading.value = true
  try {
    if (authed.value) {
      try {
        await socialPrefs.ensureBlocked()
      } catch {
        // ignore：拉黑列表失败不阻塞列表查询
      }
    } else {
      socialPrefs.clear()
    }

    if (subscribed.value === true && !authed.value) {
      showToast({ type: 'warning', text: '登录后可查看订阅内容' })
      setSubscribed(false)
      return
    }

    const resp = await listPosts({
      order: order.value,
      page: page.value,
      size: size.value,
      subscribed: subscribed.value === true,
      categoryId: categoryId.value,
      tag: tag.value
    })
    emit('trace', resp?.traceId || '')

    if (token !== lastLoadToken) return

    const base = (Array.isArray(resp?.data) ? resp.data : []).map((p) => ({
      ...p,
      author: p?.author || null,
      liked: !!p?.liked,
      likeCount: Number(p?.likeCount || 0)
    }))

    const afterBlocked = blockedSet.value.size > 0 ? base.filter((p) => !blockedSet.value.has(Number(p?.userId || 0))) : base
    blockedHiddenCount.value = Math.max(0, base.length - afterBlocked.length)
    const newItems = applyFilter(afterBlocked)
    
    if ((resp?.data || []).length < size.value) hasNext.value = false
    else hasNext.value = true
    
    if (append) {
       items.value = [...items.value, ...newItems]
    } else {
       items.value = newItems
    }

    scheduleHydrate(newItems, token)
  } catch (e) {
    if (token !== lastLoadToken) return
    if (!append) error.value = e?.message || '加载失败'
    else showToast({ type: 'error', text: '加载更多失败' })
  } finally {
    if (token === lastLoadToken) {
      loading.value = false
    }
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
     const resp = await setLike({
      entityType: 1,
      entityId: Number(p.id),
      entityUserId: Number(p.userId || 0),
      postId: Number(p.id),
      liked: null
    })
     emit('trace', resp?.traceId || '')
     if (typeof resp?.data?.likeCount === 'number') p.likeCount = resp.data.likeCount
     if (typeof resp?.data?.liked === 'boolean') p.liked = resp.data.liked
  } catch (e) {
    showToast({ type: 'error', text: e?.message || '点赞失败' })
  }
}

async function createPost() {
  createError.value = ''
  commitNewTags()
  if (newTagError.value) {
    createError.value = newTagError.value
    return
  }
  if (!newTitle.value || !newContent.value) {
    createError.value = '请填写完整内容'
    return
  }
  creating.value = true
  try {
    const cid = Number(newCategoryId.value || 0)
    const resp = await apiCreatePost({
      title: newTitle.value,
      content: newContent.value,
      categoryId: cid > 0 ? cid : undefined,
      tags: newTags.value
    })
    emit('trace', resp?.traceId || '')
    
    showToast({ type: 'success', title: '发布成功', text: '你的帖子已发布' })
    
    newTitle.value = ''
    newContent.value = ''
    newCategoryId.value = ''
    newTagDraft.value = ''
    newTags.value = []
    newTagError.value = ''
    isPublishFocused.value = false 
    await reload()
  } catch (e) {
    createError.value = e?.message || '发布失败'
  } finally {
    creating.value = false
  }
}

watch([order, filter, subscribed, categoryId, tag], () => {
  newHintDismissed.value = false
  reload()
})

onMounted(() => {
  taxonomy.ensureCategories()
  taxonomy.ensureHotTags(12)
  seenBaselineAt.value = getPostsListBaselineAt()
  if (isLatestView.value && !touchedLatestOnce) {
    touchedLatestOnce = true
    seenBaselineAt.value = touchPostsListSeen()
  }
  reload()
})

watch(isLatestView, (v) => {
  if (v && !touchedLatestOnce) {
    touchedLatestOnce = true
    seenBaselineAt.value = touchPostsListSeen()
  }
})
</script>

<style scoped>
.input-fake {
  background: var(--surface); /* White background for better contrast */
  padding: 10px 16px;
  border: 1px solid var(--border);
  border-radius: 24px; /* Fully rounded */
  font-size: 14px;
  cursor: text;
  color: var(--text-2);
  transition: all 0.2s cubic-bezier(0.25, 1, 0.5, 1);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  gap: 8px;
}
.input-fake:hover {
  border-color: var(--border-strong);
  background: var(--surface);
  box-shadow: var(--shadow-md);
  color: var(--text-1);
}

.sort-link {
  font-weight: 600;
  color: var(--muted);
  padding: 6px 4px;
  font-size: 14px;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}
.sort-link:hover {
  color: var(--text-1);
  text-decoration: none;
}
.sort-link.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}
</style>

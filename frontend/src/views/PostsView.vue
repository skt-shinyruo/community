<template>
  <div class="page posts-page">
    <section class="posts-toolbar-stage">
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

      <div class="posts-context-strip">
        <span class="posts-context-item"><strong>{{ items.length || 0 }}</strong> 条当前讨论</span>
        <span class="posts-context-sep" aria-hidden="true">/</span>
        <span class="posts-context-item"><strong>{{ categories.length }}</strong> 个公开分类</span>
        <template v-if="newSinceLastSeenCount > 0">
          <span class="posts-context-sep" aria-hidden="true">/</span>
          <span class="posts-context-item posts-context-item--accent"><strong>{{ newSinceLastSeenCount }}</strong> 条新增未读</span>
        </template>
      </div>
    </section>

    <div class="posts-feed">
      <UiButton
        v-if="authed && !isPublishFocused"
        variant="secondary"
        class="posts-feed-compose-strip"
        @click="isPublishFocused = true"
      >
        <span class="posts-feed-compose-leading">
          <UiAvatar :src="me?.headerUrl" :name="me?.username || ''" :size="30" />
          <span class="posts-feed-compose-copy">
            <span class="posts-feed-compose-title">发起讨论</span>
            <span class="posts-feed-compose-sub">把今天最值得展开的问题直接丢进时间线。</span>
          </span>
        </span>
        <span class="posts-feed-compose-action">开始</span>
      </UiButton>

      <UiCard v-if="authed && isPublishFocused" class="posts-composer">
        <div class="posts-composer-editor">
        <div class="posts-composer-head">
          <div class="posts-composer-title">发起讨论</div>
          <UiIconButton
            class="posts-composer-close"
            aria-label="关闭发帖编辑器"
            @click="isPublishFocused = false"
          >
            ×
          </UiIconButton>
        </div>
        <UiInput v-model.trim="newTitle" name="post-title" placeholder="标题" autocomplete="off" class="posts-composer-input" />
        <UiTextarea v-model.trim="newContent" name="post-content" placeholder="正文内容..." :rows="6" class="posts-composer-textarea" />

        <div class="posts-composer-meta">
          <div class="posts-composer-field posts-composer-field--category">
            <div class="posts-composer-label">分类（可选）</div>
            <UiSelect
              v-model="newCategoryId"
              name="post-category"
              class="posts-composer-category-select"
              :disabled="creating"
              :options="composerCategoryOptions"
              placeholder="不选择"
            />
          </div>

          <div class="posts-composer-field posts-composer-field--tags">
            <div class="posts-composer-label">标签（回车/逗号添加，最多 5 个）</div>
            <UiAutosuggestInput
              v-model.trim="newTagDraft"
              name="post-tag-draft"
              placeholder="例如：Java（输入后回车确认）"
              autocomplete="off"
              :disabled="creating"
              :suggestions="composerTagSuggestNames"
              :commit-on-enter="true"
              :commit-on-blur="true"
              @commit="commitNewTags"
              @keydown="onTagDraftKeydown"
            />
            <div v-if="newTagError" class="error posts-composer-error">{{ newTagError }}</div>

            <div v-if="newTags.length > 0" class="posts-composer-tags">
              <UiButton
                v-for="t in newTags"
                :key="t"
                variant="ghost"
                class="tag-btn"
                :title="`移除标签 ${t}`"
                @click="removeNewTag(t)"
              >
                <span class="tag">#{{ t }}</span>
                <span class="tag-btn-x" aria-hidden="true">×</span>
              </UiButton>
            </div>
          </div>
        </div>
        
        <div class="posts-composer-actions">
          <div class="error posts-composer-submit-error">{{ createError }}</div>
          <div class="posts-composer-action-group">
            <UiButton @click="createPost" :disabled="creating" class="posts-composer-submit">
              {{ creating ? '发布中' : '发布' }}
            </UiButton>
          </div>
        </div>
        </div>
      </UiCard>

      <!-- Skeletons Loading State -->
      <div v-if="loading && items.length === 0" class="posts-skeletons">
         <div v-for="i in 3" :key="i" class="posts-skeleton-card">
            <div class="posts-skeleton-meta">
              <div class="skeleton skeleton-text posts-skeleton-pill"></div>
              <div class="skeleton skeleton-text posts-skeleton-pill posts-skeleton-pill--sm"></div>
            </div>
            <div class="skeleton skeleton-text posts-skeleton-title"></div>
            <div class="skeleton skeleton-text posts-skeleton-line"></div>
            <div class="skeleton skeleton-text posts-skeleton-line posts-skeleton-line--short"></div>
            <div class="skeleton skeleton-text posts-skeleton-footer"></div>
         </div>
      </div>

      <!-- Post List -->
      <UiEmpty v-if="error && items.length === 0" type="error">{{ error }}</UiEmpty>
      <div v-else-if="error" class="error">{{ error }}</div>

      <div v-if="blockedHiddenCount > 0" class="muted posts-muted-note">
        已隐藏 {{ blockedHiddenCount }} 条来自已屏蔽用户的帖子
      </div>

      <UiEmpty v-if="!loading && items.length === 0 && !error" class="posts-empty-inline">
        暂时还没有新的讨论进入这条时间线
        <template #description>
          试试切换到「热门」，或者直接发起一个问题。
          {{ authed ? '你现在就可以把今天最值得讨论的话题抛出来。' : '登录后就能把第一篇主帖送上时间线。' }}
        </template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">刷新时间线</UiButton>
          <UiButton v-if="!authed" variant="ghost" @click="goLogin">登录</UiButton>
          <UiButton v-else variant="ghost" @click="isPublishFocused = true">发起讨论</UiButton>
        </template>
      </UiEmpty>

      <div v-if="shouldShowNewHint" class="topic-new-hint">
        <div class="topic-new-hint-left">
          自上次访问后新增 <span class="topic-new-hint-num">{{ newSinceLastSeenCount }}</span> 条
        </div>
        <div class="topic-new-hint-actions">
          <UiButton variant="secondary" class="topic-new-hint-btn" :disabled="!canJumpToLastSeen" @click="scrollToLastSeenDivider">上次位置</UiButton>
          <UiButton variant="ghost" class="topic-new-hint-btn" @click="newHintDismissed = true">收起</UiButton>
        </div>
      </div>
      
      <div v-if="items.length > 0" class="discussion-feed">
        <template v-for="(p, idx) in items" :key="p.id">
          <div v-if="shouldShowLastSeenDivider && idx === newDividerIndex" ref="lastSeenDividerRef" class="discussion-divider">
            上次看到这里
          </div>

          <article
            class="discussion-card"
            :class="{ 'is-unread': isUnread(p) }"
            role="link"
            tabindex="0"
            @keydown.enter="openPost(p)"
            @click="openPost(p)"
          >
            <div class="discussion-card-head">
              <div class="discussion-card-taxonomy">
                <span v-if="isUnread(p)" class="discussion-unread-pill" title="未读" aria-label="未读">未读</span>
                <span v-if="p.categoryId" class="discussion-category-chip">
                  {{ categoryLabel(p.categoryId) }}
                </span>
                <span v-for="t in (Array.isArray(p.tags) ? p.tags : [])" :key="t" class="discussion-tag-chip">
                  #{{ t }}
                </span>
              </div>
              <div class="discussion-card-badges" v-if="p.type === 1 || p.status >= 1">
                <UiBadge v-if="p.type === 1" variant="accent">置顶</UiBadge>
                <UiBadge v-if="p.status === 1" variant="success">精华</UiBadge>
              </div>
            </div>

            <h2 class="discussion-card-title">{{ p.title }}</h2>

            <div class="discussion-card-snippet" v-if="p.content">
              {{ p.content?.slice(0, 140) }}{{ (p.content?.length || 0) > 140 ? '...' : '' }}
            </div>

            <div class="discussion-card-meta">
              <div
                class="discussion-card-author"
                @click.stop="router.push({ name: 'userProfile', params: { userId: String(p.userId) } })"
              >
                <UiAvatar :src="p.author?.headerUrl || ''" :name="p.author?.username || ''" :size="18" />
                <span class="discussion-card-author-name">
                  {{ p.author?.username || `成员 ${p.userId}` }}
                </span>
              </div>
              <span>·</span>
              <span :title="formatTime(p.createTime)">发布 {{ formatTimeAgo(p.createTime) }}</span>
            </div>

            <div class="discussion-card-foot">
              <div class="discussion-card-stats">
                <span class="discussion-stat">{{ Number(p.commentCount || 0) }} 回复</span>
                <UiButton
                  variant="ghost"
                  class="discussion-like-btn"
                  :class="{ active: p.liked }"
                  :aria-label="p.liked ? '取消点赞' : '点赞'"
                  @click.stop="togglePostLike(p)"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    <path d="M12 19V5M5 12l7-7 7 7" />
                  </svg>
                  <span>{{ p.likeCount || 0 }} 赞</span>
                </UiButton>
              </div>

              <div class="discussion-card-activity">
                <template v-if="activityUserId(p)">
                  <UiButton
                    variant="ghost"
                    class="discussion-activity-user"
                    :aria-label="`查看用户 ${activityUser(p)?.username || `成员 ${activityUserId(p)}`}`"
                    @click.stop="router.push({ name: 'userProfile', params: { userId: String(activityUserId(p)) } })"
                  >
                    <UiAvatar
                      :src="activityUser(p)?.headerUrl || ''"
                      :name="activityUser(p)?.username || ''"
                      :size="18"
                    />
                    <span>{{ activityUser(p)?.username || `成员 ${activityUserId(p)}` }}</span>
                  </UiButton>
                  <span class="discussion-activity-time">{{ formatTimeAgo(activityTime(p)) }}</span>
                </template>
                <span v-else class="discussion-activity-time" :title="formatTime(activityTime(p))">
                  {{ formatTimeAgo(activityTime(p)) }}
                </span>
              </div>
            </div>
          </article>
        </template>
      </div>
    </div>

    <!-- Load More -->
    <div class="posts-load-more" v-if="hasNext || loading">
      <UiButton v-if="loading" variant="ghost" disabled>加载中...</UiButton>
      <UiButton v-else variant="secondary" @click="loadMore" class="posts-load-more-btn">加载更多</UiButton>
    </div>
    <div v-if="!hasNext && items.length > 0" class="muted posts-end-note">
       没有更多内容了
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiIconButton from '../components/ui/UiIconButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import FeedToolbar from '../components/posts/FeedToolbar.vue'
import { listPosts, createPost as apiCreatePost } from '../api/services/postService'
import { setLike } from '../api/services/socialService'
import { suggestTags as apiSuggestTags } from '../api/services/taxonomyService'
import {
  canJumpToLastSeenDivider,
  findLastSeenDividerIndex,
  hasLastSeenDivider,
  isDefaultLatestFeedView,
  resolveAppendPageAfterLoad
} from './postsFeedState'
import { formatTime, formatTimeAgo } from '../utils/time'
import { getPostReadAt, getPostsListBaselineAt, markPostRead, touchPostsListSeen } from '../utils/readTracker'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { useTaxonomyStore } from '../stores/taxonomy'
import { usePostMetaCacheStore } from '../stores/postMetaCache'
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
const postMetaCache = usePostMetaCacheStore()

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
const composerCategoryOptions = computed(() => [
  { label: '不选择', value: '' },
  ...categories.value.map((category) => ({
    label: category.name,
    value: String(category.id)
  }))
])

function categoryLabel(id) {
  const cid = normalizeOpaqueId(id)
  if (!cid) return ''
  const c = taxonomy.categoriesById.get(cid)
  return c?.name || `分类#${cid}`
}

const showClear = computed(
  () =>
    order.value !== POSTS_ORDER.LATEST ||
    filter.value !== POSTS_FILTER.ALL ||
    subscribed.value === true ||
    !!categoryId.value ||
    !!tag.value
)

const socialPrefs = useSocialPrefsStore()
const blockedSet = computed(() => socialPrefs.blockedSet)
const blockedHiddenCount = ref(0)

function goLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath || '/posts' } })
}

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
  replaceQuery({ order: POSTS_ORDER.LATEST, filter: POSTS_FILTER.ALL, subscribed: false, categoryId: '', tag: '' })
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

const composerTagSuggest = ref([])
const composerTagSuggestNames = computed(() =>
  (Array.isArray(composerTagSuggest.value) ? composerTagSuggest.value : []).map((tag) => String(tag?.name || '').trim()).filter(Boolean)
)
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
  const v = normalizeOpaqueId(p?.lastReplyUserId)
  if (v) return v
  return normalizeOpaqueId(p?.userId)
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
  if (!newCategoryId.value && categoryId.value) {
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

const isDefaultLatestFeed = computed(() =>
  isDefaultLatestFeedView({
    order: order.value,
    filter: filter.value,
    subscribed: subscribed.value,
    categoryId: categoryId.value,
    tag: tag.value,
    page: page.value
  })
)

const newSinceLastSeenCount = computed(() => {
  if (!isDefaultLatestFeed.value) return 0
  const baseline = Number(seenBaselineAt.value || 0)
  if (!baseline) return 0
  return (Array.isArray(items.value) ? items.value : []).reduce((acc, p) => {
    return toMs(activityTime(p)) > baseline ? acc + 1 : acc
  }, 0)
})

const newDividerIndex = computed(() => {
  if (!isDefaultLatestFeed.value) return -1
  return findLastSeenDividerIndex(items.value, seenBaselineAt.value, (item) => toMs(activityTime(item)))
})

const shouldShowLastSeenDivider = computed(() =>
  hasLastSeenDivider({
    isLatestFeedView: isDefaultLatestFeed.value,
    dividerIndex: newDividerIndex.value,
    itemsLength: items.value?.length || 0
  })
)

const shouldShowNewHint = computed(
  () => isDefaultLatestFeed.value && newSinceLastSeenCount.value > 0 && !newHintDismissed.value
)

const canJumpToLastSeen = computed(() =>
  canJumpToLastSeenDivider({
    isLatestFeedView: isDefaultLatestFeed.value,
    newSinceLastSeenCount: newSinceLastSeenCount.value,
    newHintDismissed: newHintDismissed.value,
    dividerIndex: newDividerIndex.value,
    itemsLength: items.value?.length || 0
  })
)

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

function collectBatchIds(list) {
  const userIds = []
  const postIds = []
  const seenUsers = new Set()
  const seenPosts = new Set()

  for (const p of Array.isArray(list) ? list : []) {
    const uid = normalizeOpaqueId(p?.userId)
    const lr = normalizeOpaqueId(p?.lastReplyUserId)
    const pid = normalizeOpaqueId(p?.id)

    if (uid && !seenUsers.has(uid)) {
      seenUsers.add(uid)
      userIds.push(uid)
    }
    if (lr && !seenUsers.has(lr)) {
      seenUsers.add(lr)
      userIds.push(lr)
    }
    if (pid && !seenPosts.has(pid)) {
      seenPosts.add(pid)
      postIds.push(pid)
    }

    // 后端建议 max=200，这里前置截断，避免请求体/URL 过大。
    if (userIds.length >= 200 && postIds.length >= 200) break
  }
  return { userIds, postIds }
}

async function hydrateBatch(list, token) {
  if (!Array.isArray(list) || list.length === 0) return

  const { userIds, postIds } = collectBatchIds(list)
  let users = {}
  let counts = {}
  let statuses = {}

  try {
    users = await postMetaCache.ensureUserSummaries(userIds)
  } catch {
    users = {}
  }
  try {
    counts = await postMetaCache.ensureLikeCounts(1, postIds)
  } catch {
    counts = {}
  }
  if (authed.value) {
    try {
      statuses = await postMetaCache.ensureLikeStatuses(1, postIds)
    } catch {
      statuses = {}
    }
  }

  if (token !== lastLoadToken) return

  for (const p of list) {
    if (!p) continue
    const uid = normalizeOpaqueId(p?.userId)
    const lr = normalizeOpaqueId(p?.lastReplyUserId)
    const pid = normalizeOpaqueId(p?.id)

    p.author = users?.[uid] || null
    p.lastReplyAuthor = lr ? (users?.[lr] || null) : null

    const likeCount = counts?.[pid]
    p.likeCount = typeof likeCount === 'number' ? likeCount : 0

    const liked = statuses?.[pid]
    p.liked = !!liked
  }
}

function scheduleHydrate(list, token) {
  if (!Array.isArray(list) || list.length === 0) return

  // 让首屏先渲染：异步补水作者/点赞数，避免阻塞列表展示。
  setTimeout(() => {
    if (token !== lastLoadToken) return
    hydrateBatch(list, token)
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
  if (append && loading.value) return false

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
      return false
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

    const afterBlocked = blockedSet.value.size > 0 ? base.filter((p) => !blockedSet.value.has(normalizeOpaqueId(p?.userId))) : base
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
    return true
  } catch (e) {
    if (token !== lastLoadToken) return
    if (!append) error.value = e?.message || '加载失败'
    else showToast({ type: 'error', text: '加载更多失败' })
    return false
  } finally {
    if (token === lastLoadToken) {
      loading.value = false
    }
  }
}

async function loadMore() {
  const previousPage = page.value
  page.value = resolveAppendPageAfterLoad({ previousPage, didLoadSucceed: true })
  const didLoadSucceed = await load(true)
  page.value = resolveAppendPageAfterLoad({ previousPage, didLoadSucceed })
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
      entityId: p.id,
      entityUserId: p.userId,
      postId: p.id,
      liked: null
    })
     emit('trace', resp?.traceId || '')
     if (typeof resp?.data?.likeCount === 'number') {
       p.likeCount = resp.data.likeCount
       postMetaCache.setLikeCount(1, p.id, p.likeCount)
     }
     if (typeof resp?.data?.liked === 'boolean') {
       p.liked = resp.data.liked
       postMetaCache.setLikeStatus(1, p.id, p.liked)
     }
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
    const cid = normalizeOpaqueId(newCategoryId.value)
    const resp = await apiCreatePost({
      title: newTitle.value,
      content: newContent.value,
      categoryId: cid || undefined,
      tags: newTags.value
    })
    emit('trace', resp?.traceId || '')

    const createdPostId = normalizeOpaqueId(resp?.data?.postId)
    const hasPostId = !!createdPostId
    showToast({
      type: 'success',
      title: '发布成功',
      text: '你的帖子已发布。搜索/通知为最终一致，结果可能延迟数秒到数十秒。',
      duration: 6000,
      actionText: hasPostId ? '立即查看帖子' : '',
      onAction: hasPostId ? () => router.push({ name: 'postDetail', params: { postId: String(createdPostId) } }) : null
    })
    
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

watch(
  () => auth.accessToken,
  () => {
    // 点赞状态与登录态相关：切换账号/退出登录时清理缓存并刷新 UI。
    postMetaCache.clearLikeStatuses()
    if (!authed.value) {
      for (const p of Array.isArray(items.value) ? items.value : []) {
        if (p) p.liked = false
      }
    }
    scheduleHydrate(items.value, lastLoadToken)
  }
)

watch([order, filter, subscribed, categoryId, tag], () => {
  newHintDismissed.value = false
  reload()
})

onMounted(() => {
  taxonomy.ensureCategories()
  taxonomy.ensureHotTags(12)
  seenBaselineAt.value = getPostsListBaselineAt()
  if (isDefaultLatestFeed.value && !touchedLatestOnce) {
    touchedLatestOnce = true
    seenBaselineAt.value = touchPostsListSeen()
  }
  reload()
})

watch(isDefaultLatestFeed, (v) => {
  if (v && !touchedLatestOnce) {
    touchedLatestOnce = true
    seenBaselineAt.value = touchPostsListSeen()
  }
})
</script>

<style scoped>
.posts-page {
  gap: 18px;
}

.posts-toolbar-stage {
  display: grid;
  gap: 8px;
}

.posts-context-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 0 2px;
  font-size: 12px;
  color: var(--text-3);
}

.posts-context-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.posts-context-item strong {
  color: var(--text-1);
  font-size: 13px;
}

.posts-context-item--accent {
  color: var(--accent);
}

.posts-context-sep {
  color: var(--text-3);
}

.posts-feed {
  display: grid;
  gap: 12px;
}

.posts-feed-compose-strip {
  width: 100%;
  height: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 18px;
  border-radius: 20px;
  border: 1px solid color-mix(in srgb, var(--border) 78%, transparent 22%);
  background: color-mix(in srgb, var(--surface) 94%, var(--bg) 6%);
  color: inherit;
  cursor: pointer;
  box-shadow: none;
}

.posts-feed-compose-strip:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 96%, var(--bg) 4%);
}

.posts-feed-compose-leading {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.posts-feed-compose-copy {
  min-width: 0;
  display: grid;
  gap: 3px;
  text-align: left;
}

.posts-feed-compose-title {
  font-size: 14px;
  font-weight: 800;
  color: var(--text-1);
}

.posts-feed-compose-sub {
  color: var(--text-3);
  font-size: 12px;
}

.posts-feed-compose-action {
  flex: none;
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  background: var(--surface);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 700;
}

.posts-composer {
  border-radius: 22px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  background: linear-gradient(180deg, color-mix(in srgb, var(--surface) 96%, #fff 4%), var(--surface));
  box-shadow: var(--shadow-sm);
}

.posts-composer-editor {
  display: grid;
  gap: 16px;
}

.posts-composer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.posts-composer-title {
  font-size: 18px;
  font-weight: 800;
  line-height: 1;
  color: var(--text-1);
}

.posts-composer-close {
  width: 28px;
  height: 28px;
  padding: 0;
}

.posts-composer-input {
  font-weight: 700;
  font-size: 16px;
}

.posts-composer-textarea {
  resize: none;
}

.posts-composer-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.posts-composer-field {
  min-width: 0;
}

.posts-composer-field--category {
  flex: 0 0 220px;
}

.posts-composer-category-select {
  width: 100%;
}

.posts-composer-category-select :deep(.ui-select-trigger) {
  width: 100%;
}

.posts-composer-field--tags {
  flex: 1;
  min-width: 240px;
}

.posts-composer-label {
  margin-bottom: 8px;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.posts-composer-error {
  margin-top: 6px;
  font-size: 12px;
}

.posts-composer-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.posts-composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.posts-composer-submit-error {
  font-size: 13px;
}

.posts-composer-action-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.posts-composer-submit {
  min-width: 88px;
}

.posts-toolbar {
  margin-top: 6px;
  margin-bottom: 10px;
}

.posts-skeletons,
.posts-feed {
  display: grid;
}

.posts-skeleton-card {
  display: grid;
  gap: 10px;
  padding: 24px;
  border-radius: 28px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, var(--surface), var(--surface-2));
  box-shadow: var(--shadow-sm);
}

.posts-skeleton-meta {
  display: flex;
  gap: 8px;
}

.posts-skeleton-pill {
  width: 84px;
  height: 20px;
  margin-bottom: 0;
}

.posts-skeleton-pill--sm {
  width: 60px;
}

.posts-skeleton-title {
  width: 72%;
  height: 1.5em;
  margin-bottom: 0;
}

.posts-skeleton-line {
  width: 100%;
  margin-bottom: 0;
}

.posts-skeleton-line--short {
  width: 82%;
}

.posts-skeleton-footer {
  width: 36%;
  margin-bottom: 0;
}

.posts-muted-note {
  padding: 0 6px;
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--text-2);
}

.posts-empty-inline :deep(.empty-state) {
  padding: 8px 0 2px;
}

.posts-empty-inline :deep(.empty-card) {
  width: 100%;
  border-style: solid;
  border-color: color-mix(in srgb, var(--border) 74%, transparent 26%);
  border-radius: 24px;
  box-shadow: none;
}

.topic-new-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--border) 74%, transparent 26%);
  background: color-mix(in srgb, var(--surface) 94%, var(--bg) 6%);
  box-shadow: none;
}

.topic-new-hint-left {
  font-size: 13px;
  color: var(--text-1);
}

.topic-new-hint-num {
  font-size: 15px;
  line-height: 1;
  color: var(--accent);
}

.topic-new-hint-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.topic-new-hint-btn {
  min-height: 34px;
}

.discussion-feed {
  display: grid;
  gap: 12px;
}

.discussion-divider {
  width: 100%;
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text-3);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  justify-self: stretch;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  text-align: center;
  line-height: 44px;
}

.discussion-card {
  position: relative;
  display: grid;
  gap: 12px;
  padding: 20px 22px;
  border-radius: 24px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, var(--surface), var(--surface-2));
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
  overflow: hidden;
}

.discussion-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: transparent;
  transition: background 0.18s ease;
}

.discussion-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
  border-color: var(--border-strong);
}

.discussion-card.is-unread {
  border-color: var(--border-strong);
}

.discussion-card.is-unread::before {
  background: var(--accent);
}

.discussion-card-head,
.discussion-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.discussion-card-taxonomy,
.discussion-card-badges,
.discussion-card-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.discussion-unread-pill,
.discussion-category-chip,
.discussion-tag-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.discussion-unread-pill {
  background: var(--accent-weak);
  color: var(--accent);
}

.discussion-category-chip {
  background: var(--surface);
  border: 1px solid color-mix(in srgb, var(--border-strong) 34%, var(--border) 66%);
  color: var(--text-1);
}

.discussion-tag-chip {
  background: color-mix(in srgb, var(--text-1) 6%, transparent 94%);
  color: var(--text-2);
}

.discussion-card-title {
  margin: 0;
  max-width: 32ch;
  font-family: var(--font-display);
  font-size: clamp(22px, 2.2vw, 28px);
  line-height: 1.12;
  letter-spacing: -0.03em;
  color: var(--text-1);
}

.discussion-card-snippet {
  max-width: 70ch;
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.7;
}

.discussion-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--text-3);
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.discussion-card-author {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.discussion-card-author-name {
  font-weight: 800;
  color: var(--text-1);
  letter-spacing: 0;
  text-transform: none;
}

.discussion-like-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  height: auto;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-2);
}

.discussion-like-btn.active {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--border-strong) 40%, var(--border) 60%);
}

.discussion-card-activity {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.discussion-activity-user {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  height: auto;
  border: none;
  background: transparent;
  padding: 0;
  color: inherit;
  box-shadow: none;
}

.discussion-activity-time,
.discussion-stat {
  font-size: 13px;
  color: var(--text-3);
}

.discussion-card-foot {
  padding-top: 14px;
  border-top: 1px solid color-mix(in srgb, var(--border) 82%, transparent 18%);
}

.posts-load-more {
  margin-top: 10px;
  text-align: center;
}

.posts-load-more-btn {
  min-width: 260px;
}

.posts-end-note {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

@media (max-width: 768px) {
  .posts-feed-compose-strip,
  .posts-composer-actions,
  .discussion-card-foot {
    grid-template-columns: 1fr;
    display: grid;
  }

  .topic-new-hint {
    align-items: flex-start;
    flex-direction: column;
  }

  .posts-composer-actions {
    justify-content: stretch;
  }

  .posts-composer-action-group {
    justify-content: flex-end;
  }

  .posts-composer-field--category,
  .posts-composer-field--tags {
    flex: 1 1 100%;
  }

  .discussion-card {
    padding: 18px 18px;
  }

  .discussion-card-title {
    font-size: clamp(20px, 6.2vw, 24px);
  }
}

html[data-theme='dark'] .posts-context-chip,
html[data-theme='dark'] .posts-composer,
html[data-theme='dark'] .posts-skeleton-card,
html[data-theme='dark'] .discussion-card {
  border-color: var(--border);
  background: linear-gradient(180deg, var(--surface-2), #0f0f0f);
  box-shadow: 0 20px 36px rgba(0, 0, 0, 0.26);
}

html[data-theme='dark'] .input-fake,
html[data-theme='dark'] .discussion-category-chip,
html[data-theme='dark'] .discussion-like-btn {
  background: var(--surface-3);
  border-color: var(--border);
  box-shadow: none;
}

html[data-theme='dark'] .discussion-tag-chip {
  background: rgba(255, 255, 255, 0.08);
}

html[data-theme='dark'] .topic-new-hint {
  border-color: var(--border);
  background: linear-gradient(180deg, var(--surface-2), #0d0d0d);
  box-shadow: 0 18px 28px rgba(0, 0, 0, 0.24);
}

html[data-theme='dark'] .posts-context-chip,
html[data-theme='dark'] .discussion-divider {
  color: var(--text-3);
}
</style>

<template>
  <div class="page reading">
    <UiCard>
      <div style="padding: 12px 16px 0 16px">
         <UiBreadcrumb />
      </div>
      <UiPageHeader>
        <template #title>
           <UiButton variant="ghost" class="post-detail-back" @click="$router.back()">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
              返回
           </UiButton>
        </template>
        <template #actions>
          <UiButton variant="secondary" @click="reload" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>

      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
      <div v-else-if="loading" class="muted" style="margin-top: 12px">加载中…</div>
      <UiEmpty v-else-if="!post" style="margin-top: 12px">暂无数据</UiEmpty>

      <div v-else class="stack" style="margin-top: 4px; padding: 0 4px">
         <!-- Prominent Header -->
        <div class="stack" style="gap: 12px">
           <div class="row" style="gap: 8px; flex-wrap: wrap; align-items: center">
             <div class="vote-box-detail">
               <button class="vote-btn-d up" :class="{ active: post.liked }" aria-label="点赞" @click="togglePostLike">
                 <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 19V5M5 12l7-7 7 7"/></svg>
               </button>
               <span class="vote-count-d">{{ post.likeCount || 0 }}</span>
               <button class="vote-btn-d down" aria-label="点踩（占位）">
                 <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M19 12l-7 7-7-7"/></svg>
               </button>
             </div>

               <div style="flex: 1">
                 <h1 style="font-weight: 800; font-size: 24px; line-height: 1.3; margin: 0 0 8px 0; color: var(--text-1)">
                   {{ post.title }}
                 </h1>
                 <div class="row muted" style="gap: 8px; flex-wrap: wrap; font-size: 13px; align-items: center">
                   <UiBadge v-if="post.type === 1" variant="accent">置顶</UiBadge>
                    <UiBadge v-if="post.status === 1" variant="success">加精</UiBadge>
                    <UiBadge v-if="post.status === 2" variant="danger">已删除</UiBadge>

                    <RouterLink
                      v-if="Number(post.categoryId || 0) > 0"
                      class="taxonomy-link"
                      :to="{ name: 'posts', query: { categoryId: String(post.categoryId) } }"
                      :title="`查看分类 ${categoryLabel(post.categoryId)}`"
                    >
                      <span class="tag topic-category">{{ categoryLabel(post.categoryId) }}</span>
                    </RouterLink>

                    <RouterLink
                      v-for="t in (Array.isArray(post.tags) ? post.tags : [])"
                      :key="t"
                      class="taxonomy-link"
                      :to="{ name: 'posts', query: { tag: t } }"
                      :title="`查看标签 ${t}`"
                    >
                      <span class="tag">#{{ t }}</span>
                    </RouterLink>
                    
                    <UiUserCard :user="postAuthor">
                      <div class="row" style="gap: 6px; cursor: pointer">
                        <UiAvatar :src="postAuthor?.headerUrl || ''" :name="postAuthor?.username || ''" :size="20" />
                        <span style="font-weight: 600; color: var(--text-1)">{{ postAuthor?.username || `user#${post.userId}` }}</span>
                      </div>
                    </UiUserCard>
                    
                    <UiRoleBadge :user="postAuthor" size="md" />
                    <span style="background: var(--surface-2); padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 600">LV {{ Math.floor((postAuthor?.score || 0) / 100) + 1 }}</span>

                    <span>发布于 {{ formatTime(post.createTime) }}</span>
                    <span v-if="Number(post.editCount || 0) > 0" :title="post.updateTime ? formatTime(post.updateTime) : ''">· 已编辑</span>
                 </div>
               </div>
             </div>
 
             <div class="divider"></div>
 
             <UiMarkdown :content="post.content" />
          </div>
 
          <!-- Ops -->
          <div class="row" style="gap: 8px; flex-wrap: wrap; margin-top: 16px">
            <template v-if="authed">
              <UiButton variant="secondary" @click="toggleBookmark" :disabled="actionLoading">
                {{ post.bookmarked ? '已收藏' : '收藏' }}
              </UiButton>
            </template>

            <template v-if="authed && post.userId === meUserId">
              <UiButton
                variant="secondary"
                :disabled="actionLoading || !canEditPost"
                :title="canEditPost ? '' : '仅发布后 24 小时内可编辑'"
                @click="openEditPost"
              >
                编辑
              </UiButton>
              <UiButton variant="dangerSecondary" :disabled="actionLoading || post.status === 2" @click="confirmAuthorDelete">
                {{ post.status === 2 ? '已删除' : '删除' }}
              </UiButton>
            </template>

            <template v-if="authed && post.userId !== meUserId">
              <UiButton v-if="followStatus === false" @click="follow(true)" :disabled="actionLoading">关注作者</UiButton>
              <UiButton v-else-if="followStatus === true" variant="secondary" @click="follow(false)" :disabled="actionLoading">
                取关作者
              </UiButton>
              <UiButton variant="secondary" :disabled="actionLoading" @click="openReportPost">举报</UiButton>
              <UiButton :variant="isBlockedAuthor ? 'dangerSecondary' : 'secondary'" :disabled="actionLoading" @click="toggleBlockAuthor">
                {{ isBlockedAuthor ? '已屏蔽' : '屏蔽' }}
              </UiButton>
            </template>
 
            <template v-if="authed && auth.isAdminOrModerator">
              <UiButton variant="secondary" @click="confirmModeration('top')" :disabled="actionLoading || post.type === 1">
                {{ post.type === 1 ? '已置顶' : '置顶' }}
              </UiButton>
              <UiButton variant="secondary" @click="confirmModeration('wonderful')" :disabled="actionLoading || post.status === 1">
                {{ post.status === 1 ? '已加精' : '加精' }}
              </UiButton>
              <UiButton variant="dangerSecondary" @click="confirmModeration('delete')" :disabled="actionLoading || post.status === 2">
                {{ post.status === 2 ? '已删除' : '删除' }}
              </UiButton>
            </template>
          </div>
        </div>
      </UiCard>
 
      <UiCard>
        <UiPageHeader>
          <template #title>评论 {{ post?.commentCount || 0 }}</template>
          <template #actions>
            <UiButton variant="secondary" @click="reloadComments" :disabled="commentsLoading">
              {{ commentsLoading ? '加载中…' : '刷新' }}
            </UiButton>
          </template>
        </UiPageHeader>
 
        <div style="margin-top: 12px">
          <UiPagination :page="commentsPage" :has-next="commentsHasNext" @prev="prevCommentsPage" @next="nextCommentsPage" />
        </div>
 
        <div v-if="commentsError" class="error" style="margin-top: 12px">{{ commentsError }}</div>
 
        <div style="margin-top: 12px">
          <UiEmpty v-if="!commentsLoading && comments.length === 0 && !commentsError">暂无评论</UiEmpty>
          <div v-else-if="commentsLoading && comments.length === 0" class="muted">加载中…</div>
          <div v-else class="stack" style="gap: 16px">
            <div
              v-for="c in comments"
              :key="c.id"
              class="comment-thread"
              :id="commentAnchorId(c.id)"
              style="position: relative"
            >
              <!-- Thread Line (Visual) -->
              <div class="thread-line" v-if="c._repliesExpanded && c._replies.length > 0"></div>

              <div class="row" style="justify-content: space-between; align-items: flex-start">
                <div class="row" style="align-items: center; gap: 8px">
                  <UiUserCard :user="c.user">
                    <UiAvatar :src="c.user?.headerUrl || ''" :name="c.user?.username || ''" :size="28" style="cursor: pointer" />
                  </UiUserCard>

                  <div class="stack" style="gap: 0">
                    <div class="row" style="gap: 6px; align-items: center">
                      <UiUserCard :user="c.user">
                        <router-link :to="`/users/${c.userId}`" style="font-weight: 700; font-size: 13px; color: var(--text-1)">
                          {{ c.user?.username || `user#${c.userId}` }}
                        </router-link>
                      </UiUserCard>

                      <UiBadge v-if="c.userId === post.userId" variant="secondary" style="height: 16px; font-size: 10px">OP</UiBadge>
                      <UiRoleBadge :user="c.user" />
                    </div>
                    <span class="muted" style="font-size: 11px">
                      {{ formatTime(c.createTime) }}
                      <span v-if="Number(c.editCount || 0) > 0" :title="c.updateTime ? formatTime(c.updateTime) : ''">· 已编辑</span>
                    </span>
                  </div>
                </div>
              </div>

              <div class="comment-content" style="padding-left: 36px">
                <div v-if="isBlockedUser(c.userId)" class="muted blocked-placeholder">已屏蔽该用户内容</div>
                <UiMarkdown v-else variant="compact" :content="c.content" />

                <div v-if="!isBlockedUser(c.userId)" class="row muted comment-actions" style="gap: 12px; margin-top: 8px; font-size: 12px">
                  <button
                    class="comment-action"
                    type="button"
                    :aria-label="c.liked ? '取消点赞评论' : '点赞评论'"
                    @click="toggleCommentLike(c)"
                  >
                    <span aria-hidden="true" :class="{ 'red-text': c.liked }">❤️</span>
                    <span>{{ c.likeCount || 0 }}</span>
                  </button>

                  <button class="comment-action" type="button" aria-label="回复评论" @click="startReply(c)">回复</button>

                  <button v-if="canEditComment(c)" class="comment-action" type="button" aria-label="编辑评论" @click="openEditComment(c)">
                    编辑
                  </button>

                  <button
                    v-if="(c.replyCount || 0) > 0 || c._repliesExpanded"
                    class="comment-action"
                    type="button"
                    :aria-label="c._repliesExpanded ? '收起回复' : `展开 ${c.replyCount || 0} 条回复`"
                    @click="toggleReplies(c)"
                  >
                    {{ c._repliesExpanded ? '收起回复' : `展开 ${c.replyCount || 0} 条回复` }}
                  </button>
                </div>

                <!-- Reply Input -->
                <div v-if="!isBlockedUser(c.userId) && c._replying" class="card flat reply-editor">
                  <div v-if="c._replyQuote" class="reply-quote">
                    <div class="row" style="justify-content: space-between; gap: 8px">
                      <div class="muted" style="font-size: 12px">
                        引用 {{ c._replyQuote?.username || `user#${c._replyQuote?.userId || ''}` }}
                      </div>
                      <button class="btn-icon sm" type="button" aria-label="取消引用" title="取消引用" @click="clearReplyQuote(c)">
                        ×
                      </button>
                    </div>
                    <div class="reply-quote-content">{{ c._replyQuote?.preview || '' }}</div>
                  </div>

                  <UiTextarea
                    :model-value="c._replyDraft"
                    :model-modifiers="{ trim: true }"
                    :rows="3"
                    placeholder="回复…（支持 Markdown）"
                    @update:modelValue="(v) => setReplyDraft(c, v)"
                  />
                  <div v-if="c._replyError" class="error" style="margin-top: 6px; font-size: 12px">{{ c._replyError }}</div>
                  <div class="row" style="justify-content: flex-end; margin-top: 8px; gap: 8px">
                    <UiButton variant="secondary" @click="cancelReply(c)" :disabled="c._replySubmitting">收起</UiButton>
                    <UiButton @click="submitReply(c)" :disabled="c._replySubmitting">提交</UiButton>
                  </div>
                </div>

                <!-- Replies List -->
                <div v-if="!isBlockedUser(c.userId) && c._repliesExpanded" style="margin-top: 12px; display: grid; gap: 12px">
                  <div class="muted" v-if="c._repliesLoading">加载中…</div>
                  <div v-else-if="c._replies.length === 0" class="muted">暂无回复</div>
                  <div v-else v-for="r in c._replies" :key="r.id" class="reply-item" :id="replyAnchorId(r.id)">
                    <div class="row" style="align-items: center; gap: 8px">
                      <UiUserCard :user="r.user">
                        <UiAvatar :src="r.user?.headerUrl || ''" :name="r.user?.username || ''" :size="20" />
                      </UiUserCard>

                      <span style="font-weight: 600; font-size: 12px">{{ r.user?.username || `user#${r.userId}` }}</span>

                      <UiBadge v-if="r.userId === post.userId" variant="secondary" style="height: 16px; font-size: 10px">OP</UiBadge>
                      <UiRoleBadge :user="r.user" />

                      <span class="muted" style="font-size: 12px">回复 {{ r.targetUser?.username || '楼主' }}</span>
                      <span class="muted" style="font-size: 12px">
                        · {{ formatTime(r.createTime) }}
                        <span v-if="Number(r.editCount || 0) > 0" :title="r.updateTime ? formatTime(r.updateTime) : ''">· 已编辑</span>
                      </span>
                    </div>

                    <div style="margin-top: 4px; padding-left: 28px">
                      <div v-if="isBlockedUser(r.userId)" class="muted blocked-placeholder">已屏蔽该用户内容</div>
                      <UiMarkdown v-else variant="compact" :content="r.content" />
                    </div>

                    <div v-if="!isBlockedUser(r.userId)" class="row muted comment-actions" style="gap: 12px; margin-top: 6px; padding-left: 28px; font-size: 12px">
                      <button
                        class="comment-action"
                        type="button"
                        :aria-label="r.liked ? '取消点赞回复' : '点赞回复'"
                        @click="toggleReplyLike(c, r)"
                      >
                        <span aria-hidden="true" :class="{ 'red-text': r.liked }">❤️</span>
                        <span>{{ r.likeCount || 0 }}</span>
                      </button>
                      <button class="comment-action" type="button" aria-label="回复该回复" @click="startReply(c, r)">回复</button>
                      <button v-if="canEditComment(r)" class="comment-action" type="button" aria-label="编辑回复" @click="openEditComment(r)">
                        编辑
                      </button>
                    </div>
                  </div>

                  <UiPagination
                    v-if="repliesHasNext(c) || c._repliesPage > 0"
                    :page="c._repliesPage"
                    :has-next="repliesHasNext(c)"
                    @prev="prevRepliesPage(c)"
                    @next="nextRepliesPage(c)"
                    style="margin-top: 8px"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
    </UiCard>

    <UiCard v-if="authed">
      <UiPageHeader>
        <template #title>发表评论</template>
        <template #subtitle>参与讨论 · 支持回复树</template>
        <template #actions>
          <UiButton @click="addComment" :disabled="commenting">{{ commenting ? '提交中…' : '提交' }}</UiButton>
        </template>
      </UiPageHeader>

      <div class="stack" style="margin-top: 12px">
        <UiTextarea
          :model-value="newComment"
          :model-modifiers="{ trim: true }"
          placeholder="写下你的观点…（支持 Markdown）"
          :rows="4"
          @update:modelValue="setNewComment"
        />
        <div v-if="commentError" class="error">{{ commentError }}</div>
      </div>
    </UiCard>

    <UiCard v-else>
      <UiEmpty>登录后可点赞、评论、回复与关注。</UiEmpty>
    </UiCard>

    <UiModalConfirm
      v-if="confirmOpen"
      :title="confirmTitle"
      :message="confirmMessage"
      :confirm-text="confirmOkText"
      :confirm-variant="confirmVariant"
      @cancel="closeConfirm"
      @confirm="runConfirm"
    />

    <ReportModal
      v-if="reportOpen"
      target-type="post"
      :target-id="Number(post?.id || 0)"
      @close="reportOpen = false"
      @submitted="reportOpen = false"
    />

    <EditContentModal
      v-if="editOpen"
      :mode="editMode"
      :loading="actionLoading"
      :initial-title="editInitialTitle"
      :initial-content="editInitialContent"
      @close="closeEdit"
      @submit="submitEdit"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiUserCard from '../components/ui/UiUserCard.vue'
import UiMarkdown from '../components/ui/UiMarkdown.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import UiRoleBadge from '../components/ui/UiRoleBadge.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'
import ReportModal from '../components/modals/ReportModal.vue'
import EditContentModal from '../components/modals/EditContentModal.vue'
import { formatTime } from '../utils/time'
import { markPostRead } from '../utils/readTracker'
import { scrollToAnchor } from '../utils/scrollToAnchor'
import { getUserProfile } from '../api/services/userService'
import { setLike, getLikeCount, getLikeStatus, followUser, unfollowUser, getFollowStatus } from '../api/services/socialService'
import { bookmarkPost, unbookmarkPost } from '../api/services/bookmarkService'
import { blockUser, unblockUser } from '../api/services/blockService'
import {
  getPostDetail,
  listComments as apiListComments,
  listReplies as apiListReplies,
  addComment as apiAddComment,
  updatePost as apiUpdatePost,
  deletePostByAuthor as apiDeletePostByAuthor,
  updateComment as apiUpdateComment,
  moderationTop,
  moderationWonderful,
  moderationDelete
} from '../api/services/postService'

const emit = defineEmits(['trace'])

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const prefs = useSocialPrefsStore()
const authed = computed(() => !!auth.accessToken)
const taxonomy = useTaxonomyStore()

function categoryLabel(id) {
  const cid = Number(id || 0)
  if (!cid) return ''
  const c = taxonomy.categoriesById.get(cid)
  return c?.name || `分类#${cid}`
}

const postId = computed(() => String(route.params.postId || ''))

const post = ref(null)
const postAuthor = ref(null)
const loading = ref(false)
const error = ref('')

const actionLoading = ref(false)
const reportOpen = ref(false)

const meUserId = computed(() => Number(auth.userId || 0))
const followStatus = ref(null) // Boolean|null

const isBlockedAuthor = computed(() => {
  const uid = Number(post.value?.userId || 0)
  if (!uid) return false
  return prefs.blockedSet.has(uid)
})

const canEditPost = computed(() => {
  if (!authed.value || !post.value) return false
  if (Number(post.value.userId || 0) !== meUserId.value) return false
  if (Number(post.value.status || 0) === 2) return false
  const t = new Date(post.value.createTime).getTime()
  if (!Number.isFinite(t) || t <= 0) return false
  return Date.now() - t <= 24 * 3600 * 1000
})

const newComment = ref('')
const commenting = ref(false)
const commentError = ref('')

// 草稿：按 postId 隔离，避免跨帖污染（同时便于安全检查时确认键空间隔离）。
function safeStorageGet(key) {
  if (typeof window === 'undefined') return ''
  try {
    return window.localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

function safeStorageSet(key, value) {
  if (typeof window === 'undefined') return
  const v = String(value || '')
  try {
    if (!v) window.localStorage.removeItem(key)
    else window.localStorage.setItem(key, v)
  } catch {
    // ignore
  }
}

function commentDraftKey() {
  return `community.draft.posts.${String(postId.value || '')}.comment`
}

function replyDraftKey(commentId) {
  return `community.draft.posts.${String(postId.value || '')}.reply.${Number(commentId || 0)}`
}

function setNewComment(v) {
  newComment.value = String(v || '')
  safeStorageSet(commentDraftKey(), newComment.value)
}

function setReplyDraft(c, v) {
  if (!c) return
  c._replyDraft = String(v || '')
  safeStorageSet(replyDraftKey(c.id), c._replyDraft)
}

function commentAnchorId(id) {
  return `c-${Number(id || 0)}`
}

function replyAnchorId(id) {
  return `r-${Number(id || 0)}`
}

function buildQuotePreview(text) {
  const s = String(text || '').replace(/\s+/g, ' ').trim()
  if (!s) return ''
  return s.length > 120 ? `${s.slice(0, 120)}…` : s
}

function buildQuoteMarkdown(quote) {
  const raw = String(quote?.raw || '').trim()
  if (!raw) return ''

  const username = String(quote?.username || '').trim()
  const userId = Number(quote?.userId || 0)
  const who = username ? `@${username}` : userId ? `user#${userId}` : '用户'

  const lines = raw
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .slice(0, 6)

  const header = `> 引用 ${who}`
  const body = lines.map((l) => `> ${l}`).join('\n')
  return body ? `${header}\n${body}` : header
}

function composeReplyContent(draft, quote) {
  const d = String(draft || '').trim()
  const q = quote ? buildQuoteMarkdown(quote) : ''
  if (!q) return d
  if (!d) return q
  return `${q}\n\n${d}`
}

function clearReplyQuote(c) {
  if (!c) return
  c._replyQuote = null
}

const followStatusText = computed(() => (followStatus.value === null ? '-' : followStatus.value ? '已关注' : '未关注'))

const comments = ref([])
const commentsPage = ref(0)
const commentsSize = ref(10)
const commentsLoading = ref(false)
const commentsError = ref('')
const commentsHasNext = computed(() => comments.value.length === Number(commentsSize.value || 10))

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const confirmAction = ref('') // top|wonderful|delete|authorDelete

const confirmVariant = computed(() => (confirmAction.value === 'delete' || confirmAction.value === 'authorDelete' ? 'danger' : 'primary'))
const confirmOkText = computed(() => (confirmAction.value === 'delete' || confirmAction.value === 'authorDelete' ? '删除' : '确认'))

function closeConfirm() {
  confirmOpen.value = false
  confirmTitle.value = ''
  confirmMessage.value = ''
  confirmAction.value = ''
}

function confirmModeration(type) {
  if (!post.value) return
  confirmAction.value = type
  confirmOpen.value = true
  if (type === 'top') {
    confirmTitle.value = '确认置顶'
    confirmMessage.value = `是否将帖子 #${post.value.id} 置顶？`
  } else if (type === 'wonderful') {
    confirmTitle.value = '确认加精'
    confirmMessage.value = `是否将帖子 #${post.value.id} 加精？`
  } else if (type === 'delete') {
    confirmTitle.value = '确认删除'
    confirmMessage.value = `是否删除帖子 #${post.value.id}？删除后列表将不再展示。`
  } else if (type === 'authorDelete') {
    confirmTitle.value = '确认删除'
    confirmMessage.value = `是否删除帖子 #${post.value.id}？该操作会将帖子标记为已删除。`
  } else {
    confirmTitle.value = '确认操作'
    confirmMessage.value = '是否继续？'
  }
}

function confirmAuthorDelete() {
  confirmModeration('authorDelete')
}

async function runConfirm() {
  const type = confirmAction.value
  closeConfirm()
  if (!type || !post.value) return
  actionLoading.value = true
  try {
    if (type === 'top') {
      const r = await moderationTop(post.value.id)
      emit('trace', r?.traceId || '')
    } else if (type === 'wonderful') {
      const r = await moderationWonderful(post.value.id)
      emit('trace', r?.traceId || '')
    } else if (type === 'delete') {
      const r = await moderationDelete(post.value.id)
      emit('trace', r?.traceId || '')
    } else if (type === 'authorDelete') {
      const r = await apiDeletePostByAuthor(post.value.id)
      emit('trace', r?.traceId || '')
      router.push({ name: 'posts' })
      return
    }
    await reload()
  } catch (e) {
    error.value = e?.message || '管理操作失败'
  } finally {
    actionLoading.value = false
  }
}

async function loadPost() {
  error.value = ''
  loading.value = true
  try {
    const resp = await getPostDetail(postId.value)
    post.value = resp?.data || null
    emit('trace', resp?.traceId || '')

    if (post.value?.userId) {
      postAuthor.value = await getUserProfile(post.value.userId).catch(() => null)
    } else {
      postAuthor.value = null
    }
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadFollowStatus() {
  if (!authed.value || !post.value || !post.value.userId || post.value.userId === meUserId.value) {
    followStatus.value = null
    return
  }
  try {
    const resp = await getFollowStatus(3, post.value.userId, { force: true })
    emit('trace', resp?.traceId || '')
    followStatus.value = resp?.data ?? null
  } catch {
    followStatus.value = null
  }
}

async function togglePostLike() {
  if (!authed.value || !post.value) return
  actionLoading.value = true
  try {
    const resp = await setLike({
      entityType: 1,
      entityId: Number(postId.value),
      entityUserId: post.value.userId,
      postId: Number(postId.value),
      liked: null
    })
    emit('trace', resp?.traceId || '')
    if (typeof resp?.data?.likeCount === 'number') post.value.likeCount = resp.data.likeCount
    if (typeof resp?.data?.liked === 'boolean') post.value.liked = resp.data.liked
  } catch (e) {
    error.value = e?.message || '点赞操作失败'
  } finally {
    actionLoading.value = false
  }
}

async function follow(doFollow) {
  if (!authed.value || !post.value || !post.value.userId || post.value.userId === meUserId.value) return
  actionLoading.value = true
  try {
    if (doFollow) {
      const r = await followUser(3, post.value.userId, post.value.userId)
      emit('trace', r?.traceId || '')
      followStatus.value = true
    } else {
      const r = await unfollowUser(3, post.value.userId)
      emit('trace', r?.traceId || '')
      followStatus.value = false
    }
  } catch (e) {
    error.value = e?.message || '关注操作失败'
  } finally {
    actionLoading.value = false
  }
}

function isBlockedUser(userId) {
  return prefs.blockedSet.has(Number(userId || 0))
}

async function toggleBookmark() {
  if (!authed.value || !post.value) return
  actionLoading.value = true
  try {
    if (post.value.bookmarked) {
      const r = await unbookmarkPost(post.value.id)
      emit('trace', r?.traceId || '')
      post.value.bookmarked = false
    } else {
      const r = await bookmarkPost(post.value.id)
      emit('trace', r?.traceId || '')
      post.value.bookmarked = true
    }
  } catch (e) {
    error.value = e?.message || '收藏操作失败'
  } finally {
    actionLoading.value = false
  }
}

function openReportPost() {
  if (!authed.value) return
  reportOpen.value = true
}

async function toggleBlockAuthor() {
  if (!authed.value || !post.value) return
  const uid = Number(post.value.userId || 0)
  if (!uid || uid === meUserId.value) return
  actionLoading.value = true
  try {
    if (isBlockedAuthor.value) {
      await unblockUser(uid)
      if (typeof window !== 'undefined' && window.$toast) window.$toast({ type: 'success', text: '已解除屏蔽' })
    } else {
      await blockUser(uid)
      if (typeof window !== 'undefined' && window.$toast) window.$toast({ type: 'success', text: '已屏蔽该用户' })
    }
    await prefs.ensureBlocked(true)
  } catch (e) {
    error.value = e?.message || '屏蔽操作失败'
  } finally {
    actionLoading.value = false
  }
}

function canEditComment(c) {
  if (!authed.value) return false
  const uid = Number(c?.userId || 0)
  if (!uid || uid !== meUserId.value) return false
  if (Number(c?.status || 0) !== 0) return false
  const t = new Date(c?.createTime).getTime()
  if (!Number.isFinite(t) || t <= 0) return false
  return Date.now() - t <= 15 * 60 * 1000
}

const editOpen = ref(false)
const editMode = ref('post') // post | comment
const editInitialTitle = ref('')
const editInitialContent = ref('')
const editCommentId = ref(0)

function closeEdit() {
  editOpen.value = false
  editMode.value = 'post'
  editInitialTitle.value = ''
  editInitialContent.value = ''
  editCommentId.value = 0
}

function openEditPost() {
  if (!post.value || !canEditPost.value) return
  editMode.value = 'post'
  editInitialTitle.value = String(post.value.title || '')
  editInitialContent.value = String(post.value.content || '')
  editCommentId.value = 0
  editOpen.value = true
}

function openEditComment(c) {
  if (!c || !canEditComment(c)) return
  const cid = Number(c?.id || 0)
  if (!cid) return
  editMode.value = 'comment'
  editInitialTitle.value = ''
  editInitialContent.value = String(c?.content || '')
  editCommentId.value = cid
  editOpen.value = true
}

async function submitEdit(payload) {
  if (!post.value) return
  actionLoading.value = true
  try {
    if (editMode.value === 'post') {
      const r = await apiUpdatePost(post.value.id, {
        title: String(payload?.title || '').trim(),
        content: String(payload?.content || '').trim(),
        categoryId: post.value.categoryId,
        tags: Array.isArray(post.value.tags) ? post.value.tags : []
      })
      emit('trace', r?.traceId || '')
      if (typeof window !== 'undefined' && window.$toast) window.$toast({ type: 'success', text: '已保存' })
      closeEdit()
      await loadPost()
    } else {
      const cid = Number(editCommentId.value || 0)
      const r = await apiUpdateComment(post.value.id, cid, { content: String(payload?.content || '').trim() })
      emit('trace', r?.traceId || '')
      if (typeof window !== 'undefined' && window.$toast) window.$toast({ type: 'success', text: '已保存' })
      closeEdit()
      await loadComments()
    }
  } catch (e) {
    if (typeof window !== 'undefined' && window.$toast) window.$toast({ type: 'error', text: e?.message || '保存失败' })
  } finally {
    actionLoading.value = false
  }
}

async function hydrateComment(raw) {
  const commentId = Number(raw?.id || 0)
  const userId = Number(raw?.userId || 0)
  const [user, likeCount, liked] = await Promise.all([
    userId ? getUserProfile(userId).catch(() => null) : Promise.resolve(null),
    commentId ? getLikeCount(2, commentId).catch(() => ({ data: 0 })) : Promise.resolve({ data: 0 }),
    authed.value && commentId ? getLikeStatus(2, commentId).catch(() => ({ data: false })) : Promise.resolve({ data: false })
  ])

  return {
    ...raw,
    user,
    likeCount: Number(likeCount?.data || 0),
    liked: !!liked?.data,
    _likeLoading: false,

    _replying: false,
    _replyDraft: '',
    _replyError: '',
    _replySubmitting: false,
    _replyTargetId: 0,
    _replyTargetUser: null,
    _replyQuote: null,

    _repliesExpanded: false,
    _replies: [],
    _repliesPage: 0,
    _repliesSize: 5,
    _repliesLoading: false,
    _repliesError: ''
  }
}

async function hydrateReply(raw) {
  const replyId = Number(raw?.id || 0)
  const userId = Number(raw?.userId || 0)
  const targetUserId = Number(raw?.targetId || 0)
  const [user, targetUser, likeCount, liked] = await Promise.all([
    userId ? getUserProfile(userId).catch(() => null) : Promise.resolve(null),
    targetUserId ? getUserProfile(targetUserId).catch(() => null) : Promise.resolve(null),
    replyId ? getLikeCount(2, replyId).catch(() => ({ data: 0 })) : Promise.resolve({ data: 0 }),
    authed.value && replyId ? getLikeStatus(2, replyId).catch(() => ({ data: false })) : Promise.resolve({ data: false })
  ])

  return {
    ...raw,
    user,
    targetUserId,
    targetUser,
    likeCount: Number(likeCount?.data || 0),
    liked: !!liked?.data,
    _likeLoading: false
  }
}

async function maybeScrollFromRoute() {
  // 1) 优先使用 hash（例如：#c-123 / #r-456）
  const rawHash = String(route.hash || '').trim()
  const anchor = rawHash.startsWith('#') ? rawHash.slice(1) : ''
  if (anchor) {
    await nextTick()
    if (scrollToAnchor(anchor)) return
  }

  // 2) query 模式（便于“回复定位”：?commentId=1&replyId=2）
  const commentId = Number(route.query?.commentId || 0)
  const replyId = Number(route.query?.replyId || 0)
  if (!commentId) return

  await nextTick()
  scrollToAnchor(commentAnchorId(commentId))

  if (!replyId) return

  const c = comments.value.find((x) => Number(x?.id || 0) === commentId)
  if (!c) return

  if (!c._repliesExpanded) c._repliesExpanded = true
  if (Array.isArray(c._replies) && c._replies.length === 0) {
    c._repliesPage = 0
    await loadReplies(c)
  }

  await nextTick()
  scrollToAnchor(replyAnchorId(replyId))
}

async function loadComments() {
  commentsError.value = ''
  commentsLoading.value = true
  try {
    const resp = await apiListComments(postId.value, { page: commentsPage.value, size: commentsSize.value })
    emit('trace', resp?.traceId || '')
    comments.value = await Promise.all((resp?.data || []).map((c) => hydrateComment(c)))
    await maybeScrollFromRoute()
  } catch (e) {
    commentsError.value = e?.message || '加载评论失败'
  } finally {
    commentsLoading.value = false
  }
}

function repliesHasNext(c) {
  return Array.isArray(c?._replies) && c._replies.length === Number(c?._repliesSize || 5)
}

async function loadReplies(c) {
  if (!c) return
  c._repliesError = ''
  c._repliesLoading = true
  try {
    const resp = await apiListReplies(postId.value, c.id, { page: c._repliesPage, size: c._repliesSize })
    emit('trace', resp?.traceId || '')
    c._replies = await Promise.all((resp?.data || []).map((r) => hydrateReply(r)))
  } catch (e) {
    c._repliesError = e?.message || '加载回复失败'
  } finally {
    c._repliesLoading = false
  }
}

async function toggleReplies(c) {
  if (!c) return
  c._repliesExpanded = !c._repliesExpanded
  if (c._repliesExpanded && c._replies.length === 0) {
    c._repliesPage = 0
    await loadReplies(c)
  }
}

async function reloadReplies(c) {
  if (!c) return
  c._repliesPage = 0
  await loadReplies(c)
}

async function nextRepliesPage(c) {
  if (!c || c._repliesLoading || !repliesHasNext(c)) return
  c._repliesPage += 1
  await loadReplies(c)
}

async function prevRepliesPage(c) {
  if (!c || c._repliesLoading) return
  c._repliesPage = Math.max(0, c._repliesPage - 1)
  await loadReplies(c)
}

function startReply(c, reply) {
  if (!authed.value || !c) return
  c._replying = true
  c._replyError = ''

  // 恢复草稿（按 postId + commentId 隔离）。
  c._replyDraft = safeStorageGet(replyDraftKey(c.id))

  // 引用内容：回复回复时引用 reply；回复评论时引用 comment。
  if (reply && reply.userId) {
    c._replyTargetId = Number(reply.userId)
    c._replyTargetUser = reply.user || null
    c._replyQuote = {
      sourceType: 'reply',
      sourceId: Number(reply.id || 0),
      userId: Number(reply.userId || 0),
      username: String(reply.user?.username || ''),
      raw: String(reply.content || ''),
      preview: buildQuotePreview(reply.content)
    }
  } else {
    c._replyTargetId = Number(c.userId || 0)
    c._replyTargetUser = c.user || null
    c._replyQuote = {
      sourceType: 'comment',
      sourceId: Number(c.id || 0),
      userId: Number(c.userId || 0),
      username: String(c.user?.username || ''),
      raw: String(c.content || ''),
      preview: buildQuotePreview(c.content)
    }
  }
}

function cancelReply(c) {
  if (!c) return
  c._replying = false
  c._replyError = ''
  c._replySubmitting = false
  c._replyTargetId = 0
  c._replyTargetUser = null
  c._replyQuote = null
}

async function submitReply(c) {
  if (!authed.value || !c) return
  c._replyError = ''
  if (!String(c._replyDraft || '').trim()) {
    c._replyError = '回复内容不能为空'
    return
  }
  c._replySubmitting = true
  try {
    const resp = await apiAddComment(postId.value, {
      content: composeReplyContent(c._replyDraft, c._replyQuote),
      entityType: 2,
      entityId: c.id,
      targetId: c._replyTargetId || undefined
    })
    emit('trace', resp?.traceId || '')
    c._replyDraft = ''
    safeStorageSet(replyDraftKey(c.id), '')
    c._replying = false
    c._replyQuote = null
    if (!c._repliesExpanded) {
      c._repliesExpanded = true
    }
    c._repliesPage = 0
    await loadReplies(c)
  } catch (e) {
    c._replyError = e?.message || '回复失败'
  } finally {
    c._replySubmitting = false
  }
}

async function toggleCommentLike(c) {
  if (!authed.value || !c) return
  c._likeLoading = true
  try {
    const resp = await setLike({
      entityType: 2,
      entityId: Number(c.id),
      entityUserId: Number(c.userId || 0),
      postId: Number(postId.value),
      liked: null
    })
    emit('trace', resp?.traceId || '')
    if (typeof resp?.data?.likeCount === 'number') c.likeCount = resp.data.likeCount
    if (typeof resp?.data?.liked === 'boolean') c.liked = resp.data.liked
  } catch (e) {
    commentsError.value = e?.message || '点赞失败'
  } finally {
    c._likeLoading = false
  }
}

async function toggleReplyLike(c, r) {
  if (!authed.value || !c || !r) return
  r._likeLoading = true
  try {
    const resp = await setLike({
      entityType: 2,
      entityId: Number(r.id),
      entityUserId: Number(r.userId || 0),
      postId: Number(postId.value),
      liked: null
    })
    emit('trace', resp?.traceId || '')
    if (typeof resp?.data?.likeCount === 'number') r.likeCount = resp.data.likeCount
    if (typeof resp?.data?.liked === 'boolean') r.liked = resp.data.liked
  } catch (e) {
    c._repliesError = e?.message || '点赞失败'
  } finally {
    r._likeLoading = false
  }
}

async function addComment() {
  commentError.value = ''
  if (!String(newComment.value || '').trim()) {
    commentError.value = '评论不能为空'
    return
  }
  commenting.value = true
  try {
    const resp = await apiAddComment(postId.value, { content: newComment.value })
    emit('trace', resp?.traceId || '')
    setNewComment('')
    commentsPage.value = 0
    await loadComments()
    await loadPost()
  } catch (e) {
    commentError.value = e?.message || '评论失败'
  } finally {
    commenting.value = false
  }
}

async function nextCommentsPage() {
  if (!commentsHasNext.value || commentsLoading.value) return
  commentsPage.value += 1
  await loadComments()
}

async function prevCommentsPage() {
  if (commentsLoading.value) return
  commentsPage.value = Math.max(0, commentsPage.value - 1)
  await loadComments()
}

async function reloadComments() {
  commentsPage.value = 0
  await loadComments()
}

async function reload() {
  if (authed.value) {
    try {
      await prefs.ensureBlocked()
    } catch {
      // ignore：拉黑列表失败不阻塞页面加载
    }
  } else {
    prefs.clear()
  }
  await loadPost()
  await loadComments()
  await loadFollowStatus()
}

watch(
  () => [route.hash, route.query?.commentId, route.query?.replyId],
  () => {
    if (commentsLoading.value) return
    maybeScrollFromRoute()
  }
)

watch(
  () => route.params.postId,
  () => {
    post.value = null
    postAuthor.value = null
    comments.value = []
    commentsPage.value = 0
    followStatus.value = null
    reportOpen.value = false
    closeEdit()
    closeConfirm()
    // 恢复当前帖子草稿（进入新帖子时才触发）
    newComment.value = safeStorageGet(commentDraftKey())
    if (authed.value) markPostRead(postId.value)
    reload()
  }
)

onMounted(() => {
  taxonomy.ensureCategories()
  newComment.value = safeStorageGet(commentDraftKey())
  if (authed.value) markPostRead(postId.value)
  reload()
})
</script>

<style scoped>
.vote-box-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-right: 16px;
  background: var(--surface-2);
  border-radius: 8px;
  padding: 4px;
}

.post-detail-back {
  padding-left: 0;
  margin-left: -8px;
}

.vote-btn-d {
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px;
  color: var(--text-3);
  border-radius: 4px;
}
.vote-btn-d:hover {
  background: var(--hover-bg);
  color: var(--accent);
}
.vote-btn-d.up.active {
  color: #ff4500;
}
.vote-count-d {
  font-size: 15px;
  font-weight: 800;
  margin: 4px 0;
  color: var(--text-1);
}

.taxonomy-link {
  display: inline-flex;
  align-items: center;
  text-decoration: none;
}

.taxonomy-link:hover {
  text-decoration: none;
}

.thread-line {
  position: absolute;
  left: 14px;
  top: 36px;
  bottom: 0;
  width: 2px;
  background: var(--border);
  z-index: 0;
}
.comment-thread:hover .thread-line {
  background: var(--border-strong);
}

.comment-thread {
  padding: 12px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 78%, var(--bg) 22%);
}

.comment-actions {
  flex-wrap: wrap;
}

.comment-action {
  background: transparent;
  border: 1px solid transparent;
  padding: 4px 6px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-2);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font: inherit;
}

.comment-action:hover {
  background: var(--surface-2);
  color: var(--text-1);
}

.comment-action:focus-visible {
  box-shadow: var(--focus-ring);
}

.reply-item {
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: var(--surface);
}

.reply-editor {
  padding: 10px;
  margin-top: 10px;
  border-radius: 14px;
  background: var(--surface-2);
  border: 1px solid color-mix(in srgb, var(--border) 70%, transparent 30%);
}

.reply-quote {
  border-left: 3px solid var(--border-strong);
  padding-left: 10px;
  margin-bottom: 10px;
}

.reply-quote-content {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-2);
}

.red-text {
  color: var(--danger);
}

.blocked-placeholder {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px dashed var(--border);
  background: var(--surface-2);
}
</style>

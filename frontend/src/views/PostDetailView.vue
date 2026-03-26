<template>
  <div class="page reading">
    <UiCard class="post-detail-shell">
      <div class="post-detail-head">
        <div class="post-detail-breadcrumb">
          <UiBreadcrumb />
        </div>
        <div class="post-detail-shell-actions">
          <UiButton variant="ghost" class="post-detail-back" @click="$router.back()">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            返回
          </UiButton>
          <UiButton variant="secondary" @click="reload" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
        </div>
      </div>

      <div v-if="error" class="error post-detail-state">{{ error }}</div>
      <div v-else-if="loading" class="muted post-detail-state">加载中…</div>
      <UiEmpty v-else-if="!post" class="post-detail-state">暂无数据</UiEmpty>

      <div v-else class="post-detail-layout">
        <article class="post-article-card">
          <div class="post-article-frame">
            <div class="post-article-head">
              <div class="post-article-vote">
                <div class="post-article-vote-label">Audience</div>
                <div class="vote-box-detail">
                  <UiIconButton class="vote-btn-d up" :class="{ active: post.liked }" aria-label="点赞" @click="togglePostLike">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 19V5M5 12l7-7 7 7"/></svg>
                  </UiIconButton>
                  <span class="vote-count-d">{{ post.likeCount || 0 }}</span>
                  <UiIconButton class="vote-btn-d down" aria-label="点踩（占位）">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M19 12l-7 7-7-7"/></svg>
                  </UiIconButton>
                </div>
              </div>

              <div class="post-article-main">
                <div class="post-article-kicker">
                  <span class="post-article-kicker-label">讨论线程</span>
                  <span class="post-article-kicker-meta">帖子 #{{ post.id || postId }}</span>
                </div>

                <div class="post-article-taxonomy">
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
                </div>

                <h1 class="post-article-title">{{ post.title }}</h1>

                <div class="post-article-meta">
                  <UiUserCard :user="postAuthor">
                    <div class="post-article-author">
                      <UiAvatar :src="postAuthor?.headerUrl || ''" :name="postAuthor?.username || ''" :size="20" />
                      <span class="post-article-author-name">{{ postAuthor?.username || `成员 ${post.userId}` }}</span>
                    </div>
                  </UiUserCard>

                  <UiRoleBadge :user="postAuthor" size="md" />
                  <UiBadge>LV {{ Math.floor((postAuthor?.score || 0) / 100) + 1 }}</UiBadge>

                  <span>发布于 {{ formatTime(post.createTime) }}</span>
                  <span v-if="Number(post.editCount || 0) > 0" :title="post.updateTime ? formatTime(post.updateTime) : ''">· 已编辑</span>
                </div>

                <div class="post-article-ledger">
                  <div class="post-ledger-item">
                    <span class="post-ledger-label">赞同</span>
                    <strong>{{ post.likeCount || 0 }}</strong>
                  </div>
                  <div class="post-ledger-item">
                    <span class="post-ledger-label">回复</span>
                    <strong>{{ post.commentCount || 0 }}</strong>
                  </div>
                  <div class="post-ledger-item">
                    <span class="post-ledger-label">当前状态</span>
                    <strong>{{ Number(post.editCount || 0) > 0 ? '持续更新' : '等待回应' }}</strong>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="divider"></div>

          <div class="post-article-body">
            <UiMarkdown :content="post.content" />
          </div>

          <div class="post-article-actions">
            <div v-if="authed" class="post-article-action-group">
              <UiButton variant="secondary" @click="toggleBookmark" :disabled="actionLoading">
                {{ post.bookmarked ? '已收藏' : '收藏' }}
              </UiButton>
            </div>

            <div v-if="authed && post.userId === meUserId" class="post-article-action-group">
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
            </div>

            <div v-if="authed && post.userId !== meUserId" class="post-article-action-group">
              <UiButton v-if="followStatus === false" @click="follow(true)" :disabled="actionLoading">关注作者</UiButton>
              <UiButton v-else-if="followStatus === true" variant="secondary" @click="follow(false)" :disabled="actionLoading">
                取关作者
              </UiButton>
              <UiButton variant="secondary" :disabled="actionLoading" @click="openReportPost">举报</UiButton>
              <UiButton :variant="isBlockedAuthor ? 'dangerSecondary' : 'secondary'" :disabled="actionLoading" @click="toggleBlockAuthor">
                {{ isBlockedAuthor ? '已屏蔽' : '屏蔽' }}
              </UiButton>
            </div>
 
            <div v-if="authed && auth.isAdminOrModerator" class="post-article-action-group post-article-action-group--moderation">
              <UiButton variant="secondary" @click="confirmModeration('top')" :disabled="actionLoading || post.type === 1">
                {{ post.type === 1 ? '已置顶' : '置顶' }}
              </UiButton>
              <UiButton variant="secondary" @click="confirmModeration('wonderful')" :disabled="actionLoading || post.status === 1">
                {{ post.status === 1 ? '已加精' : '加精' }}
              </UiButton>
              <UiButton variant="dangerSecondary" @click="confirmModeration('delete')" :disabled="actionLoading || post.status === 2">
                {{ post.status === 2 ? '已删除' : '删除' }}
              </UiButton>
            </div>
          </div>
        </article>

        <section class="post-comments-card">
          <div class="post-comments-head">
            <div class="post-comments-head-copy">
              <div class="post-comments-title">回复 {{ post?.commentCount || 0 }}</div>
              <div class="post-comments-meta">按回复关系继续往下读</div>
            </div>
            <UiButton variant="secondary" @click="reloadComments" :disabled="commentsLoading">
              {{ commentsLoading ? '加载中…' : '刷新' }}
            </UiButton>
          </div>

          <div class="post-comments-toolbar">
            <UiPagination :page="commentsPage" :has-next="commentsHasNext" @prev="prevCommentsPage" @next="nextCommentsPage" />
          </div>

          <div v-if="commentsError" class="error post-comments-error">{{ commentsError }}</div>

          <div class="post-comments-body">
            <UiEmpty v-if="!commentsLoading && comments.length === 0 && !commentsError">暂无评论</UiEmpty>
            <div v-else-if="commentsLoading && comments.length === 0" class="muted">加载中…</div>
            <div v-else class="post-comment-thread-list">
              <div
                v-for="c in comments"
                :key="c.id"
                class="comment-thread"
                :id="commentAnchorId(c.id)"
              >
                <!-- Thread Line (Visual) -->
                <div class="thread-line" v-if="c._repliesExpanded && c._replies.length > 0"></div>

                <div class="comment-thread-head">
                  <div class="comment-author">
                    <UiUserCard :user="c.user">
                      <UiAvatar class="comment-author-avatar" :src="c.user?.headerUrl || ''" :name="c.user?.username || ''" :size="28" />
                    </UiUserCard>

                    <div class="comment-author-stack">
                      <div class="comment-author-line">
                        <UiUserCard :user="c.user">
                          <router-link :to="`/users/${c.userId}`" class="comment-author-link">
                            {{ c.user?.username || `成员 ${c.userId}` }}
                          </router-link>
                        </UiUserCard>

                        <UiBadge v-if="c.userId === post.userId" variant="secondary" class="comment-op-badge">OP</UiBadge>
                        <UiRoleBadge :user="c.user" />
                      </div>
                      <span class="muted comment-author-meta">
                        {{ formatTime(c.createTime) }}
                        <span v-if="Number(c.editCount || 0) > 0" :title="c.updateTime ? formatTime(c.updateTime) : ''">· 已编辑</span>
                      </span>
                    </div>
                  </div>
                  <div class="comment-thread-index">评论 {{ c.id }}</div>
                </div>

                <div class="comment-content">
                  <div v-if="isBlockedUser(c.userId)" class="muted blocked-placeholder">已屏蔽该用户内容</div>
                  <UiMarkdown v-else variant="compact" :content="c.content" />

                  <div v-if="!isBlockedUser(c.userId)" class="row muted comment-actions">
                    <UiButton
                      class="comment-action"
                      variant="ghost"
                      :aria-label="c.liked ? '取消点赞评论' : '点赞评论'"
                      @click="toggleCommentLike(c)"
                    >
                      <span aria-hidden="true" :class="{ 'red-text': c.liked }">❤️</span>
                      <span>{{ c.likeCount || 0 }}</span>
                    </UiButton>

                    <UiButton class="comment-action" variant="ghost" aria-label="回复评论" @click="startReply(c)">回复</UiButton>

                    <UiButton v-if="canEditComment(c)" class="comment-action" variant="ghost" aria-label="编辑评论" @click="openEditComment(c)">
                      编辑
                    </UiButton>

                    <UiButton
                      v-if="(c.replyCount || 0) > 0 || c._repliesExpanded"
                      class="comment-action"
                      variant="ghost"
                      :aria-label="c._repliesExpanded ? '收起回复' : `展开 ${c.replyCount || 0} 条回复`"
                      @click="toggleReplies(c)"
                    >
                      {{ c._repliesExpanded ? '收起回复' : `展开 ${c.replyCount || 0} 条回复` }}
                    </UiButton>
                  </div>

                  <!-- Reply Input -->
                  <div v-if="!isBlockedUser(c.userId) && c._replying" class="card flat reply-editor">
                    <div v-if="c._replyQuote" class="reply-quote">
                      <div class="reply-quote-head">
                        <div class="muted reply-quote-label">
                          引用 {{ c._replyQuote?.username || `成员 ${c._replyQuote?.userId || ''}` }}
                        </div>
                        <UiIconButton size="sm" aria-label="取消引用" title="取消引用" @click="clearReplyQuote(c)">
                          ×
                        </UiIconButton>
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
                    <div v-if="c._replyError" class="error reply-editor-error">{{ c._replyError }}</div>
                    <div class="reply-editor-actions">
                      <UiButton variant="secondary" @click="cancelReply(c)" :disabled="c._replySubmitting">收起</UiButton>
                      <UiButton @click="submitReply(c)" :disabled="c._replySubmitting">提交</UiButton>
                    </div>
                  </div>

                  <!-- Replies List -->
                  <div v-if="!isBlockedUser(c.userId) && c._repliesExpanded" class="reply-list">
                    <div class="muted" v-if="c._repliesLoading">加载中…</div>
                    <div v-else-if="c._replies.length === 0" class="muted">暂无回复</div>
                    <div v-else v-for="r in c._replies" :key="r.id" class="reply-item" :id="replyAnchorId(r.id)">
                      <div class="reply-item-head">
                        <UiUserCard :user="r.user">
                          <UiAvatar :src="r.user?.headerUrl || ''" :name="r.user?.username || ''" :size="20" />
                        </UiUserCard>

                        <span class="reply-author-name">{{ r.user?.username || `成员 ${r.userId}` }}</span>

                        <UiBadge v-if="r.userId === post.userId" variant="secondary" class="comment-op-badge">OP</UiBadge>
                        <UiRoleBadge :user="r.user" />

                        <span class="muted reply-target">回复 {{ r.targetUser?.username || '楼主' }}</span>
                        <span class="muted reply-meta">
                          · {{ formatTime(r.createTime) }}
                          <span v-if="Number(r.editCount || 0) > 0" :title="r.updateTime ? formatTime(r.updateTime) : ''">· 已编辑</span>
                        </span>
                      </div>

                      <div class="reply-body">
                        <div v-if="isBlockedUser(r.userId)" class="muted blocked-placeholder">已屏蔽该用户内容</div>
                        <UiMarkdown v-else variant="compact" :content="r.content" />
                      </div>

                      <div v-if="!isBlockedUser(r.userId)" class="row muted comment-actions reply-actions">
                        <UiButton
                          class="comment-action"
                          variant="ghost"
                          :aria-label="r.liked ? '取消点赞回复' : '点赞回复'"
                          @click="toggleReplyLike(c, r)"
                        >
                          <span aria-hidden="true" :class="{ 'red-text': r.liked }">❤️</span>
                          <span>{{ r.likeCount || 0 }}</span>
                        </UiButton>
                        <UiButton class="comment-action" variant="ghost" aria-label="回复该回复" @click="startReply(c, r)">回复</UiButton>
                        <UiButton v-if="canEditComment(r)" class="comment-action" variant="ghost" aria-label="编辑回复" @click="openEditComment(r)">
                          编辑
                        </UiButton>
                      </div>
                    </div>

                    <UiPagination
                      v-if="repliesHasNext(c) || c._repliesPage > 0"
                      class="reply-pagination"
                      :page="c._repliesPage"
                      :has-next="repliesHasNext(c)"
                      @prev="prevRepliesPage(c)"
                      @next="nextRepliesPage(c)"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </UiCard>

    <UiCard v-if="authed" class="comment-composer-card">
      <UiPageHeader>
        <template #title>发表评论</template>
        <template #subtitle>参与讨论 · 支持回复树</template>
        <template #actions>
          <UiButton @click="addComment" :disabled="commenting">{{ commenting ? '提交中…' : '提交' }}</UiButton>
        </template>
      </UiPageHeader>

      <div class="stack comment-composer">
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
import { usePostMetaCacheStore } from '../stores/postMetaCache'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiUserCard from '../components/ui/UiUserCard.vue'
import UiMarkdown from '../components/ui/UiMarkdown.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiIconButton from '../components/ui/UiIconButton.vue'
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
import { setLike, followUser, unfollowUser, getFollowStatus } from '../api/services/socialService'
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
const postMetaCache = usePostMetaCacheStore()

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
  const who = username ? `@${username}` : userId ? `成员 ${userId}` : '用户'

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

    applyPostLikeOverlay()

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

function applyPostLikeOverlay() {
  if (!post.value) return
  const pid = Number(postId.value || 0)
  if (!pid) return

  // 计数是全局读模型：短 TTL 覆盖用于减轻“写后刷新读旧投影”的感知不一致。
  const cachedCount = postMetaCache.getLikeCount(1, pid)
  if (typeof cachedCount === 'number') {
    post.value.likeCount = cachedCount
  }

  // liked 与登录态相关：未登录时强制为 false，避免跨账号/退出登录后误展示。
  if (!authed.value) {
    post.value.liked = false
    return
  }
  const cachedLiked = postMetaCache.getLikeStatus(1, pid)
  if (typeof cachedLiked === 'boolean') {
    post.value.liked = cachedLiked
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
    if (typeof resp?.data?.likeCount === 'number') {
      post.value.likeCount = resp.data.likeCount
      postMetaCache.setLikeCount(1, Number(postId.value), post.value.likeCount)
    }
    if (typeof resp?.data?.liked === 'boolean') {
      post.value.liked = resp.data.liked
      postMetaCache.setLikeStatus(1, Number(postId.value), post.value.liked)
    }
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
      const q = String(payload?.title || '').trim()
      if (typeof window !== 'undefined' && window.$toast) window.$toast({
        type: 'success',
        title: '已保存',
        text: '帖子已更新。搜索结果更新为最终一致，可能延迟数秒到数十秒。',
        duration: 6000,
        actionText: '去搜索',
        onAction: () => router.push({ name: 'search', query: q ? { q } : {} })
      })
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

function hydrateComment(raw, { users = {}, counts = {}, statuses = {} } = {}) {
  const commentId = Number(raw?.id || 0)
  const userId = Number(raw?.userId || 0)
  const likeCount = counts?.[commentId]
  const liked = statuses?.[commentId]

  return {
    ...raw,
    user: users?.[userId] || null,
    likeCount: typeof likeCount === 'number' ? likeCount : 0,
    liked: !!liked,
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

function hydrateReply(raw, { users = {}, counts = {}, statuses = {} } = {}) {
  const replyId = Number(raw?.id || 0)
  const userId = Number(raw?.userId || 0)
  const targetUserId = Number(raw?.targetId || 0)
  const likeCount = counts?.[replyId]
  const liked = statuses?.[replyId]

  return {
    ...raw,
    user: users?.[userId] || null,
    targetUserId,
    targetUser: targetUserId > 0 ? (users?.[targetUserId] || null) : null,
    likeCount: typeof likeCount === 'number' ? likeCount : 0,
    liked: !!liked,
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
    const raw = Array.isArray(resp?.data) ? resp.data : []
    const userIds = []
    const commentIds = []
    const seenUsers = new Set()
    const seenComments = new Set()
    for (const c of raw) {
      const uid = Number(c?.userId || 0)
      const cid = Number(c?.id || 0)
      if (uid > 0 && !seenUsers.has(uid)) {
        seenUsers.add(uid)
        userIds.push(uid)
      }
      if (cid > 0 && !seenComments.has(cid)) {
        seenComments.add(cid)
        commentIds.push(cid)
      }
      if (userIds.length >= 200 && commentIds.length >= 200) break
    }

    let users = {}
    let counts = {}
    let statuses = {}
    try {
      users = await postMetaCache.ensureUserSummaries(userIds)
    } catch {
      users = {}
    }
    try {
      counts = await postMetaCache.ensureLikeCounts(2, commentIds)
    } catch {
      counts = {}
    }
    if (authed.value) {
      try {
        statuses = await postMetaCache.ensureLikeStatuses(2, commentIds)
      } catch {
        statuses = {}
      }
    }

    comments.value = raw.map((c) => hydrateComment(c, { users, counts, statuses }))
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
    const raw = Array.isArray(resp?.data) ? resp.data : []
    const userIds = []
    const replyIds = []
    const seenUsers = new Set()
    const seenReplies = new Set()
    for (const r of raw) {
      const uid = Number(r?.userId || 0)
      const tid = Number(r?.targetId || 0)
      const rid = Number(r?.id || 0)
      if (uid > 0 && !seenUsers.has(uid)) {
        seenUsers.add(uid)
        userIds.push(uid)
      }
      if (tid > 0 && !seenUsers.has(tid)) {
        seenUsers.add(tid)
        userIds.push(tid)
      }
      if (rid > 0 && !seenReplies.has(rid)) {
        seenReplies.add(rid)
        replyIds.push(rid)
      }
      if (userIds.length >= 200 && replyIds.length >= 200) break
    }

    let users = {}
    let counts = {}
    let statuses = {}
    try {
      users = await postMetaCache.ensureUserSummaries(userIds)
    } catch {
      users = {}
    }
    try {
      counts = await postMetaCache.ensureLikeCounts(2, replyIds)
    } catch {
      counts = {}
    }
    if (authed.value) {
      try {
        statuses = await postMetaCache.ensureLikeStatuses(2, replyIds)
      } catch {
        statuses = {}
      }
    }

    c._replies = raw.map((r) => hydrateReply(r, { users, counts, statuses }))
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
    if (post.value) {
      post.value.commentCount = Number(post.value.commentCount || 0) + 1
    }
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
    if (typeof resp?.data?.likeCount === 'number') {
      c.likeCount = resp.data.likeCount
      postMetaCache.setLikeCount(2, Number(c.id), c.likeCount)
    }
    if (typeof resp?.data?.liked === 'boolean') {
      c.liked = resp.data.liked
      postMetaCache.setLikeStatus(2, Number(c.id), c.liked)
    }
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
    if (typeof resp?.data?.likeCount === 'number') {
      r.likeCount = resp.data.likeCount
      postMetaCache.setLikeCount(2, Number(r.id), r.likeCount)
    }
    if (typeof resp?.data?.liked === 'boolean') {
      r.liked = resp.data.liked
      postMetaCache.setLikeStatus(2, Number(r.id), r.liked)
    }
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
  () => auth.accessToken,
  () => {
    // 点赞状态与登录态强相关：切换账号/退出登录时清理覆盖，避免误展示。
    postMetaCache.clearLikeStatuses()
    applyPostLikeOverlay()
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
.post-detail-shell {
  display: grid;
  gap: 12px;
}

.post-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.post-detail-breadcrumb {
  padding: 12px 0 0;
}

.post-detail-shell-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.post-detail-state {
  margin-top: 4px;
}

.post-detail-layout {
  display: grid;
  gap: 14px;
  margin-top: 4px;
  padding: 0 2px;
}

.post-article-frame {
  position: relative;
  display: grid;
  gap: 18px;
}

.post-article-card,
.post-comments-card {
  display: grid;
  gap: 12px;
  padding: 20px;
  border-radius: 22px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  background: linear-gradient(180deg, color-mix(in srgb, var(--surface) 95%, white 5%), var(--surface));
  box-shadow: var(--shadow-sm);
}

.post-article-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.post-article-main {
  min-width: 0;
  display: grid;
  gap: 12px;
}

.post-article-taxonomy,
.post-article-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.post-article-kicker,
.post-comments-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.post-article-kicker-label,
.post-ledger-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-3);
}

.post-article-kicker-meta,
.post-comments-meta {
  font-size: 12px;
  color: var(--text-3);
}

.post-article-title {
  margin: 0;
  font-family: "Iowan Old Style", "Palatino Linotype", "Book Antiqua", Georgia, serif;
  font-size: clamp(28px, 3vw, 38px);
  line-height: 1.1;
  color: var(--text-1);
}

.post-article-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--text-3);
  font-size: 13px;
}

.post-article-author {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.post-article-author-name {
  font-weight: 700;
  color: var(--text-1);
}

.post-article-ledger {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 2px;
}

.post-ledger-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--border) 72%, transparent 28%);
  background: color-mix(in srgb, var(--surface) 82%, var(--bg) 18%);
}

.post-ledger-item strong {
  font-size: 14px;
  line-height: 1;
  color: var(--text-1);
}

.post-article-body {
  max-width: 70ch;
}

.post-article-action-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.post-article-action-group--moderation {
  padding-left: 4px;
  border-left: 1px solid color-mix(in srgb, var(--border) 82%, transparent 18%);
}

.post-article-body :deep(p),
.post-article-body :deep(li),
.post-article-body :deep(blockquote) {
  font-size: 16px;
  line-height: 1.8;
}

.post-article-body :deep(h1),
.post-article-body :deep(h2),
.post-article-body :deep(h3) {
  font-family: "Iowan Old Style", "Palatino Linotype", "Book Antiqua", Georgia, serif;
  line-height: 1.2;
}

.post-article-body :deep(p:first-child) {
  font-size: 17px;
}

.post-comments-toolbar,
.post-comments-body {
  margin-top: 4px;
}

.post-comments-error {
  margin-top: 4px;
}

.post-comment-thread-list {
  display: grid;
  gap: 16px;
}

.vote-box-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: color-mix(in srgb, var(--surface) 70%, var(--bg) 30%);
  border: 1px solid color-mix(in srgb, var(--border) 74%, transparent 26%);
  border-radius: 16px;
  padding: 8px 6px;
}

.post-article-vote {
  display: grid;
  gap: 8px;
}

.post-article-vote-label {
  writing-mode: vertical-rl;
  transform: rotate(180deg);
  align-self: center;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
}

.post-detail-back {
  min-height: 34px;
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
  color: var(--editorial-accent);
}
.vote-count-d {
  font-size: 14px;
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
  position: relative;
  padding: 16px 16px 16px 18px;
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  border-left: 3px solid color-mix(in srgb, var(--accent) 26%, var(--border) 74%);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 92%, var(--bg) 8%), var(--surface)),
    repeating-linear-gradient(
      180deg,
      transparent,
      transparent 28px,
      color-mix(in srgb, var(--border) 10%, transparent 90%) 28px,
      color-mix(in srgb, var(--border) 10%, transparent 90%) 29px
    );
}

.comment-thread-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.comment-author {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-author-avatar {
  cursor: pointer;
}

.comment-author-stack {
  display: grid;
  gap: 0;
}

.comment-author-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.comment-author-link {
  font-weight: 700;
  font-size: 13px;
  color: var(--text-1);
  text-decoration: none;
}

.comment-author-link:hover {
  color: var(--text-1);
  text-decoration: underline;
  text-decoration-color: color-mix(in srgb, var(--text-1) 30%, transparent 70%);
  text-underline-offset: 3px;
}

.comment-op-badge {
  height: 16px;
  font-size: 10px;
}

.comment-author-meta {
  font-size: 11px;
}

.post-comments-title,
.comment-thread-index {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-3);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.post-comments-head-copy {
  display: grid;
  gap: 4px;
}

.post-comments-title {
  color: var(--text-1);
  font-size: 13px;
  font-weight: 800;
}

.comment-content {
  padding-left: 36px;
}

.comment-actions {
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  flex-wrap: wrap;
}

.comment-action {
  background: transparent;
  border: 1px solid transparent;
  min-height: 0;
  height: auto;
  padding: 4px 6px;
  border-radius: 8px;
  color: var(--text-2);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font: inherit;
  font-weight: inherit;
  justify-content: flex-start;
  box-shadow: none;
}

.comment-action:hover {
  background: var(--surface-2);
  color: var(--text-1);
  box-shadow: none;
}

.comment-action:focus-visible {
  box-shadow: var(--focus-ring);
}

.reply-item {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  background: color-mix(in srgb, var(--surface) 94%, var(--bg) 6%);
}

.reply-editor {
  padding: 12px;
  margin-top: 10px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--surface) 88%, var(--bg) 12%);
  border: 1px solid color-mix(in srgb, var(--border) 70%, transparent 30%);
}

.reply-quote {
  border-left: 3px solid var(--border-strong);
  padding-left: 10px;
  margin-bottom: 10px;
}

.reply-quote-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.reply-quote-label,
.reply-quote-content {
  font-size: 12px;
}

.reply-quote-content {
  margin-top: 6px;
  color: var(--text-2);
}

.reply-editor-error {
  margin-top: 6px;
  font-size: 12px;
}

.reply-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.reply-list {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.red-text {
  color: var(--danger);
}

.reply-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.reply-author-name,
.reply-target,
.reply-meta {
  font-size: 12px;
}

.reply-author-name {
  font-weight: 600;
  color: var(--text-1);
}

.reply-body {
  margin-top: 4px;
  padding-left: 28px;
}

.reply-actions {
  margin-top: 6px;
  padding-left: 28px;
}

.reply-pagination,
.comment-composer {
  margin-top: 12px;
}

.comment-composer-card {
  border-radius: 24px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 94%, white 6%), var(--surface)),
    repeating-linear-gradient(
      180deg,
      transparent,
      transparent 30px,
      color-mix(in srgb, var(--border) 10%, transparent 90%) 30px,
      color-mix(in srgb, var(--border) 10%, transparent 90%) 31px
    );
}

.blocked-placeholder {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px dashed var(--border);
  background: var(--surface-2);
}

@media (max-width: 768px) {
  .post-detail-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .post-article-head {
    grid-template-columns: 1fr;
  }

  .post-article-card,
  .post-comments-card {
    padding: 16px;
  }

  .post-article-vote {
    grid-template-columns: auto 1fr;
    align-items: start;
  }

  .post-article-vote-label {
    writing-mode: initial;
    transform: none;
  }

  .post-article-ledger {
    gap: 6px;
  }

  .post-article-action-group--moderation {
    padding-left: 0;
    border-left: none;
  }

  .comment-thread-head {
    gap: 12px;
    flex-wrap: wrap;
  }

  .comment-content,
  .reply-body,
  .reply-actions {
    padding-left: 0;
  }
}
</style>

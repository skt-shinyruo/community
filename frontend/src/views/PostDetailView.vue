<template>
  <div class="page reading">
    <UiCard>
      <UiPageHeader>
        <template #title>
           <UiButton v-if="true" variant="ghost" @click="$router.back()" style="padding-left: 0; margin-left: -8px; color: var(--muted)">
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
               <button class="vote-btn-d up" :class="{ active: post.liked }" @click="togglePostLike">
                 <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 19V5M5 12l7-7 7 7"/></svg>
               </button>
               <span class="vote-count-d">{{ post.likeCount || 0 }}</span>
               <button class="vote-btn-d down">
                 <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M19 12l-7 7-7-7"/></svg>
               </button>
             </div>

             <div style="flex: 1">
               <h1 style="font-weight: 800; font-size: 24px; line-height: 1.3; margin: 0 0 8px 0; color: var(--text-1)">
                 {{ post.title }}
               </h1>
               <div class="row muted" style="gap: 8px; flex-wrap: wrap; font-size: 13px">
                 <UiBadge v-if="post.type === 1" variant="accent">置顶</UiBadge>
                  <UiBadge v-if="post.status === 1" variant="success">加精</UiBadge>
                  <UiBadge v-if="post.status === 2" variant="danger">已删除</UiBadge>
                  <UiAvatar :src="postAuthor?.headerUrl || ''" :name="postAuthor?.username || ''" :size="20" />
                  <RouterLink :to="{ name: 'userProfile', params: { userId: String(post.userId) } }" style="font-weight: 600; color: var(--text-1)">
                    {{ postAuthor?.username || `user#${post.userId}` }}
                  </RouterLink>
                  <span>发布于 {{ formatTime(post.createTime) }}</span>
               </div>
             </div>
           </div>

           <div class="divider"></div>

           <div class="post-body" style="font-size: 16px; line-height: 1.8; color: var(--text-1)">{{ post.content }}</div>
        </div>

        <!-- Ops -->
        <div class="row" style="gap: 8px; flex-wrap: wrap; margin-top: 16px">
          <template v-if="authed && post.userId !== meUserId">
            <UiButton v-if="followStatus === false" @click="follow(true)" :disabled="actionLoading">关注作者</UiButton>
            <UiButton v-else-if="followStatus === true" variant="secondary" @click="follow(false)" :disabled="actionLoading">
              取关作者
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
        <UiEmpty v-if="comments.length === 0">暂无评论</UiEmpty>
        <div v-else class="stack" style="gap: 16px"> <!-- Increased gap for threads -->
          <div class="comment-thread" v-for="c in comments" :key="c.id" style="position: relative">
             <!-- Thread Line (Visual) -->
             <div class="thread-line" v-if="c._repliesExpanded && c._replies.length > 0"></div>

             <div class="row" style="justify-content: space-between; align-items: flex-start;">
               <div class="row" style="align-items: center; gap: 8px;">
                  <UiAvatar :src="c.user?.headerUrl || ''" :name="c.user?.username || ''" :size="28" />
                  <div class="stack" style="gap: 0">
                     <RouterLink :to="`/users/${c.userId}`" style="font-weight: 700; font-size: 13px">
                      {{ c.user?.username || `user#${c.userId}` }}
                    </RouterLink>
                    <span class="muted" style="font-size: 11px">{{ formatTime(c.createTime) }}</span>
                  </div>
               </div>
             </div>

             <div class="comment-content" style="padding-left: 36px">
                <div class="comment-body">{{ c.content }}</div>
                
                <div class="row muted" style="gap: 12px; margin-top: 8px; font-size: 12px">
                   <div style="cursor: pointer; display: flex; align-items: center; gap: 4px" @click="toggleCommentLike(c)">
                      <span :class="{ 'red-text': c.liked }">❤️</span> {{ c.likeCount || 0 }}
                   </div>
                   <div style="cursor: pointer" @click="startReply(c)">回复</div>
                   <div style="cursor: pointer" v-if="!c._repliesExpanded && (c.replyCount || 0) > 0" @click="toggleReplies(c)">
                      展开 {{ c.replyCount || 0 }} 条回复
                   </div>
                   <div style="cursor: pointer" v-if="c._repliesExpanded" @click="toggleReplies(c)">收起</div>
                </div>

                <!-- Reply Input -->
                <div v-if="c._replying" class="card flat" style="padding: 10px; margin-top: 10px; background: var(--surface-2)">
                   <UiTextarea v-model.trim="c._replyDraft" :rows="3" placeholder="回复..." />
                   <div class="row" style="justify-content: flex-end; margin-top: 8px; gap: 8px">
                      <UiButton variant="secondary" @click="cancelReply(c)" :disabled="c._replySubmitting">取消</UiButton>
                      <UiButton @click="submitReply(c)" :disabled="c._replySubmitting">提交</UiButton>
                   </div>
                </div>

                <!-- Replies List -->
                <div v-if="c._repliesExpanded" style="margin-top: 12px; display: grid; gap: 12px">
                   <div class="muted" v-if="c._repliesLoading">加载中...</div>
                   <div v-else-if="c._replies.length === 0" class="muted">暂无回复</div>
                   <div v-else v-for="r in c._replies" :key="r.id" class="reply-item">
                      <div class="row" style="align-items: center; gap: 8px">
                         <UiAvatar :src="r.user?.headerUrl || ''" :name="r.user?.username || ''" :size="20" />
                         <span style="font-weight: 600; font-size: 12px">{{ r.user?.username || `user#${r.userId}` }}</span>
                         <span class="muted" style="font-size: 12px">回复 {{ r.targetUser?.username || '楼主' }}</span>
                         <span class="muted" style="font-size: 12px">· {{ formatTime(r.createTime) }}</span>
                      </div>
                      <div class="comment-body" style="font-size: 13px; margin-top: 4px; padding-left: 28px">{{ r.content }}</div>
                      
                      <div class="row muted" style="gap: 12px; margin-top: 4px; padding-left: 28px; font-size: 12px">
                         <div style="cursor: pointer" @click="toggleReplyLike(c, r)">
                           <span :class="{ 'red-text': r.liked }">❤️</span> {{ r.likeCount || 0 }}
                         </div>
                         <div style="cursor: pointer" @click="startReply(c, r)">回复</div>
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
        <UiTextarea v-model.trim="newComment" placeholder="写下你的观点…" :rows="4" />
        <div v-if="commentError" class="error">{{ commentError }}</div>
      </div>
    </UiCard>

    <UiCard v-else>
      <UiEmpty>登录后可点赞、评论、回复与关注。</UiEmpty>
    </UiCard>

    <UiModalConfirm v-if="confirmOpen" :title="confirmTitle" :message="confirmMessage" @cancel="closeConfirm" @confirm="runConfirm" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'
import { formatTime } from '../utils/time'
import { getUserProfile } from '../api/services/userService'
import { setLike, getLikeCount, getLikeStatus, followUser, unfollowUser, getFollowStatus } from '../api/services/socialService'
import {
  getPostDetail,
  listComments as apiListComments,
  listReplies as apiListReplies,
  addComment as apiAddComment,
  moderationTop,
  moderationWonderful,
  moderationDelete
} from '../api/services/postService'

const emit = defineEmits(['trace'])

const route = useRoute()
const auth = useAuthStore()
const authed = computed(() => !!auth.accessToken)

const postId = computed(() => String(route.params.postId || ''))

const post = ref(null)
const postAuthor = ref(null)
const loading = ref(false)
const error = ref('')

const actionLoading = ref(false)

const meUserId = computed(() => Number(auth.userId || 0))
const followStatus = ref(null) // Boolean|null

const newComment = ref('')
const commenting = ref(false)
const commentError = ref('')

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
const confirmAction = ref('') // top|wonderful|delete

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
  } else {
    confirmTitle.value = '确认操作'
    confirmMessage.value = '是否继续？'
  }
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

async function loadComments() {
  commentsError.value = ''
  commentsLoading.value = true
  try {
    const resp = await apiListComments(postId.value, { page: commentsPage.value, size: commentsSize.value })
    emit('trace', resp?.traceId || '')
    comments.value = await Promise.all((resp?.data || []).map((c) => hydrateComment(c)))
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
  if (reply && reply.userId) {
    c._replyTargetId = Number(reply.userId)
    c._replyTargetUser = reply.user || null
  } else {
    c._replyTargetId = Number(c.userId || 0)
    c._replyTargetUser = c.user || null
  }
}

function cancelReply(c) {
  if (!c) return
  c._replying = false
  c._replyDraft = ''
  c._replyError = ''
  c._replySubmitting = false
  c._replyTargetId = 0
  c._replyTargetUser = null
}

async function submitReply(c) {
  if (!authed.value || !c) return
  c._replyError = ''
  if (!c._replyDraft) {
    c._replyError = '回复内容不能为空'
    return
  }
  c._replySubmitting = true
  try {
    const resp = await apiAddComment(postId.value, {
      content: c._replyDraft,
      entityType: 2,
      entityId: c.id,
      targetId: c._replyTargetId || undefined
    })
    emit('trace', resp?.traceId || '')
    c._replyDraft = ''
    c._replying = false
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
  if (!newComment.value) {
    commentError.value = '评论不能为空'
    return
  }
  commenting.value = true
  try {
    const resp = await apiAddComment(postId.value, { content: newComment.value })
    emit('trace', resp?.traceId || '')
    newComment.value = ''
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
  await loadPost()
  await loadComments()
  await loadFollowStatus()
}

watch(
  () => route.params.postId,
  () => {
    post.value = null
    postAuthor.value = null
    comments.value = []
    commentsPage.value = 0
    followStatus.value = null
    closeConfirm()
    reload()
  }
)

onMounted(reload)
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

.vote-btn-d {
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px;
  color: var(--muted);
  border-radius: 4px;
}
.vote-btn-d:hover {
  background: rgba(0,0,0,0.05);
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

.red-text {
  color: #ff453a;
}
</style>

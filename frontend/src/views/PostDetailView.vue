<template>
  <div class="page reading">
    <UiCard>
      <UiPageHeader>
        <template #title>帖子详情</template>
        <template #subtitle>postId={{ postId }}</template>
        <template #actions>
          <UiButton variant="secondary" @click="reload" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>

      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
      <div v-else-if="loading" class="muted" style="margin-top: 12px">加载中…</div>
      <UiEmpty v-else-if="!post" style="margin-top: 12px">暂无数据</UiEmpty>

      <div v-else class="stack" style="margin-top: 12px">
        <div class="stack" style="gap: 10px">
          <div class="row" style="gap: 8px; flex-wrap: wrap">
            <UiBadge v-if="post.type === 1" variant="accent">置顶</UiBadge>
            <UiBadge v-if="post.status === 1" variant="success">加精</UiBadge>
            <UiBadge v-if="post.status === 2" variant="danger">已删除</UiBadge>
            <div style="font-weight: 900; font-size: 18px; line-height: 1.35">{{ post.title }}</div>
          </div>

          <div class="row muted" style="gap: 8px; flex-wrap: wrap">
            <UiAvatar :src="postAuthor?.headerUrl || ''" :name="postAuthor?.username || ''" :size="26" />
            <RouterLink :to="{ name: 'userProfile', params: { userId: String(post.userId) } }">
              {{ postAuthor?.username || `user#${post.userId}` }}
            </RouterLink>
            <span>·</span>
            <span>评论 {{ post.commentCount }}</span>
            <span>·</span>
            <span>点赞 {{ post.likeCount }}</span>
            <span v-if="authed" class="muted">· {{ post.liked ? '已点赞' : '未点赞' }}</span>
          </div>

          <div class="muted" style="font-size: 12px">创建时间：{{ formatTime(post.createTime) }}</div>
          <div class="post-body">{{ post.content }}</div>
        </div>

        <div class="row" style="gap: 8px; flex-wrap: wrap">
          <UiButton v-if="authed" @click="togglePostLike" :disabled="actionLoading">
            {{ post.liked ? '取消点赞' : '点赞' }}
          </UiButton>

          <template v-if="authed && post.userId !== meUserId">
            <UiButton v-if="followStatus === false" @click="follow(true)" :disabled="actionLoading">关注作者</UiButton>
            <UiButton v-else-if="followStatus === true" variant="secondary" @click="follow(false)" :disabled="actionLoading">
              取关作者
            </UiButton>
            <UiButton v-else variant="secondary" disabled>关注状态查询中…</UiButton>
            <UiBadge v-if="followStatus !== null" :variant="followStatus ? 'success' : 'default'">关注：{{ followStatusText }}</UiBadge>
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
        <template #title>评论</template>
        <template #subtitle>支持点赞、回复与回复树</template>
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
        <div v-else class="stack" style="gap: 8px">
          <div class="card flat" v-for="c in comments" :key="c.id" style="padding: 12px">
            <div class="row" style="justify-content: space-between; align-items: flex-start; flex-wrap: wrap">
              <div class="row" style="align-items: center; gap: 8px; flex-wrap: wrap">
                <UiAvatar :src="c.user?.headerUrl || ''" :name="c.user?.username || ''" :size="26" />
                <RouterLink :to="`/users/${c.userId}`" style="font-weight: 800">
                  {{ c.user?.username || `user#${c.userId}` }}
                </RouterLink>
                <span class="muted" style="font-size: 12px">{{ formatTime(c.createTime) }}</span>
              </div>
              <div class="row" style="gap: 8px; flex-wrap: wrap">
                <UiButton
                  variant="secondary"
                  v-if="authed"
                  @click="toggleCommentLike(c)"
                  :disabled="commentsLoading || c._likeLoading"
                >
                  {{ c.liked ? '取消点赞' : '点赞' }} · {{ c.likeCount }}
                </UiButton>
                <UiButton variant="secondary" v-if="authed" @click="startReply(c)" :disabled="commentsLoading">回复</UiButton>
                <UiButton variant="secondary" @click="toggleReplies(c)" :disabled="c._repliesLoading">
                  {{ c._repliesExpanded ? '收起回复' : '查看回复' }}
                </UiButton>
              </div>
            </div>

            <div class="comment-body">{{ c.content }}</div>

            <div v-if="c._replying" class="card flat" style="padding: 10px; margin-top: 10px">
              <div class="stack" style="gap: 10px">
                <div class="muted">
                  回复
                  <span v-if="c._replyTargetUser"> @{{ c._replyTargetUser.username || `user#${c._replyTargetId}` }}</span>
                </div>
                <UiTextarea v-model.trim="c._replyDraft" :rows="3" placeholder="写下回复…" />
                <div class="row" style="justify-content: space-between; flex-wrap: wrap">
                  <div class="error" v-if="c._replyError">{{ c._replyError }}</div>
                  <div class="row">
                    <UiButton variant="secondary" @click="cancelReply(c)" :disabled="c._replySubmitting">取消</UiButton>
                    <UiButton @click="submitReply(c)" :disabled="c._replySubmitting">
                      {{ c._replySubmitting ? '提交中…' : '提交回复' }}
                    </UiButton>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="c._repliesExpanded" class="card flat" style="padding: 10px; margin-top: 10px">
              <div class="stack" style="gap: 10px">
                <div class="row" style="justify-content: space-between; flex-wrap: wrap">
                  <div class="muted">回复列表</div>
                  <UiButton variant="secondary" @click="reloadReplies(c)" :disabled="c._repliesLoading">
                    {{ c._repliesLoading ? '加载中…' : '刷新' }}
                  </UiButton>
                </div>

                <UiPagination
                  :page="c._repliesPage"
                  :has-next="repliesHasNext(c)"
                  @prev="prevRepliesPage(c)"
                  @next="nextRepliesPage(c)"
                />

                <div v-if="c._repliesError" class="error">{{ c._repliesError }}</div>
                <UiEmpty v-if="c._replies.length === 0">暂无回复</UiEmpty>

                <div v-else class="stack" style="gap: 8px">
                  <div class="card flat" v-for="r in c._replies" :key="r.id" style="padding: 10px">
                    <div class="row" style="justify-content: space-between; align-items: flex-start; flex-wrap: wrap">
                      <div class="row" style="align-items: center; gap: 8px; flex-wrap: wrap">
                        <UiAvatar :src="r.user?.headerUrl || ''" :name="r.user?.username || ''" :size="24" />
                        <RouterLink :to="`/users/${r.userId}`" style="font-weight: 800">
                          {{ r.user?.username || `user#${r.userId}` }}
                        </RouterLink>
                        <span class="muted">回复</span>
                        <RouterLink v-if="r.targetUserId" :to="`/users/${r.targetUserId}`" class="muted">
                          @{{ r.targetUser?.username || `user#${r.targetUserId}` }}
                        </RouterLink>
                        <span class="muted" style="font-size: 12px">{{ formatTime(r.createTime) }}</span>
                      </div>
                      <div class="row" style="gap: 8px; flex-wrap: wrap">
                        <UiButton variant="secondary" v-if="authed" @click="toggleReplyLike(c, r)" :disabled="r._likeLoading">
                          {{ r.liked ? '取消点赞' : '点赞' }} · {{ r.likeCount }}
                        </UiButton>
                        <UiButton variant="secondary" v-if="authed" @click="startReply(c, r)">回复</UiButton>
                      </div>
                    </div>
                    <div class="comment-body" style="margin-top: 6px">{{ r.content }}</div>
                  </div>
                </div>
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

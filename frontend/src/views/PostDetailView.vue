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
      <UiState v-else-if="!post" class="post-detail-state">暂无数据</UiState>

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
                    v-if="post.categoryId"
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
            <PostBlockRenderer :blocks="post.blocks" />
          </div>

          <PostDetailActions
            :authed="authed"
            :post="post"
            :action-loading="actionLoading"
            :can-edit-post="canEditPost"
            :is-owner="sameOpaqueId(post.userId, meUserId)"
            :follow-status="followStatus"
            :is-blocked-author="isBlockedAuthor"
            :can-moderate="auth.isAdminOrModerator"
            @toggle-bookmark="toggleBookmark"
            @open-edit-post="openEditPost"
            @confirm-author-delete="confirmAuthorDelete"
            @follow="follow"
            @open-report-post="openReportPost"
            @toggle-block-author="toggleBlockAuthor"
            @confirm-moderation="confirmModeration"
          />
        </article>

        <PostDetailComments>
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
            <UiState v-if="!commentsLoading && comments.length === 0 && !commentsError">暂无评论</UiState>
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

                        <UiBadge v-if="sameOpaqueId(c.userId, post.userId)" variant="secondary" class="comment-op-badge">OP</UiBadge>
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

                        <UiBadge v-if="sameOpaqueId(r.userId, post.userId)" variant="secondary" class="comment-op-badge">OP</UiBadge>
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
        </PostDetailComments>
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
      <UiState>登录后可点赞、评论、回复与关注。</UiState>
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
      :target-id="post?.id || ''"
      @close="reportOpen = false"
      @submitted="reportOpen = false"
    />

    <EditContentModal
      v-if="editOpen"
      :mode="editMode"
      :loading="actionLoading"
      :initial-title="editInitialTitle"
      :initial-content="editInitialContent"
      :initial-blocks="editInitialBlocks"
      @close="closeEdit"
      @submit="submitEdit"
    />
  </div>
</template>

<script setup>
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiUserCard from '../components/ui/UiUserCard.vue'
import UiMarkdown from '../components/ui/UiMarkdown.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiState from '../components/ui/UiState.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiIconButton from '../components/ui/UiIconButton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import UiRoleBadge from '../components/ui/UiRoleBadge.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'
import ReportModal from '../components/modals/ReportModal.vue'
import EditContentModal from '../components/modals/EditContentModal.vue'
import PostBlockRenderer from '../components/posts/PostBlockRenderer.vue'
import PostDetailActions from './post-detail/PostDetailActions.vue'
import PostDetailComments from './post-detail/PostDetailComments.vue'
import { usePostDetailLoader } from './post-detail/usePostDetailLoader'

const emit = defineEmits(['trace'])
const {
  auth,
  authed,
  categoryLabel,
  postId,
  post,
  postAuthor,
  loading,
  error,
  actionLoading,
  reportOpen,
  meUserId,
  followStatus,
  isBlockedAuthor,
  canEditPost,
  newComment,
  commenting,
  commentError,
  setNewComment,
  setReplyDraft,
  commentAnchorId,
  replyAnchorId,
  clearReplyQuote,
  followStatusText,
  comments,
  commentsPage,
  commentsHasNext,
  commentsLoading,
  commentsError,
  confirmOpen,
  confirmTitle,
  confirmMessage,
  confirmVariant,
  confirmOkText,
  closeConfirm,
  confirmModeration,
  confirmAuthorDelete,
  runConfirm,
  loadPost,
  loadComments,
  reloadComments,
  nextCommentsPage,
  prevCommentsPage,
  reload,
  togglePostLike,
  follow,
  isBlockedUser,
  toggleBookmark,
  openReportPost,
  toggleBlockAuthor,
  canEditComment,
  editOpen,
  editMode,
  editInitialTitle,
  editInitialContent,
  editInitialBlocks,
  closeEdit,
  openEditPost,
  openEditComment,
  submitEdit,
  maybeScrollFromRoute,
  repliesHasNext,
  loadReplies,
  toggleReplies,
  reloadReplies,
  nextRepliesPage,
  prevRepliesPage,
  startReply,
  cancelReply,
  submitReply,
  toggleCommentLike,
  toggleReplyLike,
  addComment,
  sameOpaqueId,
  formatTime
} = usePostDetailLoader(emit)
</script>

<style scoped src="./post-detail/PostDetailView.css"></style>

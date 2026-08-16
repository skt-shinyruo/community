<template>
  <section class="post-comments-card">
    <div class="post-comments-head">
      <div class="post-comments-head-copy">
        <div class="post-comments-title">回复 {{ post?.commentCount || 0 }}</div>
        <div class="post-comments-meta">按回复关系继续往下读</div>
      </div>
      <UiButton variant="secondary" :disabled="discussion.loading" @click="discussion.reload">
        {{ discussion.loading ? '加载中…' : '刷新' }}
      </UiButton>
    </div>

    <div class="post-comments-toolbar">
      <UiPagination
        :page="discussion.page"
        :has-next="discussion.hasNext"
        :disabled="discussion.loading"
        @prev="discussion.prevPage"
        @next="discussion.nextPage"
      />
    </div>

    <div v-if="discussion.error" class="error post-comments-error">{{ discussion.error }}</div>

    <div class="post-comments-body">
      <UiState v-if="!discussion.loading && discussion.comments.length === 0 && !discussion.error">暂无评论</UiState>
      <div v-else-if="discussion.loading && discussion.comments.length === 0" class="muted">加载中…</div>
      <div v-else class="post-comment-thread-list">
        <div
          v-for="comment in discussion.comments"
          :id="discussion.commentAnchorId(comment.id)"
          :key="comment.id"
          class="comment-thread"
        >
          <div v-if="comment._repliesExpanded && comment._replies.length > 0" class="thread-line"></div>

          <div class="comment-thread-head">
            <div class="comment-author">
              <UiUserCard :user="comment.user">
                <UiAvatar class="comment-author-avatar" :src="comment.user?.headerUrl || ''" :name="comment.user?.username || ''" :size="28" />
              </UiUserCard>

              <div class="comment-author-stack">
                <div class="comment-author-line">
                  <UiUserCard :user="comment.user">
                    <router-link :to="`/users/${comment.userId}`" class="comment-author-link">
                      {{ comment.user?.username || `成员 ${comment.userId}` }}
                    </router-link>
                  </UiUserCard>
                  <UiBadge v-if="sameOpaqueId(comment.userId, post?.userId)" variant="secondary" class="comment-op-badge">OP</UiBadge>
                  <UiRoleBadge :user="comment.user" />
                </div>
                <span class="muted comment-author-meta">
                  {{ formatTime(comment.createTime) }}
                  <span v-if="Number(comment.editCount || 0) > 0" :title="comment.updateTime ? formatTime(comment.updateTime) : ''">· 已编辑</span>
                </span>
              </div>
            </div>
            <div class="comment-thread-index">评论 {{ comment.id }}</div>
          </div>

          <div class="comment-content">
            <div v-if="discussion.isBlockedUser(comment.userId)" class="muted blocked-placeholder">已屏蔽该用户内容</div>
            <UiMarkdown v-else variant="compact" :content="comment.content" />

            <div v-if="!discussion.isBlockedUser(comment.userId)" class="row muted comment-actions">
              <UiButton
                class="comment-action"
                variant="ghost"
                :aria-label="comment.liked ? '取消点赞评论' : '点赞评论'"
                @click="discussion.toggleCommentLike(comment)"
              >
                <span aria-hidden="true" :class="{ 'red-text': comment.liked }">❤️</span>
                <span>{{ comment.likeCount || 0 }}</span>
              </UiButton>
              <UiButton class="comment-action" variant="ghost" aria-label="回复评论" @click="discussion.startReply(comment)">回复</UiButton>
              <UiButton
                v-if="commentEditing.canEdit(comment)"
                class="comment-action"
                variant="ghost"
                aria-label="编辑评论"
                @click="commentEditing.open(comment)"
              >
                编辑
              </UiButton>
              <UiButton
                v-if="(comment.replyCount || 0) > 0 || comment._repliesExpanded"
                class="comment-action"
                variant="ghost"
                :aria-label="comment._repliesExpanded ? '收起回复' : `展开 ${comment.replyCount || 0} 条回复`"
                @click="discussion.toggleReplies(comment)"
              >
                {{ comment._repliesExpanded ? '收起回复' : `展开 ${comment.replyCount || 0} 条回复` }}
              </UiButton>
            </div>

            <div v-if="!discussion.isBlockedUser(comment.userId) && comment._replying" class="card flat reply-editor">
              <div v-if="comment._replyQuote" class="reply-quote">
                <div class="reply-quote-head">
                  <div class="muted reply-quote-label">
                    引用 {{ comment._replyQuote?.username || `成员 ${comment._replyQuote?.userId || ''}` }}
                  </div>
                  <UiIconButton size="sm" aria-label="取消引用" title="取消引用" @click="discussion.clearReplyQuote(comment)">×</UiIconButton>
                </div>
                <div class="reply-quote-content">{{ comment._replyQuote?.preview || '' }}</div>
              </div>

              <textarea
                :value="comment._replyDraft"
                :rows="3"
                placeholder="回复…（支持 Markdown）"
                class="input multiline"
                @input="discussion.setReplyDraft(comment, $event.target.value.trim())"
              />
              <div v-if="comment._replyError" class="error reply-editor-error">{{ comment._replyError }}</div>
              <div class="reply-editor-actions">
                <UiButton variant="secondary" :disabled="comment._replySubmitting" @click="discussion.cancelReply(comment)">收起</UiButton>
                <UiButton :disabled="comment._replySubmitting" @click="discussion.submitReply(comment)">提交</UiButton>
              </div>
            </div>

            <div v-if="!discussion.isBlockedUser(comment.userId) && comment._repliesExpanded" class="reply-list">
              <div v-if="comment._repliesLoading" class="muted">加载中…</div>
              <div v-else-if="comment._replies.length === 0" class="muted">暂无回复</div>
              <div
                v-for="reply in comment._replies"
                v-else
                :id="discussion.replyAnchorId(reply.id)"
                :key="reply.id"
                class="reply-item"
              >
                <div class="reply-item-head">
                  <UiUserCard :user="reply.user">
                    <UiAvatar :src="reply.user?.headerUrl || ''" :name="reply.user?.username || ''" :size="20" />
                  </UiUserCard>
                  <span class="reply-author-name">{{ reply.user?.username || `成员 ${reply.userId}` }}</span>
                  <UiBadge v-if="sameOpaqueId(reply.userId, post?.userId)" variant="secondary" class="comment-op-badge">OP</UiBadge>
                  <UiRoleBadge :user="reply.user" />
                  <span class="muted reply-target">回复 {{ reply.targetUser?.username || '楼主' }}</span>
                  <span class="muted reply-meta">
                    · {{ formatTime(reply.createTime) }}
                    <span v-if="Number(reply.editCount || 0) > 0" :title="reply.updateTime ? formatTime(reply.updateTime) : ''">· 已编辑</span>
                  </span>
                </div>

                <div class="reply-body">
                  <div v-if="discussion.isBlockedUser(reply.userId)" class="muted blocked-placeholder">已屏蔽该用户内容</div>
                  <UiMarkdown v-else variant="compact" :content="reply.content" />
                </div>

                <div v-if="!discussion.isBlockedUser(reply.userId)" class="row muted comment-actions reply-actions">
                  <UiButton
                    class="comment-action"
                    variant="ghost"
                    :aria-label="reply.liked ? '取消点赞回复' : '点赞回复'"
                    @click="discussion.toggleReplyLike(comment, reply)"
                  >
                    <span aria-hidden="true" :class="{ 'red-text': reply.liked }">❤️</span>
                    <span>{{ reply.likeCount || 0 }}</span>
                  </UiButton>
                  <UiButton class="comment-action" variant="ghost" aria-label="回复该回复" @click="discussion.startReply(comment, reply)">回复</UiButton>
                  <UiButton
                    v-if="commentEditing.canEdit(reply)"
                    class="comment-action"
                    variant="ghost"
                    aria-label="编辑回复"
                    @click="commentEditing.open(reply)"
                  >
                    编辑
                  </UiButton>
                </div>
              </div>

              <UiPagination
                v-if="discussion.repliesHasNext(comment) || comment._repliesPage > 0"
                class="reply-pagination"
                :page="comment._repliesPage"
                :has-next="discussion.repliesHasNext(comment)"
                :disabled="comment._repliesLoading"
                @prev="discussion.prevRepliesPage(comment)"
                @next="discussion.nextRepliesPage(comment)"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import UiAvatar from '../../components/ui/UiAvatar.vue'
import UiBadge from '../../components/ui/UiBadge.vue'
import UiButton from '../../components/ui/UiButton.vue'
import UiIconButton from '../../components/ui/UiIconButton.vue'
import UiMarkdown from '../../components/ui/UiMarkdown.vue'
import UiPagination from '../../components/ui/UiPagination.vue'
import UiRoleBadge from '../../components/ui/UiRoleBadge.vue'
import UiState from '../../components/ui/UiState.vue'
import UiUserCard from '../../components/ui/UiUserCard.vue'
import { formatTime } from '../../utils/time'
import { sameOpaqueId } from '../../utils/opaqueId'

defineProps({
  post: {
    type: Object,
    default: null
  },
  discussion: {
    type: Object,
    required: true
  },
  commentEditing: {
    type: Object,
    required: true
  }
})
</script>

<style scoped src="./PostDetailComments.css"></style>

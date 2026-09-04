<template>
  <section class="post-comments-card" aria-label="评论区">
    <div class="post-comments-head">
      <h2 class="post-comments-title">评论 {{ post?.commentCount || 0 }}</h2>
      <UiButton variant="secondary" :disabled="discussion.loading" @click="discussion.reload">刷新</UiButton>
    </div>

    <div v-if="discussion.loading && discussion.comments.length === 0" class="post-comments-skeletons">
      <UiSkeleton variant="list" :rows="3" />
    </div>

    <UiState v-else-if="discussion.error && discussion.comments.length === 0" variant="error" :title="discussion.error">
      <template #description>评论加载失败，可以重试或稍后再来。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="discussion.loading" @click="discussion.reload">重试</UiButton>
      </template>
    </UiState>

    <UiState v-else-if="discussion.comments.length === 0">
      暂无评论
      <template #description>写下第一条评论，推动讨论继续。</template>
    </UiState>

    <template v-else>
      <div class="post-comment-thread-list">
        <div
          v-for="comment in discussion.comments"
          :id="discussion.commentAnchorId(comment.id)"
          :key="comment.id"
          class="comment-thread"
        >
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
                <span class="comment-author-meta">
                  {{ formatTime(comment.createTime) }}
                  <span v-if="Number(comment.editCount || 0) > 0" :title="comment.updateTime ? formatTime(comment.updateTime) : ''">· 已编辑</span>
                </span>
              </div>
            </div>
          </div>

          <div class="comment-content">
            <div v-if="discussion.isBlockedUser(comment.userId)" class="blocked-placeholder">已屏蔽该用户内容</div>
            <template v-else>
              <UiMarkdown variant="compact" :content="comment.content" />

              <div class="comment-actions">
                <UiButton
                  class="comment-action"
                  variant="ghost"
                  :aria-label="comment.ui.like.liked ? '取消点赞评论' : '点赞评论'"
                  :disabled="comment.ui.like.loading"
                  @click="discussion.toggleCommentLike(comment)"
                >
                  <Heart :size="13" aria-hidden="true" :class="{ 'comment-action-like--active': comment.ui.like.liked }" />
                  <span>{{ comment.ui.like.count || 0 }}</span>
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
                  v-if="discussion.canReport(comment)"
                  class="comment-action"
                  variant="ghost"
                  aria-label="举报评论"
                  @click="openReport(comment)"
                >
                  举报
                </UiButton>
                <UiButton
                  v-if="(comment.replyCount || 0) > 0 || comment.ui.replyList.expanded"
                  class="comment-action comment-action--replies"
                  variant="ghost"
                  :aria-expanded="comment.ui.replyList.expanded ? 'true' : 'false'"
                  :aria-label="comment.ui.replyList.expanded ? '收起回复' : `展开 ${comment.replyCount || 0} 条回复`"
                  @click="discussion.toggleReplies(comment)"
                >
                  {{ comment.ui.replyList.expanded ? '收起回复' : `展开 ${comment.replyCount || 0} 条回复` }}
                </UiButton>
              </div>
              <div v-if="comment.ui.like.error" class="error comment-inline-error">{{ comment.ui.like.error }}</div>
            </template>

            <div v-if="!discussion.isBlockedUser(comment.userId) && comment.ui.replyEditor.open" class="reply-editor">
              <div v-if="comment.ui.replyEditor.quote" class="reply-quote">
                <div class="reply-quote-head">
                  <div class="reply-quote-label">
                    引用 {{ comment.ui.replyEditor.quote?.username || `成员 ${comment.ui.replyEditor.quote?.userId || ''}` }}
                  </div>
                  <UiIconButton size="sm" aria-label="取消引用" title="取消引用" @click="discussion.clearReplyQuote(comment)">×</UiIconButton>
                </div>
                <div class="reply-quote-content">{{ comment.ui.replyEditor.quote?.preview || '' }}</div>
              </div>

              <UiTextarea
                :model-value="comment.ui.replyEditor.draft"
                :rows="3"
                placeholder="回复…（支持 Markdown）"
                aria-label="回复内容"
                :disabled="comment.ui.replyEditor.submitting"
                @update:modelValue="discussion.setReplyDraft(comment, $event)"
              />
              <div v-if="comment.ui.replyEditor.error" class="error reply-editor-error" role="alert">{{ comment.ui.replyEditor.error }}</div>
              <div class="reply-editor-actions">
                <UiButton variant="secondary" :disabled="comment.ui.replyEditor.submitting" @click="discussion.cancelReply(comment)">收起</UiButton>
                <UiButton :disabled="comment.ui.replyEditor.submitting" @click="discussion.submitReply(comment)">提交</UiButton>
              </div>
            </div>

            <div v-if="!discussion.isBlockedUser(comment.userId) && comment.ui.replyList.expanded" class="reply-list">
              <UiSkeleton v-if="comment.ui.replyList.loading && comment.ui.replyList.items.length === 0" variant="list" :rows="2" />

              <div v-else-if="comment.ui.replyList.error && comment.ui.replyList.items.length === 0" class="reply-list-error">
                <span class="error">{{ comment.ui.replyList.error }}</span>
                <UiButton variant="secondary" :disabled="comment.ui.replyList.loading" @click="discussion.reloadReplies(comment)">重试</UiButton>
              </div>

              <div v-else-if="comment.ui.replyList.items.length === 0" class="reply-list-empty">暂无回复</div>

              <div
                v-for="reply in comment.ui.replyList.items"
                :id="discussion.replyAnchorId(reply.id)"
                :key="reply.id"
                class="reply-item"
              >
                <div class="reply-item-head">
                  <UiUserCard :user="reply.user">
                    <UiAvatar :src="reply.user?.headerUrl || ''" :name="reply.user?.username || ''" :size="20" />
                  </UiUserCard>
                  <router-link :to="`/users/${reply.userId}`" class="reply-author-name">
                    {{ reply.user?.username || `成员 ${reply.userId}` }}
                  </router-link>
                  <UiBadge v-if="sameOpaqueId(reply.userId, post?.userId)" variant="secondary" class="comment-op-badge">OP</UiBadge>
                  <UiRoleBadge :user="reply.user" />
                  <span class="reply-target">回复 {{ reply.targetUser?.username || '楼主' }}</span>
                  <span class="reply-meta">
                    · {{ formatTime(reply.createTime) }}
                    <span v-if="Number(reply.editCount || 0) > 0" :title="reply.updateTime ? formatTime(reply.updateTime) : ''">· 已编辑</span>
                  </span>
                </div>

                <div class="reply-body">
                  <div v-if="discussion.isBlockedUser(reply.userId)" class="blocked-placeholder">已屏蔽该用户内容</div>
                  <UiMarkdown v-else variant="compact" :content="reply.content" />
                </div>

                <div v-if="!discussion.isBlockedUser(reply.userId)" class="comment-actions reply-actions">
                  <UiButton
                    class="comment-action"
                    variant="ghost"
                    :aria-label="reply.ui.like.liked ? '取消点赞回复' : '点赞回复'"
                    :disabled="reply.ui.like.loading"
                    @click="discussion.toggleReplyLike(comment, reply)"
                  >
                    <Heart :size="13" aria-hidden="true" :class="{ 'comment-action-like--active': reply.ui.like.liked }" />
                    <span>{{ reply.ui.like.count || 0 }}</span>
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
                  <UiButton
                    v-if="discussion.canReport(reply)"
                    class="comment-action"
                    variant="ghost"
                    aria-label="举报回复"
                    @click="openReport(reply)"
                  >
                    举报
                  </UiButton>
                </div>
                <div v-if="reply.ui.like.error" class="error comment-inline-error">{{ reply.ui.like.error }}</div>
              </div>

              <div v-if="comment.ui.replyList.error && comment.ui.replyList.items.length > 0" class="error reply-tail-error" role="alert">
                {{ comment.ui.replyList.error }}
              </div>
              <div v-if="discussion.repliesHasNext(comment) || (comment.ui.replyList.loading && comment.ui.replyList.items.length > 0)" class="reply-load-more">
                <UiButton v-if="comment.ui.replyList.loading" variant="ghost" disabled>
                  <LoaderCircle :size="14" aria-hidden="true" class="load-more-spinner" />
                  正在加载…
                </UiButton>
                <UiButton v-else variant="ghost" @click="discussion.loadMoreReplies(comment)">加载更多回复</UiButton>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="discussion.error" class="error post-comments-tail-error" role="alert">{{ discussion.error }}</div>
      <div class="post-comments-load-more" v-if="discussion.hasNext || discussion.loading">
        <UiButton v-if="discussion.loading" variant="ghost" disabled>
          <LoaderCircle :size="14" aria-hidden="true" class="load-more-spinner" />
          正在加载…
        </UiButton>
        <UiButton v-else variant="secondary" class="post-comments-load-more-btn" @click="discussion.loadMore">加载更多评论</UiButton>
      </div>
      <div v-if="!discussion.hasNext && discussion.comments.length > 0" class="post-comments-end-note">
        没有更多评论了
      </div>
    </template>

    <ReportModal
      v-if="reportTarget"
      target-type="comment"
      :target-id="reportTarget.targetId"
      @close="closeReport"
      @submitted="closeReport"
    />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { Heart, LoaderCircle } from 'lucide-vue-next'
import UiAvatar from '../../components/ui/UiAvatar.vue'
import UiBadge from '../../components/ui/UiBadge.vue'
import UiButton from '../../components/ui/UiButton.vue'
import UiIconButton from '../../components/ui/UiIconButton.vue'
import UiMarkdown from '../../components/ui/UiMarkdown.vue'
import UiRoleBadge from '../../components/ui/UiRoleBadge.vue'
import UiSkeleton from '../../components/ui/UiSkeleton.vue'
import UiState from '../../components/ui/UiState.vue'
import UiTextarea from '../../components/ui/UiTextarea.vue'
import UiUserCard from '../../components/ui/UiUserCard.vue'
import ReportModal from '../../components/modals/ReportModal.vue'
import { formatTime } from '../../utils/time'
import { sameOpaqueId } from '../../utils/opaqueId'

const props = defineProps({
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

// 评论/回复举报复用 ReportModal（targetType=comment）；提交结果的 toast 由 ReportModal 负责。
const reportTarget = ref(null)

function openReport(entry) {
  if (!props.discussion.canReport(entry)) return
  reportTarget.value = { targetId: entry?.id || '' }
}

function closeReport() {
  reportTarget.value = null
}
</script>

<style scoped src="./PostDetailComments.css"></style>

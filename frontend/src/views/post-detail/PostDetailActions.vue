<template>
  <div class="post-article-actions">
    <div v-if="authed" class="post-article-action-group">
      <UiButton variant="secondary" :disabled="actionLoading" @click="$emit('toggle-bookmark')">
        {{ post?.bookmarked ? '已收藏' : '收藏' }}
      </UiButton>
    </div>

    <div v-if="authed && isOwner" class="post-article-action-group">
      <UiButton
        variant="secondary"
        :disabled="actionLoading || !canEditPost"
        :title="canEditPost ? '' : '仅发布后 24 小时内可编辑'"
        @click="$emit('open-edit-post')"
      >
        编辑
      </UiButton>
      <UiButton
        variant="dangerSecondary"
        :disabled="actionLoading || post?.status === 2"
        @click="$emit('confirm-author-delete')"
      >
        {{ post?.status === 2 ? '已删除' : '删除' }}
      </UiButton>
    </div>

    <div v-if="authed && !isOwner" class="post-article-action-group">
      <UiButton v-if="followStatus === false" :disabled="actionLoading" @click="$emit('follow', true)">关注作者</UiButton>
      <UiButton v-else-if="followStatus === true" variant="secondary" :disabled="actionLoading" @click="$emit('follow', false)">
        取关作者
      </UiButton>
      <UiButton variant="secondary" :disabled="actionLoading" @click="$emit('open-report-post')">举报</UiButton>
      <UiButton :variant="isBlockedAuthor ? 'dangerSecondary' : 'secondary'" :disabled="actionLoading" @click="$emit('toggle-block-author')">
        {{ isBlockedAuthor ? '已屏蔽' : '屏蔽' }}
      </UiButton>
    </div>

    <div v-if="authed && canModerate" class="post-article-action-group post-article-action-group--moderation">
      <UiButton variant="secondary" :disabled="actionLoading || post?.type === 1" @click="$emit('confirm-moderation', 'top')">
        {{ post?.type === 1 ? '已置顶' : '置顶' }}
      </UiButton>
      <UiButton variant="secondary" :disabled="actionLoading || post?.status === 1" @click="$emit('confirm-moderation', 'wonderful')">
        {{ post?.status === 1 ? '已加精' : '加精' }}
      </UiButton>
      <UiButton variant="dangerSecondary" :disabled="actionLoading || post?.status === 2" @click="$emit('confirm-moderation', 'delete')">
        {{ post?.status === 2 ? '已删除' : '删除' }}
      </UiButton>
    </div>
  </div>
</template>

<script setup>
import UiButton from '../../components/ui/UiButton.vue'

defineProps({
  authed: Boolean,
  post: {
    type: Object,
    default: null
  },
  actionLoading: Boolean,
  canEditPost: Boolean,
  isOwner: Boolean,
  followStatus: {
    type: null,
    default: null
  },
  isBlockedAuthor: Boolean,
  canModerate: Boolean
})

defineEmits([
  'toggle-bookmark',
  'open-edit-post',
  'confirm-author-delete',
  'follow',
  'open-report-post',
  'toggle-block-author',
  'confirm-moderation'
])
</script>

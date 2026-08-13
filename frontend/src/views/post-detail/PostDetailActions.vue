<template>
  <div class="post-article-actions">
    <div v-if="authed" class="post-article-action-group">
      <UiButton variant="secondary" :disabled="actions.loading" @click="actions.toggleBookmark">
        {{ post?.bookmarked ? '已收藏' : '收藏' }}
      </UiButton>
    </div>

    <div v-if="authed && actions.isOwner" class="post-article-action-group">
      <UiButton
        variant="secondary"
        :disabled="actions.loading || !actions.canEditPost"
        :title="actions.canEditPost ? '' : '仅发布后 24 小时内可编辑'"
        @click="actions.openPostEditor"
      >
        编辑
      </UiButton>
      <UiButton
        variant="dangerSecondary"
        :disabled="actions.loading || post?.status === 2"
        @click="actions.confirmAuthorDelete"
      >
        {{ post?.status === 2 ? '已删除' : '删除' }}
      </UiButton>
    </div>

    <div v-if="authed && !actions.isOwner" class="post-article-action-group">
      <UiButton v-if="actions.followStatus === false" :disabled="actions.loading" @click="actions.follow(true)">关注作者</UiButton>
      <UiButton v-else-if="actions.followStatus === true" variant="secondary" :disabled="actions.loading" @click="actions.follow(false)">
        取关作者
      </UiButton>
      <UiButton variant="secondary" :disabled="actions.loading" @click="actions.report.openDialog">举报</UiButton>
      <UiButton :variant="actions.isBlockedAuthor ? 'dangerSecondary' : 'secondary'" :disabled="actions.loading" @click="actions.toggleBlockAuthor">
        {{ actions.isBlockedAuthor ? '已屏蔽' : '屏蔽' }}
      </UiButton>
    </div>

    <div v-if="authed && actions.canModerate" class="post-article-action-group post-article-action-group--moderation">
      <UiButton variant="secondary" :disabled="actions.loading || post?.type === 1" @click="actions.confirmModeration('top')">
        {{ post?.type === 1 ? '已置顶' : '置顶' }}
      </UiButton>
      <UiButton variant="secondary" :disabled="actions.loading || post?.status === 1" @click="actions.confirmModeration('wonderful')">
        {{ post?.status === 1 ? '已加精' : '加精' }}
      </UiButton>
      <UiButton variant="dangerSecondary" :disabled="actions.loading || post?.status === 2" @click="actions.confirmModeration('delete')">
        {{ post?.status === 2 ? '已删除' : '删除' }}
      </UiButton>
    </div>
  </div>

  <UiModalConfirm
    v-if="actions.confirmation.open"
    :title="actions.confirmation.title"
    :message="actions.confirmation.message"
    :confirm-text="actions.confirmation.okText"
    :confirm-variant="actions.confirmation.variant"
    @cancel="actions.confirmation.close"
    @confirm="actions.confirmation.run"
  />

  <ReportModal
    v-if="actions.report.open"
    target-type="post"
    :target-id="post?.id || ''"
    @close="actions.report.close"
    @submitted="actions.report.close"
  />

  <EditContentModal
    v-if="actions.editor.open"
    :mode="actions.editor.mode"
    :loading="actions.loading"
    :initial-title="actions.editor.initialTitle"
    :initial-content="actions.editor.initialContent"
    :initial-blocks="actions.editor.initialBlocks"
    @close="actions.editor.close"
    @submit="actions.editor.submit"
  />
</template>

<script setup>
import UiButton from '../../components/ui/UiButton.vue'
import UiModalConfirm from '../../components/ui/UiModalConfirm.vue'
import ReportModal from '../../components/modals/ReportModal.vue'
import EditContentModal from '../../components/modals/EditContentModal.vue'

defineProps({
  authed: Boolean,
  post: {
    type: Object,
    default: null
  },
  actions: {
    type: Object,
    required: true
  }
})
</script>

<style scoped src="./PostDetailActions.css"></style>

<template>
  <div class="post-article-actions">
    <UiButton v-if="authed" variant="secondary" :disabled="actions.loading" @click="actions.toggleBookmark">
      <Bookmark :size="14" aria-hidden="true" />
      {{ post?.bookmarked ? '已收藏' : '收藏' }}
    </UiButton>

    <UiButton variant="secondary" :disabled="actions.sharing" @click="actions.sharePost">
      <Share2 :size="14" aria-hidden="true" />
      分享
    </UiButton>

    <template v-if="authed && actions.isOwner">
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
    </template>

    <UiDropdown
      v-if="moreItems.length > 0"
      class="post-article-more"
      :items="moreItems"
      label="更多"
      :disabled="actions.loading"
      @select="onMoreSelect"
    >
      更多
    </UiDropdown>
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
import { computed } from 'vue'
import { Bookmark, Share2 } from 'lucide-vue-next'
import UiButton from '../../components/ui/UiButton.vue'
import UiDropdown from '../../components/ui/UiDropdown.vue'
import UiModalConfirm from '../../components/ui/UiModalConfirm.vue'
import ReportModal from '../../components/modals/ReportModal.vue'
import EditContentModal from '../../components/modals/EditContentModal.vue'

const props = defineProps({
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

// 低频动作进入“更多”菜单：关注 / 举报 / 屏蔽与治理动作（规范 6.4）。
const moreItems = computed(() => {
  if (!props.authed) return []
  const actions = props.actions
  const post = props.post
  const items = []
  if (!actions.isOwner) {
    items.push({
      value: 'follow',
      label: actions.followStatus === true ? '取关作者' : '关注作者',
      disabled: actions.followStatus === null
    })
    items.push({ value: 'report', label: '举报帖子' })
    items.push({
      value: 'block',
      label: actions.isBlockedAuthor ? '解除屏蔽作者' : '屏蔽作者',
      danger: !actions.isBlockedAuthor
    })
  }
  if (actions.canModerate) {
    items.push({
      value: 'mod-top',
      label: post?.type === 1 ? '已置顶' : '置顶',
      disabled: post?.type === 1
    })
    items.push({
      value: 'mod-wonderful',
      label: post?.status === 1 ? '已加精' : '加精',
      disabled: post?.status === 1
    })
    items.push({
      value: 'mod-delete',
      label: post?.status === 2 ? '已删除' : '删除帖子',
      disabled: post?.status === 2,
      danger: true
    })
  }
  return items
})

function onMoreSelect(item) {
  const actions = props.actions
  switch (item?.value) {
    case 'follow':
      actions.follow(actions.followStatus !== true)
      break
    case 'report':
      actions.report.openDialog()
      break
    case 'block':
      actions.toggleBlockAuthor()
      break
    case 'mod-top':
      actions.confirmModeration('top')
      break
    case 'mod-wonderful':
      actions.confirmModeration('wonderful')
      break
    case 'mod-delete':
      actions.confirmModeration('delete')
      break
  }
}
</script>

<style scoped src="./PostDetailActions.css"></style>

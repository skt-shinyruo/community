import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostDetailComments from './PostDetailComments.vue'

const stubs = {
  RouterLink: { props: ['to'], template: '<a><slot /></a>' },
  UiAvatar: { template: '<span class="avatar" />' },
  UiBadge: { template: '<span class="badge"><slot /></span>' },
  UiButton: {
    props: ['disabled', 'ariaLabel', 'ariaExpanded'],
    emits: ['click'],
    template: '<button :disabled="disabled" :aria-label="ariaLabel" :aria-expanded="ariaExpanded" @click="$emit(\'click\')"><slot /></button>'
  },
  UiIconButton: {
    props: ['ariaLabel'],
    emits: ['click'],
    template: '<button :aria-label="ariaLabel" @click="$emit(\'click\')"><slot /></button>'
  },
  UiMarkdown: { props: ['content'], template: '<div class="markdown">{{ content }}</div>' },
  UiRoleBadge: { template: '<span class="role" />' },
  UiSkeleton: { template: '<div class="skeleton-stub" role="status" />' },
  UiState: { props: ['title'], template: '<div class="state"><strong>{{ title }}</strong><slot /><slot name="description" /><slot name="actions" /></div>' },
  UiUserCard: { template: '<span><slot /></span>' },
  ReportModal: {
    name: 'ReportModal',
    props: ['targetType', 'targetId'],
    emits: ['close', 'submitted'],
    template: '<div class="report-modal-stub" :data-target-type="targetType" :data-target-id="targetId" />'
  }
}

function createDiscussion(overrides = {}) {
  return {
    loading: false,
    hasNext: true,
    error: '',
    comments: [],
    reload: vi.fn(),
    loadMore: vi.fn(),
    commentAnchorId: vi.fn((id) => `comment-${id}`),
    replyAnchorId: vi.fn((id) => `reply-${id}`),
    isBlockedUser: vi.fn(() => false),
    canReport: vi.fn(() => true),
    applyCommentEdit: vi.fn(() => true),
    toggleCommentLike: vi.fn(),
    startReply: vi.fn(),
    clearReplyQuote: vi.fn(),
    setReplyDraft: vi.fn(),
    cancelReply: vi.fn(),
    submitReply: vi.fn(),
    toggleReplies: vi.fn(),
    toggleReplyLike: vi.fn(),
    repliesHasNext: vi.fn(() => true),
    loadMoreReplies: vi.fn(),
    reloadReplies: vi.fn(),
    ...overrides
  }
}

function createReplyList(overrides = {}) {
  return {
    expanded: false,
    items: [],
    size: 5,
    nextCursor: '',
    loaded: false,
    loading: false,
    error: '',
    ...overrides
  }
}

function mountComments(discussion, commentEditing = { canEdit: vi.fn(() => true), open: vi.fn() }) {
  return mount(PostDetailComments, {
    props: {
      post: { userId: 'author-1', commentCount: discussion.comments.length },
      discussion,
      commentEditing
    },
    global: { stubs }
  })
}

describe('PostDetailComments', () => {
  it('distinguishes skeleton, empty, error and append states', async () => {
    const discussion = createDiscussion()
    const wrapper = mountComments(discussion)
    expect(wrapper.text()).toContain('暂无评论')

    await wrapper.find('.post-comments-head button').trigger('click')
    expect(discussion.reload).toHaveBeenCalledTimes(1)

    await wrapper.setProps({ discussion: createDiscussion({ loading: true, comments: [] }) })
    expect(wrapper.find('.skeleton-stub').exists()).toBe(true)

    const failed = createDiscussion({ error: 'comments unavailable', hasNext: false })
    await wrapper.setProps({ discussion: failed })
    expect(wrapper.text()).toContain('comments unavailable')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    await retry.trigger('click')
    expect(failed.reload).toHaveBeenCalledTimes(1)

    const withItems = createDiscussion({ comments: [rootComment()] })
    await wrapper.setProps({ discussion: withItems })
    const loadMore = wrapper.findAll('button').find((button) => button.text() === '加载更多评论')
    expect(loadMore).toBeTruthy()
    await loadMore.trigger('click')
    expect(withItems.loadMore).toHaveBeenCalledTimes(1)

    const appendFailed = createDiscussion({ comments: [rootComment()], error: 'next page failed' })
    await wrapper.setProps({ discussion: appendFailed })
    expect(wrapper.text()).toContain('next page failed')
    expect(wrapper.text()).toContain('Root comment')
  })

  it('forwards root-comment, reply-editor, and nested-reply interactions', async () => {
    const reply = {
      id: 'reply-1',
      userId: 'author-1',
      user: { username: 'author' },
      targetUser: { username: 'reader' },
      content: 'Nested reply',
      editCount: 1,
      createTime: '2026-01-02T00:00:00Z',
      updateTime: '2026-01-02T00:01:00Z',
      ui: { like: { liked: true, count: 3, loading: false, error: '' } }
    }
    const comment = rootComment({
      replyCount: 1,
      ui: {
        replyEditor: {
          open: true,
          draft: 'draft reply',
          error: 'reply validation failed',
          submitting: false,
          parentCommentId: 'comment-1',
          quote: { username: 'quoted-user', userId: 'quoted-1', preview: 'quoted text' }
        },
        replyList: createReplyList({
          expanded: true,
          loaded: true,
          items: [reply],
          nextCursor: 'next'
        }),
        like: { liked: false, count: 2, loading: false, error: '' }
      }
    })
    const discussion = createDiscussion({ comments: [comment] })
    const commentEditing = { canEdit: vi.fn(() => true), open: vi.fn() }
    const wrapper = mountComments(discussion, commentEditing)

    expect(wrapper.text()).toContain('Root comment')
    expect(wrapper.text()).toContain('Nested reply')
    expect(wrapper.text()).toContain('quoted text')
    expect(wrapper.text()).toContain('reply validation failed')
    expect(wrapper.find('#comment-comment-1').exists()).toBe(true)
    expect(wrapper.find('#reply-reply-1').exists()).toBe(true)

    await wrapper.find('[aria-label="点赞评论"]').trigger('click')
    await wrapper.find('[aria-label="回复评论"]').trigger('click')
    await wrapper.findAll('[aria-label="编辑评论"]')[0].trigger('click')
    await wrapper.find('[aria-label="收起回复"]').trigger('click')
    await wrapper.find('[aria-label="取消引用"]').trigger('click')
    await wrapper.find('textarea').setValue('changed draft')
    await wrapper.findAll('button').find((button) => button.text() === '收起').trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '提交').trigger('click')
    await wrapper.find('[aria-label="取消点赞回复"]').trigger('click')
    await wrapper.find('[aria-label="回复该回复"]').trigger('click')
    await wrapper.findAll('[aria-label="编辑回复"]')[0].trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '加载更多回复').trigger('click')

    expect(discussion.toggleCommentLike).toHaveBeenCalledWith(comment)
    expect(discussion.startReply).toHaveBeenNthCalledWith(1, comment)
    expect(discussion.startReply).toHaveBeenNthCalledWith(2, comment, reply)
    expect(commentEditing.open).toHaveBeenCalledWith(comment)
    expect(commentEditing.open).toHaveBeenCalledWith(reply)
    expect(discussion.toggleReplies).toHaveBeenCalledWith(comment)
    expect(discussion.clearReplyQuote).toHaveBeenCalledWith(comment)
    expect(discussion.setReplyDraft).toHaveBeenCalledWith(comment, 'changed draft')
    expect(discussion.cancelReply).toHaveBeenCalledWith(comment)
    expect(discussion.submitReply).toHaveBeenCalledWith(comment)
    expect(discussion.toggleReplyLike).toHaveBeenCalledWith(comment, reply)
    expect(discussion.loadMoreReplies).toHaveBeenCalledWith(comment)
  })

  it('opens the shared report modal for comments and replies', async () => {
    const reply = {
      id: 'reply-1',
      userId: 'author-1',
      user: { username: 'author' },
      content: 'Nested reply',
      ui: { like: { liked: false, count: 0, loading: false, error: '' } }
    }
    const comment = rootComment({
      ui: {
        replyEditor: { open: false },
        replyList: createReplyList({ expanded: true, loaded: true, items: [reply] }),
        like: { liked: false, count: 0, loading: false, error: '' }
      }
    })
    const wrapper = mountComments(createDiscussion({ comments: [comment] }))

    expect(wrapper.find('.report-modal-stub').exists()).toBe(false)

    await wrapper.find('[aria-label="举报评论"]').trigger('click')
    expect(wrapper.find('.report-modal-stub').attributes('data-target-type')).toBe('comment')
    expect(wrapper.find('.report-modal-stub').attributes('data-target-id')).toBe('comment-1')

    wrapper.findComponent({ name: 'ReportModal' }).vm.$emit('close')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.report-modal-stub').exists()).toBe(false)

    await wrapper.find('[aria-label="举报回复"]').trigger('click')
    expect(wrapper.find('.report-modal-stub').attributes('data-target-id')).toBe('reply-1')
  })

  it('marks post-author entries with OP badges and distinguishes reply error and tail loading states', async () => {
    const opReply = {
      id: 'reply-op',
      userId: 'author-1',
      user: { username: 'author' },
      content: 'OP reply',
      createTime: '2026-01-02T00:00:00Z',
      ui: { like: { liked: false, count: 0, loading: false, error: '' } }
    }
    const opComment = rootComment({
      id: 'comment-op',
      userId: 'author-1',
      user: { username: 'author' },
      ui: {
        replyEditor: { open: false },
        replyList: createReplyList({ expanded: true, loaded: true, items: [opReply], loading: true, nextCursor: 'next' }),
        like: { liked: false, count: 0, loading: false, error: '' }
      }
    })
    const wrapper = mountComments(createDiscussion({ comments: [opComment], loading: true }))

    expect(wrapper.findAll('.comment-op-badge').length).toBe(2)
    expect(wrapper.text()).toContain('OP reply')
    expect(wrapper.text()).toContain('正在加载…')

    const failedComment = rootComment({
      id: 'comment-failed',
      ui: {
        replyEditor: { open: false },
        replyList: createReplyList({ expanded: true, error: 'replies unavailable' }),
        like: { liked: false, count: 0, loading: false, error: '' }
      }
    })
    const failed = createDiscussion({ comments: [failedComment] })
    await wrapper.setProps({ discussion: failed })

    expect(wrapper.text()).toContain('replies unavailable')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    await retry.trigger('click')
    expect(failed.reloadReplies).toHaveBeenCalledWith(failedComment)
  })

  it('hides content and interaction controls for blocked authors', () => {
    const comment = rootComment({
      id: 'comment-blocked',
      userId: 'blocked-user',
      user: { username: 'blocked' },
      content: 'Hidden root content',
      replyCount: 0
    })
    const discussion = createDiscussion({
      comments: [comment],
      isBlockedUser: vi.fn((userId) => userId === 'blocked-user')
    })
    const wrapper = mountComments(discussion)

    expect(wrapper.text()).toContain('已屏蔽该用户内容')
    expect(wrapper.text()).not.toContain('Hidden root content')
    expect(wrapper.find('[aria-label="回复评论"]').exists()).toBe(false)
  })

  function rootComment(overrides = {}) {
    return {
      id: 'comment-1',
      userId: 'reader-1',
      user: { username: 'reader' },
      content: 'Root comment',
      replyCount: 1,
      editCount: 1,
      createTime: '2026-01-01T00:00:00Z',
      updateTime: '2026-01-01T00:01:00Z',
      ui: {
        replyEditor: { open: false },
        replyList: createReplyList(),
        like: { liked: false, count: 0, loading: false, error: '' }
      },
      ...overrides
    }
  }
})

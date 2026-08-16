import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostDetailComments from './PostDetailComments.vue'

const stubs = {
  RouterLink: { template: '<a><slot /></a>' },
  UiAvatar: { template: '<span class="avatar" />' },
  UiBadge: { template: '<span class="badge"><slot /></span>' },
  UiButton: {
    props: ['disabled', 'ariaLabel'],
    emits: ['click'],
    template: '<button :disabled="disabled" :aria-label="ariaLabel" @click="$emit(\'click\')"><slot /></button>'
  },
  UiIconButton: {
    props: ['ariaLabel'],
    emits: ['click'],
    template: '<button :aria-label="ariaLabel" @click="$emit(\'click\')"><slot /></button>'
  },
  UiMarkdown: { props: ['content'], template: '<div class="markdown">{{ content }}</div>' },
  UiPagination: {
    props: ['page', 'hasNext', 'disabled'],
    emits: ['prev', 'next'],
    template: '<div class="pagination"><button class="prev" @click="$emit(\'prev\')">上一页</button><button class="next" @click="$emit(\'next\')">下一页</button></div>'
  },
  UiRoleBadge: { template: '<span class="role" />' },
  UiState: { template: '<div class="state"><slot /></div>' },
  UiUserCard: { template: '<span><slot /></span>' }
}

function createDiscussion(overrides = {}) {
  return {
    loading: false,
    page: 1,
    hasNext: true,
    error: '',
    comments: [],
    reload: vi.fn(),
    prevPage: vi.fn(),
    nextPage: vi.fn(),
    commentAnchorId: vi.fn((id) => `comment-${id}`),
    replyAnchorId: vi.fn((id) => `reply-${id}`),
    isBlockedUser: vi.fn(() => false),
    toggleCommentLike: vi.fn(),
    startReply: vi.fn(),
    clearReplyQuote: vi.fn(),
    setReplyDraft: vi.fn(),
    cancelReply: vi.fn(),
    submitReply: vi.fn(),
    toggleReplies: vi.fn(),
    toggleReplyLike: vi.fn(),
    repliesHasNext: vi.fn(() => true),
    prevRepliesPage: vi.fn(),
    nextRepliesPage: vi.fn(),
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
  it('renders empty, loading, and failed discussion states without hiding controls', async () => {
    const discussion = createDiscussion()
    const wrapper = mountComments(discussion)
    expect(wrapper.text()).toContain('暂无评论')

    await wrapper.find('.post-comments-head button').trigger('click')
    await wrapper.find('.post-comments-toolbar .prev').trigger('click')
    await wrapper.find('.post-comments-toolbar .next').trigger('click')
    expect(discussion.reload).toHaveBeenCalledTimes(1)
    expect(discussion.prevPage).toHaveBeenCalledTimes(1)
    expect(discussion.nextPage).toHaveBeenCalledTimes(1)

    await wrapper.setProps({ discussion: createDiscussion({ loading: true }) })
    expect(wrapper.text()).toContain('加载中')
    await wrapper.setProps({ discussion: createDiscussion({ error: 'comments unavailable' }) })
    expect(wrapper.text()).toContain('comments unavailable')
  })

  it('forwards root-comment, reply-editor, and nested-reply interactions', async () => {
    const reply = {
      id: 'reply-1',
      userId: 'author-1',
      user: { username: 'author' },
      targetUser: { username: 'reader' },
      content: 'Nested reply',
      liked: true,
      likeCount: 3,
      editCount: 1,
      createTime: '2026-01-02T00:00:00Z',
      updateTime: '2026-01-02T00:01:00Z'
    }
    const comment = {
      id: 'comment-1',
      userId: 'reader-1',
      user: { username: 'reader' },
      content: 'Root comment',
      liked: false,
      likeCount: 2,
      replyCount: 1,
      editCount: 1,
      createTime: '2026-01-01T00:00:00Z',
      updateTime: '2026-01-01T00:01:00Z',
      _replying: true,
      _replyDraft: 'draft reply',
      _replyError: 'reply validation failed',
      _replySubmitting: false,
      _replyQuote: { username: 'quoted-user', userId: 'quoted-1', preview: 'quoted text' },
      _repliesExpanded: true,
      _repliesLoading: false,
      _repliesError: '',
      _repliesPage: 1,
      _replies: [reply]
    }
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
    await wrapper.find('.reply-pagination .prev').trigger('click')
    await wrapper.find('.reply-pagination .next').trigger('click')

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
    expect(discussion.prevRepliesPage).toHaveBeenCalledWith(comment)
    expect(discussion.nextRepliesPage).toHaveBeenCalledWith(comment)
  })

  it('hides content and interaction controls for blocked authors', () => {
    const comment = {
      id: 'comment-blocked',
      userId: 'blocked-user',
      user: { username: 'blocked' },
      content: 'Hidden root content',
      replyCount: 0,
      _replying: false,
      _repliesExpanded: false,
      _replies: []
    }
    const discussion = createDiscussion({
      comments: [comment],
      isBlockedUser: vi.fn((userId) => userId === 'blocked-user')
    })
    const wrapper = mountComments(discussion)

    expect(wrapper.text()).toContain('已屏蔽该用户内容')
    expect(wrapper.text()).not.toContain('Hidden root content')
    expect(wrapper.find('[aria-label="回复评论"]').exists()).toBe(false)
  })
})

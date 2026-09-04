// @vitest-environment jsdom

import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'
import { usePostMetaCacheStore } from '../stores/postMetaCache'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'
import { usePostDetailLoader } from './post-detail/usePostDetailLoader'

const routeState = vi.hoisted(() => ({
  params: { postId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa' },
  query: {},
  hash: ''
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routeState,
    useRouter: () => ({
      back: vi.fn(),
      push: vi.fn()
    })
  }
})

vi.mock('../api/services/postService', () => ({
  getPostDetail: vi.fn(),
  listComments: vi.fn(),
  listReplies: vi.fn(),
  addComment: vi.fn(),
  updatePost: vi.fn(),
  deletePostByAuthor: vi.fn(),
  updateComment: vi.fn(),
  moderationTop: vi.fn(),
  moderationWonderful: vi.fn(),
  moderationDelete: vi.fn()
}))

vi.mock('../api/services/userService', () => ({
  getUserProfile: vi.fn().mockResolvedValue({ userId: 'user-me', username: 'me' })
}))

vi.mock('../api/services/socialService', () => ({
  setLike: vi.fn(),
  followUser: vi.fn(),
  unfollowUser: vi.fn(),
  getFollowStatus: vi.fn()
}))

vi.mock('../api/services/bookmarkService', () => ({
  bookmarkPost: vi.fn(),
  unbookmarkPost: vi.fn()
}))

vi.mock('../api/services/blockService', () => ({
  blockUser: vi.fn(),
  unblockUser: vi.fn()
}))

vi.mock('../utils/readTracker', () => ({
  markPostRead: vi.fn()
}))

import { addComment, getPostDetail, listComments, listReplies } from '../api/services/postService'
import { getFollowStatus, setLike } from '../api/services/socialService'
import { markPostRead } from '../utils/readTracker'
import PostDetailView from './PostDetailView.vue'

describe('PostDetailView', () => {
  function deferred() {
    let resolve
    let reject
    const promise = new Promise((res, rej) => {
      resolve = res
      reject = rej
    })
    return { promise, resolve, reject }
  }

  function mountLoader(resolveIdentity = true) {
    const pinia = createPinia()
    setActivePinia(pinia)

    const auth = useAuthStore()
    auth.installSession({ accessToken: 'token' })
    if (resolveIdentity) {
      auth.setMe({ userId: 'user-me', username: 'me', headerUrl: '', authorities: [] })
    }

    const taxonomy = useTaxonomyStore()
    taxonomy.ensureCategories = vi.fn()

    const socialPrefs = useSocialPrefsStore()
    socialPrefs.ensureBlocked = vi.fn().mockResolvedValue()
    socialPrefs.clear = vi.fn()

    const postMetaCache = usePostMetaCacheStore()
    postMetaCache.getLikeCount = vi.fn().mockReturnValue(undefined)
    postMetaCache.getLikeStatus = vi.fn().mockReturnValue(undefined)
    postMetaCache.setLikeCount = vi.fn()
    postMetaCache.setLikeStatus = vi.fn()
    postMetaCache.clearLikeStatuses = vi.fn()
    postMetaCache.ensureUserSummaries = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeCounts = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeStatuses = vi.fn().mockResolvedValue({})

    const harness = defineComponent({
      setup(_, { emit }) {
        return usePostDetailLoader(emit)
      },
      render() {
        return null
      }
    })

    return mount(harness, {
      global: {
        plugins: [pinia]
      }
    })
  }

  function mountView() {
    const pinia = createPinia()
    setActivePinia(pinia)

    const auth = useAuthStore()
    auth.installSession({ accessToken: 'token' })
    auth.setMe({ userId: 'user-me', username: 'me', headerUrl: '', authorities: [] })
    useTaxonomyStore().ensureCategories = vi.fn()
    useSocialPrefsStore().ensureBlocked = vi.fn().mockResolvedValue()

    const postMetaCache = usePostMetaCacheStore()
    postMetaCache.ensureUserSummaries = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeCounts = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeStatuses = vi.fn().mockResolvedValue({})

    return mount(PostDetailView, {
      global: {
        plugins: [pinia],
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          PostBlockRenderer: { template: '<div data-test="post-blocks" />' }
        }
      }
    })
  }

  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    routeState.params.postId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    routeState.query = {}
    routeState.hash = ''
    getPostDetail.mockResolvedValue({
      data: {
        id: routeState.params.postId,
        userId: 'user-me',
        title: '帖子标题',
        blocks: [],
        commentCount: 1,
        likeCount: 0,
        createTime: '2026-01-01T00:00:00Z'
      },
      traceId: 'trace-post-detail'
    })
    listComments.mockResolvedValue({
      data: [],
      traceId: 'trace-comments'
    })
    listReplies.mockResolvedValue({
      data: [],
      traceId: 'trace-replies'
    })
    addComment.mockResolvedValue({
      data: { commentId: 'ffffffff-ffff-7fff-8fff-ffffffffffff' },
      traceId: 'trace-add-comment'
    })
    setLike.mockResolvedValue({ data: { liked: true, likeCount: 1 } })
    getFollowStatus.mockResolvedValue({ data: false, traceId: 'trace-follow-status' })
  })

  it('exposes page, post actions, and discussion as the loader interface', async () => {
    const wrapper = mountLoader()
    await flushPromises()

    expect(wrapper.vm.post).toBeUndefined()
    expect(wrapper.vm.loadComments).toBeUndefined()
    expect(wrapper.vm.page.post?.title).toBe('帖子标题')
    expect(wrapper.vm.discussion.composer.setDraft).toEqual(expect.any(Function))
    expect(wrapper.vm.postActions.toggleLike).toEqual(expect.any(Function))
  })

  it('does not write shared read state before the authenticated identity is known', async () => {
    const wrapper = mountLoader(false)
    await flushPromises()

    expect(wrapper.vm.page.post?.title).toBe('帖子标题')
    expect(markPostRead).not.toHaveBeenCalled()
  })

  it('renders the post, discussion, and composer through the grouped models', async () => {
    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.post-article-title').text()).toBe('帖子标题')
    expect(wrapper.find('.post-comments-card').exists()).toBe(true)
    expect(wrapper.find('.comment-composer-card').exists()).toBe(true)
  })

  it('does not commit post state loaded for the previous account', async () => {
    const oldDetail = deferred()
    getPostDetail
      .mockReturnValueOnce(oldDetail.promise)
      .mockResolvedValueOnce({
        data: {
          id: routeState.params.postId,
          userId: 'author-b',
          title: 'account B detail',
          blocks: [],
          commentCount: 0,
          liked: false,
          bookmarked: false
        },
        traceId: 'trace-detail-b'
      })

    const wrapper = mountLoader()
    await vi.waitFor(() => expect(getPostDetail).toHaveBeenCalledTimes(1))

    useAuthStore().installSession({
      accessToken: 'token-b',
      me: { userId: 'viewer-b', username: 'viewer-b', authorities: [] }
    })
    await vi.waitFor(() => expect(getPostDetail).toHaveBeenCalledTimes(2))
    await flushPromises()

    expect(wrapper.vm.page.post?.title).toBe('account B detail')

    oldDetail.resolve({
      data: {
        id: routeState.params.postId,
        userId: 'author-a',
        title: 'account A detail',
        blocks: [],
        commentCount: 0,
        liked: true,
        bookmarked: true
      },
      traceId: 'trace-detail-a'
    })
    await flushPromises()

    expect(wrapper.vm.page.post?.title).toBe('account B detail')
    expect(wrapper.vm.page.post?.liked).toBe(false)
    expect(wrapper.vm.page.post?.bookmarked).toBe(false)
  })

  it('keeps comment drafts isolated between accounts', async () => {
    const wrapper = mountLoader()
    await flushPromises()
    wrapper.vm.discussion.composer.setDraft('draft from account A')
    expect(window.localStorage.getItem(
      'community.draft.posts.user-me.aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa.comment'
    )).toBe('draft from account A')

    useAuthStore().installSession({
      accessToken: 'token-b',
      me: { userId: 'user-b', username: 'viewer-b', authorities: [] }
    })
    await flushPromises()

    expect(wrapper.vm.discussion.composer.draft).toBe('')
    expect(wrapper.vm.discussion.composer.draft).not.toBe('draft from account A')
  })

  it('does not clear the new account draft when the previous account comment completes', async () => {
    const oldComment = deferred()
    addComment.mockReturnValueOnce(oldComment.promise)
    const wrapper = mountLoader()
    await flushPromises()
    wrapper.vm.discussion.composer.setDraft('comment from account A')
    const staleSubmit = wrapper.vm.discussion.composer.submit()
    await vi.waitFor(() => expect(addComment).toHaveBeenCalledTimes(1))

    useAuthStore().installSession({
      accessToken: 'token-b',
      me: { userId: 'user-b', username: 'viewer-b', authorities: [] }
    })
    await flushPromises()
    wrapper.vm.discussion.composer.setDraft('draft from account B')

    oldComment.resolve({ data: { commentId: 'comment-a' }, traceId: 'trace-comment-a' })
    await staleSubmit

    expect(wrapper.vm.discussion.composer.draft).toBe('draft from account B')
  })

  it('loads comments and replies with cursor params', async () => {
    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()

    expect(listComments).toHaveBeenCalledWith(routeState.params.postId, { cursor: '', size: 10 })

    await wrapper.vm.discussion.toggleReplies({
      id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      ui: {
        replyList: {
          expanded: false,
          size: 5,
          items: [],
          nextCursor: '',
          loaded: false,
          loading: false,
          error: ''
        }
      }
    })

    expect(listReplies).toHaveBeenCalledWith(
      routeState.params.postId,
      'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      { cursor: '', size: 5 }
    )
  })

  it('appends comment pages and retries the same cursor after failure', async () => {
    const firstComment = {
      id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      userId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      content: 'first page comment'
    }
    const secondComment = {
      id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      userId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
      content: 'second page comment'
    }
    listComments
      .mockResolvedValueOnce({ data: { items: [firstComment], nextCursor: 'cursor-page-2' } })
      .mockRejectedValueOnce(new Error('temporary comment failure'))
      .mockResolvedValueOnce({ data: { items: [secondComment], nextCursor: '' } })

    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()

    await wrapper.vm.discussion.loadMore()
    expect(wrapper.vm.discussion.comments.map((comment) => comment.content)).toEqual(['first page comment'])
    expect(wrapper.vm.discussion.error).toBe('temporary comment failure')
    expect(wrapper.vm.discussion.hasNext).toBe(true)

    await wrapper.vm.discussion.loadMore()

    expect(listComments.mock.calls.map(([, request]) => request.cursor))
      .toEqual(['', 'cursor-page-2', 'cursor-page-2'])
    expect(wrapper.vm.discussion.comments.map((comment) => comment.content))
      .toEqual(['first page comment', 'second page comment'])
    expect(wrapper.vm.discussion.hasNext).toBe(false)
  })

  it('keeps the appended comments when a refresh fails, and replaces them on the next refresh', async () => {
    const firstComment = {
      id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      userId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      content: 'first page comment'
    }
    const secondComment = {
      id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      userId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
      content: 'second page comment'
    }
    const refreshedComment = {
      ...firstComment,
      content: 'refreshed first page comment'
    }
    listComments
      .mockResolvedValueOnce({ data: { items: [firstComment], nextCursor: 'cursor-page-2' } })
      .mockResolvedValueOnce({ data: { items: [secondComment], nextCursor: '' } })
      .mockRejectedValueOnce(new Error('temporary refresh failure'))
      .mockResolvedValueOnce({ data: { items: [refreshedComment], nextCursor: '' } })

    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()
    await wrapper.vm.discussion.loadMore()

    await wrapper.vm.discussion.reload()
    expect(wrapper.vm.discussion.comments.map((comment) => comment.content))
      .toEqual(['first page comment', 'second page comment'])
    expect(wrapper.vm.discussion.error).toBe('temporary refresh failure')

    await wrapper.vm.discussion.reload()
    expect(listComments.mock.calls.map(([, request]) => request.cursor)).toEqual([
      '',
      'cursor-page-2',
      '',
      ''
    ])
    expect(wrapper.vm.discussion.comments.map((comment) => comment.content))
      .toEqual(['refreshed first page comment'])
  })

  it('keeps existing replies when the refresh after a submitted reply fails', async () => {
    const firstReply = {
      id: '11111111-1111-7111-8111-111111111111',
      userId: '22222222-2222-7222-8222-222222222222',
      content: 'first page reply'
    }
    const secondReply = {
      id: '33333333-3333-7333-8333-333333333333',
      userId: '44444444-4444-7444-8444-444444444444',
      content: 'second page reply'
    }
    listReplies
      .mockResolvedValueOnce({ data: { items: [firstReply], nextCursor: 'reply-cursor-2' } })
      .mockResolvedValueOnce({ data: { items: [secondReply], nextCursor: '' } })
      .mockRejectedValueOnce(new Error('temporary reply refresh failure'))
      .mockResolvedValueOnce({ data: { items: [firstReply, secondReply], nextCursor: '' } })

    const wrapper = mountLoader()
    await flushPromises()
    const root = replyableRootComment()
    await wrapper.vm.discussion.toggleReplies(root)
    await wrapper.vm.discussion.loadMoreReplies(root)
    expect(root.ui.replyList.items.map((reply) => reply.content)).toEqual(['first page reply', 'second page reply'])

    wrapper.vm.discussion.startReply(root)
    root.ui.replyEditor.draft = 'new reply'
    await wrapper.vm.discussion.submitReply(root)
    expect(root.ui.replyList.items.map((reply) => reply.content)).toEqual(['first page reply', 'second page reply'])
    expect(root.ui.replyList.error).toBe('temporary reply refresh failure')

    wrapper.vm.discussion.startReply(root)
    root.ui.replyEditor.draft = 'another reply'
    await wrapper.vm.discussion.submitReply(root)
    expect(listReplies.mock.calls.map(([, , request]) => request.cursor)).toEqual([
      '',
      'reply-cursor-2',
      '',
      ''
    ])
    expect(root.ui.replyList.items.map((reply) => reply.content)).toEqual(['first page reply', 'second page reply'])
  })

  it('keeps the reply editor and reply page intact when submission fails', async () => {
    addComment.mockRejectedValueOnce(new Error('reply rejected'))
    const wrapper = mountLoader()
    await flushPromises()
    const root = replyableRootComment()
    root.ui.replyList.expanded = true
    root.ui.replyList.items = [{ id: 'existing-reply' }]
    wrapper.vm.discussion.startReply(root)
    root.ui.replyEditor.draft = 'retry this reply'

    await wrapper.vm.discussion.submitReply(root)

    expect(root.ui.replyEditor).toMatchObject({
      open: true,
      draft: 'retry this reply',
      error: 'reply rejected',
      submitting: false,
      parentCommentId: root.id
    })
    expect(root.ui.replyList.items).toEqual([{ id: 'existing-reply' }])
  })

  it('rolls comment and reply likes back inside their own state groups', async () => {
    listComments.mockResolvedValueOnce({
      data: {
        items: [{
          id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
          userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          content: 'root content'
        }],
        nextCursor: ''
      }
    })
    listReplies.mockResolvedValueOnce({
      data: {
        items: [{
          id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
          userId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
          content: 'nested content'
        }],
        nextCursor: ''
      }
    })
    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()
    const root = wrapper.vm.discussion.comments[0]
    await wrapper.vm.discussion.toggleReplies(root)
    const reply = root.ui.replyList.items[0]
    const editor = root.ui.replyEditor
    const replyList = root.ui.replyList

    const failedCommentLike = deferred()
    setLike.mockReturnValueOnce(failedCommentLike.promise)
    const commentAction = wrapper.vm.discussion.toggleCommentLike(root)
    expect(root.ui.like).toMatchObject({ liked: true, count: 1, loading: true, error: '' })
    failedCommentLike.reject(new Error('comment like failed'))
    await commentAction
    expect(root.ui.like).toEqual({ liked: false, count: 0, loading: false, error: 'comment like failed' })
    expect(root.ui.replyEditor).toBe(editor)
    expect(root.ui.replyList).toBe(replyList)

    reply.ui.like.liked = true
    reply.ui.like.count = 3
    const failedReplyUnlike = deferred()
    setLike.mockReturnValueOnce(failedReplyUnlike.promise)
    const replyAction = wrapper.vm.discussion.toggleReplyLike(root, reply)
    expect(reply.ui.like).toMatchObject({ liked: false, count: 2, loading: true, error: '' })
    failedReplyUnlike.reject(new Error('reply unlike failed'))
    await replyAction
    expect(reply.ui.like).toEqual({ liked: true, count: 3, loading: false, error: 'reply unlike failed' })
    expect(root.ui.like).toEqual({ liked: false, count: 0, loading: false, error: 'comment like failed' })
  })

  it('submits the selected root or nested reply as the direct parent only', async () => {
    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()
    const root = replyableRootComment()
    const nested = {
      id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      userId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
      user: { username: 'nested-author' },
      content: 'nested content'
    }

    wrapper.vm.discussion.startReply(root)
    root.ui.replyEditor.draft = 'root reply'
    await wrapper.vm.discussion.submitReply(root)

    expect(addComment).toHaveBeenNthCalledWith(1, routeState.params.postId, {
      content: expect.any(String),
      parentCommentId: root.id
    }, expect.objectContaining({ writeAttempt: expect.any(Object) }))
    expect(root.ui.replyEditor.parentCommentId).toBe('')

    wrapper.vm.discussion.startReply(root, nested)
    root.ui.replyEditor.draft = 'nested reply'
    await wrapper.vm.discussion.submitReply(root)

    expect(addComment).toHaveBeenNthCalledWith(2, routeState.params.postId, {
      content: expect.any(String),
      parentCommentId: nested.id
    }, expect.objectContaining({ writeAttempt: expect.any(Object) }))
    expect(root.ui.replyEditor.parentCommentId).toBe('')
  })

  it('clears the selected direct parent when reply editing is cancelled', async () => {
    const wrapper = mountLoader()
    await flushPromises()
    const root = replyableRootComment()
    const nested = {
      id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      userId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
      user: { username: 'nested-author' },
      content: 'nested content'
    }

    wrapper.vm.discussion.startReply(root, nested)
    expect(root.ui.replyEditor.parentCommentId).toBe(nested.id)

    wrapper.vm.discussion.cancelReply(root)

    expect(root.ui.replyEditor.parentCommentId).toBe('')
    expect(addComment).not.toHaveBeenCalled()
  })

  it('reuses the reply idempotency key after comments reload for the same persisted draft', async () => {
    const rootPayload = {
      id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      content: 'root content'
    }
    listComments.mockResolvedValue({ data: { items: [rootPayload], nextCursor: '' } })
    const keys = []
    addComment
      .mockImplementationOnce((_postId, _payload, { writeAttempt }) => {
        keys.push(writeAttempt.begin())
        return Promise.reject(new Error('response lost'))
      })
      .mockImplementationOnce((_postId, _payload, { writeAttempt }) => {
        keys.push(writeAttempt.begin())
        return Promise.resolve({ data: { commentId: 'reply-1' }, traceId: 'trace-retry' })
      })

    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()
    const firstRoot = wrapper.vm.discussion.comments[0]
    wrapper.vm.discussion.startReply(firstRoot)
    wrapper.vm.discussion.setReplyDraft(firstRoot, 'same reply draft')
    await wrapper.vm.discussion.submitReply(firstRoot)

    await wrapper.vm.discussion.reload()
    const reloadedRoot = wrapper.vm.discussion.comments[0]
    expect(reloadedRoot).not.toBe(firstRoot)
    wrapper.vm.discussion.startReply(reloadedRoot)
    expect(reloadedRoot.ui.replyEditor.draft).toBe('same reply draft')
    await wrapper.vm.discussion.submitReply(reloadedRoot)

    expect(keys).toHaveLength(2)
    expect(keys[1]).toBe(keys[0])
  })

  it('silently inserts a posted comment at the head without dropping appended pages', async () => {
    const firstComment = {
      id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      userId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      content: 'first page comment'
    }
    const secondComment = {
      id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      userId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
      content: 'second page comment'
    }
    const postedComment = {
      id: 'ffffffff-ffff-7fff-8fff-ffffffffffff',
      userId: 'user-me',
      content: 'brand new comment'
    }
    listComments
      .mockResolvedValueOnce({ data: { items: [firstComment], nextCursor: 'cursor-page-2' } })
      .mockResolvedValueOnce({ data: { items: [secondComment], nextCursor: '' } })
      .mockResolvedValueOnce({ data: { items: [postedComment, firstComment], nextCursor: 'cursor-page-2' } })
    addComment.mockResolvedValueOnce({ data: { commentId: postedComment.id }, traceId: 'trace-add' })

    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()
    await wrapper.vm.discussion.loadMore()

    wrapper.vm.discussion.composer.setDraft('brand new comment')
    await wrapper.vm.discussion.composer.submit()

    expect(wrapper.vm.discussion.comments.map((comment) => comment.content))
      .toEqual(['brand new comment', 'first page comment', 'second page comment'])
    expect(wrapper.vm.discussion.composer.draft).toBe('')
    expect(wrapper.vm.discussion.hasNext).toBe(false)
  })

  it('auto-loads appended comment pages until a deep-linked comment is found', async () => {
    const firstComment = {
      id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      userId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      content: 'first page comment'
    }
    const deepComment = {
      id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      userId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
      content: 'deep linked comment'
    }
    routeState.query = { commentId: deepComment.id }
    listComments
      .mockResolvedValueOnce({ data: { items: [firstComment], nextCursor: 'cursor-page-2' } })
      .mockResolvedValueOnce({ data: { items: [deepComment], nextCursor: '' } })

    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()

    expect(listComments.mock.calls.map(([, request]) => request.cursor)).toEqual(['', 'cursor-page-2'])
    expect(wrapper.vm.discussion.comments.map((comment) => comment.content))
      .toEqual(['first page comment', 'deep linked comment'])
  })

  it('applies comment and reply edits in place without a success toast surface', async () => {
    const reply = {
      id: '11111111-1111-7111-8111-111111111111',
      userId: '22222222-2222-7222-8222-222222222222',
      content: 'reply before edit'
    }
    const rootComment = {
      id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      content: 'comment before edit',
      editCount: 0
    }
    listComments.mockResolvedValue({ data: { items: [rootComment], nextCursor: '' } })

    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()
    const comment = wrapper.vm.discussion.comments[0]
    comment.ui.replyList.items = [reply]

    expect(wrapper.vm.discussion.applyCommentEdit(comment.id, 'comment after edit')).toBe(true)
    expect(comment.content).toBe('comment after edit')
    expect(comment.editCount).toBe(1)

    expect(wrapper.vm.discussion.applyCommentEdit(reply.id, 'reply after edit')).toBe(true)
    expect(reply.content).toBe('reply after edit')
    expect(wrapper.vm.discussion.applyCommentEdit('99999999-9999-7999-8999-999999999999', 'missing')).toBe(false)
  })

  function replyableRootComment() {
    return {
      id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      user: { username: 'root-author' },
      content: 'root content',
      ui: {
        replyEditor: {
          open: false,
          draft: '',
          error: '',
          submitting: false,
          parentCommentId: '',
          quote: null
        },
        replyList: {
          expanded: false,
          items: [],
          size: 5,
          nextCursor: '',
          loaded: false,
          loading: false,
          error: ''
        },
        like: { liked: false, count: 0, loading: false, error: '' }
      }
    }
  }
})

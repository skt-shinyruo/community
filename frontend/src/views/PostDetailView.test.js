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

vi.mock('../utils/scrollToAnchor', () => ({
  scrollToAnchor: vi.fn().mockReturnValue(false)
}))

import { addComment, getPostDetail, listComments, listReplies } from '../api/services/postService'
import { getFollowStatus } from '../api/services/socialService'
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
    auth.setAccessToken('token')
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
    auth.setAccessToken('token')
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
        },
        mocks: {
          $router: { back: vi.fn() }
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
      _repliesPage: 0,
      _repliesSize: 5,
      _replies: [],
      _repliesLoading: false,
      _repliesError: ''
    })

    expect(listReplies).toHaveBeenCalledWith(
      routeState.params.postId,
      'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      { cursor: '', size: 5 }
    )
  })

  it('keeps the current comment page and retries the same cursor after failure', async () => {
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

    await wrapper.vm.discussion.nextPage()
    expect(wrapper.vm.discussion.page).toBe(0)
    expect(wrapper.vm.discussion.comments[0].content).toBe('first page comment')
    expect(wrapper.vm.discussion.error).toBe('temporary comment failure')

    await wrapper.vm.discussion.nextPage()

    expect(listComments.mock.calls.map(([, request]) => request.cursor))
      .toEqual(['', 'cursor-page-2', 'cursor-page-2'])
    expect(wrapper.vm.discussion.page).toBe(1)
    expect(wrapper.vm.discussion.comments[0].content).toBe('second page comment')
  })

  it('keeps the current comment page when resetting to the first page fails', async () => {
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
    await wrapper.vm.discussion.nextPage()

    await wrapper.vm.discussion.reload()
    expect(wrapper.vm.discussion.page).toBe(1)
    expect(wrapper.vm.discussion.comments[0].content).toBe('second page comment')
    expect(wrapper.vm.discussion.error).toBe('temporary refresh failure')

    await wrapper.vm.discussion.reload()
    expect(listComments.mock.calls.map(([, request]) => request.cursor)).toEqual([
      '',
      'cursor-page-2',
      '',
      ''
    ])
    expect(wrapper.vm.discussion.page).toBe(0)
    expect(wrapper.vm.discussion.comments[0].content).toBe('refreshed first page comment')
  })

  it('keeps the current reply page when a submitted reply cannot refresh the thread', async () => {
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
      .mockResolvedValueOnce({ data: { items: [firstReply], nextCursor: '' } })

    const wrapper = mountLoader()
    await flushPromises()
    const root = replyableRootComment()
    await wrapper.vm.discussion.toggleReplies(root)
    await wrapper.vm.discussion.nextRepliesPage(root)

    wrapper.vm.discussion.startReply(root)
    root._replyDraft = 'new reply'
    await wrapper.vm.discussion.submitReply(root)
    expect(root._repliesPage).toBe(1)
    expect(root._replies[0].content).toBe('second page reply')
    expect(root._repliesError).toBe('temporary reply refresh failure')

    wrapper.vm.discussion.startReply(root)
    root._replyDraft = 'another reply'
    await wrapper.vm.discussion.submitReply(root)
    expect(listReplies.mock.calls.map(([, , request]) => request.cursor)).toEqual([
      '',
      'reply-cursor-2',
      '',
      ''
    ])
    expect(root._repliesPage).toBe(0)
    expect(root._replies[0].content).toBe('first page reply')
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
    root._replyDraft = 'root reply'
    await wrapper.vm.discussion.submitReply(root)

    expect(addComment).toHaveBeenNthCalledWith(1, routeState.params.postId, {
      content: expect.any(String),
      parentCommentId: root.id
    }, expect.objectContaining({ writeAttempt: expect.any(Object) }))
    expect(root._replyParentCommentId).toBe('')

    wrapper.vm.discussion.startReply(root, nested)
    root._replyDraft = 'nested reply'
    await wrapper.vm.discussion.submitReply(root)

    expect(addComment).toHaveBeenNthCalledWith(2, routeState.params.postId, {
      content: expect.any(String),
      parentCommentId: nested.id
    }, expect.objectContaining({ writeAttempt: expect.any(Object) }))
    expect(root._replyParentCommentId).toBe('')
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
    expect(root._replyParentCommentId).toBe(nested.id)

    wrapper.vm.discussion.cancelReply(root)

    expect(root._replyParentCommentId).toBe('')
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
    expect(reloadedRoot._replyDraft).toBe('same reply draft')
    await wrapper.vm.discussion.submitReply(reloadedRoot)

    expect(keys).toHaveLength(2)
    expect(keys[1]).toBe(keys[0])
  })

  function replyableRootComment() {
    return {
      id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      user: { username: 'root-author' },
      content: 'root content',
      _replying: false,
      _replyDraft: '',
      _replyError: '',
      _replySubmitting: false,
      _replyParentCommentId: '',
      _replyQuote: null,
      _repliesExpanded: false,
      _replies: [],
      _repliesPage: 0,
      _repliesSize: 5,
      _repliesNextCursor: '',
      _repliesCursorHistory: [''],
      _repliesLoading: false,
      _repliesError: ''
    }
  }
})

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
  getUserProfile: vi.fn().mockResolvedValue({ data: { userId: 'user-me', username: 'me' }, traceId: 'trace-user' })
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
import PostDetailView from './PostDetailView.vue'

describe('PostDetailView', () => {
  function mountLoader() {
    const pinia = createPinia()
    setActivePinia(pinia)

    const auth = useAuthStore()
    auth.setAccessToken('token')
    auth.setMe({ userId: 'user-me', username: 'me', headerUrl: '', authorities: [] })

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
  })

  it('loads comments and replies with cursor params', async () => {
    const wrapper = mountLoader()
    await flushPromises()
    await flushPromises()

    expect(listComments).toHaveBeenCalledWith(routeState.params.postId, { cursor: '', size: 10 })

    await wrapper.vm.loadReplies({
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

    wrapper.vm.startReply(root)
    root._replyDraft = 'root reply'
    await wrapper.vm.submitReply(root)

    expect(addComment).toHaveBeenNthCalledWith(1, routeState.params.postId, {
      content: expect.any(String),
      parentCommentId: root.id
    })
    expect(root._replyParentCommentId).toBe('')

    wrapper.vm.startReply(root, nested)
    root._replyDraft = 'nested reply'
    await wrapper.vm.submitReply(root)

    expect(addComment).toHaveBeenNthCalledWith(2, routeState.params.postId, {
      content: expect.any(String),
      parentCommentId: nested.id
    })
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

    wrapper.vm.startReply(root, nested)
    expect(root._replyParentCommentId).toBe(nested.id)

    wrapper.vm.cancelReply(root)

    expect(root._replyParentCommentId).toBe('')
    expect(addComment).not.toHaveBeenCalled()
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

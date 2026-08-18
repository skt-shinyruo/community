import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const router = vi.hoisted(() => ({ push: vi.fn() }))
const toasts = vi.hoisted(() => ({ showToast: vi.fn(), showErrorToast: vi.fn() }))

vi.mock('vue-router', () => ({ useRouter: () => router }))
vi.mock('../../ui/toastService', () => toasts)
vi.mock('../../api/services/socialService', () => ({
  setLike: vi.fn(),
  followUser: vi.fn(),
  unfollowUser: vi.fn(),
  getFollowStatus: vi.fn()
}))
vi.mock('../../api/services/bookmarkService', () => ({
  bookmarkPost: vi.fn(),
  unbookmarkPost: vi.fn()
}))
vi.mock('../../api/services/blockService', () => ({
  blockUser: vi.fn(),
  unblockUser: vi.fn()
}))
vi.mock('../../api/services/postService', () => ({
  deletePostByAuthor: vi.fn(),
  moderationDelete: vi.fn(),
  moderationTop: vi.fn(),
  moderationWonderful: vi.fn(),
  updateComment: vi.fn(),
  updatePost: vi.fn()
}))

import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { blockUser, unblockUser } from '../../api/services/blockService'
import { bookmarkPost, unbookmarkPost } from '../../api/services/bookmarkService'
import { followUser, getFollowStatus, setLike, unfollowUser } from '../../api/services/socialService'
import {
  deletePostByAuthor,
  moderationDelete,
  moderationTop,
  moderationWonderful,
  updateComment,
  updatePost
} from '../../api/services/postService'
import { usePostDetailActions } from './usePostDetailActions'

const POST_ID = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
const AUTHOR_ID = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
const VIEWER_ID = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
const COMMENT_ID = 'dddddddd-dddd-7ddd-8ddd-dddddddddddd'

describe('usePostDetailActions', () => {
  function createSubject({ owner = false, authed = true, current = true } = {}) {
    const auth = {
      tokenGeneration: 4,
      isAdminOrModerator: true
    }
    const post = ref({
      id: POST_ID,
      userId: owner ? VIEWER_ID : AUTHOR_ID,
      title: 'Original title',
      blocks: [{ type: 'paragraph', text: 'Body' }],
      categoryId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
      tags: ['vue'],
      createTime: new Date().toISOString(),
      status: 0,
      type: 0,
      liked: false,
      bookmarked: false,
      likeCount: 1
    })
    const error = ref('')
    const refreshPost = vi.fn().mockResolvedValue(undefined)
    const refreshComments = vi.fn().mockResolvedValue(undefined)
    const reloadPage = vi.fn().mockResolvedValue(undefined)
    const actions = usePostDetailActions({
      auth,
      authed: ref(authed),
      postId: ref(POST_ID),
      post,
      meUserId: ref(VIEWER_ID),
      error,
      captureViewScope: vi.fn(() => ({ generation: auth.tokenGeneration })),
      isCurrentViewScope: vi.fn(() => current),
      refreshPost,
      refreshComments,
      reloadPage
    })
    return { auth, post, error, refreshPost, refreshComments, reloadPage, ...actions }
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    setLike.mockResolvedValue({ data: { likeCount: 8, liked: true }, traceId: 'trace-like' })
    getFollowStatus.mockResolvedValue({ data: false, traceId: 'trace-follow-status' })
    followUser.mockResolvedValue({ traceId: 'trace-follow' })
    unfollowUser.mockResolvedValue({ traceId: 'trace-unfollow' })
    bookmarkPost.mockResolvedValue({ traceId: 'trace-bookmark' })
    unbookmarkPost.mockResolvedValue({ traceId: 'trace-unbookmark' })
    blockUser.mockResolvedValue({ traceId: 'trace-block' })
    unblockUser.mockResolvedValue({ traceId: 'trace-unblock' })
    moderationTop.mockResolvedValue({ traceId: 'trace-top' })
    moderationWonderful.mockResolvedValue({ traceId: 'trace-wonderful' })
    moderationDelete.mockResolvedValue({ traceId: 'trace-delete' })
    deletePostByAuthor.mockResolvedValue({ traceId: 'trace-author-delete' })
    updatePost.mockResolvedValue({ traceId: 'trace-update-post' })
    updateComment.mockResolvedValue({ traceId: 'trace-update-comment' })
  })

  it('applies cached like state and refreshes the author follow state', async () => {
    const subject = createSubject()
    const cache = usePostMetaCacheStore()
    vi.spyOn(cache, 'getLikeCount').mockReturnValue(12)
    vi.spyOn(cache, 'getLikeStatus').mockReturnValue(true)

    subject.applyPostLikeOverlay()
    expect(subject.post.value).toMatchObject({ likeCount: 12, liked: true })

    await subject.loadFollowStatus()
    expect(getFollowStatus).toHaveBeenCalledWith(3, AUTHOR_ID, { force: true })
    expect(subject.model.followStatus).toBe(false)
  })

  it('updates likes, follows, bookmarks, and reports through the grouped action model', async () => {
    const subject = createSubject()
    const cache = usePostMetaCacheStore()
    const setLikeCount = vi.spyOn(cache, 'setLikeCount')
    const setLikeStatus = vi.spyOn(cache, 'setLikeStatus')

    await subject.model.toggleLike()
    expect(subject.post.value).toMatchObject({ likeCount: 8, liked: true })
    expect(setLikeCount).toHaveBeenCalledWith(1, POST_ID, 8)
    expect(setLikeStatus).toHaveBeenCalledWith(1, POST_ID, true)

    await subject.model.follow(true)
    await subject.model.follow(false)
    expect(followUser).toHaveBeenCalledWith(3, AUTHOR_ID)
    expect(unfollowUser).toHaveBeenCalledWith(3, AUTHOR_ID)
    expect(subject.model.followStatus).toBe(false)

    await subject.model.toggleBookmark()
    await subject.model.toggleBookmark()
    expect(bookmarkPost).toHaveBeenCalledWith(POST_ID)
    expect(unbookmarkPost).toHaveBeenCalledWith(POST_ID)
    expect(subject.post.value.bookmarked).toBe(false)

    subject.model.report.openDialog()
    expect(subject.model.report.open).toBe(true)
    subject.model.report.close()
    expect(subject.model.report.open).toBe(false)
  })

  it('blocks and unblocks the author while refreshing the shared preference store', async () => {
    const subject = createSubject()
    const prefs = useSocialPrefsStore()
    prefs.ensureBlocked = vi.fn().mockResolvedValue(undefined)

    await subject.model.toggleBlockAuthor()
    expect(blockUser).toHaveBeenCalledWith(AUTHOR_ID)
    expect(toasts.showToast).toHaveBeenCalledWith({ type: 'success', text: '已屏蔽该用户' })

    prefs.blockedUserIds = [AUTHOR_ID]
    await subject.model.toggleBlockAuthor()
    expect(unblockUser).toHaveBeenCalledWith(AUTHOR_ID)
    expect(prefs.ensureBlocked).toHaveBeenCalledTimes(2)
  })

  it('opens only eligible post/comment editors and submits both edit modes', async () => {
    const owner = createSubject({ owner: true })
    expect(owner.model.canEditPost).toBe(true)
    owner.model.openPostEditor()
    expect(owner.model.editor).toMatchObject({
      open: true,
      mode: 'post',
      initialTitle: 'Original title'
    })

    await owner.model.editor.submit({
      title: ' Updated title ',
      blocks: [{ type: 'paragraph', text: 'Updated' }]
    })
    expect(updatePost).toHaveBeenCalledWith(POST_ID, expect.objectContaining({
      title: 'Updated title',
      tags: ['vue']
    }))
    expect(owner.refreshPost).toHaveBeenCalledTimes(1)
    expect(toasts.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '已保存' }))

    const comment = {
      id: COMMENT_ID,
      userId: VIEWER_ID,
      content: 'Old comment',
      status: 0,
      createTime: new Date().toISOString()
    }
    expect(owner.model.commentEditing.canEdit(comment)).toBe(true)
    owner.model.commentEditing.open(comment)
    expect(owner.model.editor).toMatchObject({ open: true, mode: 'comment', initialContent: 'Old comment' })
    await owner.model.editor.submit({ content: ' Updated comment ' })
    expect(updateComment).toHaveBeenCalledWith(POST_ID, COMMENT_ID, { content: 'Updated comment' })
    expect(owner.refreshComments).toHaveBeenCalledTimes(1)
  })

  it('runs moderator and author confirmations with their distinct outcomes', async () => {
    const subject = createSubject()
    for (const [type, request] of [
      ['top', moderationTop],
      ['wonderful', moderationWonderful],
      ['delete', moderationDelete]
    ]) {
      subject.model.confirmModeration(type)
      expect(subject.model.confirmation.open).toBe(true)
      await subject.model.confirmation.run()
      expect(request).toHaveBeenCalledWith(POST_ID)
    }
    expect(subject.reloadPage).toHaveBeenCalledTimes(3)

    subject.model.confirmAuthorDelete()
    expect(subject.model.confirmation.variant).toBe('danger')
    expect(subject.model.confirmation.okText).toBe('删除')
    await subject.model.confirmation.run()
    expect(deletePostByAuthor).toHaveBeenCalledWith(POST_ID)
    expect(router.push).toHaveBeenCalledWith({ name: 'posts' })
  })

  it('keeps stale and failed requests from mutating the active page', async () => {
    const stale = createSubject({ current: false })
    await stale.model.toggleLike()
    await stale.model.toggleBookmark()
    expect(stale.post.value).toMatchObject({ likeCount: 1, liked: false, bookmarked: false })

    const current = createSubject()
    setLike.mockRejectedValueOnce(new Error('like unavailable'))
    await current.model.toggleLike()
    expect(current.error.value).toBe('like unavailable')

    updatePost.mockRejectedValueOnce(new Error('save unavailable'))
    current.model.openPostEditor()
    await current.model.editor.submit({ title: 'Failed save', blocks: [] })
    expect(toasts.showErrorToast).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'save unavailable' }),
      expect.objectContaining({ text: 'save unavailable' })
    )
  })

  it('resets identity-specific overlays and closes transient surfaces', () => {
    const subject = createSubject()
    const cache = usePostMetaCacheStore()
    const clearLikeStatuses = vi.spyOn(cache, 'clearLikeStatuses')
    subject.post.value.liked = true
    subject.post.value.bookmarked = true
    subject.model.report.openDialog()
    subject.model.confirmModeration('top')

    subject.resetForIdentity()
    expect(clearLikeStatuses).toHaveBeenCalledTimes(1)
    expect(subject.post.value).toMatchObject({ liked: false, bookmarked: false })
    expect(subject.model.report.open).toBe(false)
    expect(subject.model.confirmation.open).toBe(false)

    subject.resetForPost()
    subject.dispose()
  })
})

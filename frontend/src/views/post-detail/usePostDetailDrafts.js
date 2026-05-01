import { normalizeOpaqueId } from '../../utils/opaqueId'

export function usePostDetailDrafts(postId, newComment) {
  function safeStorageGet(key) {
    if (typeof window === 'undefined') return ''
    try {
      return window.localStorage.getItem(key) || ''
    } catch {
      return ''
    }
  }

  function safeStorageSet(key, value) {
    if (typeof window === 'undefined') return
    const v = String(value || '')
    try {
      if (!v) window.localStorage.removeItem(key)
      else window.localStorage.setItem(key, v)
    } catch {
      // ignore
    }
  }

  function commentDraftKey() {
    return `community.draft.posts.${String(postId.value || '')}.comment`
  }

  function replyDraftKey(commentId) {
    return `community.draft.posts.${String(postId.value || '')}.reply.${normalizeOpaqueId(commentId)}`
  }

  function setNewComment(v) {
    newComment.value = String(v || '')
    safeStorageSet(commentDraftKey(), newComment.value)
  }

  function setReplyDraft(comment, value) {
    if (!comment) return
    comment._replyDraft = String(value || '')
    safeStorageSet(replyDraftKey(comment.id), comment._replyDraft)
  }

  return {
    safeStorageGet,
    safeStorageSet,
    commentDraftKey,
    replyDraftKey,
    setNewComment,
    setReplyDraft
  }
}

import { normalizeOpaqueId } from '../../utils/opaqueId'

function normalizeComment(raw) {
  const comment = raw || {}
  return {
    id: normalizeOpaqueId(comment.id),
    userId: normalizeOpaqueId(comment.userId),
    postId: normalizeOpaqueId(comment.postId),
    rootCommentId: normalizeOpaqueId(comment.rootCommentId),
    parentCommentId: normalizeOpaqueId(comment.parentCommentId),
    replyToUserId: normalizeOpaqueId(comment.replyToUserId),
    content: comment.content == null ? '' : String(comment.content),
    createTime: comment.createTime,
    updateTime: comment.updateTime,
    editCount: Number(comment.editCount || 0)
  }
}

export function normalizeCommentPage(raw) {
  const page = raw && typeof raw === 'object' ? raw : {}
  return {
    items: Array.isArray(page.items) ? page.items.map(normalizeComment) : [],
    nextCursor: page.nextCursor == null ? '' : String(page.nextCursor)
  }
}

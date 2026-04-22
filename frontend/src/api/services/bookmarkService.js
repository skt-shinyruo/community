// 收藏相关 API：收藏/取消收藏 + 我的收藏列表。

import http from '../http'
import { unwrapResultBody } from '../result'
import { requireOpaqueId } from '../../utils/opaqueId'

export async function bookmarkPost(postId) {
  const pid = requireOpaqueId(postId, 'postId')
  const resp = await http.put(`/api/posts/${pid}/bookmark`)
  const { traceId } = unwrapResultBody(resp.data, '收藏')
  return { traceId }
}

export async function unbookmarkPost(postId) {
  const pid = requireOpaqueId(postId, 'postId')
  const resp = await http.delete(`/api/posts/${pid}/bookmark`)
  const { traceId } = unwrapResultBody(resp.data, '取消收藏')
  return { traceId }
}

export async function listBookmarks({ page = 0, size = 10 } = {}) {
  const resp = await http.get('/api/bookmarks', { params: { page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询收藏列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

// 搜索相关 API：帖子搜索与索引重建（管理员）。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function searchPosts({ keyword = '', categoryId, tag, page = 0, size = 10 } = {}) {
  const params = { keyword, page, size }
  if (categoryId != null && Number(categoryId) > 0) params.categoryId = Number(categoryId)
  if (tag != null && String(tag).trim()) params.tag = String(tag).trim()
  const resp = await http.get('/api/search/posts', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '搜索')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function reindex() {
  const resp = await http.post('/api/search/internal/reindex')
  const { data, traceId } = unwrapResultBody(resp.data, '重建索引')
  return { data, traceId }
}

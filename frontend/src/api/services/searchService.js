// 搜索相关 API：帖子搜索。

import http from '../http'
import { unwrapResultBody } from '../result'
import { normalizeOpaqueId } from '../../utils/opaqueId'

export async function searchPosts({ keyword = '', categoryId, tag, page = 0, size = 10 } = {}) {
  const params = { keyword, page, size }
  {
    const cid = normalizeOpaqueId(categoryId)
    if (cid) params.categoryId = cid
  }
  if (tag != null && String(tag).trim()) params.tag = String(tag).trim()
  const resp = await http.get('/api/search/posts', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '搜索')
  return { data: Array.isArray(data) ? data : [], traceId }
}

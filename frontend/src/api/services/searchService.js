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

export async function reindex({ opsToken = '' } = {}) {
  const headers = {}
  const t = String(opsToken || '').trim()
  if (t) headers['X-Ops-Token'] = t

  try {
    const resp = await http.post('/api/ops/search/reindex', null, { headers })
    const { data, traceId } = unwrapResultBody(resp.data, '重建索引')
    return { data, traceId }
  } catch (e) {
    // 兼容历史入口：保留短期回退，避免前后端发布顺序导致 404。
    // 注意：该接口属于高风险运维入口，生产通常需要额外 ops-token/allowlist 才允许执行。
    if (e && e.response && e.response.status === 404) {
      const resp = await http.post('/api/search/internal/reindex', null, { headers })
      const { data, traceId } = unwrapResultBody(resp.data, '重建索引')
      return { data, traceId }
    }
    throw e
  }
}

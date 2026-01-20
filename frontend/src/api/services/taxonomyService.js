// taxonomy API：分类（categories）与标签（tags）。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function listCategories() {
  const resp = await http.get('/api/categories')
  const { data, traceId } = unwrapResultBody(resp.data, '查询分类列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listHotTags({ limit = 8 } = {}) {
  const resp = await http.get('/api/tags/hot', { params: { limit } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询热门标签')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function suggestTags({ q = '', limit = 8 } = {}) {
  const keyword = String(q || '').trim()
  const resp = await http.get('/api/tags/suggest', { params: { q: keyword, limit } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询标签建议')
  return { data: Array.isArray(data) ? data : [], traceId }
}

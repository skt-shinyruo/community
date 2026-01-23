// 订阅相关 API：订阅分类（MVP）+ 查询订阅列表。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function subscribeCategory(categoryId) {
  const cid = Number(categoryId || 0)
  const resp = await http.put(`/api/categories/${cid}/subscribe`)
  const { traceId } = unwrapResultBody(resp.data, '订阅分类')
  return { traceId }
}

export async function unsubscribeCategory(categoryId) {
  const cid = Number(categoryId || 0)
  const resp = await http.delete(`/api/categories/${cid}/subscribe`)
  const { traceId } = unwrapResultBody(resp.data, '取消订阅分类')
  return { traceId }
}

export async function listSubscribedCategories() {
  const resp = await http.get('/api/subscriptions/categories')
  const { data, traceId } = unwrapResultBody(resp.data, '查询订阅分类')
  return { data: Array.isArray(data) ? data : [], traceId }
}


// 订阅相关 API：查询订阅列表。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function listSubscribedCategories() {
  const resp = await http.get('/api/subscriptions/categories')
  const { data, traceId } = unwrapResultBody(resp.data, '查询订阅分类')
  return { data: Array.isArray(data) ? data : [], traceId }
}

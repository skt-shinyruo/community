// 统计相关 API：UV/DAU（仅 ADMIN/MODERATOR 可访问）。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function uv({ start, end }) {
  const resp = await http.get('/api/analytics/uv', { params: { start, end } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询 UV')
  return { data, traceId }
}

export async function dau({ start, end }) {
  const resp = await http.get('/api/analytics/dau', { params: { start, end } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询 DAU')
  return { data, traceId }
}


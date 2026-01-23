// 成长体系：榜单 API（按积分排序）。

import http from '../http'
import { unwrapResultBody } from '../result'

export async function getLeaderboard({ limit = 50 } = {}) {
  const resp = await http.get('/api/users/leaderboard', { params: { limit } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询排行榜')
  return { data: Array.isArray(data) ? data : [], traceId }
}


// 通知相关 API：汇总、列表、未读、标记已读。

import http from '../http'
import { unwrapResultBody } from '../result'

/**
 * @param {{ silent?: boolean }} [options] silent 时后台刷新不弹全局错误 toast。
 */
export async function topicSummary(options = {}) {
  const silent = options?.silent === true
  const config = /** @type {import('axios').AxiosRequestConfig} */ ({ skipGlobalErrorToast: silent })
  const resp = await http.get('/api/notices/summary', config)
  const { data, traceId } = unwrapResultBody(resp.data, '查询通知汇总')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listNotices(topic, { page = 0, size = 10 } = {}) {
  const resp = await http.get('/api/notices', { params: { topic, page, size } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询通知')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function markRead(ids) {
  const resp = await http.put('/api/notices/read', { ids })
  const { traceId } = unwrapResultBody(resp.data, '通知标记已读')
  return { traceId }
}

// 拉黑/屏蔽相关 API：拉黑/解除拉黑/列表。

import http from '../http'
import { unwrapResultBody } from '../result'
import { requireOpaqueId } from '../../utils/opaqueId'

export async function blockUser(userId) {
  const uid = requireOpaqueId(userId, 'userId')
  const resp = await http.post('/api/blocks', { userId: uid })
  const { traceId } = unwrapResultBody(resp.data, '屏蔽用户')
  return { traceId }
}

export async function unblockUser(userId) {
  const uid = requireOpaqueId(userId, 'userId')
  const resp = await http.delete('/api/blocks', { params: { userId: uid } })
  const { traceId } = unwrapResultBody(resp.data, '解除屏蔽')
  return { traceId }
}

/**
 * @param {{ silent?: boolean }} [options] silent 时后台刷新不弹全局错误 toast。
 */
export async function listBlockedUsers(options = {}) {
  const silent = options?.silent === true
  const config = /** @type {import('axios').AxiosRequestConfig} */ ({ skipGlobalErrorToast: silent })
  const resp = await http.get('/api/blocks', config)
  const { data, traceId } = unwrapResultBody(resp.data, '查询屏蔽列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

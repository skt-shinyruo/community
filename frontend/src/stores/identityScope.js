// 身份作用域：只有真实身份切换（登出 / 换账号）才变化，以此为键的缓存与视图状态随之整体失效。
// access token 轮换（401 静默刷新）不改变作用域：tokenGeneration 只服务凭证并发协调，
// 视图与缓存监听本函数返回的身份作用域，轮换期间草稿、列表与滚动位置保持不变。
import { normalizeOpaqueId } from '../utils/opaqueId'

export function identityScope(auth) {
  return `${Number(auth?.identityEpoch || 0)}:${normalizeOpaqueId(auth?.identityUserId)}`
}

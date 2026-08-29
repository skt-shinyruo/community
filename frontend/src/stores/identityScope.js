// 会话身份标识：tokenGeneration 变化（换会话/重新登录）时，以此为键的缓存数据需要整体失效。
import { normalizeOpaqueId } from '../utils/opaqueId'

export function identityScope(auth) {
  return `${Number(auth?.tokenGeneration || 0)}:${normalizeOpaqueId(auth?.userId)}`
}

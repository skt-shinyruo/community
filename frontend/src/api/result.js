// 统一解析后端 Result<T>，并在前端以异常形式处理业务错误（code != 0）。
// 该模块不依赖 router/store，避免循环依赖。

export class BusinessError extends Error {
  /**
   * @param {string} message
   * @param {{ code?: unknown, traceId?: unknown, data?: unknown }} [details]
   */
  constructor(message, { code, traceId, data } = {}) {
    super(message || '请求失败')
    this.name = 'BusinessError'
    this.code = typeof code === 'number' ? code : -1
    this.traceId = traceId || ''
    this.data = data
  }
}

/**
 * @param {{ code?: unknown, message?: unknown, traceId?: unknown, data?: unknown } | null | undefined} body
 * @param {string} [hint]
 */
export function unwrapResultBody(body, hint = '') {
  const code = body?.code
  const message = typeof body?.message === 'string' ? body.message : ''
  const traceId = typeof body?.traceId === 'string' ? body.traceId : ''
  const data = body?.data

  if (code === 0) {
    return { data, traceId }
  }

  const msg = message || (hint ? `${hint} 失败` : '请求失败')
  throw new BusinessError(msg, { code, traceId, data })
}

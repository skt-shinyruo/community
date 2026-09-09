/**
 * 发送帧写入 socket 后等待 committed / reject 回执的兜底时限。
 * 连接在写入后立刻死亡且服务端从未收到（休眠 / 断网瞬间）时不会有任何回执，
 * 超时仍未决的发送视为失联并转为失败态，开放既有的手动重试入口。
 */
export const PENDING_SEND_TIMEOUT_MS = 10_000

/**
 * Pending 发送的回执兜底计时器集合：每个在途 clientMsgId 一个 timer。
 * committed / reject 回执确认后由调用方 disarm；会话切换 / 卸载时 disarmAll。
 * 重连 backfill 不直接 disarm：它把 clientMsgId 移出 pending 集合，
 * 迟到的超时回调因此落空。超时仍未决才回调 onTimeout，由调用方转失败态。
 * @param {object} options
 * @param {(clientMsgId: string) => void} options.onTimeout
 */
export function createPendingSendTimers({ onTimeout }) {
  const timers = new Map(/** @type {Array<[string, ReturnType<typeof setTimeout>]>} */ ([]))

  /** @param {string} clientMsgId */
  function disarm(clientMsgId) {
    const timer = timers.get(clientMsgId)
    if (timer === undefined) return
    clearTimeout(timer)
    timers.delete(clientMsgId)
  }

  /** @param {string} clientMsgId */
  function arm(clientMsgId) {
    disarm(clientMsgId)
    timers.set(clientMsgId, setTimeout(() => {
      timers.delete(clientMsgId)
      onTimeout(clientMsgId)
    }, PENDING_SEND_TIMEOUT_MS))
  }

  function disarmAll() {
    for (const timer of timers.values()) clearTimeout(timer)
    timers.clear()
  }

  return { arm, disarm, disarmAll }
}

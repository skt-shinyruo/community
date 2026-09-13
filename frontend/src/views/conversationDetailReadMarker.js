import { markImConversationRead } from '../api/services/imCoreChatService'
import { useInboxUnreadStore } from '../stores/inboxUnread'
import { advanceConversationSeqWaterline } from './conversationDetailState'

/**
 * 私信已读上报的连续 seq 水位：只在已知消息连续覆盖时推进。
 * 帧乱序（先到大 seq、中间缺失）不把未收到的消息提前标读；缺口被后续帧补齐后一次性推进。
 */
export function createConversationReadMarker() {
  let waterline = 0

  function reset() {
    waterline = 0
  }

  /** 已读落库后调度壳层未读角标刷新（防抖合并高频帧）；已读与角标失败都静默，不影响会话流程。 */
  async function report(conversationId, lastReadSeq) {
    try {
      await markImConversationRead(conversationId, lastReadSeq)
      useInboxUnreadStore().scheduleRefresh()
    } catch {}
  }

  /**
   * 以 HTTP 历史页确认的连续水位为锚重定基线并上报当前水位：
   * 加载期间乱序到达的实时帧只在连续覆盖时推进，缺口之后的消息不提前标读。
   */
  async function anchorAndReport(conversationId, confirmedWaterline, items) {
    if (Number.isSafeInteger(confirmedWaterline) && confirmedWaterline > waterline) {
      waterline = advanceConversationSeqWaterline(confirmedWaterline, items)
    }
    if (waterline > 0) await report(conversationId, waterline)
  }

  /** 实时帧合并后连续推进水位；只有推进才上报。 */
  async function advanceAndReport(conversationId, items) {
    const next = advanceConversationSeqWaterline(waterline, items)
    if (next <= waterline) return
    waterline = next
    await report(conversationId, next)
  }

  return { reset, anchorAndReport, advanceAndReport }
}
